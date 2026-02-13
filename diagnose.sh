#!/bin/bash

# WePoker 系统诊断工具
# 检查依赖、资源和网络连接

echo "🔍 WePoker 系统诊断工具"
echo "════════════════════════════════════════════════════════════"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 诊断结果统计
PASS=0
WARN=0
FAIL=0

# 检查函数
check_command() {
    local name=$1
    local cmd=$2
    local required=$3  # "yes" or "optional"
    
    if command -v $cmd &> /dev/null; then
        local version=$($cmd --version 2>&1 | head -n 1)
        echo -e "${GREEN}✅${NC} $name 已安装: $version"
        ((PASS++))
    else
        if [ "$required" = "yes" ]; then
            echo -e "${RED}❌${NC} $name 未安装 (必须)"
            ((FAIL++))
        else
            echo -e "${YELLOW}⚠️${NC} $name 未安装 (可选)"
            ((WARN++))
        fi
    fi
}

check_file() {
    local name=$1
    local path=$2
    
    if [ -f "$path" ]; then
        echo -e "${GREEN}✅${NC} $name 存在"
        ((PASS++))
    else
        echo -e "${RED}❌${NC} $name 不存在: $path"
        ((FAIL++))
    fi
}

check_port() {
    local port=$1
    local name=$2
    
    if nc -z localhost $port 2>/dev/null; then
        echo -e "${GREEN}✅${NC} 端口 $port ($name) 已开放"
        ((PASS++))
    else
        echo -e "${YELLOW}⚠️${NC} 端口 $port ($name) 未开放 (应用未启动时正常)"
        ((WARN++))
    fi
}

# ═══════════════════════════════════════════════════════════════
echo -e "${BLUE}1. 检查必需的软件${NC}"
echo "───────────────────────────────────────────────────────────"

check_command "Java" "java" "yes"
check_command "Maven" "mvn" "yes"
check_command "Docker" "docker" "yes"
check_command "Docker Compose" "docker-compose" "yes"
check_command "MySQL CLI" "mysql" "optional"
check_command "Redis CLI" "redis-cli" "optional"
check_command "Git" "git" "optional"

echo ""

# ═══════════════════════════════════════════════════════════════
echo -e "${BLUE}2. 检查项目文件${NC}"
echo "───────────────────────────────────────────────────────────"

check_file "pom.xml" "pom.xml"
check_file "docker-compose.yml" "docker-compose.yml"
check_file "schema.sql" "schema.sql"
check_file "application.yml" "src/main/resources/application.yml"
check_file "index.html" "src/main/resources/static/index.html"
check_file "game-client.js" "src/main/resources/static/js/game-client.js"

echo ""

# ═══════════════════════════════════════════════════════════════
echo -e "${BLUE}3. 检查系统资源${NC}"
echo "───────────────────────────────────────────────────────────"

# 检查 Java 版本
JAVA_VERSION=$(java -version 2>&1 | grep -oP '(?<=version ")[^"]*' | head -1)
MAJOR_VERSION=$(echo $JAVA_VERSION | cut -d. -f1)

if [ "$MAJOR_VERSION" -ge 21 ]; then
    echo -e "${GREEN}✅${NC} Java 版本: $JAVA_VERSION (21+ ✓)"
    ((PASS++))
else
    echo -e "${YELLOW}⚠️${NC} Java 版本: $JAVA_VERSION (建议 21+ 以获得最佳性能)"
    ((WARN++))
fi

# 检查可用内存
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    AVAILABLE_MEM=$(free -h | awk '/^Mem:/ {print $7}')
    TOTAL_MEM=$(free -h | awk '/^Mem:/ {print $2}')
    echo -e "${GREEN}ℹ️${NC} 可用内存: $AVAILABLE_MEM / $TOTAL_MEM"
elif [[ "$OSTYPE" == "darwin"* ]]; then
    AVAILABLE_MEM=$(vm_stat | grep "Pages free" | awk '{print $3}' | sed 's/\.//' | numfmt --to=iec)
    echo -e "${GREEN}ℹ️${NC} 可用内存: $AVAILABLE_MEM"
fi

# 检查磁盘空间
DISK_AVAILABLE=$(df -h . | awk 'NR==2 {print $4}')
echo -e "${GREEN}ℹ️${NC} 可用磁盘: $DISK_AVAILABLE"

echo ""

# ═══════════════════════════════════════════════════════════════
echo -e "${BLUE}4. 检查 Docker 环境${NC}"
echo "───────────────────────────────────────────────────────────"

# 检查 Docker 守护进程
if docker info &>/dev/null; then
    echo -e "${GREEN}✅${NC} Docker 守护进程运行正常"
    ((PASS++))
else
    echo -e "${RED}❌${NC} Docker 守护进程未运行"
    ((FAIL++))
fi

# 检查现有容器
CONTAINER_COUNT=$(docker ps -a --format '{{.Names}}' | grep -c wepoker)
if [ $CONTAINER_COUNT -gt 0 ]; then
    echo -e "${GREEN}✅${NC} 发现 $CONTAINER_COUNT 个 WePoker 容器"
    docker ps -a --filter "name=wepoker" --format "table {{.Names}}\t{{.Status}}"
    ((PASS++))
else
    echo -e "${YELLOW}⚠️${NC} 未发现 WePoker 容器 (首次运行时正常)"
    ((WARN++))
fi

echo ""

# ═══════════════════════════════════════════════════════════════
echo -e "${BLUE}5. 检查网络连接${NC}"
echo "───────────────────────────────────────────────────────────"

# 检查互联网连接
if timeout 2 bash -c 'echo >/dev/tcp/8.8.8.8/53' 2>/dev/null; then
    echo -e "${GREEN}✅${NC} 互联网连接正常 (可下载依赖)"
    ((PASS++))
else
    echo -e "${YELLOW}⚠️${NC} 互联网连接断开 (可能无法下载 Maven 依赖)"
    ((WARN++))
fi

# 检查端口占用
check_port "8080" "Spring Boot HTTP"
check_port "9000" "Netty WebSocket"
check_port "3306" "MySQL"
check_port "6379" "Redis"

echo ""

# ═══════════════════════════════════════════════════════════════
echo -e "${BLUE}6. 检查 Maven 依赖${NC}"
echo "───────────────────────────────────────────────────────────"

# 检查 Maven 本地仓库
MAVEN_REPO="$HOME/.m2/repository"
if [ -d "$MAVEN_REPO" ]; then
    REPO_SIZE=$(du -sh "$MAVEN_REPO" | cut -f1)
    echo -e "${GREEN}✅${NC} Maven 本地仓库存在: $REPO_SIZE"
    ((PASS++))
else
    echo -e "${YELLOW}⚠️${NC} Maven 本地仓库不存在 (首次启动会自动创建)"
    ((WARN++))
fi

# 尝试编译测试
echo -e "${YELLOW}ℹ️${NC} 检查编译... (这可能需要一分钟)"
if mvn compile -q -DskipTests 2>/dev/null; then
    echo -e "${GREEN}✅${NC} 项目编译成功"
    ((PASS++))
else
    echo -e "${RED}❌${NC} 项目编译失败"
    ((FAIL++))
fi

echo ""

# ═══════════════════════════════════════════════════════════════
echo -e "${BLUE}7. 检查运行时配置${NC}"
echo "───────────────────────────────────────────────────────────"

# 检查 application.yml 配置
if grep -q "port: 8080" src/main/resources/application.yml; then
    echo -e "${GREEN}✅${NC} Spring Boot 端口配置: 8080"
    ((PASS++))
else
    echo -e "${YELLOW}⚠️${NC} Spring Boot 端口配置异常"
    ((WARN++))
fi

if grep -q "port: 9000" src/main/resources/application.yml; then
    echo -e "${GREEN}✅${NC} Netty 端口配置: 9000"
    ((PASS++))
else
    echo -e "${YELLOW}⚠️${NC} Netty 端口配置异常"
    ((WARN++))
fi

# 检查数据库配置
if grep -q "url: jdbc:mysql:" src/main/resources/application.yml; then
    echo -e "${GREEN}✅${NC} MySQL 连接配置存在"
    ((PASS++))
else
    echo -e "${YELLOW}⚠️${NC} MySQL 连接配置异常"
    ((WARN++))
fi

echo ""

# ═══════════════════════════════════════════════════════════════
echo -e "${BLUE}8. 综合诊断结果${NC}"
echo "═══════════════════════════════════════════════════════════"
echo ""

TOTAL=$((PASS + WARN + FAIL))

echo -e "总检查项: $TOTAL"
echo -e "  ${GREEN}✅ 通过: $PASS${NC}"
echo -e "  ${YELLOW}⚠️ 警告: $WARN${NC}"
echo -e "  ${RED}❌ 失败: $FAIL${NC}"
echo ""

if [ $FAIL -eq 0 ]; then
    if [ $WARN -eq 0 ]; then
        echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
        echo -e "${GREEN}🎉 系统就绪！可以启动 WePoker${NC}"
        echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
        echo ""
        echo "推荐命令:"
        echo "  chmod +x start.sh && ./start.sh"
        exit 0
    else
        echo -e "${YELLOW}════════════════════════════════════════════════════════════${NC}"
        echo -e "${YELLOW}⚠️ 系统可以运行，但有一些警告${NC}"
        echo -e "${YELLOW}════════════════════════════════════════════════════════════${NC}"
        echo ""
        echo "可能的问题:"
        echo "  - 某些可选组件未安装"
        echo "  - 应用尚未启动（检查显示端口未开放时正常）"
        echo "  - 首次运行需要下载依赖"
        echo ""
        echo "继续启动尝试:"
        echo "  chmod +x start.sh && ./start.sh"
        exit 0
    fi
else
    echo -e "${RED}════════════════════════════════════════════════════════════${NC}"
    echo -e "${RED}❌ 检测到关键问题，无法启动${NC}"
    echo -e "${RED}════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo "需要修复的问题:"
    echo ""
    
    check_command "Java" "java" "yes" 2>/dev/null || echo "  1. 安装 Java 21+ (https://jdk.java.net/21/)"
    command -v mvn &> /dev/null || echo "  2. 安装 Maven (https://maven.apache.org/)"
    command -v docker &> /dev/null || echo "  3. 安装 Docker Desktop (https://www.docker.com/)"
    command -v docker-compose &> /dev/null || echo "  4. 安装 Docker Compose"
    
    echo ""
    echo "安装指南:"
    echo "  - Java: https://jdk.java.net/21/"
    echo "  - Maven: https://maven.apache.org/download.html"
    echo "  - Docker: https://docs.docker.com/get-docker/"
    exit 1
fi
