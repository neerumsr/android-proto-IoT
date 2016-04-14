package io.relayr.iotsmartphone.utils;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.greenrobot.event.EventBus;
import io.relayr.android.RelayrSdk;
import io.relayr.iotsmartphone.IotApplication;
import io.relayr.iotsmartphone.storage.Constants;
import io.relayr.iotsmartphone.storage.Storage;
import io.relayr.java.helper.observer.ErrorObserver;
import io.relayr.java.helper.observer.SimpleObserver;
import io.relayr.java.model.AccelGyroscope;
import io.relayr.java.model.action.Reading;
import io.relayr.java.model.models.DeviceModel;
import io.relayr.java.model.models.error.DeviceModelsException;
import io.relayr.java.model.models.transport.Transport;
import rx.schedulers.Schedulers;

import static io.relayr.iotsmartphone.storage.Constants.DeviceType.PHONE;
import static io.relayr.iotsmartphone.storage.Constants.DeviceType.WATCH;

public class ReadingUtils {

    private static final String TAG = "ReadingUtils";

    private static float sWatchData;
    private static float sPhoneData;
    private static long sTimestamp;
    public static float sWatchSpeed;
    public static float sPhoneSpeed;

    public static final Map<String, LimitedQueue<Reading>> readingsPhone = new HashMap<>();
    public static final Map<String, LimitedQueue<Reading>> readingsWatch = new HashMap<>();

    static {
        initializeReadings();
    }

    public static void initializeReadings() {
        readingsPhone.clear();
        readingsPhone.put("acceleration", new LimitedQueue<Reading>(Constants.defaultSizes.get("acceleration")));
        readingsPhone.put("angularSpeed", new LimitedQueue<Reading>(Constants.defaultSizes.get("angularSpeed")));
        readingsPhone.put("luminosity", new LimitedQueue<Reading>(Constants.defaultSizes.get("luminosity")));
        readingsPhone.put("batteryLevel", new LimitedQueue<Reading>(Constants.defaultSizes.get("batteryLevel")));
        readingsPhone.put("touch", new LimitedQueue<Reading>(Constants.defaultSizes.get("touch")));
        readingsPhone.put("rssi", new LimitedQueue<Reading>(Constants.defaultSizes.get("rssi")));
        readingsPhone.put("location", new LimitedQueue<Reading>(Constants.defaultSizes.get("location")));
        readingsPhone.put("message", new LimitedQueue<Reading>(Constants.defaultSizes.get("message")));

        readingsWatch.clear();
        readingsWatch.put("acceleration", new LimitedQueue<Reading>(Constants.defaultSizes.get("acceleration")));
        readingsWatch.put("luminosity", new LimitedQueue<Reading>(Constants.defaultSizes.get("luminosity")));
        readingsWatch.put("batteryLevel", new LimitedQueue<Reading>(Constants.defaultSizes.get("batteryLevel")));
        readingsWatch.put("touch", new LimitedQueue<Reading>(Constants.defaultSizes.get("touch")));
    }

    public static boolean isComplex(String meaning) {
        return meaning.equals("acceleration") || meaning.equals("angularSpeed") || meaning.equals("luminosity");
    }

    public static void getReadings() {
        RelayrSdk.getDeviceModelsApi()
                .getDeviceModelById(Storage.MODEL_PHONE)
                .subscribeOn(Schedulers.io())
                .subscribe(new SimpleObserver<DeviceModel>() {
                    @Override public void error(Throwable e) {
                        Log.e(TAG, "PHONE model error");
                        e.printStackTrace();
                    }

                    @Override public void success(DeviceModel deviceModel) {
                        try {
                            final Transport transport = deviceModel.getLatestFirmware().getDefaultTransport();
                            Storage.instance().savePhoneReadings(transport.getReadings());
                            EventBus.getDefault().post(new Constants.DeviceModelEvent());
                        } catch (DeviceModelsException e) {
                            e.printStackTrace();
                        }
                    }
                });

        RelayrSdk.getDeviceModelsApi()
                .getDeviceModelById(Storage.MODEL_WATCH)
                .subscribeOn(Schedulers.io())
                .subscribe(new SimpleObserver<DeviceModel>() {
                    @Override public void error(Throwable e) {
                        Log.e(TAG, "WATCH model error");
                        e.printStackTrace();
                    }

                    @Override public void success(DeviceModel deviceModel) {
                        try {
                            final Transport transport = deviceModel.getLatestFirmware().getDefaultTransport();
                            Storage.instance().saveWatchReadings(transport.getReadings());
                            EventBus.getDefault().post(new Constants.DeviceModelEvent());
                        } catch (DeviceModelsException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    public static Reading createAccelReading(float x, float y, float z) {
        final AccelGyroscope.Acceleration acceleration = new AccelGyroscope.Acceleration();
        acceleration.x = x;
        acceleration.y = y;
        acceleration.z = z;
        return new Reading(0, System.currentTimeMillis(), "acceleration", "/", acceleration);
    }

    public static Reading createGyroReading(float x, float y, float z) {
        final AccelGyroscope.AngularSpeed angularSpeed = new AccelGyroscope.AngularSpeed();
        angularSpeed.x = x;
        angularSpeed.y = y;
        angularSpeed.z = z;
        return new Reading(0, System.currentTimeMillis(), "angularSpeed", "/", angularSpeed);
    }

    public static void publish(Reading reading) {
        ReadingUtils.readingsPhone.get(reading.meaning).add(reading);
        if (IotApplication.isVisible(PHONE))
            EventBus.getDefault().post(new Constants.ReadingRefresh(PHONE, reading.meaning));
        if (Storage.ACTIVITY_PHONE.get(reading.meaning)) {
            if (Storage.instance().getDeviceId(PHONE) == null) return;
            sPhoneData += new Gson().toJson(reading).getBytes().length + 100;
            RelayrSdk.getWebSocketClient()
                    .publish(Storage.instance().getDeviceId(PHONE), reading)
                    .subscribeOn(Schedulers.io())
                    .subscribe(new ErrorObserver<Void>() {
                        @Override public void error(Throwable e) {
                            Crashlytics.log(Log.ERROR, TAG, "publish phone reading - error");
                            e.printStackTrace();
                        }
                    });
        }
    }

    public static void publishWatch(DataItem dataItem) {
        final String path = dataItem.getUri().getPath();
        final DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
        if (Constants.DEVICE_INFO_PATH.equals(path)) {
            Storage.instance().saveWatchData(dataMap.getString(Constants.DEVICE_MANUFACTURER),
                    dataMap.getString(Constants.DEVICE_MODEL), dataMap.getInt(Constants.DEVICE_SDK));
        } else if (Constants.SENSOR_ACCEL_PATH.equals(path)) {
            final float[] array = dataMap.getFloatArray(Constants.SENSOR_ACCEL);
            publishWatch(createAccelReading(array[0], array[1], array[2]));
        } else if (Constants.SENSOR_BATTERY_PATH.equals(path)) {
            String batteryData = dataMap.getString(Constants.SENSOR_BATTERY);
            final long ts = Long.parseLong(batteryData.split("#")[0]);
            final float val = Float.parseFloat(batteryData.split("#")[1]);
            publishWatch(new Reading(0, ts, "batteryLevel", "/", val));
        } else if (Constants.SENSOR_LIGHT_PATH.equals(path)) {
            float val = dataMap.getFloat(Constants.SENSOR_LIGHT);
            publishWatch(new Reading(0, System.currentTimeMillis(), "luminosity", "/", val));
        } else if (Constants.SENSOR_TOUCH_PATH.equals(path)) {
            String touchData = dataMap.getString(Constants.SENSOR_TOUCH);
            final long ts = Long.parseLong(touchData.split("#")[0]);
            final boolean val = Boolean.parseBoolean(touchData.split("#")[1]);
            publishWatch(new Reading(0, ts, "touch", "/", val));
        }
    }

    private static void publishWatch(Reading reading) {
        ReadingUtils.readingsWatch.get(reading.meaning).add(reading);
        if (IotApplication.isVisible(WATCH))
            EventBus.getDefault().post(new Constants.ReadingRefresh(WATCH, reading.meaning));
        if (Storage.ACTIVITY_WATCH.get(reading.meaning)) {
            if (Storage.instance().getDeviceId(WATCH) == null) return;
            sWatchData += new Gson().toJson(reading.value).getBytes().length;
            RelayrSdk.getWebSocketClient()
                    .publish(Storage.instance().getDeviceId(WATCH), reading)
                    .subscribeOn(Schedulers.io())
                    .subscribe(new ErrorObserver<Void>() {
                        @Override public void error(Throwable e) {
                            Crashlytics.log(Log.ERROR, TAG, "publish watch reading - error");
                            e.printStackTrace();
                        }
                    });
        }
    }

    public static void publishLocation(Context context, Location location) {
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses.isEmpty()) return;

            Address obj = addresses.get(0);
            String address = obj.getCountryName() + ", ";
            address += obj.getAddressLine(1) + ", ";
            address += obj.getAddressLine(0);

            publish(new Reading(0, System.currentTimeMillis(), "location", "/", address));
        } catch (IOException e) {
            publish(new Reading(0, System.currentTimeMillis(), "location", "/", "Unresolved"));
            Crashlytics.log(Log.DEBUG, TAG, "Failed to get location.");
            e.printStackTrace();
        }
    }

    public static void calculateSpeeds() {
        final long now = System.currentTimeMillis();
        if (sTimestamp <= 0) {
            sTimestamp = now;
            return;
        }
        final float seconds = (now - sTimestamp) / 1000f;
        sWatchSpeed = sWatchData / seconds;
        sPhoneSpeed = sPhoneData / seconds;

        sWatchData = 0;
        sPhoneData = 0;
        sTimestamp = now;
    }

    public static void publishTouch(boolean active) {
        ReadingUtils.publish(new Reading(0, System.currentTimeMillis(), "touch", "/", active));
    }
}
