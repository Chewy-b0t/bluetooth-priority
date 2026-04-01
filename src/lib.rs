//! Bluetooth Priority Manager for Android 16
//! 
//! Disables your Bluetooth when you're near a priority device to give it access.
//! Uses RSSI-based proximity detection with configurable thresholds.

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, JNI_TRUE};
use log::LevelFilter;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use once_cell::sync::Lazy;

/// Priority device configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PriorityDevice {
    /// Bluetooth MAC address or device name
    pub identifier: String,
    /// RSSI threshold (dBm) - closer = higher (less negative)
    pub rssi_threshold: i16,
    /// Priority level (higher = more priority)
    pub priority: u8,
    /// Whether this device is currently detected
    #[serde(skip)]
    pub detected: bool,
    /// Last seen RSSI
    #[serde(skip)]
    pub last_rssi: Option<i16>,
}

/// Bluetooth state manager
pub struct BluetoothPriorityManager {
    /// List of priority devices to watch for
    priority_devices: HashMap<String, PriorityDevice>,
    /// Whether Bluetooth is currently disabled
    bluetooth_disabled: AtomicBool,
    /// Minimum RSSI to consider "nearby" (default: -70 dBm)
    proximity_threshold: i16,
    /// Debounce interval in milliseconds
    debounce_ms: u64,
}

static mut MANAGER: Lazy<BluetoothPriorityManager> = Lazy::new(|| {
    BluetoothPriorityManager {
        priority_devices: HashMap::new(),
        bluetooth_disabled: AtomicBool::new(false),
        proximity_threshold: -70,
        debounce_ms: 2000,
    }
});

/// Result of proximity scan
#[derive(Debug)]
pub struct ProximityResult {
    /// Device identifier
    pub device_id: String,
    /// Current RSSI
    pub rssi: i16,
    /// Whether device is within threshold
    pub is_nearby: bool,
    /// Priority level
    pub priority: u8,
}

impl BluetoothPriorityManager {
    pub fn new() -> Self {
        Self {
            priority_devices: HashMap::new(),
            bluetooth_disabled: AtomicBool::new(false),
            proximity_threshold: -70,
            debounce_ms: 2000,
        }
    }

    /// Add a priority device to watch
    pub fn add_priority_device(&mut self, device: PriorityDevice) {
        self.priority_devices.insert(device.identifier.clone(), device);
    }

    /// Remove a priority device
    pub fn remove_priority_device(&mut self, identifier: &str) {
        self.priority_devices.remove(identifier);
    }

    /// Process RSSI update from a Bluetooth device
    /// Returns true if Bluetooth should be disabled
    pub fn process_rssi_update(&mut self, device_address: &str, rssi: i16) -> bool {
        if let Some(device) = self.priority_devices.get_mut(device_address) {
            device.last_rssi = Some(rssi);
            device.detected = rssi >= device.rssi_threshold;

            // Check if any high-priority device is nearby
            let should_disable = self.check_priority_conflict();
            
            if should_disable && !self.bluetooth_disabled.load(Ordering::SeqCst) {
                self.bluetooth_disabled.store(true, Ordering::SeqCst);
                return true;
            } else if !should_disable && self.bluetooth_disabled.load(Ordering::SeqCst) {
                self.bluetooth_disabled.store(false, Ordering::SeqCst);
                return false;
            }
        }
        self.bluetooth_disabled.load(Ordering::SeqCst)
    }

    /// Check if any priority conflicts exist
    fn check_priority_conflict(&self) -> bool {
        for device in self.priority_devices.values() {
            if device.detected && device.last_rssi.unwrap_or(i16::MIN) >= self.proximity_threshold {
                return true;
            }
        }
        false
    }

    /// Get current status
    pub fn get_status(&self) -> String {
        let mut status = String::from("Bluetooth Priority Status:\n");
        status.push_str(&format!("  Bluetooth Disabled: {}\n", self.bluetooth_disabled.load(Ordering::SeqCst)));
        status.push_str(&format!("  Proximity Threshold: {} dBm\n", self.proximity_threshold));
        status.push_str("  Priority Devices:\n");
        
        for device in self.priority_devices.values() {
            status.push_str(&format!(
                "    - {}: priority={}, detected={}, rssi={:?}\n",
                device.identifier,
                device.priority,
                device.detected,
                device.last_rssi
            ));
        }
        status
    }
}

// JNI Functions for Android integration

#[no_mangle]
pub extern "system" fn Java_com_bluetooth_1priority_BluetoothPriorityService_initializeNative(
    _env: JNIEnv,
    _class: JClass,
) {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(LevelFilter::Debug)
            .with_tag("BluetoothPriority"),
    );
    log::info!("Bluetooth Priority Native Library Initialized");
}

#[no_mangle]
pub extern "system" fn Java_com_bluetooth_1priority_BluetoothPriorityService_addPriorityDevice(
    mut env: JNIEnv,
    _class: JClass,
    device_address: JString,
    rssi_threshold: jint,
    priority: jint,
) -> jboolean {
    let address: String = env.get_string(&device_address).unwrap().into();
    
    unsafe {
        let manager = &mut *std::ptr::addr_of_mut!(MANAGER);
        manager.add_priority_device(PriorityDevice {
            identifier: address.clone(),
            rssi_threshold: rssi_threshold as i16,
            priority: priority as u8,
            detected: false,
            last_rssi: None,
        });
    }
    
    log::info!("Added priority device: {} (threshold: {} dBm, priority: {})", 
               address, rssi_threshold, priority);
    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_com_bluetooth_1priority_BluetoothPriorityService_processRssiUpdate(
    mut env: JNIEnv,
    _class: JClass,
    device_address: JString,
    rssi: jint,
) -> jboolean {
    let address: String = env.get_string(&device_address).unwrap().into();
    
    unsafe {
        let manager = &mut *std::ptr::addr_of_mut!(MANAGER);
        manager.process_rssi_update(&address, rssi as i16)
    }
    .into()
}

#[no_mangle]
pub extern "system" fn Java_com_bluetooth_1priority_BluetoothPriorityService_isBluetoothDisabled(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    unsafe {
        let manager = &*std::ptr::addr_of_mut!(MANAGER);
        manager.bluetooth_disabled.load(Ordering::SeqCst)
    }
    .into()
}

#[no_mangle]
pub extern "system" fn Java_com_bluetooth_1priority_BluetoothPriorityService_getStatus<
    'a>(
    env: JNIEnv<'a>,
    _class: JClass<'a>,
) -> JString<'a> {
    unsafe {
        let manager = &*std::ptr::addr_of_mut!(MANAGER);
        let status = manager.get_status();
        env.new_string(status).unwrap()
    }
}

#[no_mangle]
pub extern "system" fn Java_com_bluetooth_1priority_BluetoothPriorityService_setProximityThreshold(
    _env: JNIEnv,
    _class: JClass,
    threshold_dbm: jint,
) {
    unsafe {
        let manager = &mut *std::ptr::addr_of_mut!(MANAGER);
        manager.proximity_threshold = threshold_dbm as i16;
    }
    log::info!("Proximity threshold set to {} dBm", threshold_dbm);
}

#[no_mangle]
pub extern "system" fn Java_com_bluetooth_1priority_BluetoothPriorityService_removePriorityDevice(
    mut env: JNIEnv,
    _class: JClass,
    device_address: JString,
) -> jboolean {
    let address: String = env.get_string(&device_address).unwrap().into();
    
    unsafe {
        let manager = &mut *std::ptr::addr_of_mut!(MANAGER);
        manager.remove_priority_device(&address);
    }
    
    log::info!("Removed priority device: {}", address);
    JNI_TRUE
}
