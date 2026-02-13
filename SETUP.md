# 🎮 WePoker 快速设置指南

**你好！欢迎使用 WePoker 德州扑克系统。** 

本文档将指导您快速启动系统，让朋友们可以点链接直接进来游戏。

---

## ⚡ 30 秒快速启动

### 仅需 3 步！

#### 步骤 1：进入项目目录
```bash
cd /workspaces/Texas-Poker
```

#### 步骤 2：给启动脚本权限（仅需一次）
```bash
chmod +x start.sh diagnose.sh
```

#### 步骤 3：启动！
```bash
./start.sh
```

**然后选择选项 `1` (完整启动)**

---

## ✅ 系统检查 (可选但推荐)

在启动前，检查您的系统是否满足要求：

```bash
./diagnose.sh
```

这个脚本会检查：
- ✅ Java 环境
- ✅ Maven 环境
- ✅ Docker 环境
- ✅ 网络连接
- ✅ 项目文件完整性
- ✅ 端口占用情况

---

## 🎮 启动后做什么？

### 第 1 步：等待启动完成 (约 2-3 分钟)

看到类似这样的输出，说明启动成功：
```
🎉 WePoker 启动成功！

📍 游戏地址: http://localhost:8080
📊 API 地址: http://localhost:8080/api/game

💡 下一步:
  1. 用浏览器打开上面的游戏地址
  2. 输入昵称和买入金额
  3. 复制房间链接发送给朋友
```

### 第 2 步：在浏览器打开游戏

**点击这个链接或复制到浏览器地址栏：**
```
http://localhost:8080
```

你会看到一个绿色的德州扑克牌桌界面。

### 第 3 步：加入房间

1. 输入你的昵称（比如"老李"）
2. 输入买入金额（比如 500 元）
3. 点击"加入房间"

### 第 4 步：邀请朋友

1. 在游戏界面右侧，找到"邀请朋友"
2. 点击"复制房间链接"
3. 发送到微信、QQ、Email 等
4. 朋友点击链接就能进来了！

---

## 🚀 不同系统的启动方式

### macOS / Linux

```bash
cd /workspaces/Texas-Poker
chmod +x start.sh
./start.sh
# 选择 1
```

### Windows (使用 PowerShell 或 CMD)

```cmd
cd \path\to\Texas-Poker
start.bat
REM 选择 1
```

### 使用 Docker 只启动中间件（手动运行应用）

如果你想自己运行以便开发或调试：

```bash
# 终端 1：启动 Docker 服务
./start.sh
# 选择 2

# 终端 2：编译并运行应用
mvn clean package
mvn spring-boot:run
```

---

## 🎯 常见场景

### 场景 1：局域网内朋友要加入

你的局域网 IP 可能是 `192.168.1.100`

**获取 IP：**
```bash
# Linux/Mac
ifconfig | grep "inet "

# Windows
ipconfig | findstr "IPv4"
```

**分享给朋友的链接：**
```
http://192.168.1.100:8080?table=123456
```

### 场景 2：想在另一台电脑上部署

```bash
# 在目标电脑上
git clone <项目仓库>
cd Texas-Poker
./start.sh  # 选择 1
```

### 场景 3：想到公网上部署让任何人都能玩

需要一台服务器（云服务器或 VPS）：

```bash
# 在服务器上
ssh user@你的服务器IP
cd /opt/Texas-Poker
./start.sh  # 选择 1

# 分享给朋友：
# http://你的服务器IP:8080?table=123456
```

详见 `DEPLOYMENT_GUIDE.md` 中的"如何在公网上部署"部分。

---

## 🎮 在线游戏玩法

### 游戏界面说明

```
┌─────────────────────────────────────────────────────────┐
│                    德州扑克牌桌                           │
│                                                           │
│              💰 底池: 1000 元                           │
│          🃏  🃏  🃏  🃏  🃏                              │
│      ┌─────────────────────────────────────┐           │
│      │    玩家1      玩家2          玩家3    │           │
│      │  (小盲注)   (大盲注)      (活跃)     │           │
│      │   🎴A🎴K   💰500      ▲ 我的回合   │           │
│      │   栈:1200   栈:800       栈:2000    │           │
│      └─────────────────────────────────────┘           │
│                                                           │
│  右侧面板:                                               │
│  ┌────────────────┐                                     │
│  │ 游戏信息:      │                                     │
│  │ 类型: NL Hold'em                                     │
│  │ 盲注: 5/10     │                                     │
│  │                │                                     │
│  │ 我的信息:      │                                     │
│  │ 昵称: Jack     │                                     │
│  │ 栈: 3500       │                                     │
│  │ 位置: BTN      │                                     │
│  │                │                                     │
│  │ [邀请朋友]     │                                     │
│  │ [复制链接]     │                                     │
│  │                │                                     │
│  │ ⏱️ 倒计时: 15  │                                     │
│  │                │                                     │
│  │ [过牌] [跟注]  │                                     │
│  │ [下注] [加注]  │                                     │
│  │ [全下] [弃牌]  │                                     │
│  └────────────────┘                                     │
└─────────────────────────────────────────────────────────┘
```

### 快速操作参考

| 想做的事 | 怎么做 |
|---------|-------|
| 过牌 | 点"过牌"或 `C` 键 |
| 跟注 | 点"跟注"或 `R` 键 |
| 下注 | 输入金额，点"下注" |
| 加注 | 输入金额，点"加注" |
| 全下 | 点"全下"或 `A` 键 |
| 弃牌 | 点"弃牌"或 `F` 键 |
| 邀请朋友 | 点"邀请朋友"按钮 |

---

## 🐛 遇到问题？

### 问题：无法打开 localhost:8080

**检查清单：**
```bash
# 1. 确认应用运行中
curl http://localhost:8080

# 2. 查看 Maven 输出，应该看到绿色的 "Started" 信息
# 不要关闭终端

# 3. 重启浏览器（彻底关闭后重新打开）

# 4. 尝试其他浏览器
```

### 问题：连接后显示"未连接" (红色)

**这意味着 WebSocket 连接失败。**
```bash
# 检查防火墙是否阻止了 9000 端口
# 或重启应用

# 在终端按 Ctrl+C 停止
# 然后重新运行 mvn spring-boot:run
```

### 问题：朋友无法看到我创建的房间

**检查：**
1. 确保你也看到了房间（房间 ID 显示）
2. 确保使用的是同一个 IP 地址（localhost vs 192.168.x.x）
3. 确保互联网/局域网连接正常

```bash
# 重新启动应用
# 清除浏览器缓存
# 在隐私窗口测试
```

### 问题：游戏很卡或经常掉线

**可能的原因与解决：**

1. **网络延迟太高**
   - 确保 Wi-Fi/网络连接稳定
   - 尝试有线连接

2. **服务器资源不足**
   - 检查内存是否充足: `free -h`
   - 关闭其他程序释放资源
   - 增加 Docker 内存限制

3. **数据库连接过多**
   ```bash
   # 重启数据库
   docker-compose restart wepoker-mysql wepoker-redis
   ```

### 更多问题？

查看完整的故障排查指南：
- 📖 `DEPLOYMENT_GUIDE.md` - 详细部署和故障排查
- 📖 `QUICK_REFERENCE.md` - 快速命令参考
- 📖 `GAME_CLIENT_GUIDE.md` - 游戏客户端说明

---

## 📊 服务状态和管理

### 检查所有服务是否运行

```bash
docker-compose ps
```

应该看到：
```
NAME                COMMAND             SERVICE             STATUS
wepoker-mysql       "docker-entrypoint" mysql               Up 5 minutes
wepoker-redis       "redis-server"      redis               Up 5 minutes
wepoker-phpMyAdmin  ...                 phpmyadmin          Up 5 minutes
wepoker-redis-cmd   ...                 redis-commander     Up 5 minutes
```

### 管理员工具

**MySQL 数据库管理：**
```
地址: http://localhost:8081
用户: root
密码: root
```

**Redis 缓存查看：**
```
地址: http://localhost:8082
```

### 查看应用日志

```bash
# 如果应用在后台运行
docker logs <容器名>

# 或直接看终端输出（应用仍运行时）
```

---

## 🎓 进阶使用

### 修改游戏参数

编辑 `src/main/resources/application.yml`：

```yaml
wepoker:
  game:
    smallBlind: 5          # 小盲注（分）
    bigBlind: 10           # 大盲注（分）
    actionTimeoutDefault: 15  # 标准倒计时（秒）
    actionTimeoutExtended: 30  # 延时倒计时（秒）
    rakePercent: 5         # 手续费（%）
    rakeMax: 50000         # 最大手续费（分）
    buyInMax: 50000        # 最大买入（分）
    buyInMin: 1000         # 最小买入（分）
```

修改后需要 **重启应用**。

### 修改游戏 UI

编辑 `src/main/resources/static/index.html` 修改界面样式，或编辑 `src/main/resources/static/js/game-client.js` 修改游戏逻辑。

重启应用后生效。

### 部署到云服务器

详见 `DEPLOYMENT_GUIDE.md` 中的"如何在公网上部署"。

---

## ✨ 总结

| 步骤 | 命令 |
|------|------|
| 1. 进入目录 | `cd /workspaces/Texas-Poker` |
| 2. 给脚本权限 | `chmod +x start.sh` |
| 3. 启动系统 | `./start.sh` (选项 1) |
| 4. 打开游戏 | 浏览器访问 `http://localhost:8080` |
| 5. 邀请朋友 | 复制房间链接发送 |

---

**现在就开始吧！🎉**

```bash
cd /workspaces/Texas-Poker
chmod +x start.sh
./start.sh
```

选择选项 `1`，等待 2-3 分钟，然后在浏览器打开 `http://localhost:8080`！

有问题？查看 `QUICK_REFERENCE.md` 或 `DEPLOYMENT_GUIDE.md`。
