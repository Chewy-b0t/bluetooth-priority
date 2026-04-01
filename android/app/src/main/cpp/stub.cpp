// Stub.cpp - Minimal JNI bridge stub
// The actual implementation is in the Rust library (libbluetooth_priority.so)

#include <jni.h>

// These functions are implemented in Rust (libbluetooth_priority.so)
// This stub just ensures the Android build system links correctly

extern "C" {
    JNIEXPORT void JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_initializeNative(
        JNIEnv* env, jclass clazz);
    
    JNIEXPORT jboolean JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_addPriorityDevice(
        JNIEnv* env, jclass clazz, jstring deviceAddress, jint rssiThreshold, jint priority);
    
    JNIEXPORT jboolean JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_processRssiUpdate(
        JNIEnv* env, jclass clazz, jstring deviceAddress, jint rssi);
    
    JNIEXPORT jboolean JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_isBluetoothDisabled(
        JNIEnv* env, jclass clazz);
    
    JNIEXPORT jstring JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_getStatus(
        JNIEnv* env, jclass clazz);
    
    JNIEXPORT void JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_setProximityThreshold(
        JNIEnv* env, jclass clazz, jint thresholdDbm);
    
    JNIEXPORT jboolean JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_removePriorityDevice(
        JNIEnv* env, jclass clazz, jstring deviceAddress);
}

// Stub implementations - Rust library overrides these
JNIEXPORT void JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_initializeNative(
    JNIEnv* env, jclass clazz) {
    // Implemented in Rust
}

JNIEXPORT jboolean JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_addPriorityDevice(
    JNIEnv* env, jclass clazz, jstring deviceAddress, jint rssiThreshold, jint priority) {
    // Implemented in Rust
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_processRssiUpdate(
    JNIEnv* env, jclass clazz, jstring deviceAddress, jint rssi) {
    // Implemented in Rust
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_isBluetoothDisabled(
    JNIEnv* env, jclass clazz) {
    // Implemented in Rust
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_getStatus(
    JNIEnv* env, jclass clazz) {
    // Implemented in Rust
    return env->NewStringUTF("Status: Not initialized");
}

JNIEXPORT void JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_setProximityThreshold(
    JNIEnv* env, jclass clazz, jint thresholdDbm) {
    // Implemented in Rust
}

JNIEXPORT jboolean JNICALL Java_com_bluetooth_1priority_BluetoothPriorityService_removePriorityDevice(
    JNIEnv* env, jclass clazz, jstring deviceAddress) {
    // Implemented in Rust
    return JNI_FALSE;
}
