@echo off
chcp 65001 >nul
title SparkX 停止脚本

echo ========================================
echo   SparkX 校园智能问答平台 - 停止中...
echo ========================================
echo.

cd /d "%~dp0docker"
docker compose down

echo.
echo 容器已停止
pause
