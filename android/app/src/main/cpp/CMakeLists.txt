# CMakeLists.txt for Bluetooth Priority JNI Bridge
# Note: This is a stub - the actual native library is built by Cargo

cmake_minimum_required(VERSION 3.4.1)

project("bluetooth_priority")

# The Rust build produces libbluetooth_priority.so
# We just need to link it here

# Find the pre-built Rust library
set(RUST_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../../target/${ANDROID_ABI}/release)

# Create imported library target for Rust code
add_library(bluetooth_priority SHARED IMPORTED)
set_target_properties(bluetooth_priority PROPERTIES
    IMPORTED_LOCATION ${RUST_LIB_DIR}/libbluetooth_priority.so)

# Android log library
find_library(log-lib log)

# The actual work is done by the Rust library
# This just ensures proper linking
add_library(native-bridge SHARED
    stub.cpp)

target_link_libraries(native-bridge
    bluetooth_priority
    ${log-lib}
    android)
