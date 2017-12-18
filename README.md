# RGB_Walkway
Lidar enabled Pathway lighting

Bug filed with Android Things team:
https://issuetracker.google.com/issues/70775258
but would like to investigate possible solutions with the greater Android community so I posted the Code.

This very simple "hello world" implementation attempts to scan the I2C1 bus of a Raspberry Pi Model 3 using the builtin I2C functions.

Initially; the problem started with the DP 0.6.1 preview; but also fails in 0.6.0. I haven't gone back to an earlier preview. 

## STEPS TO REPRODUCE:

DP06x seem to have permission issues with opening the I2cDevice on Raspberry Pi 3.

Created a new application with a very simple onCreate().

```java
  PeripheralManagerService service = new PeripheralManagerService();
  final I2cDevice device = service.openI2cDevice("I2C1", 0x62);
```  
  
## OBSERVED RESULTS:
Application throws Exception on the openI2cDevice() call:

```
12-18 23:24:09.952 2035-2035/com.pinball_mods.walkwayrgb I/HomeActivity: Starting BlinkActivity
12-18 23:24:09.966 2035-2035/com.pinball_mods.walkwayrgb I/HomeActivity: Device: rpi3
12-18 23:24:09.967 2035-2035/com.pinball_mods.walkwayrgb I/HomeActivity: List of available SPI devices: [SPI0.0, SPI0.1]
12-18 23:24:09.968 2035-2035/com.pinball_mods.walkwayrgb I/HomeActivity: List of available I2C devices: [I2C1]
12-18 23:24:09.970 2035-2035/com.pinball_mods.walkwayrgb I/HomeActivity: List of available GPIO: [BCM10, BCM11, BCM12, BCM13, BCM14, BCM15, BCM16, BCM17, BCM18, BCM19, BCM2, BCM20, BCM21, BCM22, BCM23, BCM24, BCM25, BCM26, BCM27, BCM3, BCM4, BCM5, BCM6, BCM7, BCM8, BCM9]
12-18 23:24:09.970 2035-2035/com.pinball_mods.walkwayrgb I/HomeActivity: Searching for LiDAR on I2C1
12-18 23:24:09.981 2035-2035/com.pinball_mods.walkwayrgb I/HomeActivity: Trying: 0x60 - FAIL
12-18 23:24:09.994 2035-2035/com.pinball_mods.walkwayrgb I/HomeActivity: Unable to search I2C1 for LIDAR @ 0x60
                                                                         
                                                                         com.google.android.things.pio.PioException: android.os.ServiceSpecificException: Caller does not own I2C1(0x60) (code 13)
                                                                             at com.google.android.things.pio.I2cDeviceImpl.close(I2cDeviceImpl.java:59)
                                                                             at com.pinball_mods.walkwayrgb.HomeActivity.onCreate(HomeActivity.java:132)
                                                                             at android.app.Activity.performCreate(Activity.java:7000)
                                                                             at android.app.Activity.performCreate(Activity.java:6991)
                                                                             at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1214)
                                                                             at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:2731)
                                                                             at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:2856)
                                                                             at android.app.ActivityThread.-wrap11(Unknown Source:0)
                                                                             at android.app.ActivityThread$H.handleMessage(ActivityThread.java:1589)
                                                                             at android.os.Handler.dispatchMessage(Handler.java:106)
                                                                             at android.os.Looper.loop(Looper.java:164)
                                                                             at android.app.ActivityThread.main(ActivityThread.java:6494)
                                                                             at java.lang.reflect.Method.invoke(Native Method)
                                                                             at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:438)
                                                                             at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:807)
                                                                          Caused by: android.os.ServiceSpecificException: Caller does not own I2C1(0x60) (code 13)
                                                                             at android.os.Parcel.readException(Parcel.java:2018)
                                                                             at android.os.Parcel.readException(Parcel.java:1950)
                                                                             at com.google.android.things.pio.IPeripheralManagerClient$Stub$Proxy.ReleaseI2cDevice(IPeripheralManagerClient.java:1259)
                                                                             at com.google.android.things.pio.I2cDeviceImpl.close(I2cDeviceImpl.java:57)
                                                                             at com.pinball_mods.walkwayrgb.HomeActivity.onCreate(HomeActivity.java:132) 
                                                                             at android.app.Activity.performCreate(Activity.java:7000) 
                                                                             at android.app.Activity.performCreate(Activity.java:6991) 
                                                                             at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1214) 
                                                                             at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:2731) 
                                                                             at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:2856) 
                                                                             at android.app.ActivityThread.-wrap11(Unknown Source:0) 
                                                                             at android.app.ActivityThread$H.handleMessage(ActivityThread.java:1589) 
                                                                             at android.os.Handler.dispatchMessage(Handler.java:106) 
                                                                             at android.os.Looper.loop(Looper.java:164) 
                                                                             at android.app.ActivityThread.main(ActivityThread.java:6494) 
                                                                             at java.lang.reflect.Method.invoke(Native Method) 
                                                                             at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:438) 
                                                                             at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:807) 
12-18 23:24:10.122 2035-2035/com.pinball_mods.walkwayrgb D/vndksupport: Loading /vendor/lib/hw/android.hardware.graphics.mapper@2.0-impl.so from current namespace instead of sphal namespace.
```
 
## Workarounds Attempted: 
app/src/main/AndroidManifest.xml
```
    <uses-permission android:name="com.google.android.things.permission.MANAGE_INPUT_DRIVERS" />
    <uses-permission android:name="com.google.android.things.permission.MANAGE_SENSOR_DRIVERS" />
```
No impact. 
Tried Dev Previous 0.6.1 and 0.6.0.

Tried uninstalling app using ADB and rebuilding and deloying. Reboot. Same issue.    
