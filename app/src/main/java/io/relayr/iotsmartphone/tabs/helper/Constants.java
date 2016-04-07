package io.relayr.iotsmartphone.tabs.helper;

public class Constants {

    public static final int REQUEST_RESOLVE_ERROR = 1000;
    public static final String ACTIVATE_PATH = "/activate";
    public static final String ACTIVATE = "activate";

    public static final String SENSOR_ACCEL_PATH = "/acceleration";
    public static final String SENSOR_ACCEL = "acceleration";
    public static final String SENSOR_BATTERY_PATH = "/battery";
    public static final String SENSOR_BATTERY = "battery";
    public static final String SENSOR_LIGHT_PATH = "/luminosity";
    public static final String SENSOR_LIGHT = "luminosity";
    public static final String SENSOR_TOUCH_PATH = "/touch";
    public static final String SENSOR_TOUCH = "touch";

    public enum DeviceType {PHONE, WATCH}

    public static class DeviceModelEvent {
        public DeviceModelEvent() {}
    }

    public static class WatchSelected {
        public WatchSelected() {}
    }
}
