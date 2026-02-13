#!/bin/bash

# WePoker 一键启动脚本
# 使用方式：./start.sh

echo "🚀 WePoker 服务启动中..."
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Docker
echo "📋 检查环境..."
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ 错误: Docker 未安装${NC}"
    echo "请访问 https://docs.docker.com/get-docker/ 安装 Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ 错误: Docker Compose 未安装${NC}"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ 错误: Maven 未安装${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Docker 已安装${NC}"
echo -e "${GREEN}✅ Docker Compose 已安装${NC}"
echo -e "${GREEN}✅ Maven 已安装${NC}"
echo ""

# 选择启动模式
echo "选择启动模式:"
echo "1) 完整启动 (Docker Compose + Spring Boot) - 推荐 ⭐"
echo "2) 仅启动中间件 (Docker Compose only) - 用于本地开发"
echo "3) 清空并重新启动 - 用于故障排查"
echo ""
read -p "请输入选项 (1-3): " choice

case $choice in
    1)
        echo ""
        echo "🔧 启动模式: 完整启动 (Docker + Spring Boot)"
        echo ""
        
        # 启动 Docker 服务
        echo "📦 启动 MySQL + Redis + 管理工具..."
        docker-compose up -d
        
        if [ $? -ne 0 ]; then
            echo -e "${RED}❌ Docker Compose 启动失败${NC}"
            exit 1
        fi
        
        echo -e "${GREEN}✅ Docker 服务已启动${NC}"
        
        # 等待数据库启动
        echo ""
        echo "⏳ 等待数据库初始化（约 15 秒）..."
        sleep 15
        
        # 初始化数据库
        echo "📝 初始化数据库..."
        docker exec wepoker-mysql mysql -u root -proot << EOF
CREATE DATABASE IF NOT EXISTS wepoker DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wepoker;
$(cat schema.sql)
EOF
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ 数据库初始化完成${NC}"
        else
            echo -e "${YELLOW}⚠️ 数据库初始化可能已完成或出错，继续启动应用...${NC}"
        fi
        
        echo ""
        echo "🔨 编译 WePoker 应用..."
        mvn clean package -q
        
        if [ $? -ne 0 ]; then
            echo -e "${RED}❌ 编译失败${NC}"
            exit 1
        fi
        
        echo -e "${GREEN}✅ 编译完成${NC}"
        
        echo ""
        echo "🎮 启动 WePoker 应用服务..."
        mvn spring-boot:run
        ;;
        
    2)
        echo ""
        echo "🔧 启动模式: 仅启动中间件"
        echo ""
        echo "📦 启动 MySQL + Redis + 管理工具..."
        docker-compose up -d
        
        if [ $? -ne 0 ]; then
            echo -e "${RED}❌ Docker Compose 启动失败${NC}"
            exit 1
        fi
        
        echo -e "${GREEN}✅ Docker 服务已启动${NC}"
        echo ""
        echo "⏳ 等待数据库初始化（约 15 秒）..."
        sleep 15
        
        # 初始化数据库
        echo "📝 初始化数据库..."
        docker exec wepoker-mysql mysql -u root -proot << EOF
CREATE DATABASE IF NOT EXISTS wepoker DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wepoker;
$(cat schema.sql)
EOF
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ 数据库初始化完成${NC}"
        fi
        
        echo ""
        echo "📊 管理界面访问地址:"
        echo "  MySQL 管理 (phpMyAdmin): http://localhost:8081"
        echo "  账户: root"
        echo "  密码: root"
        echo ""
        echo "  Redis 管理 (Redis Commander): http://localhost:8082"
        echo ""
        echo -e "${YELLOW}提示: 应用服务未启动，请在另一个终端运行:${NC}"
        echo "  mvn spring-boot:run"
        echo ""
        docker-compose logs -f
        ;;
        
    3)
        echo ""
        echo "🧹 清空所有数据并重新启动..."
        echo ""
        read -p "确定要清空所有数据吗？(yes/no): " confirm
        
        if [ "$confirm" != "yes" ]; then
            echo "已取消"
            exit 0
        fi
        
        echo "🛑 停止所有服务..."
        docker-compose down -v
        
        echo "🗑️ 清除编译输出..."
        mvn clean -q
        
        echo ""
        echo "🔄 重新启动..."
        echo "📦 启动 MySQL + Redis..."
        docker-compose up -d
        
        echo -e "${GREEN}✅ Docker 服务已启动${NC}"
        
        echo ""
        echo "⏳ 等待数据库启动（约 15 秒）..."
        sleep 15
        
        echo "📝 初始化数据库..."
        docker exec wepoker-mysql mysql -u root -proot << EOF
CREATE DATABASE IF NOT EXISTS wepoker DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE wepoker;
$(cat schema.sql)
EOF
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ 数据库已重置${NC}"
        fi
        
        echo ""
        echo "🔨 重新编译..."
        mvn clean package -q
        
        echo -e "${GREEN}✅ 所有服务已就绪${NC}"
        echo ""
        echo "🎮 启动应用..."
        mvn spring-boot:run
        ;;
        
    *)
        echo -e "${RED}❌ 无效选项${NC}"
        exit 1
        ;;
esac

echo ""
echo "════════════════════════════════════════════════════════════"
echo "🎉 WePoker 启动成功！"
echo ""
echo "📍 游戏地址: http://localhost:8080"
echo "📊 API 地址: http://localhost:8080/api/game"
echo ""
echo "💡 下一步:"
echo "  1. 用浏览器打开上面的游戏地址"
echo "  2. 输入昵称和买入金额"
echo "  3. 复制房间链接发送给朋友"
echo "════════════════════════════════════════════════════════════"
