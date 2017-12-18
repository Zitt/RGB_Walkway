package com.pinball_mods.walkwayrgb;

import android.app.Activity;
import android.os.Bundle;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.I2cBusDriver;
import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.graphics.Color;
import android.view.View;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Locale;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 * <p>
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */
public class HomeActivity extends Activity {
    private static final String TAG = HomeActivity.class.getSimpleName();

    private Handler mHandler = new Handler();

    //private Apa102 mApa102;
    private I2cDevice mI2CDevice = null;
    private static final int LIDAR_I2C_Address = 0x62;

    private Handler mLidarHandler = new Handler();
    private int LIDAR_CHK_Interval = 500;
    private int nLidarCnt = 0;
    private int LIDAR_UNITID = 0;
    private Gpio mLidarEnable = null;
    private Gpio mLidarModeCtrl = null;

    // LED configuration.
    /*private static final int NUM_LEDS = 8;
    private static final int LED_BRIGHTNESS = 5; // 0 ... 31
    private static final Apa102.Mode LED_MODE = Apa102.Mode.BGR;
    private int[] mLedColors;
    private int mFrame = 0;
    private int nFrameCnt = 0;

    // Animation configuration.
    private static final int FRAME_DELAY_MS = 100; // 10fps*/

    private static final String DEVICE_RPI3 = "rpi3";


    public static String getSPIPort(byte ss) {
        switch (Build.DEVICE) {
            // same for Edison Arduino breakout and Edison SOM
            case "edison":
                return "SPI2.1";

            case DEVICE_RPI3:
                return "SPI0";

            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);

        }
    }

    public static String getI2CPort(PeripheralManagerService pms) {
        switch (Build.DEVICE) {
            // same for Edison Arduino breakout and Edison SOM
            case "edison":
                List<String> deviceList = pms.getI2cBusList();
                if (deviceList.contains("I2C1"))  return "I2C1";
                if (deviceList.contains("I2C6"))  return "I2C6";

            case DEVICE_RPI3:
                return "I2C1";

            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Log.i(TAG, "Starting BlinkActivity");
        PeripheralManagerService service = new PeripheralManagerService();

        Log.i(TAG, "Device: " + Build.DEVICE);

        List<String> deviceList = service.getSpiBusList();
        Log.i(TAG, "List of available SPI devices: " + deviceList);

        deviceList = service.getI2cBusList();
        Log.i(TAG, "List of available I2C devices: " + deviceList);

        Log.i(TAG, "List of available GPIO: " + service.getGpioList());

        Log.i(TAG, "Searching for LiDAR on " + getI2CPort(service));

        for (int address = 0x60; address < 0x70; address++) {
            //auto-close the devices
            try (final I2cDevice device = service.openI2cDevice(getI2CPort(service), address)) {
                try {
                    Byte b = device.readRegByte(0x1);
                    Log.i(TAG, String.format(Locale.US, "Trying: 0x%02X - SUCCESS (0x%02x)", address, b));
                } catch (final IOException e) {
                    Log.i(TAG, String.format(Locale.US, "Trying: 0x%02X - FAIL", address));
                }
                device.close();
            } catch (final Exception e) {
                //in case the openI2cDevice(name, address) fails
                Log.i(TAG, String.format(Locale.US, "Unable to search %s for LIDAR @ 0x%02X\n", getI2CPort(service), address), e);
                break;
            }

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove pending blink Runnable from the handler.
        //mHandler.removeCallbacks(mBlinkRunnable);
    }

}
