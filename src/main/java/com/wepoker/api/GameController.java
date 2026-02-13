package com.wepoker.api;

import com.wepoker.domain.model.Table;
import com.wepoker.service.GameService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 游戏 REST API 控制器
 * 
 * 提供：
 * - 房间信息查询
 * - 玩家统计
 * - 系统状态
 */
@RestController
@RequestMapping("/api/game")
public class GameController {
    
    @Autowired
    private GameService gameService;
    
    /**
     * 获取所有活跃房间
     */
    @GetMapping("/tables")
    public ResponseEntity<?> getTables() {
        Collection<Table> tables = gameService.getAllTables();
        return ResponseEntity.ok(new ApiResponse(200, "success", tables));
    }
    
    /**
     * 获取指定房间信息
     */
    @GetMapping("/tables/{tableId}")
    public ResponseEntity<?> getTable(@PathVariable Long tableId) {
        Table table = gameService.getTable(tableId);
        if (table == null) {
            return ResponseEntity.ok(new ApiResponse(404, "Table not found", null));
        }
        return ResponseEntity.ok(new ApiResponse(200, "success", table));
    }

    /**
     * 房间状态（别名接口，便于前端轮询）
     */
    @GetMapping("/tables/{tableId}/state")
    public ResponseEntity<?> getTableState(
        @PathVariable Long tableId,
        @RequestParam(value = "playerId", required = false) String playerId
    ) {
        Map<String, Object> view = gameService.getTableView(tableId, playerId);
        if (view == null) {
            return ResponseEntity.ok(new ApiResponse(404, "Table not found", null));
        }
        return ResponseEntity.ok(new ApiResponse(200, "success", view));
    }

    /**
     * 加入房间
     */
    @PostMapping("/tables/{tableId}/join")
    public ResponseEntity<?> joinTable(@PathVariable Long tableId, @RequestBody JoinTableRequest request) {
        String playerId = request.getPlayerId();
        if (playerId == null || playerId.isBlank()) {
            playerId = String.valueOf(System.currentTimeMillis());
        }

        try {
            Table table = gameService.joinTable(
                tableId,
                playerId,
                request.getNickname(),
                request.getBuyIn()
            );
            return ResponseEntity.ok(new ApiResponse(200, "success", table));
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(400, e.getMessage(), null));
        }
    }

    /**
     * 开始游戏
     */
    @PostMapping("/tables/{tableId}/start")
    public ResponseEntity<?> startGame(@PathVariable Long tableId) {
        try {
            Table table = gameService.startGame(tableId);
            return ResponseEntity.ok(new ApiResponse(200, "success", table));
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(400, e.getMessage(), null));
        }
    }

    /**
     * 玩家行动
     */
    @PostMapping("/tables/{tableId}/action")
    public ResponseEntity<?> action(@PathVariable Long tableId, @RequestBody ActionRequest request) {
        try {
            Table table = gameService.playerAction(tableId, request.getPlayerId(), request.getAction(), request.getAmount());
            return ResponseEntity.ok(new ApiResponse(200, "success", table));
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(400, e.getMessage(), null));
        }
    }

    @PostMapping("/tables/{tableId}/rebuy")
    public ResponseEntity<?> rebuy(@PathVariable Long tableId, @RequestBody RebuyRequest request) {
        try {
            Table table = gameService.rebuy(tableId, request.getPlayerId(), request.getAmount());
            return ResponseEntity.ok(new ApiResponse(200, "success", table));
        } catch (Exception e) {
            return ResponseEntity.ok(new ApiResponse(400, e.getMessage(), null));
        }
    }
    
    /**
     * 获取系统统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        Collection<Table> tables = gameService.getAllTables();
        
        int totalTables = tables.size();
        int totalPlayers = tables.stream().mapToInt(t -> t.getPlayers().size()).sum();
        long totalPot = tables.stream().mapToLong(Table::getTotalPot).sum();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTables", totalTables);
        stats.put("totalPlayers", totalPlayers);
        stats.put("totalPot", totalPot);
        
        return ResponseEntity.ok(new ApiResponse(200, "success", stats));
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(new ApiResponse(200, "OK", null));
    }

    /**
     * 获取可供局域网分享的地址信息
     */
    @GetMapping("/network-info")
    public ResponseEntity<?> networkInfo(HttpServletRequest request) {
        Map<String, Object> data = new HashMap<>();
        String lanIp = detectLanIpv4();
        data.put("lanIp", lanIp);
        data.put("serverPort", request.getServerPort());
        data.put("scheme", request.getScheme());
        return ResponseEntity.ok(new ApiResponse(200, "success", data));
    }

    private String detectLanIpv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    /**
     * API 响应包装类
     */
    @Data
    @AllArgsConstructor
    public static class ApiResponse {
        private int code;
        private String message;
        private Object data;
    }

    @Data
    public static class JoinTableRequest {
        private String playerId;
        private String nickname;
        private long buyIn;
    }

    @Data
    public static class ActionRequest {
        private String playerId;
        private String action;
        private long amount;
    }

    @Data
    public static class RebuyRequest {
        private String playerId;
        private long amount;
    }
}
