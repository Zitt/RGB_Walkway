package com.pinball_mods.walkwayrgb;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import com.google.android.things.pio.Gpio;
//import com.google.android.things.pio.I2cBusDevice;
import com.google.android.things.pio.I2cDevice;
//import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.PeripheralManager;
import android.os.Build;
import android.os.Handler;
import android.support.v4.graphics.ColorUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.android.things.contrib.driver.apa102.Apa102;

import static java.lang.Math.abs;
import static java.lang.Math.round;

import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.google.android.things.userdriver.input.InputDriver;
import com.google.android.things.userdriver.UserDriverManager;
import com.google.android.things.userdriver.input.InputDriverEvent;

import org.apache.commons.math3.analysis.function.Gaussian;

import javax.xml.transform.Result;


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

    private ZittsHandler mHandler = new ZittsHandler("mHandler");

    private Apa102 mApa102;
    private I2cDevice mI2CDevice = null;
    private static final int LIDAR_I2C_Address = 0x62;

    //private Handler mLidarHandler = new Handler();
    private ZittsHandler mLidarHandler = new ZittsHandler("mLidarHandler");
    private ZittsHandler mDateHandler = new ZittsHandler("mDateHandler");
    private int LIDAR_CHK_Interval = 500;
    private int nLidarCnt = 0;
    private Short LIDAR_UNITID = 0;
    private Gpio mLidarEnable = null;
    private Gpio mLidarModeCtrl = null;

    public class Lidar_cm extends AtomicInteger {
       private AtomicBoolean isStrong = new AtomicBoolean(false);
       private AtomicInteger lastError = new AtomicInteger(0);

       public boolean isValueStrong() { return isStrong.get(); }
       public void SetIsStrong(boolean bool) {
           isStrong.set(bool);
       }

       public boolean wasError() {
           final byte status = lastError.byteValue();
           //bit3 = invalid signal
           if ((status & 0b001000) == 0b001000) return true;
           return false;
       }
       public byte getLastError() { return lastError.byteValue(); }
       public void setLastError(byte val) { lastError.set( val ); }

       public Lidar_cm(int Val) {
         this.set(Val);
         isStrong.set(false);
         lastError.set(0);
       }
    }

    //private Short lidar_cm = 0;
    //private AtomicInteger lidar_cm = new AtomicInteger(0);
    private Lidar_cm lidar_cm = new Lidar_cm(0);
    public int distance() {
        return lidar_cm.get();
    }
    public double distance_in_meter() { return lidar_cm.get() / 100f; }

    public static double mm2in( double mm ) {
        return mm * 0.0393701;
    }

    public static double in2mm( double in ) {
        return in / 0.0393701;
    }

    public static double in2meter ( double in ) {
        return in2mm( in ) / 1000;
    }

    private LidarMeasurements lidarMeasurements= new LidarMeasurements();

    // LED configuration.
    // wiring: https://github.com/androidthings/drivers-samples/tree/master/apa102
    private short LED_BRIGHTNESS = Apa102.MAX_BRIGHTNESS >> 1; //5; // 0 ... 31
    private static final Apa102.Mode LED_MODE = Apa102.Mode.BGR;
    private int[] mLedColors;
    private int mFrame = 0;
    private int nFrameCnt = 0;

    // Animation configuration.
    private static final int FRAME_DELAY_MS = 100; // 10fps

    //Strip Configuration
    private int APAperMeter = 60;
    private float StripLenInMeter = 5.0f;
    private int NUM_LEDS = (int)(APAperMeter * StripLenInMeter);
    private double LedStartOffset = in2meter(75);

    private static final String DEVICE_RPI3 = "rpi3";

    private TextView mDisLIDAR = null;
    private TextView mDisTime = null;
    private TextView mSunData = null;
    private TextView mDisFrame = null;
    private TextView mDisLed = null;

    private EditText LEDDensity_ET = null;
    private EditText LEDLen_ET = null;
    private TextView NumLEDs_TV = null;
    private TextWatcher mStripChanged_TW = null;
    private CheckBox LoadLine_chk = null;
    private SeekBar Brightness_SB = null;

    private InputDriver mDriver;

    /**
     * Utility function to convert java Date to TimeZone format
     * @param date
     * @param format
     * @param timeZone
     * @return
     */
    public static String formatDateToString(Date date, String format,
                                     String timeZone) {
        // null check
        if (date == null) return null;
        // create SimpleDateFormat object with input format
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        // default system timezone if passed null or empty
        if (timeZone == null || "".equalsIgnoreCase(timeZone.trim())) {
            timeZone = Calendar.getInstance().getTimeZone().getID();
        }
        // set timezone to SimpleDateFormat
        sdf.setTimeZone(TimeZone.getTimeZone(timeZone));
        // return Date in required format with timezone as String
        return sdf.format(date);
    }

    private Calendar nextSunRise = new GregorianCalendar(TimeZone.getTimeZone("CST"));
    private Calendar nextSunSet = new GregorianCalendar(TimeZone.getTimeZone("CST"));

    private IsDayTime isDayTime = new IsDayTime(IsDayTime.Values.unknown);

    public static String getSPIPort(byte ss) {
        switch (Build.DEVICE) {
            // same for Edison Arduino breakout and Edison SOM
            case "edison":
                return "SPI2.1";

            case DEVICE_RPI3:
                return "SPI0." + Byte.toString(ss);

            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);

        }
    }

    //public static String getI2CPort(PeripheralManagerService pms) {
    public static String getI2CPort(PeripheralManager pms) {
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

    //public static String[] getLIDARpins(PeripheralManagerService pms ) {
    public static String[] getLIDARpins(PeripheralManager pms ) {
        String res[] = new String[2];
        res[0] = res[1] = "unk";

        switch (Build.DEVICE) {
            // same for Edison Arduino breakout and Edison SOM
            case "edison":
                res[0] = "IO3";
                res[1] = "IO2";
                return res;

            case DEVICE_RPI3:
                res[0] = "BCM4";
                res[1] = "BCM5";
                return res;

            default:
                throw new IllegalStateException("Unknown Build.DEVICE " + Build.DEVICE);

        }
    }

    public boolean coloreqzero() {
        final byte rainbowLen = 8;
        int AryLenUBound = (mLedColors.length < NUM_LEDS) ? mLedColors.length : NUM_LEDS-1;

        final double[] blkLab = new double[3];
        ColorUtils.colorToLAB( Color.BLACK, blkLab);

        for (int i = 0; i < AryLenUBound-rainbowLen-1; i++) {
            int pxAlpha = Color.alpha(mLedColors[i]);
            //if ( ( mLedColors[i] != Color.BLACK ) && (pxAlpha > 0) ) return false;
            Color px = Color.valueOf(mLedColors[i]);
            double[] Lab1 = new double[3];
            ColorUtils.colorToLAB(mLedColors[i], Lab1);

            double dis = ColorUtils.distanceEuclidean(Lab1, blkLab);
            if (dis > 2.01f) return false;
        }

        return true;
    }

    public int stripAveragecolor() {
        final byte rainbowLen = 8;
        int AryLenUBound = (mLedColors.length < NUM_LEDS) ? mLedColors.length : NUM_LEDS-1;

        long rColor = mLedColors[0];

        for (int i = 1; i < AryLenUBound-rainbowLen; i++) {
            rColor += mLedColors[i];
            rColor /= 2;
        }

        int irColor = (int)(rColor & 0xFFFFFFFF);

        return Color.argb(0xFF, Color.red(irColor), Color.green(irColor), Color.blue(irColor));

        //return //Remove Alpha from calculation
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean lidarfound = false;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        /*nextSunRise.setTimeInMillis(0);
        nextSunSet.setTimeInMillis(1);*/
        Calendar today = Calendar.getInstance();
        Calendar[] sunVals;

        sunVals = ca.rmen.sunrisesunset.SunriseSunset.getSunriseSunset(today, 30.251062, -97.832617);
        nextSunRise = sunVals[0/*SUNRISE*/];
        nextSunSet = sunVals[1/*SUNSET*/];

        mDisLIDAR = (TextView)this.findViewById(R.id.display_lidar);
        mDisTime = (TextView)this.findViewById(R.id.display_datetime);
        mSunData = (TextView)this.findViewById(R.id.display_sundata);
        mDisFrame = (TextView)this.findViewById(R.id.frame);
        mDisLed = (TextView)this.findViewById(R.id.lednum);
        mDateHandler.postDelayed(mDateRunnable, 1000); //update clock once a second

        LEDDensity_ET = (EditText)this.findViewById(R.id.stripDensity);
        LEDLen_ET = (EditText)this.findViewById(R.id.stripLen);
        NumLEDs_TV = (TextView)this.findViewById(R.id.numLEDs);
        LoadLine_chk = (CheckBox)this.findViewById(R.id.chkLoadLine);
        Brightness_SB = (SeekBar)this.findViewById(R.id.brightSB);

        if (mSunData != null) {
            String aBuf = "";
            aBuf = "s: " +  formatDateToString(nextSunSet.getTime(), "hh:mm:ss a", "America/Chicago");
            aBuf += " r:" + formatDateToString(nextSunRise.getTime(), "hh:mm:ss a", "America/Chicago");
            mSunData.setText(aBuf);
        }

        Log.i(TAG, "Starting BlinkActivity");
        //PeripheralManagerService service = new PeripheralManagerService();
        PeripheralManager service = PeripheralManager.getInstance();

        Log.i(TAG, "Device: " + Build.DEVICE);

        SharedPreferences sharedPref = getSharedPreferences("StripSettings", MODE_PRIVATE);
        Log.i(TAG, sharedPref.getAll().toString() );

        final int oldDen = APAperMeter;
        final float oldLen = StripLenInMeter;
        final short oldBrightness = LED_BRIGHTNESS;

        if ( sharedPref.contains("APAperMeter") ) {
            //sharedPref.getInt("APAperMeter", APAperMeter); <<--- doesn't appear to work
            try {
                APAperMeter = Integer.parseInt( sharedPref.getAll().get("APAperMeter").toString() );
            } catch (  NumberFormatException nfe) {
                APAperMeter = oldDen;
            };
        }
        if ( sharedPref.contains("StripLength") ) {
            //sharedPref.getFloat("StripLength", StripLenInMeter); <<--- doesn't appear to work
            try {
                StripLenInMeter = Float.parseFloat( sharedPref.getAll().get("StripLength").toString() );
            } catch (  NumberFormatException nfe) {
                StripLenInMeter = oldLen;
            };
        }

        if ( sharedPref.contains("StripBrightness") ) {
            //sharedPref.getFloat("StripBrightness", LED_BRIGHTNESS); <<--- doesn't appear to work
            try {
                LED_BRIGHTNESS = Short.parseShort( sharedPref.getAll().get("StripBrightness").toString() );
            } catch (  NumberFormatException nfe) {
                LED_BRIGHTNESS = oldBrightness;
            };
        }

        LEDDensity_ET.setText( Integer.toString(this.APAperMeter) );
        LEDLen_ET.setText( Float.toString(this.StripLenInMeter) );
        if (StripLenInMeter < 0.91f) { StripLenInMeter = 9.1f; };
        NUM_LEDS = (int)(APAperMeter * StripLenInMeter);

        if (NumLEDs_TV != null) {
            String posStr = String.format(Locale.US, "(=%d LEDs over %.2f ft long)", NUM_LEDS, (StripLenInMeter * 3.28084) );
            NumLEDs_TV.setText(posStr);
        }

        LEDDensity_ET.addTextChangedListener(mStripChanged_TW = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                mHandler.removeCallbacks(mBlinkRunnable); //stop LED runnable

                try {
                    Thread.sleep(FRAME_DELAY_MS+2); //let thread sleep; paranoid that LED update in process
                } catch (  InterruptedException e) { };

                final int oldDen = APAperMeter;
                final float oldLen = StripLenInMeter;

                try {
                    APAperMeter = Integer.parseInt( LEDDensity_ET.getText().toString() );
                } catch (  NumberFormatException nfe) {
                    APAperMeter = oldDen;
                };

                try {
                    StripLenInMeter = Float.parseFloat(LEDLen_ET.getText().toString());
                    if (StripLenInMeter == 0.0f) StripLenInMeter = oldLen;
                } catch (  NumberFormatException nfe) {
                    StripLenInMeter = oldLen;
                };

                final int oldNumLEDs = NUM_LEDS;
                NUM_LEDS = (int)(APAperMeter * StripLenInMeter);
                if (NUM_LEDS > oldNumLEDs) {
                    int[] mLedNewColors = Arrays.copyOf(mLedColors, NUM_LEDS);
                    Arrays.fill(mLedColors, 0,  NUM_LEDS-1, Color.BLACK);
                    mLedColors = mLedNewColors;
                }
                if (NumLEDs_TV != null) {
                    //String posStr = String.format(Locale.US, "(=%d LEDs over %.2f ft long)", NUM_LEDS, (StripLenInMeter * 3.28084) );
                    String posStr = String.format(Locale.US, "(=%d LEDs, %.2f ft long @%.1f%% brightness)", NUM_LEDS, (StripLenInMeter * 3.28084), (((float)LED_BRIGHTNESS/Apa102.MAX_BRIGHTNESS)*100) );
                    NumLEDs_TV.setText(posStr);
                }

                SharedPreferences sharedPref = getSharedPreferences("StripSettings", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putInt("APAperMeter", APAperMeter);
                editor.putFloat("StripLength", StripLenInMeter);
                editor.commit();

                mHandler.postDelayed(mBlinkRunnable, FRAME_DELAY_MS); //restart RGB pixels
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        LEDLen_ET.addTextChangedListener(mStripChanged_TW);

        Brightness_SB.setMax( Apa102.MAX_BRIGHTNESS );
        Brightness_SB.setMin(0);
        Brightness_SB.setProgress(LED_BRIGHTNESS);

        Brightness_SB.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                LED_BRIGHTNESS = (short) i;
                if (mApa102 != null) mApa102.setBrightness(i);

                SharedPreferences sharedPref = getSharedPreferences("StripSettings", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putInt("StripBrightness", i);
                editor.commit();

                if (NumLEDs_TV != null) {
                    String posStr = String.format(Locale.US, "(=%d LEDs, %.2f ft long @%.1f%% brightness)", NUM_LEDS, (StripLenInMeter * 3.28084), (((float)i/Apa102.MAX_BRIGHTNESS)*100) );
                    NumLEDs_TV.setText(posStr);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        if (NumLEDs_TV != null) {
            String posStr = String.format(Locale.US, "(=%d LEDs, %.2f ft long @%.1f%% brightness)", NUM_LEDS, (StripLenInMeter * 3.28084), (((float)LED_BRIGHTNESS/Apa102.MAX_BRIGHTNESS)*100) );
            NumLEDs_TV.setText(posStr);
        }

        // Create a new driver instance
        mDriver = new InputDriver.Builder()
                .setName("PowerButton")
                .setSupportedKeys(new int[] {KeyEvent.KEYCODE_POWER})
                .build();

        // Register with the framework
        UserDriverManager manager = UserDriverManager.getInstance();
        manager.registerInputDriver(mDriver);

        List<String> deviceList = service.getSpiBusList();
        Log.i(TAG, "List of available SPI devices: " + deviceList);

        deviceList = service.getI2cBusList();
        Log.i(TAG, "List of available I2C devices: " + deviceList);

        Log.i(TAG, "List of available GPIO: " + service.getGpioList());

        /*Log.i(TAG, "Searching for LiDAR on " + getI2CPort(service));

        for (int address = 0x60; address < 0x70; address++) {
            //auto-close the devices
            try (final I2cDevice device = service.openI2cDevice(getI2CPort(service), address)) {
                try {
                    Byte b = device.readRegByte(0x1);
                    Log.i(TAG, String.format(Locale.US, "Trying: 0x%02X - SUCCESS (0x%02x)", address, b));
                    lidarfound = true;
                } catch (final IOException e) {
                    Log.i(TAG, String.format(Locale.US, "Trying: 0x%02X - FAIL", address));
                }
                //device.close(); not needed; try auto-closes scope: https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
            } catch (final Exception e) {
                //in case the openI2cDevice(name, address) fails
                Log.i(TAG, String.format(Locale.US, "Unable to search %s for LIDAR @ 0x%02X\n", getI2CPort(service), address), e);
                break;
            }
        }*/

        try {
            Log.d(TAG, "Initializing LIDAR");
            lidarfound = false;
            mI2CDevice = service.openI2cDevice(getI2CPort(service), LIDAR_I2C_Address);

            try {
                Byte b = mI2CDevice.readRegByte(0x1);
                lidarfound = true;
            } catch (IOException e) {
                Log.e(TAG, "LIDAR read Failed: ", e);
            }

            mLidarHandler.post(mLidarRunnable);
        } catch (IOException e) {
            // couldn't configure the device...
            Log.i(TAG, "Unable to configure " + getI2CPort(service) + " for LIDAR", e);
            mI2CDevice = null;
        }

        if (lidarfound != true) mI2CDevice = null;
        else {
            String pinLidarEnable = "unk", pinLidarModeCtrl = "unk";
            final String[] str = getLIDARpins(service );
            pinLidarEnable = str[0];
            pinLidarModeCtrl = str[1];
            Log.i(TAG, String.format(Locale.US, "LIDAR pins Enable=%s ModeCTRL=%s\n",  pinLidarEnable, pinLidarModeCtrl));

            try {
                mLidarEnable = service.openGpio(pinLidarEnable);
                mLidarEnable.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
                Log.i(TAG, "LIDAR set Enable High initially..");
                mLidarEnable.setActiveType(Gpio.ACTIVE_HIGH);
                mLidarEnable.setValue(false);

                mLidarModeCtrl = service.openGpio(pinLidarModeCtrl);
                mLidarModeCtrl.setDirection(Gpio.DIRECTION_IN);

                mLidarEnable.setValue(true);
                try {
                    Thread.sleep(24);
                } catch (  InterruptedException e) { };
            } catch (IOException e) {
                Log.e(TAG, "Unable to configure LIDAR enable pin, high", e);
            }

            /*if (mLidarEnable != null) try {
                mLidarEnable.setValue(false);
                Thread.sleep(250);
                mLidarEnable.setValue(true);
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Unable to disable LIDAR, low", e);
            }*/

            try {
                Byte b = mI2CDevice.readRegByte(0x1);
                Log.i(TAG, String.format(Locale.US, "Trying: 0x%02X - SUCCESS (0x%02x)", LIDAR_I2C_Address, b));
            } catch (IOException  e) { }

            mLedColors = new int[NUM_LEDS];
            Arrays.fill(mLedColors, Color.BLACK);

            final String apa102bus = getSPIPort((byte)0);

            try {
                Log.i(TAG, String.format(Locale.US, "Initializing LED strip on %s", apa102bus));
                mApa102 = new Apa102(apa102bus, LED_MODE);
                mApa102.setBrightness(LED_BRIGHTNESS);

                //test rgb colors - temp
                mLedColors[0] = Color.RED;
                mLedColors[1] = Color.GREEN;
                mLedColors[2] = Color.BLUE;

                mApa102.write(mLedColors);

                //mHandler.post(mBlinkRunnable);
                mHandler.postDelayed(mBlinkRunnable, 3000); //show RGB pixels for 3sec at start
            } catch (IOException e) {
                // couldn't configure the device...
                Log.i(TAG, "Unable to configure " + apa102bus + " for APA102 led strip", e);
                mApa102 = null;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove pending blink Runnable from the handler.
        mHandler.removeCallbacks(mBlinkRunnable);
        mLidarHandler.removeCallbacks(mLidarRunnable);
        mDateHandler.removeCallbacks(mDateRunnable);

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

        if (mLidarModeCtrl != null) {
            try {
                mLidarModeCtrl.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on closing mLidarModeCtrl", e);
            } finally {
                mLidarModeCtrl = null;
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

        SharedPreferences sharedPref = getSharedPreferences("StripSettings", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("APAperMeter", APAperMeter);
        editor.putFloat("StripLength", StripLenInMeter);
        editor.putInt("StripBrightness", LED_BRIGHTNESS);
        editor.commit();
    }

    private final boolean NextPostDelayed( ZittsHandler h, Runnable r, long delayMillis) {
        final long mSperHour = 60 /*min*/ * 60/*seconds/min*/ * 1000 /*ms/sec*/;
        long dMS = delayMillis;
        Calendar now = Calendar.getInstance();

        //Location location = new Location("30.251062", "-97.832617");

        /*TZID hhZone = Timezone.of("America/Chicago").getID();
        PlainDate today = moment.toZonalTimestamp(hhZone).getCalendarDate();

        SolarTime.Sunshine hhSun =
                SolarTime.ofLocation()
                        .northernLatitude(30, 15, 3.8232)
                        .easternLongitude(-97, 49, 57.4206)
                        .build()
                        .sunshine(hhZone).apply(today);

        if ( hhSun.isPresent(moment) ) {
            PlainTimestamp sunset = hhSun.endLocal();
            PlainTimestamp now = PlainTimestamp.nowInSystemTime();
            //EPOCH_DAY
            //long msLeft = sunset.minus(now.minus();
        }*/

        switch ( isDayTime.getEnum() ) {
            case isDayTime :
                long msLeft = nextSunSet.getTimeInMillis() - now.getTimeInMillis();

                if (coloreqzero() != true) break; //there is still some color on strip, so use existing delay

                if (msLeft > mSperHour) dMS = mSperHour;
                else dMS = msLeft;

                Log.i(TAG, String.format(Locale.US, "%s: DayTime override for %s set to %.3f secs", h.Name, Thread.currentThread().getStackTrace()[2].getMethodName(), (dMS/1000.0f)));

                break;
        }

        return h.postDelayed(r, dMS);
    }

    private Runnable mLidarRunnable = new Runnable() {
        private static final byte ACQ_COMMAND = 0x00;
        private static final byte STATUS = 0x01;
        private static final byte SIG_COUNT_VAL = 0x02;
        private static final byte ACQ_CONFIG_REG = 0x04;
        private static final byte THRESHOLD_BYPASS = 0x1c;
        private static final byte FULL_DELAY_WORD = (byte)0x8f;
        private static final byte UNIT_ID_WORD = (byte)0x96;

        public int distance_cm = 0;

        //LIDAR must have SMBUS repeated start turned off to function
        //echo -n 0 > /sys/module/i2c_bcm2708/parameters/combined
        //https://issuetracker.google.com/issues/70110756

        /**
         * Read a byte from a given register.
         *
         * @param reg The register to read from (0x00-0xFF).
         * @return byte read from register
         */
        private byte readRegByte(byte reg) throws IOException {
            /*if (reg < 0 || reg > 0xFF) {
                throw new IllegalArgumentException("The register must be between 0x00-0xFF. Register:" + reg);
            }*/
            final byte[] buffer = {(byte) (reg & 0xFF)};
            mI2CDevice.write(buffer, 1);
            //buffer[0] = 0;
            mI2CDevice.read(buffer, 1);
            return buffer[0];
        }

        /**
         * Write a byte to a given register.
         *
         * @param reg  The register to write to (0x00-0xFF).
         * @param data Value to write
         */
        private void writeRegByte(byte reg, byte data) throws IOException {
            /*if (reg < 0 || reg > 0xFF) {
                throw new IllegalArgumentException("The register must be between 0x00-0xFF. Register:" + reg);
            }*/
            final byte[] buffer = {(byte) (reg & 0xFF), data};
            mI2CDevice.write(buffer, buffer.length);
        }

        /**
         * Read a word (Short) from a given register.
         *
         * @param reg The register to read from (0x00-0xFF).
         * @return short read from register
         */
        private Short readRegWord(byte reg) throws IOException {
            final byte[] buffer = new byte[2];//{(byte) (reg & 0xFF)};
            buffer[0] = reg;
            mI2CDevice.write(buffer, 1);
            //buffer[0] = 0;
            mI2CDevice.read(buffer, 2);
            //Log.i(TAG, String.format(Locale.US, "readRegWord got %s", Arrays.toString(buffer)));
            return (short)(((byte)buffer[1] << 8) | (byte)buffer[0]) ;
        }

        @Override
        public void run() {
            if (mI2CDevice != null) {
                String strStep = "";
                try {
                    byte Acq_req = 0x03;

                    if (nLidarCnt == 0 ) {
                        if (mLidarEnable != null) mLidarEnable.setValue(true);

                        nLidarCnt += 1;
                        //mLidarHandler.postDelayed(mLidarRunnable, 24);
                        NextPostDelayed(mLidarHandler, mLidarRunnable, 24);
                        return;
                    } else if ( nLidarCnt == 1 ) {

                        strStep = "reset";
                        this.writeRegByte(ACQ_COMMAND, (byte)0x0);

                        //delay emperically determined (passes at 12mS); else STATUS fails after reset command
                        //datasheet page 4 says roughly 22mS
                        try {
                            Thread.sleep(24);
                        } catch (  InterruptedException e ) { };

                        strStep = "rSTATUS1";
                        while ( (this.readRegByte(STATUS) & 1) == 1 ) {
                            //do nothing
                            try {
                                Thread.sleep(25);
                            } catch (  InterruptedException e) { };
                        }
                        Log.i(TAG, "Lidar not busy");

                        nLidarCnt += 1;
                        //mLidarHandler.postDelayed(mLidarRunnable, 24);
                        NextPostDelayed(mLidarHandler, mLidarRunnable, 24);
                        return;
                    } else if ( nLidarCnt == 2 ) {
                        strStep = "rUID";

                        Short tUID = this.readRegWord(UNIT_ID_WORD);
                        LIDAR_UNITID = Short.reverseBytes(tUID);
                        Log.i(TAG, String.format(Locale.US, "Lidar UNITID is 0x%04x", LIDAR_UNITID));

                        Acq_req = this.readRegByte(ACQ_CONFIG_REG);
                        Log.i(TAG, String.format(Locale.US, "Lidar ACQ_CONFIG_REG = (0x%02x)", Acq_req));

                        //set default mode, balanced performance
                        //mI2CDevice.writeRegByte(SIG_COUNT_VAL, (byte)0x80);
                        this.writeRegByte(SIG_COUNT_VAL, (byte)0xFF);
                        this.writeRegByte(ACQ_CONFIG_REG, (byte)0x19 );
                        this.writeRegByte(THRESHOLD_BYPASS, (byte)0x00);

                        nLidarCnt += 1;
                        //mLidarHandler.postDelayed(mLidarRunnable, 24);
                        NextPostDelayed(mLidarHandler, mLidarRunnable, 24);
                        return;
                    }

                    if ((nLidarCnt % 100) == 3) Acq_req = 0x4;
                    //1) Write Reg 0x04 with 0
                    strStep = "wACQ CMD " + Acq_req;
                    this.writeRegByte(ACQ_COMMAND, Acq_req);

                    byte status;
                    long startNs = System.nanoTime();
                    //2) loop read r0x01 bit[0] until low
                    strStep = "rSTATUS2";
                    while ( ((status = this.readRegByte(STATUS)) & 1) == 1 ) {
                        //do nothing
                        try {
                            Thread.sleep(25);
                        } catch (  InterruptedException e) { };
                    }
                    long elaspedNs = System.nanoTime() - startNs;

                    //check status register for error
                    if ( Acq_req == 0x03 ) {
                        lidar_cm.setLastError(status);
                        if ((status & 0b001000) == 0b001000) {
                            //bit3 = invalid signal
                            //lidar_cm.set(Short.MAX_VALUE);
                            if (lidar_cm.isValueStrong() == false) lidar_cm.set(Short.MAX_VALUE);
                            //else lidar_cm.set( lidar_cm.get() + 5 );
                            lidar_cm.SetIsStrong(false);
                            if ( lidar_cm.wasError() == false ) nLidarCnt += 1;
                            //mLidarHandler.postDelayed(mLidarRunnable, LIDAR_CHK_Interval);
                            NextPostDelayed(mLidarHandler, mLidarRunnable, LIDAR_CHK_Interval);
                            return;
                        } else if ((status & 0b0100000) == 0b0100000) {
                            //do nothing - health ok
                        } else {
                            Log.e(TAG, String.format(Locale.US, "LIDAR returned error: %s after %.3f secs", Integer.toBinaryString(status), elaspedNs / 1e6));
                            if ( lidar_cm.wasError() == false ) nLidarCnt += 1;
                            //mLidarHandler.postDelayed(mLidarRunnable, LIDAR_CHK_Interval);
                            NextPostDelayed(mLidarHandler, mLidarRunnable, LIDAR_CHK_Interval);
                            return;
                        }
                    }

                    //3) read word from 0x8f for 16bit distance in cm
                    strStep = "rCM";
                    Short raw_cm = (short)(this.readRegWord(FULL_DELAY_WORD) & 0xFFFF);
                    distance_cm = Short.reverseBytes(raw_cm);
                    lidarMeasurements.setNewDistance( distance_cm );

                    //Log.i(TAG, "Lidar distance = " + distance_cm + "cm");
                    if (abs(lidar_cm.get() - distance_cm) > 4) {
                        Log.d(TAG, String.format(Locale.US, "Lidar distance = %d (0x%04x)", distance_cm, distance_cm));
                        Log.i(TAG, String.format(Locale.US, "LIDAR returned 0b%s after %.3f secs", Integer.toBinaryString(status), elaspedNs/1e6));

                        String str, stat;
                        stat = "";

                        if ((status & 0b000100) == 0b000100) stat += "S";
                        if ( lidar_cm.wasError() == true ) stat += "E";

                        str = String.format(Locale.US, "Lidar distance = %d cm %s", distance_cm, stat);
                        if (mDisLIDAR != null) mDisLIDAR.setText(str);
                    }

                    //do thread safe
                    raw_cm = (short)lidar_cm.get();
                    //lidarMeasurements.setNewDistance( raw_cm );
                    raw_cm = (short)( ((raw_cm + distance_cm) / 2) & 0xFFFF); //running average
                    if ((status & 0b000100) == 0b000100) {
                      //strong received signal, use detected distance
                      lidar_cm.set( distance_cm );
                      lidar_cm.SetIsStrong(true);
                    } else lidar_cm.set(raw_cm);

                    if (mDisFrame != null) {
                        String frame = String.format(Locale.US, "%08x %08X", nFrameCnt, nLidarCnt);
                        mDisFrame.setText(frame);
                    }

                    if ( lidar_cm.wasError() == false ) nLidarCnt += 1;
                    if (nLidarCnt % 100 == 0) {
                        Log.d(TAG, "Lidar frame " + nLidarCnt);
                    }

                    //gently handel rollover to 0x3 for recalibrating LIDAR
                    if (nLidarCnt > 0x7FFFFFD0) nLidarCnt = 0x3;

                } catch (IOException e) {
                    Log.e(TAG, "Error while accessing LIDAR @" + strStep, e);

                    try { mLidarEnable.setValue(false); } catch (IOException e1) { };
                    //mLidarHandler.postDelayed(mLidarRunnable, LIDAR_CHK_Interval*10);
                    NextPostDelayed(mLidarHandler, mLidarRunnable, LIDAR_CHK_Interval*10);
                    try {
                        if (mLidarEnable != null) mLidarEnable.setValue(true);
                        Byte b = this.readRegByte((byte)0x1);
                        Log.i(TAG, "LIDAR(0x1)=" + b);
                        nLidarCnt = 0;
                    } catch (IOException e1) { };
                    return;
                }
            }
            //mLidarHandler.postDelayed(mLidarRunnable, LIDAR_CHK_Interval);
            NextPostDelayed(mLidarHandler, mLidarRunnable, LIDAR_CHK_Interval);
        };
    };

    final private static String t_sensor = "/sys/devices/virtual/thermal/thermal_zone0/";
    public String readThermalZoneFile( String pathRoot, String nameFile ) {
        String aBuffer = "";
        try {
            File myFile = new File(pathRoot + nameFile);
            FileInputStream fIn = new FileInputStream(myFile);
            BufferedReader myReader = new BufferedReader(new InputStreamReader(fIn));
            String aDataRow = "";
            while ((aDataRow = myReader.readLine()) != null) {
                aBuffer += aDataRow;
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return aBuffer;
    }

    public float readCPU_Tj() {
        String aBuffer = "";
        aBuffer = readThermalZoneFile(t_sensor, "type");
        if ( aBuffer.equals("bcm2835_thermal") ) {
            aBuffer = readThermalZoneFile(t_sensor, "temp");
            float aVal = Float.parseFloat( aBuffer ) / 1000.0f;
            return aVal;
        } else {
            Log.e(TAG,  String.format(Locale.US, "Error %s doesn't match type (%s)", t_sensor+"type", aBuffer));
            return 0.0f;
        }
    }

    private Runnable mDateRunnable = new Runnable() {
        private final TimeZone TZCST = TimeZone.getTimeZone("America/Chicago");
        private final byte SUNRISE = 0;
        private final byte SUNSET  = 1;

        @Override
        public void run() {
            //Log.d(TAG, String.format(Locale.US, "CPU Tj(%s)=%.2fC", readThermalZoneFile(t_sensor, "type"), readCPU_Tj()));
            if (mDisTime != null) {
                String aBuf = "";
                aBuf = String.format(Locale.US, "%.2fC ", readCPU_Tj());
                aBuf += formatDateToString( Calendar.getInstance().getTime(), "dd MMM yyyy hh:mm:ss a", "America/Chicago");
                mDisTime.setText( aBuf );
            }

            Calendar today, tomorrow;
            today = Calendar.getInstance();
            tomorrow = (Calendar) today.clone();
            tomorrow.add(Calendar.DATE, 1); //add a Day to current date

            if (isDayTime.getEnum() == IsDayTime.Values.unknown) isDayTime.setEnum(IsDayTime.Values.isNightTime);

            Calendar[] todaySunData = ca.rmen.sunrisesunset.SunriseSunset.getSunriseSunset(today, 30.251062, -97.832617);
            Calendar[] tomSunData = ca.rmen.sunrisesunset.SunriseSunset.getSunriseSunset(tomorrow, 30.251062, -97.832617);
            Calendar now = Calendar.getInstance();

            if ( now.after(todaySunData[SUNRISE]) && now.before(todaySunData[SUNSET]) ) {
                //Daytime... before sunset
                if ( ( nextSunSet.hashCode() != todaySunData[SUNSET].hashCode()) ||
                     ( nextSunRise.hashCode() != tomSunData[SUNRISE].hashCode())    ) {
                    //only do the cloning once to save garbage collection
                    nextSunSet = (Calendar) todaySunData[SUNSET].clone();
                    nextSunRise = (Calendar) tomSunData[SUNRISE].clone();
                }
                isDayTime.setEnum(IsDayTime.Values.isDayTime);

                if (mSunData != null) {
                        String aBuf = "";
                        aBuf = "s: " + formatDateToString(nextSunSet.getTime(), "hh:mm:ss a", "America/Chicago");
                        aBuf += " r:" + formatDateToString(nextSunRise.getTime(), "hh:mm:ss a", "America/Chicago");
                        mSunData.setText(aBuf);
                }

            } else if ( now.after(todaySunData[SUNSET]) && now.before(tomSunData[SUNRISE]) ) {
                //nighttime... after sunset
                if ( ( nextSunRise.hashCode() != tomSunData[SUNRISE].hashCode()) ||
                     ( nextSunSet.hashCode() != tomSunData[SUNSET].hashCode()  )    ) {
                    //only do the cloning once to save garbage collection
                    nextSunRise = (Calendar) tomSunData[SUNRISE].clone();
                    nextSunSet = (Calendar) tomSunData[SUNSET].clone();
                }
                isDayTime.setEnum(IsDayTime.Values.isNightTime);

                if ( mSunData != null) {
                    String aBuf = "";
                    aBuf = "r: " + formatDateToString( nextSunRise.getTime(),  "hh:mm:ss a", "America/Chicago");
                    aBuf += " s:" + formatDateToString( nextSunSet.getTime(),  "hh:mm:ss a", "America/Chicago");
                    mSunData.setText( aBuf );
                }
            }

            /*long SunriseMS = sunriseSunset[0].getTimeInMillis();
            long nowMS = Calendar.getInstance().getTimeInMillis();
            long msLeft = SunriseMS - nowMS;
            if ( (SunriseMS < nowMS) ) {
                if ( msLeft > mSperHour ) {
                    //dMS = mSperHour;
                }
            }*/

            //mDateHandler.postDelayed(mDateRunnable, 100l);
            NextPostDelayed(mDateHandler, mDateRunnable, 100l);
        }
    };

    private int fadeTowardColor( int cur, int target, float ratio) {
        if (cur == target) return cur;

        int rColor = cur;
        rColor = ColorUtils.blendARGB( cur, target, ratio);
        return rColor;
    }

    private Runnable mBlinkRunnable = new Runnable() {
        final float[] hsv = {1f, 1f, 1f};
        int oldLED = 0;
        int AryLenUBound = NUM_LEDS;
        boolean doCurrentTest = false;
        int whiteLEDcnt = 260;

        //int XmasColors[] = {Color.RED, Color.YELLOW, Color.GREEN};
        //int XmasColors[] = {Color.RED, Color.YELLOW, Color.GREEN, Color.WHITE, Color.MAGENTA, Color.rgb(255, 105, 180)};
        //int XmasColors[] = {Color.RED, Color.WHITE, Color.rgb(0, 0x4D, 0xFF), Color.BLACK};
        int XmasColors[] = {Color.rgb(0xFF, 0xCF, 0x00), Color.MAGENTA, Color.BLACK};
        //int XmasColors[] = {Color.BLACK};
        int idxColor = 2;

        @Override
        public void run() {
            if (mApa102 != null) {
                final byte rainbowLen = 8;
                AryLenUBound = (mLedColors.length < NUM_LEDS) ? mLedColors.length : NUM_LEDS-1;

                Button plusbtn = (Button) findViewById(R.id.plusBtn);
                Button minusbtn = (Button) findViewById(R.id.minusBtn);

                if (LoadLine_chk != null) {
                    if ( LoadLine_chk.isChecked() ) {
                        plusbtn.setVisibility(View.VISIBLE);
                        minusbtn.setVisibility(View.VISIBLE);
                        doCurrentTest = true;

                        View.OnClickListener mAddRemBtnListner;

                        plusbtn.setOnClickListener(mAddRemBtnListner = new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                int id = view.getId();
                                if (id == R.id.plusBtn) {
                                    whiteLEDcnt++;
                                }
                                if (id == R.id.minusBtn) {
                                    whiteLEDcnt--;
                                }
                            }
                        });

                        minusbtn.setOnClickListener(mAddRemBtnListner);
                    } else if (doCurrentTest) {
                        plusbtn.setVisibility(View.INVISIBLE);
                        minusbtn.setVisibility(View.INVISIBLE);
                        doCurrentTest = false;

                        plusbtn.setOnClickListener(null);
                        minusbtn.setOnClickListener(null);
                    }
                }

                if (doCurrentTest) {
                    if (mDisLed != null) {
                        String posStr = String.format(Locale.US, "@%d", whiteLEDcnt );
                        mDisLed.setText(posStr);
                    }

                    Arrays.fill(mLedColors, whiteLEDcnt, AryLenUBound, Color.BLACK);
                    Arrays.fill(mLedColors, 0, whiteLEDcnt, Color.WHITE);

                    try {
                        mApa102.write(mLedColors);
                    } catch (IOException e) {
                        Log.e(TAG, "Error while writing to LED strip", e);
                    }

                    nFrameCnt += 1;

                } else
                    try {
                        //fade all to black or holiday colors
                        for (int i = 0; i < AryLenUBound; i++) {
                            int pxColor =  mLedColors[i];

                            int nextColor = XmasColors[idxColor];

                            switch ( isDayTime.getEnum() ) {
                                case isNightTime:
                                    if ((nFrameCnt % 120) == 0) {
                                        //Log.d(TAG, String.format(Locale.US, "idx=%d, len=%d", idxColor, XmasColors.length));
                                        idxColor = (idxColor + 1) % XmasColors.length;
                                        nFrameCnt += 1;
                                        continue;
                                    }
                                    break;
                                case isDayTime:
                                    //always fade to black during day
                                    if ( coloreqzero() == true ) continue;
                                    int pxAlpha = Color.alpha(pxColor) - 1;
                                    if (pxAlpha < 0) { mLedColors[i] = Color.BLACK; }
                                    else {
                                        mLedColors[i] = Color.argb(pxAlpha, Color.red(pxColor), Color.green(pxColor), Color.blue(pxColor));
                                    }
                                    nextColor = Color.BLACK;
                                    break;
                            }

                            mLedColors[i] = fadeTowardColor(pxColor, nextColor, 0.005f);

                            /*if (pxColor == Color.BLACK) continue;

                            int pxAlpha = Color.alpha(pxColor) - 5;//0;
                            if (pxAlpha < 0) { mLedColors[i] = Color.BLACK; }
                            else {
                                mLedColors[i] = Color.argb(pxAlpha, Color.red(pxColor), Color.green(pxColor), Color.blue(pxColor));
                            }*/
                        }

                        for (int i = AryLenUBound-rainbowLen; i < AryLenUBound; i++) { // Assigns gradient colors at end of strip.
                            int n = (i + mFrame) % rainbowLen;
                            hsv[0] = n * 360.f / rainbowLen;
                            mLedColors[i] = Color.HSVToColor(0, hsv);
                        }

                        double adjDistance = (distance_in_meter() - LedStartOffset);
                        if (adjDistance < 0) {
                            adjDistance = 0.0f;
                            if (round(distance_in_meter() * (float)APAperMeter) > 1) {
                                adjDistance = distance_in_meter()/* / APAperMeter*/;
                            }
                        }

                        long atLed = round(adjDistance * APAperMeter);

                        if (mDisLed != null) {
                            String posStr = String.format(Locale.US, "#%d", atLed );
                            mDisLed.setText(posStr);
                        }

                        final int SD = APAperMeter/2;

                        if (atLed > AryLenUBound) {
                            if ( atLed > (AryLenUBound + APAperMeter) ) {
                                //way outside of strip
                                int nB = mApa102.getBrightness() - 1;
                                if ( nB < 0 ) nB = 0;
                                if ( false/*true*/ ) {
                                    mApa102.setBrightness(nB);
                                    mApa102.write(mLedColors);
                                    mFrame = (mFrame + 1) % rainbowLen;
                                    nFrameCnt += 1;
                                    //mHandler.postDelayed(mBlinkRunnable, FRAME_DELAY_MS);
                                    NextPostDelayed(mHandler, mBlinkRunnable, FRAME_DELAY_MS);
                                    return;
                                }
                            }
                            atLed = AryLenUBound; //bound it
                        }

                        mLedColors[(int)atLed] = Color.argb(0xFF, 0xFF,0xFF, 0xFF);//Color.WHITE;
                        oldLED = (int)atLed;


                        int minLED = (int)atLed;
                        int maxLED = (int)atLed;

                        if (atLed < 2) {
                            int nB = mApa102.getBrightness() - 1;
                            if ( nB < 0 ) nB = 0;
                            if ( coloreqzero() == true) {
                                mApa102.setBrightness(nB);
                                mApa102.write(mLedColors);
                                mFrame = (mFrame + 1) % rainbowLen;
                                nFrameCnt += 1;
                                //mHandler.postDelayed(mBlinkRunnable, FRAME_DELAY_MS*10);
                                NextPostDelayed(mHandler, mBlinkRunnable, FRAME_DELAY_MS * 2);
                                return;
                            }
                        }  else {
                            mApa102.setBrightness(LED_BRIGHTNESS);
                        }

                        final Gaussian f = new Gaussian(0xFF, atLed, SD);

                        for (int LED = (int)(atLed)+1; LED < (3*SD)+atLed; LED++ ) {
                            int dX = Math.abs(LED-(int)atLed);
                            //double yf = 1 / (SD * Math.sqrt(2*Math.PI)) * Math.exp( - (Math.pow(LED-atLed,2) / 2*Math.pow(SD,2)) );
                            double yf = f.value(LED);
                            final int gColor = Color.argb( (int)yf, (int)yf, (int)yf, (int)yf);

                            if (LED < AryLenUBound) {
                                mLedColors[LED] = gColor;
                                if (LED > maxLED) maxLED = LED;
                            }
                            if ( ((int)atLed - dX) >= 0 ) {
                                int lowLED = (int)atLed - dX;
                                mLedColors[lowLED] = gColor;
                                if (lowLED < minLED) minLED = lowLED;
                            }
                        }

                        if (mDisLed != null) {
                            String posStr = String.format(Locale.US, "%d < %d < %d", minLED, atLed, maxLED );
                            if ( lidarMeasurements.getFinalVelocity() < Double.MAX_VALUE ) {
                                posStr = String.format(Locale.US, "%d < %d < %d\n%f\n%f", minLED, atLed, maxLED, lidarMeasurements.getFinalVelocity(), lidarMeasurements.getAcceleration() );
                            }
                            mDisLed.setText(posStr);
                        }

                        mApa102.write(mLedColors);
                        mFrame = (mFrame + 1) % rainbowLen;

                        //if (nFrameCnt % 10 == 0) mApa102.setBrightness(LED_BRIGHTNESS);

                        nFrameCnt += 1;
                        if (nFrameCnt % 100 == 0) {
                            Log.i(TAG, "Write Apa102 frame " + nFrameCnt);
                            mApa102.setBrightness(LED_BRIGHTNESS);
                        }

                    /*if (nFrameCnt % 60 == 0) {
                        Random rnd = new Random();
                        mLedColors[0] = Color.argb(rnd.nextInt(255), rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
                        //Log.i(TAG, "Setting LED[0] @ frm" + nFrameCnt + " = " +  mLedColors[0] );
                        for (int i = 1; i < rainbowLen; i++) { // Assigns gradient colors.
                            //Color mColor = new Color();
                            int alpha = (mLedColors[i-1] >> 24) & 0xff; //i * (255 / mLedColors.length);
                            int r = (mLedColors[i-1] >> 16) & 0xff;
                            int b = (mLedColors[i-1] >>  8) & 0xff;
                            int g = (mLedColors[i-1]      ) & 0xff;

                            alpha -= (255 / rainbowLen);
                            if (alpha < 0) alpha += 0xFF;
                            alpha &= 0xFF;

                            mLedColors[i] = Color.argb( alpha, r, g, b);
                        }
                        mApa102.write(mLedColors);
                        mHandler.postDelayed(mBlinkRunnable, FRAME_DELAY_MS*10);
                        return;
                    }*/
                    } catch (IOException e) {
                        Log.e(TAG, "Error while writing to LED strip", e);
                    }
            }

            //mHandler.postDelayed(mBlinkRunnable, FRAME_DELAY_MS);
            NextPostDelayed(mHandler, mBlinkRunnable, FRAME_DELAY_MS );
        }
    };

    public void ShutdownBtn_OnClick(View view) {
        /*Intent i = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
        i.putExtra("android.intent.extra.KEY_CONFIRM", true);
        i.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);*/
        InputDriverEvent event = new InputDriverEvent();
        event.setKeyPressed(KeyEvent.KEYCODE_POWER, true);

        mDriver.emit(event);
    }
}
