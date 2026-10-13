# HyTune

HyTune is an open-source Android performance tuning and system monitoring application designed for AOSP and AOSP-based environments.

## Features
- Performance Modes (Powersave, Balance, Performance, Fast, Custom)
- Multi-tier privilege architecture (Root via libsu, Shizuku, and unprivileged fallback)
- CPU, GPU, Memory (ZRAM), and Storage I/O tuning
- Real-time system telemetry and zero-allocation Canvas sparklines
- Per-app performance profiling and automated mode switching
- App manager and freeze engine
- Floating monitor overlay and FPS frame latency recorder
- Battery and thermal management
- Automation rules engine
- Safe by default: transactional state diffing, read-back verification, and boot-loop recovery guards

## Architecture
- Tech Stack: Kotlin, Jetpack Compose, Material 3, Coroutines, Flow, Hilt DI, Room DB, DataStore.
- Package Name: `com.mahin.hytune`
- Minimum SDK: 26 (Android 8.0 Oreo)
