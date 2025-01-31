package com.pinball_mods.zitt.edisontest;


import android.app.Activity;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.graphics.Color;
import android.view.View;

import com.google.android.things.contrib.driver.apa102.Apa102;

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
    private static final int INTERVAL_BETWEEN_BLINKS_MS = 1000;

    private Handler mHandler = new Handler();
    private Gpio mLedGpio;
    private boolean mLedState = false;

    private Apa102 mApa102;
    private I2cDevice mI2CDevice = null;
    private static final int LIDAR_I2C_Address = 0x62;

    private Handler mLidarHandler = new Handler();
    private int LIDAR_CHK_Interval = 500;
    private int nLidarCnt = 0;
    private int LIDAR_UNITID = 0;
    private Gpio mLidarEnable = null;
    private Gpio mLidarModeCtrl = null;

    // LED configuration.
    private static final int NUM_LEDS = 8;
    private static final int LED_BRIGHTNESS = 5; // 0 ... 31
    private static final Apa102.Mode LED_MODE = Apa102.Mode.BGR;
    private int[] mLedColors;
    private int mFrame = 0;
    private int nFrameCnt = 0;

    // Animation configuration.
    private static final int FRAME_DELAY_MS = 100; // 10fps


    public static String getSPIPort() {
        switch (Build.DEVICE) {
            // same for Edison Arduino breakout and Edison SOM
            case "edison":
                return "SPI2.1";

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

            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);

        }
    }

    private static final String apa102BusName = getSPIPort();

    private void Exit(View v) {
        System.runFinalizersOnExit(true);
        System.exit(0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        Log.i(TAG, "Starting BlinkActivity");
        PeripheralManagerService service = new PeripheralManagerService();

        /*try {
            //String pinName = BoardDefaults.getGPIOForLED();
            //mLedGpio = service.openGpio(pinName);
            mLedGpio = service.openGpio("IO13");
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Log.i(TAG, "Start blinking LED GPIO pin");

            // Post a Runnable that continuously switch the state of the GPIO, blinking the
            // corresponding LED
            mHandler.post(mBlinkRunnable);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
        */
        mLedGpio = null;

        List<String> deviceList = service.getSpiBusList();
        Log.i(TAG, "List of available SPI devices: " + deviceList);

        deviceList = service.getI2cBusList();
        Log.i(TAG, "List of available I2C devices: " + deviceList);

        Exit(null);

        mLedColors = new int[NUM_LEDS];
        Arrays.fill(mLedColors, Color.WHITE);

        /*try {
            Log.d(TAG, "Initializing LED strip");
            mApa102 = new Apa102(apa102BusName, LED_MODE);
            mApa102.setBrightness(LED_BRIGHTNESS);
            mHandler.post(mBlinkRunnable);
        } catch (IOException e) {
            // couldn't configure the device...
            Log.i(TAG, "Unable to configure " + apa102BusName + " for APA102 led strip", e);*/
            mApa102 = null;
        //}

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
            } catch (final IOException e) {
                //in case the openI2cDevice(name, address) fails
            }

        }

        try {
            Log.d(TAG, "Initializing LIDAR");
            mI2CDevice = service.openI2cDevice(getI2CPort(service), LIDAR_I2C_Address);

            try {
                mI2CDevice.readRegByte(0x1);

            } catch (IOException e) {
                Log.e(TAG, "LIDAR read Failed: ", e);
                /*mI2CDevice.close();
                mI2CDevice = null;
                return;*/
            }

            mLidarHandler.post(mLidarRunnable);
        } catch (IOException e) {
            // couldn't configure the device...
            Log.i(TAG, "Unable to configure " + getI2CPort(service) + " for LIDAR", e);
            mI2CDevice = null;
        }

       try {
            mLidarEnable = service.openGpio("IO3");
            mLidarEnable.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
            Log.i(TAG, "LIDAR set Enable High initially..");
            mLidarEnable.setActiveType(Gpio.ACTIVE_HIGH);
            mLidarEnable.setValue(false);

            mLidarModeCtrl = service.openGpio("IO2");
            mLidarModeCtrl.setDirection(Gpio.DIRECTION_IN);
            mLidarModeCtrl.close();

            mLidarEnable.setValue(true);
        } catch (IOException e) {
            Log.e(TAG, "Unable to configure LIDAR enable pin, high", e);
        }

        if (mLidarEnable != null) try {
            mLidarEnable.setValue(false);
            Thread.sleep(250);
            mLidarEnable.setValue(true);
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "Unable to disable LIDAR, low", e);
        }


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove pending blink Runnable from the handler.
        mHandler.removeCallbacks(mBlinkRunnable);
        mLidarHandler.removeCallbacks(mLidarRunnable);

        if (mLedGpio != null) {
            // Close the Gpio pin.
            Log.i(TAG, "Closing LED GPIO pin");

            try {
                mLedGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            } finally {
                mLedGpio = null;
            }
        }

        if (mApa102 != null) {
            // Close the Gpio pin.
            Log.i(TAG, "Closing Apa102");

            try {
                mApa102.close();
            } catch (IOException e) {
                // error closing LED strip
                Log.e(TAG, "Error on closing APA102 ", e);
            } finally {
                mApa102 = null;
            }
        }

        if (mI2CDevice  != null) {
            Log.i(TAG, "Closing I2C");

            try {
                mI2CDevice.close();
            } catch (IOException e) {
                // error closing LED strip
                Log.e(TAG, "Error on closing I2C", e);
            } finally {
                mI2CDevice = null;
            }
        }

        if (mLidarEnable != null) {
            try {
                mLidarEnable.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on closing LidarEnable", e);
            } finally {
                mLidarEnable = null;
            }
        }
    }

    private Runnable mLidarRunnable = new Runnable() {
        private static final byte ACQ_COMMAND = 0x00;
        private static final byte STATUS = 0x01;
        private static final byte ACQ_CONFIG_REG = 0x04;
        private static final int  FULL_DELAY_WORD = 0x8f;
        private static final int  UNIT_ID_WORD = 0x96;

        public int distance_cm = 0;

        @Override
        public void run() {
            if (mI2CDevice != null) {
                String strStep = "";
                try {
                    byte Acq_req = 0x03;

                    if (nLidarCnt == 0 ) {
                        mLidarEnable.setValue(true);

                        nLidarCnt += 1;
                        mLidarHandler.postDelayed(mLidarRunnable, 200);
                        return;
                    } else if ( nLidarCnt == 1 ) {

                        strStep = "reset";
                        mI2CDevice.writeRegByte(ACQ_COMMAND, (byte)0x0);

                        /*strStep = "rSTATUS1";
                        while ( (mI2CDevice.readRegByte(STATUS) & 1) == 1 ) {
                            //do nothing
                        }
                        Log.i(TAG, "Lidar not busy");*/
                        nLidarCnt += 1;
                        mLidarHandler.postDelayed(mLidarRunnable, 200);
                        return;
                    } else if ( nLidarCnt == 2 ) {
                        strStep = "rUID";

                        LIDAR_UNITID = mI2CDevice.readRegWord(UNIT_ID_WORD);
                        Log.i(TAG, String.format(Locale.US, "Lidar UNITID is 0x%04x", LIDAR_UNITID));

                        Acq_req = mI2CDevice.readRegByte(ACQ_CONFIG_REG);
                        Log.i(TAG, String.format(Locale.US, "Lidar ACQ_CONFIG_REG = (0x%02x)", Acq_req));

                        nLidarCnt += 1;
                        mLidarHandler.postDelayed(mLidarRunnable, 10);
                        return;
                    }

                    if ((nLidarCnt % 100) == 3) Acq_req = 0x4;
                    //1) Write Reg 0x04 with 0
                    strStep = "wACQ CMD " + Acq_req;
                    mI2CDevice.writeRegByte(ACQ_COMMAND, Acq_req);
                    //2) loop read r0x01 bit[0] until low
                    strStep = "rSTATUS2";
                    while ( (mI2CDevice.readRegByte(STATUS) & 1) == 1 ) {
                        //do nothing
                    }
                    //3) read word from 0x8f for 16bit distance in cm
                    strStep = "rCM";
                    distance_cm = mI2CDevice.readRegWord(FULL_DELAY_WORD);

                    Log.i(TAG, "Lidar distance = " + distance_cm + "cm");

                    nLidarCnt += 1;
                    if (nLidarCnt % 10 == 0) Log.i(TAG, "Lidar frame " + nLidarCnt);
                } catch (IOException e) {
                    Log.e(TAG, "Error while accessing LIDAR @" + strStep, e);

                    try { mLidarEnable.setValue(false); } catch (IOException e1) { };
                    mLidarHandler.postDelayed(mLidarRunnable, LIDAR_CHK_Interval*10);
                    try {
                        mLidarEnable.setValue(true);
                        Byte b = mI2CDevice.readRegByte(0x1);
                        Log.i(TAG, "LIDAR(0x1)=" + b);
                    } catch (IOException e1) { };
                    return;
                }
            }
            mLidarHandler.postDelayed(mLidarRunnable, LIDAR_CHK_Interval);
        };
    };


    private Runnable mBlinkRunnable = new Runnable() {
        final float[] hsv = {1f, 1f, 1f};

        @Override
        public void run() {
            // Exit Runnable if the GPIO is already closed
            if (mLedGpio != null) {
                try {
                    // Toggle the GPIO state
                    mLedState = !mLedState;
                    mLedGpio.setValue(mLedState);
                    Log.d(TAG, "State set to " + mLedState);

                    // Reschedule the same runnable in {#INTERVAL_BETWEEN_BLINKS_MS} milliseconds
                    mHandler.postDelayed(mBlinkRunnable, INTERVAL_BETWEEN_BLINKS_MS);
                } catch (IOException e) {
                    Log.e(TAG, "Error on PeripheralIO API", e);
                }
            }

            if (mApa102 != null) {
                try {
                    for (int i = 0; i < mLedColors.length; i++) { // Assigns gradient colors.
                        int n = (i + mFrame) % mLedColors.length;
                        hsv[0] = n * 360.f / mLedColors.length;
                        mLedColors[i] = Color.HSVToColor(0, hsv);
                    }

                    mApa102.write(mLedColors);
                    mFrame = (mFrame + 1) % mLedColors.length;
                    nFrameCnt += 1;
                    if (nFrameCnt % 10 == 0) Log.i(TAG, "Write Apa102 frame " + nFrameCnt);
                    if (nFrameCnt % 60 == 0) {
                        Random rnd = new Random();
                        mLedColors[0] = Color.argb(rnd.nextInt(255), rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
                        Log.i(TAG, "Setting LED[0] @ frm" + nFrameCnt + " = " +  mLedColors[0] );
                        for (int i = 1; i < mLedColors.length; i++) { // Assigns gradient colors.
                            //Color mColor = new Color();
                            int alpha = (mLedColors[i-1] >> 24) & 0xff; //i * (255 / mLedColors.length);
                            int r = (mLedColors[i-1] >> 16) & 0xff;
                            int b = (mLedColors[i-1] >>  8) & 0xff;
                            int g = (mLedColors[i-1]      ) & 0xff;

                            alpha -= (255 / mLedColors.length);
                            if (alpha < 0) alpha += 0xFF;
                            alpha &= 0xFF;

                            mLedColors[i] = Color.argb( alpha, r, g, b);
                        }
                        mApa102.write(mLedColors);
                        mHandler.postDelayed(mBlinkRunnable, FRAME_DELAY_MS*10);
                        return;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error while writing to LED strip", e);
                }
            }

            mHandler.postDelayed(mBlinkRunnable, FRAME_DELAY_MS);
        }
    };
}
