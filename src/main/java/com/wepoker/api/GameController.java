package com.wepoker.api;

import com.wepoker.domain.model.Table;
import com.wepoker.service.GameService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * API 响应包装类
     */
    @Data
    @AllArgsConstructor
    public static class ApiResponse {
        private int code;
        private String message;
        private Object data;
    }
}
