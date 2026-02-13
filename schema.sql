-- WePoker 数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS wepoker CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wepoker;

-- 玩家账户表
CREATE TABLE IF NOT EXISTS player (
    player_id BIGINT PRIMARY KEY COMMENT '玩家ID',
    username VARCHAR(100) UNIQUE NOT NULL COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    nickname VARCHAR(100) NOT NULL COMMENT '昵称',
    avatar_url VARCHAR(500) COMMENT '头像URL',
    total_balance BIGINT DEFAULT 0 NOT NULL COMMENT '总余额（分为单位）',
    status ENUM('ACTIVE', 'SUSPENDED', 'BANNED') DEFAULT 'ACTIVE' COMMENT '账户状态',
    ip_address VARCHAR(45) COMMENT 'IP地址',
    device_fingerprint VARCHAR(255) COMMENT '设备指纹',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_username (username),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家账户';

-- 游戏房间表
CREATE TABLE IF NOT EXISTS game_table (
    table_id BIGINT PRIMARY KEY COMMENT '房间ID',
    name VARCHAR(100) NOT NULL COMMENT '房间名称',
    status ENUM('WAITING', 'PLAYING', 'CLOSED') DEFAULT 'WAITING' COMMENT '房间状态',
    small_blind BIGINT NOT NULL COMMENT '小盲注（分为单位）',
    big_blind BIGINT NOT NULL COMMENT '大盲注（分为单位）',
    buy_in_min BIGINT NOT NULL COMMENT '最小买入（分为单位）',
    buy_in_max BIGINT NOT NULL COMMENT '最大买入（分为单位）',
    max_players INT DEFAULT 6 COMMENT '最大玩家数',
    rake_percentage DOUBLE DEFAULT 0.05 COMMENT '抽水百分比',
    max_rake BIGINT COMMENT '最高抽水（分为单位）',
    total_pot BIGINT DEFAULT 0 COMMENT '总底池（分为单位）',
    current_game_id BIGINT COMMENT '当前游戏ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_status (status),
    KEY idx_blind (small_blind, big_blind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏房间';

-- 单局游戏记录表
CREATE TABLE IF NOT EXISTS game_round (
    game_id BIGINT PRIMARY KEY COMMENT '游戏ID',
    table_id BIGINT NOT NULL COMMENT '房间ID',
    game_number INT NOT NULL COMMENT '第几局',
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time TIMESTAMP COMMENT '结束时间',
    final_state ENUM('WAITING', 'DEALING', 'PRE_FLOP', 'FLOP', 'TURN', 'RIVER', 'SHOWDOWN', 'CLEANUP') COMMENT '最终状态',
    total_pot BIGINT COMMENT '最终底池',
    winner_id BIGINT COMMENT '赢家ID',
    winner_amount BIGINT COMMENT '赢得金额',
    FOREIGN KEY (table_id) REFERENCES game_table(table_id),
    FOREIGN KEY (winner_id) REFERENCES player(player_id),
    KEY idx_table_id (table_id),
    KEY idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='单局游戏记录';

-- 玩家操作记录表
CREATE TABLE IF NOT EXISTS player_action (
    action_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '操作ID',
    game_id BIGINT NOT NULL COMMENT '游戏ID',
    player_id BIGINT NOT NULL COMMENT '玩家ID',
    action_type ENUM('FOLD', 'CHECK', 'CALL', 'BET', 'RAISE', 'ALL_IN') NOT NULL COMMENT '操作类型',
    amount BIGINT COMMENT '投注金额（分为单位）',
    action_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    street ENUM('PRE_FLOP', 'FLOP', 'TURN', 'RIVER') COMMENT '操作轮次',
    FOREIGN KEY (game_id) REFERENCES game_round(game_id),
    FOREIGN KEY (player_id) REFERENCES player(player_id),
    KEY idx_game_id (game_id),
    KEY idx_player_id (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='玩家操作记录';

-- 金额交易表
CREATE TABLE IF NOT EXISTS transaction (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '交易ID',
    player_id BIGINT NOT NULL COMMENT '玩家ID',
    game_id BIGINT COMMENT '关联游戏ID（可为空）',
    type ENUM('BUY_IN', 'CASH_OUT', 'WIN', 'LOSE', 'RAKE', 'BONUS', 'PENALTY') NOT NULL COMMENT '交易类型',
    amount BIGINT NOT NULL COMMENT '金额（分为单位）',
    balance_before BIGINT COMMENT '交易前余额',
    balance_after BIGINT COMMENT '交易后余额',
    description VARCHAR(500) COMMENT '描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '交易时间',
    FOREIGN KEY (player_id) REFERENCES player(player_id),
    FOREIGN KEY (game_id) REFERENCES game_round(game_id),
    KEY idx_player_id (player_id),
    KEY idx_type (type),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='金额交易记录';

-- 防作弊记录表
CREATE TABLE IF NOT EXISTS anti_cheat_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    player_id BIGINT COMMENT '玩家ID',
    ip_address VARCHAR(45) COMMENT 'IP地址',
    check_type ENUM('IP_BLACKLIST', 'BEHAVIOR_ANALYSIS', 'GPS_ANOMALY', 'WIN_RATE') NOT NULL COMMENT '检测类型',
    result ENUM('PASSED', 'FLAGGED', 'BANNED') NOT NULL COMMENT '检查结果',
    details JSON COMMENT '详细信息（JSON）',
    action_taken VARCHAR(255) COMMENT '采取的措施',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES player(player_id),
    KEY idx_player_id (player_id),
    KEY idx_result (result),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='防作弊检查日志';

-- 索引优化
CREATE INDEX idx_player_balance ON player(total_balance);
CREATE INDEX idx_game_table ON game_round(table_id, start_time);
CREATE INDEX idx_transaction_player ON transaction(player_id, created_at);

-- 初始化测试数据（可选）
-- INSERT INTO player (player_id, username, password_hash, nickname, total_balance) 
-- VALUES (1, 'player1', 'hash1', '玩家1', 1000000);
