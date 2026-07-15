@echo off
chcp 65001 >nul
echo ========================================
echo   SleepWatch 一键打包脚本
echo ========================================
echo.

set PROJECT_DIR=%~dp0
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk

if not exist "%ANDROID_HOME%" (
    echo [错误] 未找到 Android SDK: %ANDROID_HOME%
    echo 请安装 Android SDK 或设置 ANDROID_HOME 环境变量
    pause
    exit /b 1
)

echo [1/4] 清理旧构建...
call "%PROJECT_DIR%gradlew.bat" -p "%PROJECT_DIR%" clean
if %errorlevel% neq 0 (
    echo [错误] 清理失败
    pause
    exit /b 1
)

echo.
echo [2/4] 运行单元测试...
call "%PROJECT_DIR%gradlew.bat" -p "%PROJECT_DIR%" testDebugUnitTest
if %errorlevel% neq 0 (
    echo [错误] 测试失败
    pause
    exit /b 1
)

echo.
echo [3/4] 打包 Debug APK...
call "%PROJECT_DIR%gradlew.bat" -p "%PROJECT_DIR%" assembleDebug
if %errorlevel% neq 0 (
    echo [错误] 打包失败
    pause
    exit /b 1
)

echo.
echo [4/4] 复制 APK 到项目根目录...
copy /Y "%PROJECT_DIR%app\build\outputs\apk\debug\app-debug.apk" "%PROJECT_DIR%SleepWatch-debug.apk" >nul
echo.

echo ========================================
echo   打包完成！
echo ========================================
echo.
echo   APK 位置: %PROJECT_DIR%SleepWatch-debug.apk
echo   大小: %~z1
echo.
echo   安装到手机:
echo   adb install SleepWatch-debug.apk
echo.
pause
