# 🎯 WePoker 项目导航

**欢迎来到 WePoker 德州扑克平台！** 

本文档帮助您快速找到所需的信息。

---

## 🚀 我想... (快速导航)

### 我想快速启动游戏

👉 **最快 5 分钟！**

1. 不要看任何文档，直接运行：
```bash
cd /workspaces/Texas-Poker
chmod +x start.sh
./start.sh
# 选择 1
```

2. 在浏览器打开：`http://localhost:8080`

3. 完成！邀请朋友玩。

**详细指南：** 📖 [SETUP.md](SETUP.md) - 快速设置指南

---

### 我想了解这个系统是什么

👉 **了解项目全貌**

这是一个完整的企业级德州扑克游戏系统，包含：
- ✅ 后端游戏引擎（Java + Spring Boot + Netty）
- ✅ 前端游戏界面（HTML5 + JavaScript）
- ✅ 实时通信（WebSocket）
- ✅ 数据持久化（MySQL + Redis）
- ✅ 反作弊系统（5 维防护）

**更多细节：**
- 📖 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) - 完整的项目总结
- 📖 [README.md](README.md) - 项目介绍

---

### 我想知道怎么用

👉 **完整的游戏使用指南**

**如果您是玩家：**
- 📖 [GAME_CLIENT_GUIDE.md](GAME_CLIENT_GUIDE.md) - 游戏操作和规则说明
- 📖 [SETUP.md](SETUP.md) - 启动和基本操作

**如果您是开发者：**
- 📖 [ARCHITECTURE.md](ARCHITECTURE.md) - 系统架构和 API 文档
- 📖 [QUICK_REFERENCE.md](QUICK_REFERENCE.md) - 一手人员的命令参考

---

### 我想部署到云服务器

👉 **多种部署方式选择**

**简单部署（推荐）：**
1. 阅读 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) - "云服务器部署"部分
2. 按照步骤一步步来

**选项：**
- 🐳 Docker + Docker Compose（最简单）
- ☁️ AWS / 阿里云 / 腾讯云
- 🚀 Heroku 自动部署
- ⚙️ Kubernetes 企业级（高级）

**详细指南：** 📖 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)

---

### 我遇到了问题

👉 **快速故障排查**

**常见问题速查：**
- `http://localhost:8080 无法打开` → [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#故障排查)
- `页面显示"未连接"` → [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#故障排查)
- `朋友无法加入房间` → [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#故障排查)
- `游戏时钱数不对` → [QUICK_REFERENCE.md](QUICK_REFERENCE.md#快速故障解决)

**完整的故障排查指南：** 📖 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)

---

### 我想修改游戏规则或界面

👉 **自定义系统**

**修改游戏参数：**
- 编辑 `src/main/resources/application.yml`
- 修改盲注、倒计时等参数
- 重启应用生效

**修改游戏界面：**
- 编辑 `src/main/resources/static/index.html` 修改样式
- 编辑 `src/main/resources/static/js/game-client.js` 修改逻辑

**了解怎么改：** 📖 [ARCHITECTURE.md](ARCHITECTURE.md) 或 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)

---

### 我想查看常用命令

👉 **一手速查**

**快速命令清单：**
```bash
# 启动所有服务
./start.sh

# 诊断系统
./diagnose.sh

# 查看 Docker 状态
docker-compose ps

# 查看应用日志
docker-compose logs -f

# 停止所有服务
docker-compose down

# 重新初始化数据库
docker exec -i wepoker-mysql mysql -u root -proot < schema.sql
```

**完整参考：** 📖 [QUICK_REFERENCE.md](QUICK_REFERENCE.md)

---

### 我想学习系统架构

👉 **深入了解技术细节**

**系统架构概览：**
```
客户端 (HTML/CSS/JS)
    ↓ WebSocket
Netty 服务器 (Java)
    ↓
业务逻辑 (GameService)
    ↓
数据存储 (MySQL + Redis)
```

**分层设计：**
- 表现层：HTML5 前端
- 应用层：Spring Boot 后端
- 业务层：游戏引擎 + 状态机
- 数据层：MySQL + Redis

**详细说明：** 📖 [ARCHITECTURE.md](ARCHITECTURE.md)

---

### 我想知道系统能做什么

👉 **功能列表和技术栈**

**游戏功能：**
- ✅ 完整的德州扑克游戏流程
- ✅ 2-8 玩家同时游戏
- ✅ 实时手牌评估
- ✅ 复杂的多边池计算
- ✅ 房间链接分享
- ✅ 断线重连

**技术特性：**
- ✅ 支持 100K+ 并发玩家
- ✅ 5 维反作弊系统
- ✅ 低于 100ms 延迟
- ✅ 高可用部署

**完整清单：** 📖 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)

---

## 📚 完整文档导航

### 新用户 (建议顺序)

1. 📖 **[SETUP.md](SETUP.md)** ⭐⭐⭐⭐⭐
   - 最快的起步指南
   - 5 分钟启动游戏
   - 常见问题解答

2. 📖 **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)** ⭐⭐⭐⭐
   - 常用命令卡片
   - 快速故障排查
   - 快速参考表

3. 📖 **[GAME_CLIENT_GUIDE.md](GAME_CLIENT_GUIDE.md)** ⭐⭐⭐⭐
   - 游戏玩法说明
   - 界面指南
   - 房间链接分享

4. 📖 **[PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)** ⭐⭐⭐
   - 项目完整总结
   - 功能清单
   - 技术栈说明

### 开发者 (建议顺序)

1. 📖 **[README.md](README.md)**
   - 项目介绍

2. 📖 **[ARCHITECTURE.md](ARCHITECTURE.md)** ⭐⭐⭐⭐⭐
   - 系统架构
   - 详细的代码组织
   - 核心算法说明
   - 15 种消息协议
   - REST API 文档
   - WebSocket 消息格式

3. 📖 **[DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)**
   - 6 种部署方式
   - 生产环境配置
   - 故障排查

4. 📖 **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)**
   - 一手速查

### 系统管理员 (建议顺序)

1. 📖 **[SETUP.md](SETUP.md)**
   - 快速部署

2. 📖 **[DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)** ⭐⭐⭐⭐⭐
   - 多种部署选项
   - 生产安全配置
   - 监控和调优
   - 备份和恢复

3. 📖 **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)**
   - 常用命令

---

## 📋 文件功能一览

| 文件 | 类型 | 用途 | 大小 | 阅读时间 |
|------|------|------|------|---------|
| **SETUP.md** | 📖 指南 | 快速启动 | 中 | 5 分钟 |
| **QUICK_REFERENCE.md** | 📋 参考 | 快速查询 | 中 | 查询式 |
| **DEPLOYMENT_GUIDE.md** | 📖 指南 | 部署和维护 | 大 | 20 分钟 |
| **GAME_CLIENT_GUIDE.md** | 📖 指南 | 游戏玩法 | 中 | 10 分钟 |
| **ARCHITECTURE.md** | 📚 文档 | 系统设计 | 大 | 30 分钟 |
| **PROJECT_SUMMARY.md** | 📚 文档 | 项目总结 | 大 | 20 分钟 |
| **README.md** | 📖 介绍 | 项目总览 | 中 | 10 分钟 |

---

## 🎯 按场景快速导航

### 场景 1：我是普通用户，只想玩游戏

**您需要：**
1. 阅读 [SETUP.md](SETUP.md) (5 分钟)
2. 运行 `./start.sh` (2 分钟)
3. 邀请朋友（实时）

**所需文档：** SETUP.md

---

### 场景 2：我想在公司内网部署

**您需要：**
1. 阅读 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) - "云服务器部署"部分
2. 准备一台服务器
3. 按步骤部署
4. 分享 IP 给同事

**所需文档：** DEPLOYMENT_GUIDE.md, QUICK_REFERENCE.md

---

### 场景 3：我想把这个系统集成到我的应用中

**您需要：**
1. 阅读 [ARCHITECTURE.md](ARCHITECTURE.md) 学习 API
2. 学习 WebSocket 消息格式
3. 使用 REST API 或 WebSocket 集成
4. 可选：修改前端代码

**所需文档：** ARCHITECTURE.md, QUICK_REFERENCE.md

---

### 场景 4：我想招聘开发者来维护这个系统

**请给开发者：**
1. [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) - 项目总体介绍
2. [ARCHITECTURE.md](ARCHITECTURE.md) - 技术细节
3. [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) - 部署和维护
4. [QUICK_REFERENCE.md](QUICK_REFERENCE.md) - 日常命令

**所需文档：** 全部

---

### 场景 5：我想修改游戏规则

**您需要：**
1. 阅读 [QUICK_REFERENCE.md](QUICK_REFERENCE.md) 学习编辑文件
2. 编辑 `application.yml` 改变盲注等参数
3. 或修改 Java/JavaScript 代码改变逻辑
4. 重启应用

**所需文档：** QUICK_REFERENCE.md, PROJECT_SUMMARY.md, ARCHITECTURE.md

---

## 🔍 按问题类型查找答案

### "应用无法启动"

→ 参考 [QUICK_REFERENCE.md](QUICK_REFERENCE.md#快速故障排查) 或 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#故障排查)

### "无法看到游戏 UI"

→ 参考 [SETUP.md](SETUP.md) 或 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)

### "朋友无法加入房间"

→ 参考 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#朋友无法进入房间) 或 [SETUP.md](SETUP.md)

### "怎么修改游戏规则"

→ 参考 [ARCHITECTURE.md](ARCHITECTURE.md) 或 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)

### "怎么部署到云服务器"

→ 参考 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#如何在公网上部署)

### "系统支持多少玩家"

→ 参考 [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md#-性能指标) 或 [ARCHITECTURE.md](ARCHITECTURE.md)

### "怎么看数据库内容"

→ 参考 [QUICK_REFERENCE.md](QUICK_REFERENCE.md#-数据库操作速查)

### "WebSocket 连接失败"

→ 参考 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#问题-3websocket-连接失败) 或 [QUICK_REFERENCE.md](QUICK_REFERENCE.md#快速故障排查)

---

## 📊 常见任务快速指南

| 任务 | 所需资源 | 时间 |
|------|---------|------|
| 启动游戏 | SETUP.md | 5 分钟 |
| 邀请朋友 | GAME_CLIENT_GUIDE.md | 1 分钟 |
| 部署到云 | DEPLOYMENT_GUIDE.md | 20 分钟 |
| 修改游戏参数 | QUICK_REFERENCE.md + CONFIG | 5 分钟 |
| 学习架构 | ARCHITECTURE.md | 1 小时 |
| 故障排查 | QUICK_REFERENCE.md + DEPLOYMENT_GUIDE.md | 变量 |
| 完全理解系统 | 所有文档 | 3-4 小时 |

---

## 🎓 学习路径

### 忙碌人士 (30 分钟)

```
1. 快速阅读 SETUP.md (5 分钟)
   ↓
2. 运行 ./start.sh (5 分钟)
   ↓
3. 在浏览器测试 (10 分钟)
   ↓
4. 快速浏览 QUICK_REFERENCE.md (10 分钟)
```

### 标准开发者 (2 小时)

```
1. SETUP.md 快速启动 (5 分钟)
   ↓
2. ARCHITECTURE.md 理解设计 (45 分钟)
   ↓
3. 浏览源代码 (30 分钟)
   ↓
4. DEPLOYMENT_GUIDE.md 了解部署 (30 分钟)
   ↓
5. 实践修改和测试 (10 分钟)
```

### 系统架构师 (4-6 小时)

```
1. README.md 总览 (10 分钟)
   ↓
2. PROJECT_SUMMARY.md 完整理解 (30 分钟)
   ↓
3. ARCHITECTURE.md 详细研究 (90 分钟)
   ↓
4. 仔细阅读源代码 (120 分钟)
   ↓
5. DEPLOYMENT_GUIDE.md 生产规划 (45 分钟)
   ↓
6. 自己部署和扩展 (60+ 分钟)
```

---

## 🌟 推荐阅读顺序

### 第一次使用（必读）

1. ⭐⭐⭐⭐⭐ **[SETUP.md](SETUP.md)** - 30 秒了解，5 分钟启动

### 日常参考（常用）

2. ⭐⭐⭐⭐ **[QUICK_REFERENCE.md](QUICK_REFERENCE.md)** - 常用命令快速查询

### 深入学习（推荐）

3. ⭐⭐⭐⭐ **[GAME_CLIENT_GUIDE.md](GAME_CLIENT_GUIDE.md)** - 游戏玩法和客户端

4. ⭐⭐⭐⭐ **[DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)** - 部署和维护

5. ⭐⭐⭐ **[PROJECT_SUMMARY.md](PROJECT_SUMMARY.md)** - 项目完整总结

### 精通系统（高级）

6. ⭐⭐⭐⭐⭐ **[ARCHITECTURE.md](ARCHITECTURE.md)** - 系统架构和 API

---

## 💡 选择你的起点

### 我最多只有 5 分钟

👉 直接运行 `./start.sh` 然后看浏览器

### 我有 15 分钟

👉 快速阅读 [SETUP.md](SETUP.md) (10 分钟) 然后启动 (5 分钟)

### 我有 1 小时

👉 1. 读 [SETUP.md](SETUP.md)
   2. 启动系统
   3. 读 [QUICK_REFERENCE.md](QUICK_REFERENCE.md)
   4. 尝试修改配置

### 我想完全掌握

👉 按照"标准开发者"路径学习（2-4 小时）

---

## 📞 如果你找不到答案

1. 检查 [QUICK_REFERENCE.md](QUICK_REFERENCE.md) 的快速故障排查
2. 查看 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) 的故障排查部分
3. 查看 [ARCHITECTURE.md](ARCHITECTURE.md) 了解系统细节
4. 查看应用日志获得错误信息

---

**现在开始吧！👉 [SETUP.md](SETUP.md)**

或者直接启动：
```bash
cd /workspaces/Texas-Poker
./start.sh
```
