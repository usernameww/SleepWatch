# SleepWatch 开发进度

> 最后更新：2026-07-14

## 总体进度

| 阶段 | 状态 | 进度 |
|------|------|------|
| Phase 1 — 核心监测 (MVP) | 进行中 | 10% |
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

## 进行中

- [ ] Phase 1 源代码实现
  - [ ] 数据层：Entity（含 hasEmergency 字段）、DAO、Database（exportSchema）、DataStore（含 skippedDate/emergencyDate）、Repository
  - [ ] 领域层：Model、UseCase、Repository 接口
  - [ ] 服务层：MonitorService、AlarmScheduler、BroadcastReceiver
  - [ ] DI 模块：AppModule、DatabaseModule、RepositoryModule
  - [ ] UI 层：Theme、Navigation、HomeScreen、AlertActivity
  - [ ] 资源：strings.xml、themes.xml、AndroidManifest.xml

## 文件清单（已创建）

```
SleepWatch/
├── docs/
│   ├── REQUIREMENTS.md          ✅ 需求文档（含导航、紧急事项、通知渠道、测试策略）
│   └── PROGRESS.md              ✅ 进度文档
├── gradle/
│   └── libs.versions.toml       ✅ 版本目录
├── build.gradle.kts             ✅ 根构建
├── settings.gradle.kts          ✅ 设置
├── gradle.properties            ✅ 属性
└── app/
    └── build.gradle.kts         ✅ 应用构建
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
