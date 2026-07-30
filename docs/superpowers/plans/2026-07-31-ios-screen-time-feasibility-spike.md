# SleepWatch iOS Screen Time Feasibility Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove on a physical iPhone that an individually authorized Device Activity monitor can observe all foreground activity at 1, 2, and 3 minute thresholds, persist callbacks through an App Group, and issue local notifications without the main app remaining open.

**Architecture:** Build a deterministic XcodeGen project with one SwiftUI app target, one Device Activity monitor extension, and one unit-test target. The app requests permissions and registers short nonrepeating schedules; the extension writes immutable JSON events into an App Group and requests immediate local notifications; the app reloads and displays those events.

**Tech Stack:** iOS 17+, Swift 6, SwiftUI, FamilyControls, DeviceActivity, UserNotifications, App Groups, XCTest, XcodeGen.

---

## Scope and gate

This is the first of three implementation plans:

1. This plan: platform feasibility spike.
2. Automatic supervision MVP: configuration, nightly records, home, settings, and reliable reconciliation.
3. Insights: statistics, scoring, achievements, migrations, and final visual polish.

Do not write plans 2 and 3 or begin their implementation until every physical-device acceptance check in Task 7 passes. A failure may require changing the approved architecture.

The current machine has Apple Command Line Tools and Swift 6.3.2, but not the full Xcode application. XcodeGen is also absent. Task 1 is therefore a hard prerequisite.

## File map

- Modify `.gitignore`: ignore iOS build products and Xcode user state.
- Create `ios/SleepWatchSpike/project.yml`: deterministic app, extension, tests, schemes, identifiers, and build settings.
- Create `ios/SleepWatchSpike/Config/App.entitlements`: Family Controls and App Group entitlements for the app.
- Create `ios/SleepWatchSpike/Config/MonitorExtension.entitlements`: matching entitlements for the extension.
- Create `ios/SleepWatchSpike/Sources/App/SleepWatchSpikeApp.swift`: SwiftUI application entry point.
- Create `ios/SleepWatchSpike/Sources/App/ContentView.swift`: permission, scheduling, density-check, and event-inspection UI.
- Create `ios/SleepWatchSpike/Sources/App/SpikeViewModel.swift`: main-actor orchestration and visible state.
- Create `ios/SleepWatchSpike/Sources/App/SpikeScheduler.swift`: Device Activity schedule registration and 48-event density validation.
- Create `ios/SleepWatchSpike/Sources/Shared/SpikeConstants.swift`: shared bundle-independent names and App Group identifier.
- Create `ios/SleepWatchSpike/Sources/Shared/SpikeThresholdPlan.swift`: validated cumulative threshold generation.
- Create `ios/SleepWatchSpike/Sources/Shared/SpikeWindowBuilder.swift`: deterministic minimum-interval schedule windows.
- Create `ios/SleepWatchSpike/Sources/Shared/SpikeEvent.swift`: Codable callback event model.
- Create `ios/SleepWatchSpike/Sources/Shared/SpikeEventFactory.swift`: stable event identifiers and threshold parsing.
- Create `ios/SleepWatchSpike/Sources/Shared/SharedEventStore.swift`: atomic App Group JSON persistence.
- Create `ios/SleepWatchSpike/Sources/Extension/SleepWatchMonitorExtension.swift`: Device Activity callback and notification implementation.
- Create `ios/SleepWatchSpike/Tests/SmokeTests.swift`: initial generated-project smoke test.
- Create `ios/SleepWatchSpike/Tests/SpikeThresholdPlanTests.swift`: threshold-plan tests.
- Create `ios/SleepWatchSpike/Tests/SpikeWindowBuilderTests.swift`: short-window tests.
- Create `ios/SleepWatchSpike/Tests/SharedEventStoreTests.swift`: event persistence and deduplication tests.
- Create `ios/SleepWatchSpike/Tests/SpikeEventFactoryTests.swift`: stable event mapping tests.

### Task 1: Install the Apple development prerequisites

**Files:**
- No repository files change.

- [ ] **Step 1: Install the full Xcode application**

Open the Mac App Store page:

```bash
open "macappstore://itunes.apple.com/app/id497799835"
```

Install Xcode, launch it once, allow it to install required components, and sign in to the Apple Account that owns the development team.

- [ ] **Step 2: Select Xcode and accept its license**

Run:

```bash
sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer
sudo xcodebuild -license accept
xcodebuild -version
swift --version
```

Expected: `xcodebuild -version` prints an Xcode version instead of reporting that `/Library/Developer/CommandLineTools` is active.

- [ ] **Step 3: Install XcodeGen**

Run:

```bash
brew install xcodegen
xcodegen --version
```

Expected: XcodeGen prints its installed version.

- [ ] **Step 4: Confirm a signing identity and connected iPhone prerequisites**

Run:

```bash
security find-identity -v -p codesigning
```

Expected: at least one valid `Apple Development` identity.

On the iPhone, enable Developer Mode, connect it to the Mac, trust the Mac, and confirm it appears under Xcode → Window → Devices and Simulators.

### Task 2: Generate and compile the app-extension project

**Files:**
- Modify: `.gitignore`
- Create: `ios/SleepWatchSpike/project.yml`
- Create: `ios/SleepWatchSpike/Config/App.entitlements`
- Create: `ios/SleepWatchSpike/Config/MonitorExtension.entitlements`
- Create: `ios/SleepWatchSpike/Sources/App/SleepWatchSpikeApp.swift`
- Create: `ios/SleepWatchSpike/Sources/App/ContentView.swift`
- Create: `ios/SleepWatchSpike/Sources/Shared/SpikeConstants.swift`
- Create: `ios/SleepWatchSpike/Sources/Extension/SleepWatchMonitorExtension.swift`
- Create: `ios/SleepWatchSpike/Tests/SmokeTests.swift`

- [ ] **Step 1: Add iOS generated-file ignores**

Append these exact lines to `.gitignore`:

```gitignore
/ios/**/DerivedData/
/ios/**/build/
/ios/**/*.xcodeproj/xcuserdata/
```

- [ ] **Step 2: Create the XcodeGen project definition**

Create `ios/SleepWatchSpike/project.yml`:

```yaml
name: SleepWatchSpike

options:
  bundleIdPrefix: com.ranran
  deploymentTarget:
    iOS: "17.0"
  createIntermediateGroups: true

settings:
  base:
    SWIFT_VERSION: "6.0"
    SWIFT_STRICT_CONCURRENCY: targeted
    CODE_SIGN_STYLE: Automatic
    CURRENT_PROJECT_VERSION: 1
    MARKETING_VERSION: 0.1.0

targets:
  SleepWatchSpike:
    type: application
    platform: iOS
    sources:
      - path: Sources/App
      - path: Sources/Shared
    info:
      path: Config/App-Info.plist
      properties:
        CFBundleDisplayName: SleepWatch Spike
        UILaunchScreen: {}
    entitlements:
      path: Config/App.entitlements
    dependencies:
      - target: SleepWatchMonitorExtension
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.ranran.sleepwatch.spike
        PRODUCT_NAME: SleepWatchSpike

  SleepWatchMonitorExtension:
    type: app-extension
    platform: iOS
    sources:
      - path: Sources/Extension
      - path: Sources/Shared
    info:
      path: Config/MonitorExtension-Info.plist
      properties:
        CFBundleDisplayName: SleepWatch Monitor
        NSExtension:
          NSExtensionPointIdentifier: com.apple.deviceactivity.monitor-extension
          NSExtensionPrincipalClass: $(PRODUCT_MODULE_NAME).SleepWatchMonitorExtension
    entitlements:
      path: Config/MonitorExtension.entitlements
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.ranran.sleepwatch.spike.monitor
        PRODUCT_NAME: SleepWatchMonitorExtension
        APPLICATION_EXTENSION_API_ONLY: YES
        SKIP_INSTALL: YES

  SleepWatchSpikeTests:
    type: bundle.unit-test
    platform: iOS
    sources:
      - path: Tests
    dependencies:
      - target: SleepWatchSpike
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: com.ranran.sleepwatch.spike.tests

schemes:
  SleepWatchSpike:
    build:
      targets:
        SleepWatchSpike: all
        SleepWatchMonitorExtension: all
        SleepWatchSpikeTests:
          - test
    run:
      config: Debug
    test:
      config: Debug
      gatherCoverageData: true
      targets:
        - name: SleepWatchSpikeTests
```

- [ ] **Step 3: Create matching app and extension entitlements**

Create `ios/SleepWatchSpike/Config/App.entitlements`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.developer.family-controls</key>
    <true/>
    <key>com.apple.security.application-groups</key>
    <array>
        <string>group.com.ranran.sleepwatch</string>
    </array>
</dict>
</plist>
```

Create `ios/SleepWatchSpike/Config/MonitorExtension.entitlements` with the same content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.developer.family-controls</key>
    <true/>
    <key>com.apple.security.application-groups</key>
    <array>
        <string>group.com.ranran.sleepwatch</string>
    </array>
</dict>
</plist>
```

- [ ] **Step 4: Create the minimum app entry point and view**

Create `ios/SleepWatchSpike/Sources/App/SleepWatchSpikeApp.swift`:

```swift
import SwiftUI

@main
struct SleepWatchSpikeApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

Create `ios/SleepWatchSpike/Sources/App/ContentView.swift`:

```swift
import SwiftUI

struct ContentView: View {
    var body: some View {
        NavigationStack {
            Text("SleepWatch Screen Time Spike")
                .padding()
                .navigationTitle("SleepWatch")
        }
    }
}
```

- [ ] **Step 5: Create shared identifiers and a compilable extension entry point**

Create `ios/SleepWatchSpike/Sources/Shared/SpikeConstants.swift`:

```swift
import DeviceActivity

enum SpikeConstants {
    static let appGroupID = "group.com.ranran.sleepwatch"
    static let activity = DeviceActivityName("sleepwatch-spike")
    static let densityActivity = DeviceActivityName("sleepwatch-density")
}

extension DeviceActivityEvent.Name {
    static func sleepWatchThreshold(_ minutes: Int) -> Self {
        Self("usage-\(minutes)")
    }
}
```

Create `ios/SleepWatchSpike/Sources/Extension/SleepWatchMonitorExtension.swift`:

```swift
import DeviceActivity

final class SleepWatchMonitorExtension: DeviceActivityMonitor {}
```

- [ ] **Step 6: Write the initial smoke test**

Create `ios/SleepWatchSpike/Tests/SmokeTests.swift`:

```swift
import XCTest
@testable import SleepWatchSpike

final class SmokeTests: XCTestCase {
    func test_testTargetLoadsAppModule() {
        XCTAssertEqual(2 + 2, 4)
    }
}
```

- [ ] **Step 7: Generate and compile the project**

Run:

```bash
cd ios/SleepWatchSpike
xcodegen generate
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 8: Commit the deterministic project skeleton**

```bash
git add .gitignore ios/SleepWatchSpike
git commit -m "build: scaffold iOS Screen Time spike"
```

### Task 3: Generate cumulative thresholds with pure tested logic

**Files:**
- Create: `ios/SleepWatchSpike/Sources/Shared/SpikeThresholdPlan.swift`
- Create: `ios/SleepWatchSpike/Tests/SpikeThresholdPlanTests.swift`

- [ ] **Step 1: Write the failing threshold tests**

Create `ios/SleepWatchSpike/Tests/SpikeThresholdPlanTests.swift`:

```swift
import XCTest
@testable import SleepWatchSpike

final class SpikeThresholdPlanTests: XCTestCase {
    func test_spikePlanGeneratesOneTwoThreeMinuteEvents() throws {
        XCTAssertEqual(
            try SpikeThresholdPlan.make(
                first: 1,
                repeatEvery: 1,
                ceiling: 3
            ),
            [1, 2, 3]
        )
    }

    func test_productShapeGeneratesFirstThenTenMinuteBoundaries() throws {
        XCTAssertEqual(
            try SpikeThresholdPlan.make(
                first: 1,
                repeatEvery: 10,
                ceiling: 35
            ),
            [1, 10, 20, 30]
        )
    }

    func test_planStopsAtConfiguredEventLimit() throws {
        XCTAssertEqual(
            try SpikeThresholdPlan.make(
                first: 1,
                repeatEvery: 1,
                ceiling: 100,
                maxEvents: 3
            ),
            [1, 2, 3]
        )
    }

    func test_invalidValuesAreRejected() {
        XCTAssertThrowsError(
            try SpikeThresholdPlan.make(
                first: 0,
                repeatEvery: 10,
                ceiling: 30
            )
        ) { error in
            XCTAssertEqual(error as? SpikeThresholdPlanError, .invalidRange)
        }
    }
}
```

- [ ] **Step 2: Run the tests and verify the missing-type failure**

Run:

```bash
cd ios/SleepWatchSpike
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  -only-testing:SleepWatchSpikeTests/SpikeThresholdPlanTests \
  test
```

Expected: compilation fails because `SpikeThresholdPlan` is not defined. If the installed simulator has a different name, select an available simulator shown by `xcrun simctl list devices available` and use that exact name for every simulator-test command in this plan.

- [ ] **Step 3: Implement the minimal threshold generator**

Create `ios/SleepWatchSpike/Sources/Shared/SpikeThresholdPlan.swift`:

```swift
import Foundation

enum SpikeThresholdPlanError: Error, Equatable {
    case invalidRange
}

enum SpikeThresholdPlan {
    static func make(
        first: Int,
        repeatEvery: Int,
        ceiling: Int,
        maxEvents: Int = 48
    ) throws -> [Int] {
        guard first >= 1,
              repeatEvery >= 1,
              ceiling >= first,
              maxEvents >= 1 else {
            throw SpikeThresholdPlanError.invalidRange
        }

        var result = [first]
        var next = repeatEvery
        while next <= first {
            next += repeatEvery
        }

        while next <= ceiling && result.count < maxEvents {
            result.append(next)
            next += repeatEvery
        }
        return result
    }
}
```

- [ ] **Step 4: Regenerate the project and run the tests**

Run:

```bash
cd ios/SleepWatchSpike
xcodegen generate
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  -only-testing:SleepWatchSpikeTests/SpikeThresholdPlanTests \
  test
```

Expected: `SpikeThresholdPlanTests` passes.

- [ ] **Step 5: Commit the tested threshold policy**

```bash
git add ios/SleepWatchSpike
git commit -m "feat: add iOS activity threshold planning"
```

### Task 4: Persist extension events atomically and idempotently

**Files:**
- Create: `ios/SleepWatchSpike/Sources/Shared/SpikeEvent.swift`
- Create: `ios/SleepWatchSpike/Sources/Shared/SharedEventStore.swift`
- Create: `ios/SleepWatchSpike/Tests/SharedEventStoreTests.swift`

- [ ] **Step 1: Write the failing shared-store tests**

Create `ios/SleepWatchSpike/Tests/SharedEventStoreTests.swift`:

```swift
import XCTest
@testable import SleepWatchSpike

final class SharedEventStoreTests: XCTestCase {
    private var directory: URL!

    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: directory)
    }

    func test_appendThenLoadRoundTripsEvent() throws {
        let store = try SharedEventStore(containerURL: directory)
        let event = SpikeEvent(
            id: "threshold_1",
            kind: .threshold,
            thresholdMinutes: 1,
            callbackAt: Date(timeIntervalSince1970: 1_000)
        )

        try store.append(event)

        XCTAssertEqual(try store.load(), [event])
    }

    func test_sameEventIDOverwritesInsteadOfDuplicating() throws {
        let store = try SharedEventStore(containerURL: directory)
        let first = SpikeEvent(
            id: "threshold_1",
            kind: .threshold,
            thresholdMinutes: 1,
            callbackAt: Date(timeIntervalSince1970: 1_000)
        )
        let duplicate = SpikeEvent(
            id: "threshold_1",
            kind: .threshold,
            thresholdMinutes: 1,
            callbackAt: Date(timeIntervalSince1970: 1_001)
        )

        try store.append(first)
        try store.append(duplicate)

        XCTAssertEqual(try store.load(), [duplicate])
    }

    func test_loadSortsEventsByCallbackTime() throws {
        let store = try SharedEventStore(containerURL: directory)
        let later = SpikeEvent(
            id: "threshold_2",
            kind: .threshold,
            thresholdMinutes: 2,
            callbackAt: Date(timeIntervalSince1970: 2_000)
        )
        let earlier = SpikeEvent(
            id: "threshold_1",
            kind: .threshold,
            thresholdMinutes: 1,
            callbackAt: Date(timeIntervalSince1970: 1_000)
        )

        try store.append(later)
        try store.append(earlier)

        XCTAssertEqual(try store.load(), [earlier, later])
    }
}
```

- [ ] **Step 2: Run the tests and verify the missing-type failure**

Run:

```bash
cd ios/SleepWatchSpike
xcodegen generate
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  -only-testing:SleepWatchSpikeTests/SharedEventStoreTests \
  test
```

Expected: compilation fails because `SpikeEvent` and `SharedEventStore` do not exist.

- [ ] **Step 3: Implement the event model**

Create `ios/SleepWatchSpike/Sources/Shared/SpikeEvent.swift`:

```swift
import Foundation

struct SpikeEvent: Codable, Equatable, Identifiable, Sendable {
    enum Kind: String, Codable, Sendable {
        case intervalStarted
        case threshold
        case intervalEnded
    }

    let id: String
    let kind: Kind
    let thresholdMinutes: Int?
    let callbackAt: Date
}
```

- [ ] **Step 4: Implement the atomic JSON store**

Create `ios/SleepWatchSpike/Sources/Shared/SharedEventStore.swift`:

```swift
import Foundation

enum SharedEventStoreError: Error {
    case appGroupUnavailable
}

struct SharedEventStore {
    private let eventsDirectory: URL
    private let fileManager: FileManager

    init(
        appGroupID: String = SpikeConstants.appGroupID,
        fileManager: FileManager = .default
    ) throws {
        guard let container = fileManager.containerURL(
            forSecurityApplicationGroupIdentifier: appGroupID
        ) else {
            throw SharedEventStoreError.appGroupUnavailable
        }
        try self.init(containerURL: container, fileManager: fileManager)
    }

    init(
        containerURL: URL,
        fileManager: FileManager = .default
    ) throws {
        self.fileManager = fileManager
        self.eventsDirectory = containerURL
            .appendingPathComponent("spike-events", isDirectory: true)
        try fileManager.createDirectory(
            at: eventsDirectory,
            withIntermediateDirectories: true
        )
    }

    func append(_ event: SpikeEvent) throws {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        let data = try encoder.encode(event)
        try data.write(to: eventURL(for: event.id), options: .atomic)
    }

    func load() throws -> [SpikeEvent] {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let urls = try fileManager.contentsOfDirectory(
            at: eventsDirectory,
            includingPropertiesForKeys: nil
        ).filter { $0.pathExtension == "json" }
        return try urls
            .map { try decoder.decode(SpikeEvent.self, from: Data(contentsOf: $0)) }
            .sorted { $0.callbackAt < $1.callbackAt }
    }

    func removeAll() throws {
        let urls = try fileManager.contentsOfDirectory(
            at: eventsDirectory,
            includingPropertiesForKeys: nil
        )
        for url in urls where url.pathExtension == "json" {
            try fileManager.removeItem(at: url)
        }
    }

    private func eventURL(for eventID: String) -> URL {
        let safeName = eventID.map { character in
            character.isLetter || character.isNumber
                ? character
                : Character("_")
        }
        return eventsDirectory
            .appendingPathComponent(String(safeName))
            .appendingPathExtension("json")
    }
}
```

- [ ] **Step 5: Regenerate and run the shared-store tests**

Run:

```bash
cd ios/SleepWatchSpike
xcodegen generate
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  -only-testing:SleepWatchSpikeTests/SharedEventStoreTests \
  test
```

Expected: `SharedEventStoreTests` passes.

- [ ] **Step 6: Commit event persistence**

```bash
git add ios/SleepWatchSpike
git commit -m "feat: persist iOS monitor callback events"
```

### Task 5: Request permissions and register short all-activity schedules

**Files:**
- Create: `ios/SleepWatchSpike/Sources/Shared/SpikeWindowBuilder.swift`
- Create: `ios/SleepWatchSpike/Tests/SpikeWindowBuilderTests.swift`
- Create: `ios/SleepWatchSpike/Sources/App/SpikeScheduler.swift`
- Create: `ios/SleepWatchSpike/Sources/App/SpikeViewModel.swift`
- Modify: `ios/SleepWatchSpike/Sources/App/ContentView.swift`

- [ ] **Step 1: Write the failing short-window tests**

Create `ios/SleepWatchSpike/Tests/SpikeWindowBuilderTests.swift`:

```swift
import XCTest
@testable import SleepWatchSpike

final class SpikeWindowBuilderTests: XCTestCase {
    func test_nextWindowStartsOnNextMinuteAndLastsSixteenMinutes() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = Date(timeIntervalSince1970: 1_735_689_630)

        let interval = try SpikeWindowBuilder.nextWindow(
            now: now,
            durationMinutes: 16,
            calendar: calendar
        )

        XCTAssertEqual(
            calendar.component(.second, from: interval.start),
            0
        )
        XCTAssertGreaterThan(interval.start, now)
        XCTAssertEqual(interval.duration, 16 * 60)
    }

    func test_windowShorterThanAppleMinimumIsRejected() {
        XCTAssertThrowsError(
            try SpikeWindowBuilder.nextWindow(
                now: Date(),
                durationMinutes: 14
            )
        ) { error in
            XCTAssertEqual(error as? SpikeWindowError, .intervalTooShort)
        }
    }
}
```

- [ ] **Step 2: Run the tests and verify the missing-type failure**

Run:

```bash
cd ios/SleepWatchSpike
xcodegen generate
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  -only-testing:SleepWatchSpikeTests/SpikeWindowBuilderTests \
  test
```

Expected: compilation fails because `SpikeWindowBuilder` is not defined.

- [ ] **Step 3: Implement the minimum-valid window builder**

Create `ios/SleepWatchSpike/Sources/Shared/SpikeWindowBuilder.swift`:

```swift
import Foundation

enum SpikeWindowError: Error, Equatable {
    case intervalTooShort
    case dateCalculationFailed
}

enum SpikeWindowBuilder {
    static func nextWindow(
        now: Date,
        durationMinutes: Int,
        calendar: Calendar = .current
    ) throws -> DateInterval {
        guard durationMinutes >= 15 else {
            throw SpikeWindowError.intervalTooShort
        }
        var nextMinute = calendar.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: now
        )
        nextMinute.minute = (nextMinute.minute ?? 0) + 1
        guard let start = calendar.date(from: nextMinute),
              let end = calendar.date(
                byAdding: .minute,
                value: durationMinutes,
                to: start
              ) else {
            throw SpikeWindowError.dateCalculationFailed
        }
        return DateInterval(start: start, end: end)
    }
}
```

- [ ] **Step 4: Implement Device Activity registration and density validation**

Create `ios/SleepWatchSpike/Sources/App/SpikeScheduler.swift`:

```swift
import DeviceActivity
import Foundation

struct SpikeScheduler {
    private let center = DeviceActivityCenter()

    func startShortSpike(now: Date = .now) throws -> DateInterval {
        let interval = try SpikeWindowBuilder.nextWindow(
            now: now,
            durationMinutes: 16
        )
        let thresholds = try SpikeThresholdPlan.make(
            first: 1,
            repeatEvery: 1,
            ceiling: 3
        )
        try start(
            activity: SpikeConstants.activity,
            interval: interval,
            thresholds: thresholds
        )
        return interval
    }

    func validateFortyEightEventDensity(now: Date = .now) throws -> Int {
        let interval = try SpikeWindowBuilder.nextWindow(
            now: now,
            durationMinutes: 12 * 60
        )
        let thresholds = try SpikeThresholdPlan.make(
            first: 1,
            repeatEvery: 15,
            ceiling: 705,
            maxEvents: 48
        )
        try start(
            activity: SpikeConstants.densityActivity,
            interval: interval,
            thresholds: thresholds
        )
        let registeredCount = center.events(
            for: SpikeConstants.densityActivity
        ).count
        center.stopMonitoring([SpikeConstants.densityActivity])
        return registeredCount
    }

    func stopAll() {
        center.stopMonitoring([
            SpikeConstants.activity,
            SpikeConstants.densityActivity
        ])
    }

    private func start(
        activity: DeviceActivityName,
        interval: DateInterval,
        thresholds: [Int]
    ) throws {
        let calendar = Calendar.current
        let startComponents = calendar.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: interval.start
        )
        let endComponents = calendar.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: interval.end
        )
        let schedule = DeviceActivitySchedule(
            intervalStart: startComponents,
            intervalEnd: endComponents,
            repeats: false
        )
        let events = Dictionary(uniqueKeysWithValues: thresholds.map { minutes in
            (
                DeviceActivityEvent.Name.sleepWatchThreshold(minutes),
                DeviceActivityEvent(
                    applications: [],
                    categories: [],
                    webDomains: [],
                    threshold: DateComponents(
                        hour: minutes / 60,
                        minute: minutes % 60
                    )
                )
            )
        })
        center.stopMonitoring([activity])
        try center.startMonitoring(
            activity,
            during: schedule,
            events: events
        )
    }
}
```

- [ ] **Step 5: Implement the permission and spike view model**

Create `ios/SleepWatchSpike/Sources/App/SpikeViewModel.swift`:

```swift
import Combine
import FamilyControls
import Foundation
import UserNotifications

@MainActor
final class SpikeViewModel: ObservableObject {
    @Published private(set) var status = "尚未授权"
    @Published private(set) var events: [SpikeEvent] = []
    @Published private(set) var scheduledWindow: DateInterval?

    private let scheduler = SpikeScheduler()

    func requestFamilyControls() async {
        do {
            try await AuthorizationCenter.shared.requestAuthorization(
                for: .individual
            )
            status = "Family Controls 已授权"
        } catch {
            status = "Family Controls 授权失败：\(error.localizedDescription)"
        }
    }

    func requestNotifications() async {
        do {
            let granted = try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound])
            status = granted ? "通知已授权" : "通知被拒绝"
        } catch {
            status = "通知授权失败：\(error.localizedDescription)"
        }
    }

    func startSpike() {
        do {
            scheduledWindow = try scheduler.startShortSpike()
            status = "短窗口已注册"
        } catch {
            status = "注册失败：\(error.localizedDescription)"
        }
    }

    func validateDensity() {
        do {
            let count = try scheduler.validateFortyEightEventDensity()
            status = "系统接受 \(count) 个阈值事件"
        } catch {
            status = "48 事件验证失败：\(error.localizedDescription)"
        }
    }

    func stop() {
        scheduler.stopAll()
        scheduledWindow = nil
        status = "监测已停止"
    }

    func refreshEvents() {
        do {
            events = try SharedEventStore().load()
        } catch {
            status = "读取共享事件失败：\(error.localizedDescription)"
        }
    }

    func clearEvents() {
        do {
            try SharedEventStore().removeAll()
            events = []
        } catch {
            status = "清除共享事件失败：\(error.localizedDescription)"
        }
    }
}
```

- [ ] **Step 6: Replace the spike UI**

Replace `ios/SleepWatchSpike/Sources/App/ContentView.swift` with:

```swift
import SwiftUI

struct ContentView: View {
    @StateObject private var model = SpikeViewModel()

    var body: some View {
        NavigationStack {
            List {
                Section("状态") {
                    Text(model.status)
                    if let window = model.scheduledWindow {
                        Text(
                            "\(window.start.formatted()) – \(window.end.formatted())"
                        )
                    }
                }

                Section("授权") {
                    Button("授权 Screen Time") {
                        Task { await model.requestFamilyControls() }
                    }
                    Button("授权通知") {
                        Task { await model.requestNotifications() }
                    }
                }

                Section("验证") {
                    Button("启动 1 / 2 / 3 分钟验证") {
                        model.startSpike()
                    }
                    Button("验证 48 个事件密度") {
                        model.validateDensity()
                    }
                    Button("停止监测", role: .destructive) {
                        model.stop()
                    }
                }

                Section("扩展事件") {
                    Button("刷新事件") {
                        model.refreshEvents()
                    }
                    Button("清除事件", role: .destructive) {
                        model.clearEvents()
                    }
                    ForEach(model.events) { event in
                        VStack(alignment: .leading) {
                            Text(event.kind.rawValue)
                            Text(event.callbackAt, format: .dateTime)
                            if let minutes = event.thresholdMinutes {
                                Text("累计使用 \(minutes) 分钟")
                            }
                        }
                    }
                }
            }
            .navigationTitle("SleepWatch Spike")
            .task {
                model.refreshEvents()
            }
        }
    }
}
```

- [ ] **Step 7: Regenerate, test, and compile**

Run:

```bash
cd ios/SleepWatchSpike
xcodegen generate
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  test
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Expected: all unit tests pass and the app plus extension compile.

- [ ] **Step 8: Commit authorization and scheduling**

```bash
git add ios/SleepWatchSpike
git commit -m "feat: schedule all-activity iOS spike"
```

### Task 6: Record extension callbacks and send notifications

**Files:**
- Create: `ios/SleepWatchSpike/Sources/Shared/SpikeEventFactory.swift`
- Create: `ios/SleepWatchSpike/Tests/SpikeEventFactoryTests.swift`
- Modify: `ios/SleepWatchSpike/Sources/Extension/SleepWatchMonitorExtension.swift`

- [ ] **Step 1: Write the failing event-factory tests**

Create `ios/SleepWatchSpike/Tests/SpikeEventFactoryTests.swift`:

```swift
import DeviceActivity
import XCTest
@testable import SleepWatchSpike

final class SpikeEventFactoryTests: XCTestCase {
    func test_thresholdNameMapsToStableEvent() {
        let callbackAt = Date(timeIntervalSince1970: 1_000)

        let event = SpikeEventFactory.threshold(
            activity: SpikeConstants.activity,
            eventName: .sleepWatchThreshold(2),
            callbackAt: callbackAt
        )

        XCTAssertEqual(event.id, "sleepwatch_spike_usage_2")
        XCTAssertEqual(event.kind, .threshold)
        XCTAssertEqual(event.thresholdMinutes, 2)
        XCTAssertEqual(event.callbackAt, callbackAt)
    }

    func test_intervalEventsUseStableIDs() {
        XCTAssertEqual(
            SpikeEventFactory.intervalStarted(
                activity: SpikeConstants.activity,
                callbackAt: Date(timeIntervalSince1970: 1_000)
            ).id,
            "sleepwatch_spike_interval_started"
        )
        XCTAssertEqual(
            SpikeEventFactory.intervalEnded(
                activity: SpikeConstants.activity,
                callbackAt: Date(timeIntervalSince1970: 2_000)
            ).id,
            "sleepwatch_spike_interval_ended"
        )
    }
}
```

- [ ] **Step 2: Run the tests and verify the missing-type failure**

Run:

```bash
cd ios/SleepWatchSpike
xcodegen generate
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  -only-testing:SleepWatchSpikeTests/SpikeEventFactoryTests \
  test
```

Expected: compilation fails because `SpikeEventFactory` does not exist.

- [ ] **Step 3: Implement stable callback mapping**

Create `ios/SleepWatchSpike/Sources/Shared/SpikeEventFactory.swift`:

```swift
import DeviceActivity
import Foundation

enum SpikeEventFactory {
    static func threshold(
        activity: DeviceActivityName,
        eventName: DeviceActivityEvent.Name,
        callbackAt: Date
    ) -> SpikeEvent {
        let threshold = thresholdMinutes(from: eventName)
        return SpikeEvent(
            id: "\(safe(activity.rawValue))_\(safe(eventName.rawValue))",
            kind: .threshold,
            thresholdMinutes: threshold,
            callbackAt: callbackAt
        )
    }

    static func intervalStarted(
        activity: DeviceActivityName,
        callbackAt: Date
    ) -> SpikeEvent {
        SpikeEvent(
            id: "\(safe(activity.rawValue))_interval_started",
            kind: .intervalStarted,
            thresholdMinutes: nil,
            callbackAt: callbackAt
        )
    }

    static func intervalEnded(
        activity: DeviceActivityName,
        callbackAt: Date
    ) -> SpikeEvent {
        SpikeEvent(
            id: "\(safe(activity.rawValue))_interval_ended",
            kind: .intervalEnded,
            thresholdMinutes: nil,
            callbackAt: callbackAt
        )
    }

    static func thresholdMinutes(
        from eventName: DeviceActivityEvent.Name
    ) -> Int? {
        let prefix = "usage-"
        guard eventName.rawValue.hasPrefix(prefix) else {
            return nil
        }
        return Int(eventName.rawValue.dropFirst(prefix.count))
    }

    private static func safe(_ value: String) -> String {
        String(value.map { character in
            character.isLetter || character.isNumber
                ? character
                : Character("_")
        })
    }
}
```

- [ ] **Step 4: Implement the Device Activity monitor extension**

Replace `ios/SleepWatchSpike/Sources/Extension/SleepWatchMonitorExtension.swift` with:

```swift
import DeviceActivity
import Foundation
import UserNotifications

final class SleepWatchMonitorExtension: DeviceActivityMonitor {
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        persist(
            SpikeEventFactory.intervalStarted(
                activity: activity,
                callbackAt: .now
            )
        )
    }

    override func eventDidReachThreshold(
        _ event: DeviceActivityEvent.Name,
        activity: DeviceActivityName
    ) {
        super.eventDidReachThreshold(event, activity: activity)
        let spikeEvent = SpikeEventFactory.threshold(
            activity: activity,
            eventName: event,
            callbackAt: .now
        )
        persist(spikeEvent)
        notify(for: spikeEvent)
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        persist(
            SpikeEventFactory.intervalEnded(
                activity: activity,
                callbackAt: .now
            )
        )
    }

    private func persist(_ event: SpikeEvent) {
        do {
            try SharedEventStore().append(event)
        } catch {
            NSLog("SleepWatch spike event write failed: \(error)")
        }
    }

    private func notify(for event: SpikeEvent) {
        guard let minutes = event.thresholdMinutes else {
            return
        }
        let content = UNMutableNotificationContent()
        content.title = "该准备休息了"
        content.body = "目标就寝时间后已累计使用手机 \(minutes) 分钟。"
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: event.id,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { error in
            if let error {
                NSLog("SleepWatch spike notification failed: \(error)")
            }
        }
    }
}
```

- [ ] **Step 5: Regenerate, run all tests, and compile both targets**

Run:

```bash
cd ios/SleepWatchSpike
xcodegen generate
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  test
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Expected: all tests pass and `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Commit the monitor extension**

```bash
git add ios/SleepWatchSpike
git commit -m "feat: notify on iOS activity thresholds"
```

### Task 7: Execute the physical-iPhone gate

**Files:**
- No repository files change unless a compile or runtime defect is found. Fix defects through a new failing test and a focused commit before repeating this gate.

- [ ] **Step 1: Generate the current project and open it**

Run:

```bash
cd ios/SleepWatchSpike
xcodegen generate
open SleepWatchSpike.xcodeproj
```

In Xcode, select both `SleepWatchSpike` and `SleepWatchMonitorExtension`, choose the signed-in Personal Team, and confirm that Family Controls and the `group.com.ranran.sleepwatch` App Group are present for both targets.

- [ ] **Step 2: Build the signed device products**

Run:

```bash
SLEEPWATCH_TEAM_ID="$(
  security find-identity -v -p codesigning |
  sed -n 's/.*(\([A-Z0-9]\{10\}\)).*/\1/p' |
  head -1
)"
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -destination "generic/platform=iOS" \
  DEVELOPMENT_TEAM="$SLEEPWATCH_TEAM_ID" \
  -allowProvisioningUpdates \
  build
```

Expected: the app and embedded extension sign successfully. If automatic provisioning reports that the bundle ID or App Group is unavailable, change the three bundle identifiers and App Group identifier consistently in `project.yml`, both entitlement files, and `SpikeConstants.appGroupID`; regenerate, add a focused commit, and repeat.

- [ ] **Step 3: Verify individual and notification authorization**

Run the app from Xcode on the connected iPhone.

1. Tap “授权 Screen Time”.
2. Approve individual authorization with Face ID or Touch ID.
3. Tap “授权通知”.
4. Allow alerts and sounds.

Expected: both actions report successful authorization.

- [ ] **Step 4: Verify the 48-event safety ceiling**

Tap “验证 48 个事件密度”.

Expected: the app reports `系统接受 48 个阈值事件`. Any `excessiveActivities`, invalid schedule, or lower count fails the gate and requires reducing the product event ceiling before writing the MVP plan.

- [ ] **Step 5: Verify all-activity threshold callbacks**

1. Clear previous events.
2. Tap “启动 1 / 2 / 3 分钟验证”.
3. Note the displayed start time.
4. Before the window starts, leave the main app.
5. From the start time onward, keep a different ordinary app in the foreground for more than three minutes.
6. Reopen SleepWatch Spike and tap “刷新事件”.

Expected:

- An interval-start event exists.
- Threshold events exist for 1, 2, and 3 minutes.
- The event list is ordered by callback time.
- No threshold is duplicated.
- Each threshold generated a visible local notification.

The callbacks do not need millisecond precision, but all three must arrive while the device remains actively used and before the 16-minute interval ends.

- [ ] **Step 6: Verify the main app does not need to remain alive**

1. Clear previous events.
2. Register a new short spike.
3. Force-quit the main app before the displayed start time.
4. Keep another app in the foreground for more than three minutes after the start.
5. Reopen SleepWatch Spike and refresh events.

Expected: 1, 2, and 3 minute events and notifications still exist. Failure means the approved architecture cannot yet be accepted.

- [ ] **Step 7: Verify the no-use control case**

1. Clear previous events.
2. Register a new short spike.
3. Lock the iPhone before the displayed start time.
4. Leave it locked for at least four minutes.

Expected: no 1, 2, or 3 minute late-use notification is delivered.

- [ ] **Step 8: Run final automated verification**

Run:

```bash
cd ios/SleepWatchSpike
xcodegen generate
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -destination "platform=iOS Simulator,name=iPhone 16 Pro" \
  test
xcodebuild \
  -project SleepWatchSpike.xcodeproj \
  -scheme SleepWatchSpike \
  -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Expected: all tests pass and the simulator build succeeds.

## Completion criteria

This spike is complete only when:

- Full Xcode and XcodeGen are installed and active.
- Simulator unit tests pass.
- App and extension compile together.
- Individual Family Controls authorization succeeds on the physical iPhone.
- Empty application/category/domain sets count all foreground activity.
- The system accepts 48 registered threshold events or the product ceiling is explicitly reduced in the approved design.
- The extension writes App Group events.
- The extension requests visible local notifications.
- 1, 2, and 3 minute callbacks occur while the main app is force-quit.
- Locking the phone produces no late-use threshold notification.

After these checks pass, write the Automatic Supervision MVP implementation plan against the proven project and API behavior.
