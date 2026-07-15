#!/bin/bash
# SleepWatch 一键打包脚本

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/AppData/Local/Android/Sdk}"

echo "========================================"
echo "  SleepWatch 一键打包脚本"
echo "========================================"
echo ""

if [ ! -d "$ANDROID_HOME" ]; then
    echo "[错误] 未找到 Android SDK: $ANDROID_HOME"
    echo "请安装 Android SDK 或设置 ANDROID_HOME 环境变量"
    exit 1
fi

echo "[1/4] 清理旧构建..."
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" clean

echo ""
echo "[2/4] 运行单元测试..."
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" testDebugUnitTest

echo ""
echo "[3/4] 打包 Debug APK..."
"$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" assembleDebug

echo ""
echo "[4/4] 复制 APK 到项目根目录..."
cp "$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk" "$SCRIPT_DIR/SleepWatch-debug.apk"

echo ""
echo "========================================"
echo "  打包完成！"
echo "========================================"
echo ""
echo "  APK 位置: $SCRIPT_DIR/SleepWatch-debug.apk"
echo ""
echo "  安装到手机:"
echo "  adb install SleepWatch-debug.apk"
echo ""
