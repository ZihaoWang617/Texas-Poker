# WePoker 德州扑克后端系统 - 完整架构设计文档

## 目录
1. [系统概览](#系统概览)
2. [技术栈](#技术栈)
3. [架构设计](#架构设计)
4. [核心业务逻辑](#核心业务逻辑)
5. [并发与安全](#并发与安全)
6. [网络通信](#网络通信)
7. [防作弊机制](#防作弊机制)
8. [API 文档](#api-文档)
9. [部署与扩展](#部署与扩展)

---

## 系统概览

### 目标
实现一个企业级、高并发的德州扑克后端系统，支持：
- **高并发**：使用 Netty 和 Java 虚拟线程处理数十万+并发连接
- **实时通信**：长连接 + 心跳机制 + 断线重连
- **复杂游戏逻辑**：多人 All-in、边池计算、Run It Twice
- **防作弊**：IP 黑名单、行为分析、地理位置检测
- **金额安全**：使用 long 类型，避免精度问题

### 核心特性
- ✅ 真随机发牌（Fisher-Yates 算法）
- ✅ 复杂的底池与边池管理
- ✅ 动作倒计时（支持延时）
- ✅ 断线重连机制
- ✅ 实时状态同步
- ✅ 完整的防作弊系统

---

## 技术栈

### 后端框架
```
Java 21 + Spring Boot 3.2.1
│
├── Network Layer
│   ├── Netty 4.1.104 (长连接、高并发)
│   ├── 自定义 JSON 协议（编码/解码）
│   └── 心跳 + 超时机制
│
├── Game Logic
│   ├── 状态机管理
│   ├── 动作定时器
│   ├── 牌型计算
│   └── 底池管理
│
├── Persistence
│   ├── Redis (实时状态、会话)
│   ├── MySQL (持久化数据)
│   └── Spring Data JPA
│
└── Security
    ├── 防作弊系统
    ├── IP 黑名单
    └── 行为检测
```

### 依赖库
- **Spring Boot 3.2.1**：框架核心
- **Netty 4.1.104**：网络通信
- **Jackson**：JSON 序列化
- **Redis**：缓存 + 实时状态
- **MySQL 8.0**：持久化存储
- **Lombok**：减少样板代码
- **Log4j 2**：日志系统

---

## 架构设计

### 分层架构

```
┌─────────────────────────────────────────┐
│           客户端 (Electron/Web)          │
└──────────────────┬──────────────────────┘
                   │ Netty 长连接
┌──────────────────▼──────────────────────┐
│       Network Layer (Netty)    │
│  ├─ PokerMessageDecoder                 │
│  ├─ PokerMessageEncoder                 │
│  └─ PokerGameHandler                    │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│        API Layer (Spring MVC)           │
│  ├─ GameController                      │
│  └─ PlayerController                    │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│       Service Layer                     │
│  ├─ GameService                         │
│  ├─ PlayerService                       │
│  └─ TransactionService                  │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│      Domain Logic Layer                 │
│  ├─ GameStateMachine                    │
│  ├─ HandEvaluator                       │
│  ├─ PotManager                          │
│  ├─ DealerService                       │
│  ├─ ActionTimer                         │
│  └─ ConcurrencyGuard                    │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│   Infrastructure Layer                  │
│  ├─ Redis Repository                    │
│  ├─ MySQL Repository                    │
│  ├─ AntiCheatGuard                      │
│  └─ ConcurrencyGuard                    │
└─────────────────────────────────────────┘
```

### 核心类关系图

```
Table (房间)
  │
  ├─ List<Player> players
  │   └─ Player
  │       ├─ long playerId
  │       ├─ long stack (筹码)
  │       ├─ Hand holeCards (底牌)
  │       └─ PlayerStatus status
  │
  ├─ PotManager potManager
  │   ├─ long mainPot
  │   ├─ List<SidePot> sidePots
  │   └─ Map<Long, Long> playerContributions
  │
  ├─ GameStateMachine stateMachine
  │   └─ GameState currentState
  │
  ├─ ActionTimer actionTimer
  │   └─ Map<Long, RemainingTime> playerTimeouts
  │
  └─ DealerService dealer
      └─ Deck deck
```

---

## 核心业务逻辑

### 1. 发牌流程

#### Fisher-Yates 随机洗牌
```java
// src/main/java/com/wepoker/domain/service/DealerService.java

private void shuffleDeck(List<Card> deck) {
    SecureRandom rand = new SecureRandom();
    for (int i = deck.size() - 1; i > 0; i--) {
        int j = rand.nextInt(i + 1);
        // 交换
        Collections.swap(deck, i, j);
    }
}

// 时间复杂度：O(n)
// 真随机性：不依赖系统时间或简单伪随机
```

### 2. 牌型评分（HandEvaluator）

评分系统使用 **Rank** 值（0-7462）表示牌力强度：
- **0-1279**：Straight Flush（同花顺，最强）
- **1280-1599**：Four of a Kind（四条）
- **1600-2467**：Full House（葫芦）
- **2468-2859**：Flush（同花）
- **2860-3325**：Straight（顺子）
- **3326-6185**：Three of a Kind（三条）
- **6186-6858**：Two Pair（两对）
- **6859-7140**：One Pair（一对）
- **7141-7462**：High Card（高牌，最弱）

```java
// 7张牌选5张的最佳组合
public int evaluate(List<Card> cards) {
    int bestRank = Integer.MAX_VALUE;
    
    // C(7,5) = 21 种组合
    for (List<Card> combo : combinations(cards, 5)) {
        int rank = evaluateFiveCards(combo);
        bestRank = Math.min(bestRank, rank);
    }
    
    return bestRank; // 数值越小，牌力越强
}
```

### 3. 底池与边池管理（PotManager）

#### All-in 场景示例
```
玩家 A: 有 $50
玩家 B: 有 $100
玩家 C: 有 $200

第1轮底注：都投 $50

第2轮：
- A 全下 $50 → 主底池 $150 (A已全部投入)
- B 跟 $50 → 主底池 $200
- C 跟 $50 → 主底池 $250

第3轮：
- B 下注 $50 → 边池 $100 (B, C 参与)
- C 跟 $50 → 边池 $200

结算：
- 主底池 ($250)：A最多赢回所有投入，由 A/B/C 中最强手赢取
- 边池 ($200)：只有 B/C 参与，B/C 中最强手赢取
```

```java
/**
 * 处理多个 All-in 的复杂底池分配
 */
public List<Pot> calculateSidePots(List<Player> players) {
    // 1. 按照投入金额排序
    List<Long> investments = getUniqueInvestments();
    
    // 2. 逐层划分底池
    List<Pot> pots = new ArrayList<>();
    long previousAmount = 0;
    
    for (long amount : investments) {
        long potSize = (amount - previousAmount) * playersAtThisLevel;
        pots.add(new Pot(potSize, eligiblePlayers));
        previousAmount = amount;
    }
    
    return pots;
}
```

### 4. Run It Twice（两次发牌）

在 All-in 时，系统可以进行两次河牌发牌，分别计算底池 50/50 分配：

```java
public void runItTwice(Table table, long mainPot) {
    // 第一次运行
    List<Card> boardRun1 = dealRemainingCards();
    int winner1 = evaluateWinner(boardRun1);
    long pot1 = mainPot / 2;
    
    // 第二次运行（重新洗牌）
    List<Card> boardRun2 = dealRemainingCards();
    int winner2 = evaluateWinner(boardRun2);
    long pot2 = mainPot - pot1;
    
    // 结果合并
    distributePots(winner1, winner2, pot1, pot2);
}
```

### 5. 游戏状态机

```
        ┌─────────────┐
        │   Waiting   │← 初始状态（等待玩家）
        └──────┬──────┘
               │ 玩家人数达到要求
        ┌──────▼──────┐
        │   Dealing   │← 发底牌
        └──────┬──────┘
               │ 底牌发完
        ┌──────▼──────┐
        │  Pre-Flop   │← 翻牌前下注
        └──────┬──────┘
               │ 所有活跃玩家行动完成或仅剩1人
        ┌──────▼──────┐
        │    Flop     │← 翻牌（3张社区牌）
        └──────┬──────┘
               │
        ┌──────▼──────┐
        │    Turn     │← 转牌（+1张社区牌）
        └──────┬──────┘
               │
        ┌──────▼──────┐
        │    River    │← 河牌（+1张社区牌）
        └──────┬──────┘
               │
        ┌──────▼──────┐
        │  Showdown   │← 秀牌比大小
        └──────┬──────┘
               │
        ┌──────▼──────┐
        │   Cleanup   │← 结算分配底池
        └──────┬──────┘
               │ 房间继续或结束
        ┌──────▼──────┐
        │   Waiting   │← 回到初始
        └─────────────┘
```

---

## 并发与安全

### 1. 金额精度保证

**禁止使用 double/float，必须使用 long：**

```java
// ❌ 错误
double stack = 100.50; // 精度丢失
double total = stack + 50.25; // 可能 = 150.74999...

// ✅ 正确（以分为单位）
long stackCent = 10050; // 100.50元 = 10050分
long addCent = 5025;    // 50.25元 = 5025分
long totalCent = stackCent + addCent; // 15075 = 150.75元
```

### 2. 并发扣除筹码

使用 **原子操作** 防止"负筹码"或"钱数不对"：

```java
// src/main/java/com/wepoker/domain/concurrency/ConcurrencyGuard.java

public class BetTransaction {
    private volatile long stack; // 当前筹码
    private final Object lock = new Object();
    
    /**
     * 原子的下注操作
     * 不可分割：检查 + 扣除
     */
    public synchronized boolean deductChips(long amount) {
        if (stack < amount) {
            return false; // 筹码不足
        }
        stack -= amount;
        return true;
    }
    
    /**
     * CAS 操作（更高效）
     */
    private final AtomicLong stackAtomic = new AtomicLong(0);
    
    public boolean deductChipsAtomic(long amount) {
        while (true) {
            long current = stackAtomic.get();
            if (current < amount) {
                return false;
            }
            if (stackAtomic.compareAndSet(current, current - amount)) {
                return true;
            }
        }
    }
}
```

### 3. 玩家操作的顺序保证

使用 **序列号** 和 **消息队列** 确保操作顺序性：

```java
// 网络消息结构
public class PokerMessage {
    private int sequenceNumber; // 序列号，从0开始递增
}

// 处理器
private final PriorityQueue<PokerMessage> messageQueue = new PriorityQueue<>(
    Comparator.comparingInt(m -> m.getSequenceNumber())
);

public void handleMessage(PokerMessage msg) {
    synchronized (messageQueue) {
        messageQueue.add(msg);
        
        // 处理已序列化的消息
        while (!messageQueue.isEmpty() && 
               messageQueue.peek().getSequenceNumber() == expectedSequence) {
            PokerMessage next = messageQueue.poll();
            processMessage(next);
            expectedSequence++;
        }
    }
}
```

### 4. Runtime 性能优化

对于 Java 21，使用 **虚拟线程（Virtual Threads）**：

```java
// spring-boot-starter-web 自动支持虚拟线程（需配置）
# application.yml
spring:
  threads:
    virtual:
      enabled: true
      
# 原理：Netty 处理网络 I/O，业务逻辑用虚拟线程
# 可支持 100万+ 并发（而不仅仅是几千）
```

---

## 网络通信

### 连接流程

```
客户端                          服务器
  │                              │
  ├─────────── HANDSHAKE ───────▶│
  │ {playerId, sessionId?}       │
  │                              ├─ 验证身份
  │                              ├─ 生成 sessionId
  │◀─────────────ACK ────────────┤
  │ {sessionId, status: OK}      │
  │                              │
  ├─ 建立长连接 (保持打开) ────────┤
  │                              │
  ├─────────── JOIN_TABLE ──────▶│
  │ {tableId, buyIn}             │
  │                 ...游戏中...   │
  ├─────────────── BET ─────────▶│
  │ {amount}                     │
  │◀─────── GAME_STATE_UPDATE ───┤
  │ {players, pot, action}       │
  │                              │
  ├──── (网络故障，断线) ────X    │
  │                              │
  ├─────────── RECONNECT ───────▶│
  │ {sessionId, playerId}        │
  │                              ├─ 查询 Redis
  │◀──────── GAME_STATE ────────┤ (恢复状态)
  │ {board, stack, ...}         │
  │                              │
```

### 心跳机制

**客户端每 30 秒发送心跳，服务器响应 ACK：**

```java
// 客户端
client.sendHeartbeat();
// → 服务器 IdleStateHandler 配置：
//   读超时 30s、写超时 60s、全超时 90s

// 服务器
if (evt instanceof IdleStateEvent) {
    log.warn("Client inactive, closing connection");
    ctx.close(); // 主动断开
}
```

### 消息序列化格式

使用 **JSON + 换行符** 格式：

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "BET",
  "timestamp": 1707296000000,
  "tableId": 123,
  "playerId": 456,
  "sessionId": "abc-def-ghi",
  "sequenceNumber": 5,
  "payload": {
    "amount": 10000,
    "position": "BTN"
  }
}\n
```

---

## 防作弊机制

### 1. IP 黑名单

```java
public boolean isIpBanned(String ipAddress) {
    if (ipWhitelist.contains(ipAddress)) {
        return false; // 白名单优先级高
    }
    return ipBlacklist.contains(ipAddress);
}
```

### 2. 行为分析

```
异常行为检测：
├─ 操作频率 (RAPID_ACTIONS)
│   └─ 5秒内超过10个操作 → 可能是脚本/机器人
│
├─ 连续全下 (SUSPICIOUS_ALL_IN_PATTERN)
│   └─ 连续3次以上全下 → 高风险行为
│
└─ 快速跟注 (FAST_CALLING)
    └─ 无停顿快速跟注所有对手 → 可能是AI或配对
```

### 3. GPS 位置检测

```
异常位置跳变 (IMPOSSIBLE_LOCATION_CHANGE)：
  
玩家A在北京 (39.9°N, 116.4°E)
  │
  └─ 5分钟后
  │
玩家A出现在上海 (31.2°N, 121.4°E)

距离 ≈ 1000+ km
时间 = 5 分钟 = 300 秒
需要速度 > 3333 m/s ≈ 12000 km/h (超音速 ❌)

判定：异常，可能是位置欺骗 → 禁用账户
```

### 4. 赢率检测

```
正常赢率范围：45% - 55%（相对均衡游戏）

检测条件：
├─ 样本量 ≥ 100 局（防止小样本异常）
├─ 赢率 > 70% → 检测为异常
└─ 赢率 < 30% → 检测为异常（可能是输钱垫子）

处理：提交人工审核 → 禁用或调查
```

---

## API 文档

### REST API

#### 1. 获取所有房间
```http
GET /api/game/tables
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "tableId": 1,
      "maxPlayers": 6,
      "currentPlayers": 4,
      "totalPot": 500000,
      "currentState": "FLOP"
    }
  ]
}
```

#### 2. 获取房间详情
```http
GET /api/game/tables/{tableId}
```

#### 3. 系统统计
```http
GET /api/game/stats
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalTables": 10,
    "totalPlayers": 45,
    "totalPot": 2500000
  }
}
```

#### 4. 健康检查
```http
GET /api/game/health
```

### Netty 事件协议

#### JOIN_TABLE
```json
{
  "type": "JOIN_TABLE",
  "tableId": 1,
  "playerId": 123,
  "payload": {
    "buyIn": 50000,
    "nickname": "Player_123"
  }
}
```

#### BET (or RAISE, CALL, CHECK, FOLD, ALL_IN)
```json
{
  "type": "BET",
  "tableId": 1,
  "playerId": 123,
  "payload": {
    "amount": 10000
  }
}
```

#### GAME_STATE_UPDATE （服务器推送）
```json
{
  "type": "GAME_STATE_UPDATE",
  "tableId": 1,
  "timestamp": 1707296000000,
  "payload": {
    "gameState": "FLOP",
    "board": ["AH", "KD", "QC"],
    "pot": 250000,
    "players": [
      {
        "playerId": 123,
        "stack": 100000,
        "status": "ACTIVE",
        "position": "BTN"
      }
    ],
    "currentPlayerToAct": 124,
    "timeRemaining": 15
  }
}
```

---

## 部署与扩展

### 单机部署

```bash
# 1. 编译
mvn clean package

# 2. 运行
java -server \
  -Xms2g -Xmx2g \
  -XX:+UseG1GC \
  --enable-preview \
  -jar target/texas-poker-1.0.0.jar
```

### 分布式扩展

可通过以下方式提升性能：

#### 1. 水平扩展（多房间服务器）
```
Gateway LB
  │
  ├─ GameServer 1 (房间 1-100)
  ├─ GameServer 2 (房间 101-200)
  └─ GameServer 3 (房间 201-300)
  
  共享层：
  ├─ Redis Cluster (会话 + 实时状态)
  ├─ MySQL Master-Slave (持久化)
  └─ Message Queue (房间间消息)
```

#### 2. 虚拟线程支持（Java 21）
```yaml
spring:
  threads:
    virtual:
      enabled: true
  
# 可在单机支持 100万+ 并发连接
```

#### 3. 缓存优化

```java
// Redis 存储：
// - USER_SESSION:{sessionId} → 玩家信息
// - TABLE_STATE:{tableId} → 房间状态
// - PLAYER_STACK:{playerId} → 实时筹码

// 过期时间：
// - 会话：30 分钟空闲
// - 房间状态：1 分钟（实时更新）
// - 筹码：永不过期（除非玩家离座）
```

---

## 总结

| 特性 | 实现方案 |
|-----|--------|
| 高并发连接 | Netty + 虚拟线程 |
| 实时通信 | 长连接 + 心跳 |
| 断线重连 | sessionId + Redis |
| 金额安全 | long 类型 + 原子操作 |
| 牌型判定 | HandEvaluator (Rank 值) |
| 复杂底池 | PotManager (边池算法) |
| 防作弊 | IP、行为、位置、赢率检测 |
| 扩展性 | 分布式 + Redis + MySQL |

---

## 快速开始

### 本地开发

```bash
# 1. 克隆项目
git clone https://github.com/ZihaoWang617/Texas-Poker.git
cd Texas-Poker

# 2. 启动依赖服务
docker-compose up -d redis mysql

# 3. 运行应用
mvn clean spring-boot:run

# 4. 访问 API
curl http://localhost:8080/api/game/health
```

### 配置文件 （application.yml）

```yaml
spring:
  application:
    name: wepoker
  
  # 虚拟线程
  threads:
    virtual:
      enabled: true
  
  # Redis
  data:
    redis:
      host: localhost
      port: 6379
  
  # MySQL
  datasource:
    url: jdbc:mysql://localhost:3306/wepoker
    username: root
    password: password
  
  jpa:
    hibernate:
      ddl-auto: update

# Netty 配置
wepoker:
  netty:
    host: 0.0.0.0
    port: 9000
    bossThreads: 1
    workerThreads: 8
```

---

**作者：Senior Java Architect**  
**版本：1.0.0**  
**最后更新：2026年2月7日**
