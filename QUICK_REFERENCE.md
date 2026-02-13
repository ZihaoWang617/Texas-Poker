# 🎯 WePoker 快速参考卡

## 🚀 最快启动 (3 步)

### Linux / Mac
```bash
cd /workspaces/Texas-Poker
chmod +x start.sh
./start.sh
```
然后选择 **选项 1** 

浏览器打开 → `http://localhost:8080`

### Windows
```cmd
cd \path\to\Texas-Poker
start.bat
```
然后选择 **选项 1** 

浏览器打开 → `http://localhost:8080`

---

## 📦 Docker 命令速查

| 命令 | 功能 |
|------|------|
| `docker-compose up -d` | 启动所有服务 |
| `docker-compose down` | 停止所有服务 |
| `docker-compose ps` | 查看运行状态 |
| `docker-compose logs -f` | 查看实时日志 |
| `docker-compose down -v` | 完全删除（含数据） |

---

## 🔧 Java/Maven 命令速查

| 命令 | 功能 |
|------|------|
| `mvn clean` | 删除编译输出 |
| `mvn compile` | 仅编译 |
| `mvn package` | 编译打包成 JAR |
| `mvn spring-boot:run` | 运行应用 |
| `mvn test` | 运行测试 |

---

## 🌐 访问地址速查

| 服务 | 地址 | 用途 |
|------|------|------|
| **游戏** | `http://localhost:8080` | 玩德州扑克 |
| **API** | `http://localhost:8080/api/game/health` | API 查询 |
| **MySQL** | `http://localhost:8081` 账户: root / 密码: root | 数据库管理 |
| **Redis** | `http://localhost:8082` | 缓存状态查看 |

---

## 🐛 快速故障排查

### 问题: 无法访问 localhost:8080
```bash
# 检查应用是否运行
curl http://localhost:8080

# 查看 Maven 启动日志
# 应该看到没有红色错误信息

# 重启试试
# 停止应用: Ctrl+C
# 再次运行: mvn spring-boot:run
```

### 问题: 无法连接到数据库
```bash
# 检查 Docker 是否运行
docker ps

# 看不到 wepoker-mysql，重启：
docker-compose up -d wepoker-mysql

# 初始化数据库
docker exec wepoker-mysql mysql -u root -proot < schema.sql
```

### 问题: 页面无法连接 WebSocket (红色"未连接" 提示)
```bash
# Netty 服务器应该在端口 9000
# 检查防火墙是否开放了 9000 端口

# 查看应用日志，应该看到：
# "Netty Game Server started on 0.0.0.0:9000"
```

### 问题: 朋友无法加入房间
```bash
# 如果朋友在同一局域网，使用 IP 地址而不是 localhost
# 查看你的 IP：
ipconfig          # Windows
ifconfig          # Mac/Linux

# 房间链接应该是：
# http://192.168.X.X:8080?table=123456
```

---

## 💾 数据库操作速查

### 查看所有表
```bash
docker exec -it wepoker-mysql mysql -u root -proot -D wepoker << 'EOF'
SHOW TABLES;
EOF
```

### 查看玩家信息
```bash
docker exec -it wepoker-mysql mysql -u root -proot -D wepoker << 'EOF'
SELECT * FROM player LIMIT 10;
EOF
```

### 查看游戏历史
```bash
docker exec -it wepoker-mysql mysql -u root -proot -D wepoker << 'EOF'
SELECT * FROM game_round ORDER BY created_at DESC LIMIT 5;
EOF
```

### 重置数据库
```bash
docker exec -it wepoker-mysql mysql -u root -proot -e "DROP DATABASE wepoker;"
docker exec -i wepoker-mysql mysql -u root -proot < schema.sql
```

---

## 🎮 游戏操作速查

| 操作 | 快捷键 / 点击 |
|------|--------|
| 加入房间 | 输入昵称 + 买入金额 → 点击"加入" |
| 下注 | 输入金额 → 点击"下注" |
| 跟注 | 点击"跟注"按钮 |
| 加注 | 输入金额 → 点击"加注" |
| 全下 | 点击"全下" |
| 弃牌 | 点击"弃牌" |
| 过牌 | 点击"过牌" |
| 邀请朋友 | 点击"邀请朋友" → 复制链接 |

---

## 🚀 快速部署到云服务器

### 1. 准备 Linux 服务器
```bash
# 仅需要 Docker
curl -fsSL https://get.docker.com | sh

# 安装 Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

### 2. 部署项目
```bash
# 上传项目到服务器
scp -r Texas-Poker/ root@你的服务器IP:/opt/

# 连接到服务器
ssh root@你的服务器IP

# 启动
cd /opt/Texas-Poker
docker-compose up -d
```

### 3. 分享链接
```
朋友访问: http://你的服务器IP:8080?table=123456
```

---

## 🔑 常用文件速查

| 文件 | 用途 | 编辑建议 |
|------|------|---------|
| `application.yml` | 应用配置 | 修改端口、数据库连接 |
| `docker-compose.yml` | Docker 配置 | 修改数据库密码、版本 |
| `schema.sql` | 数据库结构 | 扩展表结构 |
| `index.html` | 游戏 UI | 自定义界面样式 |
| `game-client.js` | 游戏逻辑 | 修改游戏规则、UI 事件 |

---

## 🌟 性能优化提示

### 支持更多玩家
```yaml
# 在 application.yml 中修改
wepoker:
  netty:
    workerThreads: 32  # 增加 Netty worker 线程
    
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # 增加数据库连接池
```

### 启用 Java 21 虚拟线程 (高并发)
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

---

## ✅ 启动前检查清单

- [ ] 安装了 Docker Desktop
- [ ] 安装了 Maven 3.8+
- [ ] 安装了 Java 21+
- [ ] 8080 和 9000 端口未被占用
- [ ] 至少有 2GB 内存可用
- [ ] 至少有 10GB 硬盘空间

---

## 📞 快速获帮助

| 问题 | 快速解决 |
|------|---------|
| **应用无法启动** | 查看日志中的正确错误信息 → `mvn spring-boot:run` |
| **无法连接数据库** | 重启 MySQL: `docker-compose restart wepoker-mysql` |
| **WebSocket 未连接** | 检查防火墙: `sudo ufw allow 9000` |
| **内存溢出** | 增加 Java 堆内存: `java -Xmx1024m -jar xxx.jar` |
| **数据丢失** | 恢复备份或重新初始化: `docker exec -i wepoker-mysql mysql -u root -proot < schema.sql` |

---

## 🎓 学习更多

- **完整部署指南**: 查看 `DEPLOYMENT_GUIDE.md`
- **API 文档**: 查看 `ARCHITECTURE.md` 的 API 部分
- **游戏玩法**: 查看 `GAME_CLIENT_GUIDE.md`
- **开发文档**: 查看 `README.md`

---

**保存此文件，快速参考！** 📌
