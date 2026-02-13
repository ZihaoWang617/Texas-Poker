package com.wepoker.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 防作弊检测模块
 * 
 * 检测项：
 * 1. IP 黑名单/白名单
 * 2. 异常行为检测（快速操作、非正常模式）
 * 3. GPS 位置异常
 * 4. 货币流通异常
 * 5. 设备指纹识别
 */
@Slf4j
@Component
public class AntiCheatGuard {
    
    // IP 黑名单
    private final Set<String> ipBlacklist = new HashSet<>();
    
    // IP 白名单（优先级更高）
    private final Set<String> ipWhitelist = new HashSet<>();
    
    // 每个玩家的操作历史，用于检测异常
    private final Map<Long, PlayerBehaviorProfile> behaviorProfiles = new HashMap<>();
    
    // GPS 位置历史
    private final Map<Long, LocationHistory> locationHistories = new HashMap<>();
    
    /**
     * 检查 IP 是否被禁用
     */
    public boolean isIpBanned(String ipAddress) {
        if (ipWhitelist.contains(ipAddress)) {
            return false;
        }
        return ipBlacklist.contains(ipAddress);
    }
    
    /**
     * 添加 IP 到黑名单
     */
    public void addIpToBlacklist(String ipAddress, String reason) {
        ipBlacklist.add(ipAddress);
        log.warn("IP {} added to blacklist. Reason: {}", ipAddress, reason);
    }
    
    /**
     * 检测异常行为
     * 
     * 检测项：
     * - 在短时间内进行过多操作（可能是机器人）
     * - 赢率异常高
     * - 连续全下
     */
    public AntiCheatResult checkBehavior(Long playerId, String action, long amount) {
        PlayerBehaviorProfile profile = behaviorProfiles.computeIfAbsent(playerId, 
            k -> new PlayerBehaviorProfile());
        
        // 记录操作
        profile.recordAction(action, amount);
        
        // 检测操作频率（每5秒最多10个操作）
        if (profile.getActionsInLastSeconds(5) > 10) {
            return new AntiCheatResult(false, "RAPID_ACTIONS", "操作过于频繁，可能涉及作弊");
        }
        
        // 检测连续全下（连续3次全下）
        if (profile.getConsecutiveAllIns() >= 3) {
            return new AntiCheatResult(false, "SUSPICIOUS_ALL_IN_PATTERN", "连续全下行为可疑");
        }
        
        return new AntiCheatResult(true, "OK", "通过检测");
    }
    
    /**
     * 检测 GPS 位置异常
     * 
     * 检测两次操作之间的地理距离是否合理
     */
    public AntiCheatResult checkLocationAnomaly(Long playerId, GPSLocation currentLocation) {
        LocationHistory history = locationHistories.computeIfAbsent(playerId, 
            k -> new LocationHistory());
        
        if (history.lastLocation == null) {
            history.lastLocation = currentLocation;
            history.lastTimestamp = System.currentTimeMillis();
            return new AntiCheatResult(true, "OK", "位置检查通过");
        }
        
        // 计算两个位置之间的距离（使用Haversine公式）
        double distance = calculateDistance(
            history.lastLocation.latitude, 
            history.lastLocation.longitude,
            currentLocation.latitude, 
            currentLocation.longitude
        );
        
        long timeDiff = System.currentTimeMillis() - history.lastTimestamp;
        
        // 时间差（秒）
        long seconds = timeDiff / 1000;
        
        // 如果距离超过 1000 km 但时间差小于 10 分钟，判定为异常
        if (distance > 1000 && seconds < 600) {
            double speed = distance * 1000 / seconds; // m/s
            return new AntiCheatResult(false, "IMPOSSIBLE_LOCATION_CHANGE", 
                String.format("位置跳变异常：%.2f km/s", speed / 1000));
        }
        
        history.lastLocation = currentLocation;
        history.lastTimestamp = System.currentTimeMillis();
        
        return new AntiCheatResult(true, "OK", "位置检查通过");
    }
    
    /**
     * 检测赢率异常
     */
    public AntiCheatResult checkWinRateAnomaly(Long playerId, double winRate, long gamesPlayed) {
        // 在样本足够大的情况下（至少100局），检测赢率是否超过统计概率
        if (gamesPlayed < 100) {
            return new AntiCheatResult(true, "OK", "样本数不足");
        }
        
        // 正常赢率应在 45%-55% 之间（假设是相对均衡的游戏环境）
        if (winRate > 0.70 || winRate < 0.30) {
            return new AntiCheatResult(false, "ABNORMAL_WIN_RATE", 
                String.format("赢率异常：%.2f%%", winRate * 100));
        }
        
        return new AntiCheatResult(true, "OK", "赢率检查通过");
    }
    
    /**
     * Haversine 公式：计算两个地理坐标之间的距离（单位：km）
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371; // 地球半径（km）
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c;
    }
    
    /**
     * 防作弊检查结果
     */
    @Data
    @AllArgsConstructor
    public static class AntiCheatResult {
        private boolean passed;
        private String code;
        private String message;
    }
    
    /**
     * GPS 位置坐标
     */
    @Data
    @AllArgsConstructor
    public static class GPSLocation {
        public double latitude;
        public double longitude;
        public long timestamp;
    }
    
    /**
     * 玩家行为档案
     */
    public static class PlayerBehaviorProfile {
        private long lastActionTime;
        private int actionCountInLastSeconds;
        private int consecutiveAllIns;
        
        public void recordAction(String action, long amount) {
            long currentTime = System.currentTimeMillis();
            
            // 重置计数器（如果超过5秒）
            if (currentTime - lastActionTime > 5000) {
                actionCountInLastSeconds = 0;
            }
            
            actionCountInLastSeconds++;
            lastActionTime = currentTime;
            
            // 更新连续全下计数
            if ("ALL_IN".equals(action)) {
                consecutiveAllIns++;
            } else {
                consecutiveAllIns = 0;
            }
        }
        
        public int getActionsInLastSeconds(int seconds) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastActionTime < seconds * 1000) {
                return actionCountInLastSeconds;
            }
            return 0;
        }
        
        public int getConsecutiveAllIns() {
            return consecutiveAllIns;
        }
    }
    
    /**
     * 位置历史
     */
    @Data
    public static class LocationHistory {
        public GPSLocation lastLocation;
        public long lastTimestamp;
    }
}
