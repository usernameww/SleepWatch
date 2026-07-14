# SleepWatch 开发进度

> 最后更新：2026-07-14

## 总体进度

| 阶段 | 状态 | 进度 |
|------|------|------|
| Phase 1 — 核心监测 (MVP) | 进行中 | 80% |
| Phase 2 — 消息与配置 | 未开始 | 0% |
| Phase 3 — 统计与分析 | 未开始 | 0% |
| Phase 4 — 成就与打磨 | 未开始 | 0% |

## 已完成

- [x] 需求分析与架构设计
- [x] 技术栈选型确认
- [x] Gradle 构建系统搭建
  - [x] `gradle/libs.versions.toml` 版本目录
  - [x] `build.gradle.kts` 根构建文件
  - [x] `settings.gradle.kts` 设置文件
  - [x] `gradle.properties` 属性文件
  - [x] `app/build.gradle.kts` 应用构建文件
- [x] 需求文档归档 (`docs/REQUIREMENTS.md`)
- [x] 需求文档补充完善（导航结构、紧急事项、通知渠道、状态机边界、Android 适配、测试策略）
- [x] Phase 1 源代码实现
  - [x] 数据层：Entity（含 hasEmergency）、DAO、Database、DataStore（含 skippedDate/emergencyDate）、Repository
  - [x] 领域层：Repository 接口、UseCase（GetSleepRecords、SaveSleepRecord、GetAlertMessages、CheckAchievements）
  - [x] DI 模块：AppModule、DatabaseModule、RepositoryModule
  - [x] 服务层：MonitorService、MonitorStateMachine、AlarmScheduler、ScreenReceiver、BootReceiver
  - [x] UI 层：Theme、Navigation（BottomNav 4 Tab）、HomeScreen、AlertActivity、占位页面
  - [x] 资源：AndroidManifest.xml、strings.xml、themes.xml、colors.xml
  - [x] Git 仓库初始化

## 进行中

- [ ] Phase 1 剩余工作
  - [ ] 缺少 gradle-wrapper.jar（需 Android Studio 或 Gradle 安装后生成）
  - [ ] 渐进式消息默认数据（AlertMessage 预设 5 级消息插入）
  - [ ] 真机测试验证
  - [ ] 权限引导页（Setup）实现

## 文件清单（已创建）

```
SleepWatch/
├── .gitignore                          ✅
├── gradlew / gradlew.bat               ✅
├── gradle/wrapper/                     ✅ (jar 需补充)
├── docs/
│   ├── REQUIREMENTS.md                 ✅ 需求文档
│   └── PROGRESS.md                     ✅ 进度文档
├── build.gradle.kts                    ✅ 根构建
├── settings.gradle.kts                 ✅ 设置
├── gradle.properties                   ✅ 属性
├── gradle/libs.versions.toml           ✅ 版本目录
└── app/
    ├── build.gradle.kts                ✅ 应用构建
    ├── proguard-rules.pro              ✅
    └── src/main/
        ├── AndroidManifest.xml         ✅
        ├── java/com/sleepwatch/
        │   ├── SleepWatchApp.kt        ✅
        │   ├── MainActivity.kt         ✅
        │   ├── di/{AppModule,DatabaseModule,RepositoryModule}.kt  ✅
        │   ├── data/                   ✅ (Entity, DAO, Database, DataStore, Repository)
        │   ├── domain/                 ✅ (Repository 接口, UseCase)
        │   ├── service/                ✅ (MonitorService, StateMachine, AlarmScheduler, Receivers)
        │   └── ui/                     ✅ (Theme, Navigation, Home, Alert, 占位页面)
        └── res/values/                 ✅ (strings, themes, colors)
```

## 待创建文件清单

```
项目根目录/
├── .gitignore
├── gradlew
├── gradlew.bat
├── gradle/wrapper/gradle-wrapper.jar
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/sleepwatch/
        │   ├── SleepWatchApp.kt
        │   ├── MainActivity.kt
        │   ├── di/
        │   │   ├── AppModule.kt
        │   │   ├── DatabaseModule.kt
        │   │   └── RepositoryModule.kt
        │   ├── data/
        │   │   ├── db/
        │   │   │   ├── SleepWatchDatabase.kt
        │   │   │   ├── entity/SleepRecord.kt, AlertMessage.kt, Achievement.kt
        │   │   │   └── dao/SleepRecordDao.kt, AlertMessageDao.kt, AchievementDao.kt
        │   │   ├── datastore/SettingsDataStore.kt
        │   │   └── repository/
        │   ├── domain/
        │   │   ├── model/
        │   │   ├── usecase/
        │   │   │   ├── MonitorUseCase.kt
        │   │   │   ├── SleepRecordUseCase.kt
        │   │   │   └── AchievementUseCase.kt
        │   │   └── repository/
        │   ├── service/
        │   │   ├── MonitorService.kt
        │   │   ├── MonitorStateMachine.kt
        │   │   ├── AlarmScheduler.kt
        │   │   └── receiver/
        │   ├── ui/
        │   │   ├── navigation/NavGraph.kt
        │   │   ├── theme/
        │   │   ├── home/
        │   │   ├── settings/
        │   │   ├── statistics/
        │   │   ├── achievements/
        │   │   ├── setup/
        │   │   └── alert/AlertActivity.kt
        │   └── util/
        └── res/
            ├── values/
            │   ├── strings.xml
            │   ├── themes.xml
            │   └── colors.xml
            ├── values-night/
            │   └── themes.xml
            ├── drawable/
            └── xml/
```
