@echo off
REM WePoker Windows 一键启动脚本
REM 使用方式: start.bat

setlocal enabledelayedexpansion
chcp 65001 > nul
cls

echo 🚀 WePoker 服务启动中...
echo.

REM 检查 Docker
echo 📋 检查环境...

where docker >nul 2>nul
if errorlevel 1 (
    echo ❌ 错误: Docker 未安装
    echo 请访问 https://docs.docker.com/get-docker/ 安装 Docker Desktop
    pause
    exit /b 1
)

where docker-compose >nul 2>nul
if errorlevel 1 (
    echo ❌ 错误: Docker Compose 未安装
    pause
    exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
    echo ❌ 错误: Maven 未安装
    pause
    exit /b 1
)

echo ✅ Docker 已安装
echo ✅ Docker Compose 已安装
echo ✅ Maven 已安装
echo.

REM 选择启动模式
echo 选择启动模式:
echo 1) 完整启动 (Docker Compose + Spring Boot) - 推荐
echo 2) 仅启动中间件 (Docker Compose only) - 用于本地开发
echo 3) 清空并重新启动 - 用于故障排查
echo.
set /p choice="请输入选项 (1-3): "

if "%choice%"=="1" (
    goto :FULL_START
) else if "%choice%"=="2" (
    goto :MIDDLEWARE_ONLY
) else if "%choice%"=="3" (
    goto :CLEAN_START
) else (
    echo ❌ 无效选项
    pause
    exit /b 1
)

:FULL_START
echo.
echo 🔧 启动模式: 完整启动 (Docker + Spring Boot)
echo.

echo 📦 启动 MySQL + Redis + 管理工具...
call docker-compose up -d

if errorlevel 1 (
    echo ❌ Docker Compose 启动失败
    pause
    exit /b 1
)

echo ✅ Docker 服务已启动
echo.
echo ⏳ 等待数据库初始化（约 15 秒）...
timeout /t 15 /nobreak

echo 📝 初始化数据库...
REM 读取 schema.sql 内容并执行
for /f "delims=" %%i in (schema.sql) do (
    echo %%i | docker exec -i wepoker-mysql mysql -u root -proot
)

echo ✅ 数据库初始化完成
echo.

echo 🔨 编译 WePoker 应用...
call mvn clean package -q

if errorlevel 1 (
    echo ❌ 编译失败
    pause
    exit /b 1
)

echo ✅ 编译完成
echo.

echo 🎮 启动 WePoker 应用服务...
call mvn spring-boot:run
goto :END

:MIDDLEWARE_ONLY
echo.
echo 🔧 启动模式: 仅启动中间件
echo.

echo 📦 启动 MySQL + Redis + 管理工具...
call docker-compose up -d

if errorlevel 1 (
    echo ❌ Docker Compose 启动失败
    pause
    exit /b 1
)

echo ✅ Docker 服务已启动
echo.
echo ⏳ 等待数据库初始化（约 15 秒）...
timeout /t 15 /nobreak

echo 📝 初始化数据库...
REM 读取 schema.sql 内容并执行
for /f "delims=" %%i in (schema.sql) do (
    echo %%i | docker exec -i wepoker-mysql mysql -u root -proot
)

echo ✅ 数据库初始化完成
echo.

echo 📊 管理界面访问地址:
echo   MySQL 管理 (phpMyAdmin): http://localhost:8081
echo   账户: root
echo   密码: root
echo.
echo   Redis 管理 (Redis Commander): http://localhost:8082
echo.
echo ⚠️ 提示: 应用服务未启动，请打开另一个命令窗口运行:
echo   mvn spring-boot:run
echo.

call docker-compose logs -f
goto :END

:CLEAN_START
echo.
echo 🧹 清空所有数据并重新启动...
echo.
set /p confirm="确定要清空所有数据吗？(yes/no): "

if not "%confirm%"=="yes" (
    echo 已取消
    exit /b 0
)

echo 🛑 停止所有服务...
call docker-compose down -v

echo 🗑️ 清除编译输出...
call mvn clean -q

echo.
echo 🔄 重新启动...
echo 📦 启动 MySQL + Redis...
call docker-compose up -d

echo ✅ Docker 服务已启动
echo.
echo ⏳ 等待数据库启动（约 15 秒）...
timeout /t 15 /nobreak

echo 📝 初始化数据库...
REM 读取 schema.sql 内容并执行
for /f "delims=" %%i in (schema.sql) do (
    echo %%i | docker exec -i wepoker-mysql mysql -u root -proot
)

echo ✅ 数据库已重置
echo.

echo 🔨 重新编译...
call mvn clean package -q

echo ✅ 所有服务已就绪
echo.

echo 🎮 启动应用...
call mvn spring-boot:run

:END
echo.
echo ════════════════════════════════════════════════════════════
echo 🎉 WePoker 启动成功！
echo.
echo 📍 游戏地址: http://localhost:8080
echo 📊 API 地址: http://localhost:8080/api/game
echo.
echo 💡 下一步:
echo   1. 用浏览器打开上面的游戏地址
echo   2. 输入昵称和买入金额
echo   3. 复制房间链接发送给朋友
echo ════════════════════════════════════════════════════════════
echo.
pause
