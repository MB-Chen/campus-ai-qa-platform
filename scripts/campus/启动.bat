@echo off
chcp 65001 >nul
title SparkX 启动脚本

echo ========================================
echo   SparkX 校园智能问答平台 - 启动中...
echo ========================================
echo.

:: 检查 Docker 是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo [错误] Docker 未运行，请先启动 Docker Desktop
    pause
    exit /b 1
)

:: 进入 docker 目录并启动
cd /d "%~dp0docker"
echo [1/2] 启动容器...
docker compose up -d

if errorlevel 1 (
    echo [错误] 容器启动失败
    pause
    exit /b 1
)

echo.
echo [2/2] 等待服务就绪...
timeout /t 5 /nobreak >nul

:: 显示容器状态
echo.
docker compose ps
echo.
echo ========================================
echo   启动完成！
echo   前端: http://localhost:8189
echo   后端: http://localhost:7026
echo   MinIO: http://localhost:9001
echo   Neo4j: http://localhost:7474
echo ========================================
echo.
pause
