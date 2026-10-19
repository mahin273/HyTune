# HyTune: Android Kernel & SoC Tuning Engine

HyTune is an open-source, production-grade Android system tuning and hardware telemetry engine built for Android 8.0+ (API 26+) devices. It provides granular kernel parameter control across CPU, GPU, memory, storage, battery, and display subsystems with multi-tiered privilege backends, transactional safety rollback guards, and a Material 3 Jetpack Compose interface.

---

## 1. Core Architecture

HyTune implements a clean, layered architecture separating low-level hardware abstraction from reactive domain logic and presentation:

```mermaid
graph TD
    subgraph Presentation Layer (Jetpack Compose & Material 3)
        UI_Home[HomeScreen: Telemetry & Profiles]
        UI_Tune[TuneScreen: CPU, GPU, RAM, Storage, Display]
        UI_Apps[AppsScreen: App Manager & Freeze Groups]
        UI_Monitor[MonitorScreen: Floating HUD & Benchmark]
        UI_More[MoreScreen: Automation, Diagnostics, Backup & Settings]
    end

    subgraph Service & System Integration
        SVC_Daemon[HyTuneDaemonService: Foreground Lifecycle]
        SVC_Overlay[FloatingOverlayManager: Draggable WindowManager HUD]
        SVC_Tile[ProfileQuickTileService: Android Quick Settings]
        SVC_Widget[HyTuneWidgetProvider: Home Screen Glances]
        SVC_Receiver[BootCompletedReceiver & AutomationReceiver]
    end

    subgraph Domain Orchestration & Safety
        DOM_Profile[ProfileEngine: State Diffing & Rollback]
        DOM_Safety[SafetyManager: Path Whitelists & Bootloop Guard]
        DOM_Auto[AutomationRulesEngine: Trigger-Condition-Action Evaluator]
        DOM_Diag[HardwareDiagnosticsEngine: Sensor & Display Health]
        DOM_Backup[BackupRestoreManager: JSON Schema Export & Import]
    end

    subgraph Persistence Layer
        DB_Room[(Room Database: 6 Migrated Tables)]
        DS_Pref[(DataStore Preferences: AppSettings)]
    end

    subgraph Core Hardware & Privilege Abstraction
        CORE_Priv[PrivilegeManager: Capability Resolution]
        CORE_Root[RootPrivilegeBackend: libsu Shell]
        CORE_Shizuku[ShizukuPrivilegeBackend: Binder IPC]
        CORE_Fallback[UnprivilegedBackend: Read-Only Fallback]
        SYS_Kernel[/Linux Kernel sysfs, procfs & devfreq Nodes/]
    end

    UI_Home & UI_Tune & UI_Apps & UI_Monitor & UI_More --> DOM_Profile & DOM_Safety & DOM_Auto & DOM_Diag & DOM_Backup
    SVC_Daemon & SVC_Overlay & SVC_Tile & SVC_Widget & SVC_Receiver --> DOM_Profile & DOM_Safety & DOM_Auto
    DOM_Profile & DOM_Safety & DOM_Auto & DOM_Diag & DOM_Backup --> DB_Room & DS_Pref
    DOM_Profile & DOM_Safety & DOM_Auto & DOM_Diag --> CORE_Priv
    CORE_Priv --> CORE_Root & CORE_Shizuku & CORE_Fallback
    CORE_Root & CORE_Shizuku & CORE_Fallback --> SYS_Kernel
```

---

## 2. Key Features & Subsystems

### Hardware Control & Tuning
- **CPU Clustering & Governors**: Per-cluster frequency range clamping, governor selection (`schedutil`, `performance`, `powersave`), and individual core onlining.
- **GPU Control**: Adreno (KGSL) and Mali (devfreq) clock manipulation, governor selection, and frequency lock.
- **Memory & ZRAM Lifecycle**: Dynamic ZRAM reallocation (`swapoff` -> disksize reconfiguration -> `mkswap` -> `swapon`), swappiness, cache dropping, and vfs_cache_pressure tuning.
- **Storage & I/O Schedulers**: Per-block-device queue schedulers (`bfq`, `kyber`, `none`, `mq-deadline`), read-ahead cache tuning, and automated `fstrim` trimming.
- **Battery & Charge Control**: Real-time current/voltage flow monitoring, configurable charging current caps, bypass charging toggle, and thermal zone throttling alarms.
- **Display Tweaks**: Dynamic resolution changing (`wm size`), density adjustment (`wm density`), display refresh rate locking, and automated 15-second revert guard on failure.

### System Safety & Recovery
- **Sysfs Path Whitelist**: Strict path validation prevents writes to non-whitelisted or dangerous kernel nodes.
- **Transactional State Diffing & Rollbacks**: Captures pre-apply baseline snapshots, generates atomic state diffs, and verifies writes via read-back assertions.
- **Bootloop Guard**: Tracks crash counters during boot; automatically reverts to factory stock baselines if a crash occurs within 60 seconds of reboot.
- **Panic Restore**: One-tap global revert restoring all sysfs parameters to their initial device baseline snapshot.

### Background Automation & Integration
- **Persistent Daemon Service**: Foreground service with notification-based profile switching and low-overhead periodic polling.
- **Foreground App Detection & Per-App Profiles**: Tiered detection (UsageStatsManager -> dumpsys -> cgroups) with 500ms debounce to automatically activate custom performance profiles and Do-Not-Disturb modes for games.
- **App Manager & Freezing Engine**: Package suspension, hiding, and user-0 uninstallation via root/Shizuku, with screen-off auto-freeze workers for background battery savings.
- **Floating HUD & Benchmark Recorder**: Draggable overlay window displaying real-time FPS, CPU/GPU loads, and battery wattage, coupled with a session benchmark recorder calculating average FPS, 1% low, and 0.1% low metrics.
- **Automation Rules Engine**: Flexible Trigger-Condition-Action evaluation responding to battery level, charging states, temperatures, and display events.
- **Hardware Diagnostics**: Sensor accuracy test suite and interactive display dead-pixel/touch latency inspection tools.
- **Backup & Migration**: Complete JSON backup/restore with automated Room database migrations across versions 1 through 6.
- **Accessibility & Polish**: Strict WCAG compliance with 48dp minimum touch targets, TalkBack screen reader semantics, and centralized English string resources.

---

## 3. Privilege Architecture

HyTune detects available privileges on startup and dynamically configures capabilities:

| Privilege Level | Provider | Capabilities Available |
|---|---|---|
| **Root** | `libsu` | Full kernel sysfs read/write, cpufreq/devfreq governors, ZRAM, I/O schedulers, bypass charging, pm freeze, wm display adjustments. |
| **Shizuku** | `Shizuku API (Binder)` | Package manager freezing (`pm suspend/disable`), window manager display scaling (`wm size/density`), battery dumpsys. |
| **Unprivileged** | Safe Linux Fallback | Read-only `/proc/cpuinfo`, `/proc/stat`, `/proc/meminfo`, system telemetry, and diagnostics. |

---

## 4. Technical Specifications

- **Language**: Kotlin 2.1.10
- **UI Toolkit**: Jetpack Compose (BOM 2025.02.00) with Material 3
- **Dependency Injection**: Dagger Hilt 2.55
- **Concurrency**: Kotlin Coroutines & Reactive Flow
- **Local Database**: Room 2.6.1 with KSP code generation
- **Preferences**: Jetpack DataStore Preferences
- **Minimum SDK**: API 26 (Android 8.0 Oreo)
- **Target SDK**: API 35 (Android 15)

---

## 5. Verification & Testing

HyTune includes an extensive automated test suite of **472 unit tests across 72 test suites** verifying core engines, repositories, viewmodels, database migrations, and accessibility semantics with 100% test pass fidelity:

```bash
# Run debug unit test suite
./gradlew testDebugUnitTest

# Run release unit test suite
./gradlew testReleaseUnitTest

# Assemble debug APK
./gradlew assembleDebug

# Assemble release APK
./gradlew assembleRelease
```

---

## 6. License

This project is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
