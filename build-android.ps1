# Build script for Bluetooth Priority Android App
# This script builds the Rust library and Android APK

param(
    [string]$BuildType = "release",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$RustProject = Join-Path $ProjectRoot "bluetooth-priority"
$AndroidProject = Join-Path $ProjectRoot "android"

Write-Host "=== Bluetooth Priority Builder ===" -ForegroundColor Cyan

# Step 1: Build Rust library for Android targets
Write-Host "`n[1/3] Building Rust library for Android..." -ForegroundColor Yellow

$Targets = @(
    "aarch64-linux-android",
    "armv7-linux-androideabi",
    "x86_64-linux-android",
    "i686-linux-android"
)

foreach ($Target in $Targets) {
    Write-Host "  Building for $Target..."
    rustup target add $Target 2>$null
    cargo build --release --target $Target --manifest-path (Join-Path $RustProject "Cargo.toml")
}

# Step 2: Copy .so files to Android project
Write-Host "`n[2/3] Copying native libraries..." -ForegroundColor Yellow

$JniLibsDir = Join-Path $AndroidProject "app\src\main\jniLibs"
if (Test-Path $JniLibsDir -PathType Container) {
    Remove-Item $JniLibsDir -Recurse -Force
}

$TargetMap = @{
    "aarch64-linux-android" = "arm64-v8a"
    "armv7-linux-androideabi" = "armeabi-v7a"
    "x86_64-linux-android" = "x86_64"
    "i686-linux-android" = "x86"
}

foreach ($Target in $Targets) {
    $Arch = $TargetMap[$Target]
    $SourceDir = Join-Path $RustProject "target\$Target\release"
    $DestDir = Join-Path $JniLibsDir $Arch
    
    New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
    $SoFile = Join-Path $SourceDir "bluetooth_priority.dll"
    if (Test-Path $SoFile) {
        # On Windows, cargo produces .dll, but Android needs .so
        # Rename during copy
        Copy-Item $SoFile (Join-Path $DestDir "libbluetooth_priority.so") -Force
        Write-Host "  Copied $Arch library"
    }
}

# Step 3: Build Android APK
Write-Host "`n[3/3] Building Android APK..." -ForegroundColor Yellow

Push-Location $AndroidProject
try {
    if ($Clean) {
        .\gradlew.bat clean
    }
    .\gradlew.bat assemble$($BuildType.Substring(0,1).ToUpper() + $BuildType.Substring(1))
    Write-Host "`n Build successful!" -ForegroundColor Green
    Write-Host " APK location: app\build\outputs\apk\$BuildType\"
} finally {
    Pop-Location
}

Write-Host "`n=== Build Complete ===" -ForegroundColor Cyan
