# Bluetooth Priority Manager - Android 16

Rust-powered Android app that automatically disables your Bluetooth when you're near a priority device, giving it priority access.

## Features

- **RSSI-based proximity detection** - Detects when you're near a priority Bluetooth device
- **Automatic Bluetooth toggle** - Disables your Bluetooth to avoid interference
- **Configurable thresholds** - Set custom RSSI thresholds per device
- **Priority levels** - Multiple devices with different priority levels
- **Background monitoring** - Runs as a foreground service
- **Debounced state changes** - Prevents rapid toggling

## How It Works

1. Add priority devices by MAC address or name
2. Set RSSI threshold (e.g., -70 dBm = ~10 feet)
3. Service scans for BLE devices in background
4. When priority device detected nearby → your Bluetooth disables
5. When priority device moves away → your Bluetooth re-enables

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Android App (Kotlin)                  │
│  ┌─────────────────┐  ┌───────────────────────────────┐ │
│  │   MainActivity  │  │  BluetoothPriorityService     │ │
│  │   (UI/Config)   │  │  (BLE Scanner, State Mgmt)    │ │
│  └────────┬────────┘  └───────────────┬───────────────┘ │
│           │                           │                  │
│           └─────────────┬─────────────┘                  │
│                         │ JNI                            │
└─────────────────────────┼────────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────────┐
│              Rust Native Library                          │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  BluetoothPriorityManager                           │ │
│  │  - Priority device tracking                         │ │
│  │  - RSSI processing & threshold logic                │ │
│  │  - State arbitration                                │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

## Building

### Prerequisites

```powershell
# Install Rust Android targets
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add x86_64-linux-android
rustup target add i686-linux-android

# Install Android NDK (via Android Studio or SDK Manager)
```

### Build Command

```powershell
cd projects\bluetooth-priority
.\build-android.ps1 -BuildType release
```

Output: `android\app\build\outputs\apk\release\app-release.apk`

## Usage

### 1. Install APK on Android 16 device

```bash
adb install app-release.apk
```

### 2. Grant Permissions

- Bluetooth Scan
- Bluetooth Connect
- Location (for BLE scanning)
- Foreground Service
- Notifications

### 3. Add Priority Device

```
Device Address: 00:1A:7D:DA:71:13  (or scan nearby devices)
RSSI Threshold: -70 dBm  (closer = higher/less negative)
Priority Level: 5 (1-10)
```

### 4. Start Monitoring

Service runs in background with persistent notification.

## RSSI Reference

| RSSI (dBm) | Distance | Use Case |
|------------|----------|----------|
| -40 to -50 | < 1m | Very close, same room |
| -50 to -60 | 1-3m | Close proximity |
| -60 to -70 | 3-10m | Medium range |
| -70 to -80 | 10-15m | Far range |
| -80+ | 15m+ | Edge of range |

## Configuration

### Proximity Threshold

Default: `-70 dBm`

Adjust via app or:
```kotlin
setProximityThreshold(-60)  // More strict (closer)
setProximityThreshold(-80)  // More lenient (farther)
```

### Debounce Delay

Default: `2000ms`

Prevents rapid Bluetooth toggle when RSSI fluctuates near threshold.

## Permissions

```xml
BLUETOOTH_SCAN (neverForLocation)
BLUETOOTH_CONNECT
ACCESS_FINE_LOCATION
FOREGROUND_SERVICE
FOREGROUND_SERVICE_CONNECTED_DEVICE
POST_NOTIFICATIONS
```

## Project Structure

```
bluetooth-priority/
├── Cargo.toml              # Rust dependencies
├── src/
│   └── lib.rs              # Native library (JNI)
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── kotlin/     # Kotlin source
│   │   │   ├── res/        # Android resources
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle
│   └── gradle/
├── build-android.ps1       # Build script
└── README.md
```

## Troubleshooting

### "Permission denied" errors
- Grant all Bluetooth and Location permissions
- Android 12+ requires explicit BLUETOOTH_CONNECT permission

### Not detecting devices
- Ensure target device is broadcasting BLE
- Lower RSSI threshold (more negative)
- Check location permission (required for BLE on Android 6-11)

### Bluetooth won't re-enable
- Check if priority device is still in range
- Increase debounce delay
- Check app notification for status

## License

MIT
