/*
 ***************************************************************************
 * \brief   Embedded Android I2C Exercise 5.2
 *          This sample program shows how to use the native I2C interface
 *	        Basic i2c communication with the the MAX44009 Ambient Light Sensor
 *          on the FireFly-BFH-Cape.
 *          Show the current Ambient Light in Lux on the display.
 *	        Only a minimal error handling is implemented.
 * \file    MainActivity.java
 * \version 1.0
 * \date    06.03.2014
 * \author  Martin Aebersold
 *
 * \remark  Last Modifications:
 * \remark  V1.0, AOM1, 06.03.2014
 ***************************************************************************
 *
 * Copyright (C) 2018 Martin Aebersold, Bern University of Applied Scinces
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package ch.bfh.ti.main;


import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.ref.WeakReference;

import ch.bfh.ti.gpio.ButtonEventListener;
import ch.bfh.ti.i2c.I2C;
import ch.bfh.ti.proj.R;
import ch.bfh.ti.mqtt.MqttHelper;
import ch.bfh.ti.gpio.SysfsFileGPIO;
import ch.bfh.ti.gpio.Buttons;
import ch.bfh.ti.adc.Poti;

public class MainActivity extends AppCompatActivity {

    /* MAX44009 Register pointers */
    private static final char MAX44009_CONFIG = 0x02;    /* Sensor Configuration Register */

    /* I2C Address of the MAX44009 device */
    private static final char MAX44009_I2C_ADDR = 0x4A;

    /* I2C device file name */
    private static final String MAX44009_FILE_NAME = "/dev/i2c-4";

    private static final String MQTT_TOPIC_LEDS ="firefly/leds/led";
    private static final String MQTT_TOPIC_LUMINANCE ="firefly/sensors/lux";
    private static final String MQTT_TOPIC_BUTTONS ="firefly/buttons/T";
    private static final String MQTT_TOPIC_POTI ="firefly/poti";

    private static final String[] ledIds = {SysfsFileGPIO.LED_L1, SysfsFileGPIO.LED_L2,
                                            SysfsFileGPIO.LED_L3, SysfsFileGPIO.LED_L4};

    private MqttHelper mqttHelper;

    /* I2C object variable */
    private final I2C i2c = new I2C();

    private final SysfsFileGPIO gpio = new SysfsFileGPIO();

    private boolean mStopUpdateThread = false;

    private Buttons mButtons;

    private Poti mPoti;

    /* Updare every second */
    private int mUpdateInterval = 500;

    /* Variable for TextView widgets */
    //TextView textViewAmbientLight;
    //TextView dataReceived;

    private Handler mButtonHandler;

    private Handler mPotiHandler;

    private Thread mLuminanceValueUpdaterThread = new Thread(new Runnable() {
        @Override
        public void run() {
            while(!mStopUpdateThread) {
                double luminance = getLuminanceValue();
                Message message = new Message();
                Bundle b = new Bundle();
                b.putDouble("luminance", luminance);
                message.setData(b);
                mSensorHandler.sendMessage(message);
                try{
                    Thread.sleep(mUpdateInterval);
                }
                catch (InterruptedException ex){
                    ex.printStackTrace();
                }
                //mButtons.debug();
            }
        }
    });

    /**
     * Instances of static inner classes do not hold an implicit
     * reference to their outer class.
     */
    private class SensorDataHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public SensorDataHandler(MainActivity activity) {
            mActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity != null) {
                super.handleMessage(msg);
                double luminance = msg.getData().getDouble("luminance");
                //activity.textViewAmbientLight.setText("Lux: " + String.format("%3.2f", luminance));
                if (mqttHelper.isConnected()) {
                    try {
                        mqttHelper.sendMessage(MQTT_TOPIC_LUMINANCE, String.valueOf((int)luminance));
                        //Log.i("MQTT handle Message Thread:",Thread.currentThread().getName());
                    } catch (MqttException ex) {
                        System.err.println("Exception whilst publishing");
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    private final SensorDataHandler mSensorHandler = new SensorDataHandler(this);


    /* Temperature Degrees Celsius text symbol */
    private static final String DEGREE_SYMBOL = "\u2103";

    /* Method is run at app startup */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_i2c);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.drawable.logo_bfh);

        //getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        //textViewAmbientLight = findViewById(R.id.textViewAmbientLight);
        //dataReceived = findViewById(R.id.dataReceived);
        //textViewAmbientLight.setTextColor(Color.WHITE);
        //dataReceived.setTextColor(Color.WHITE);

        for (String ledId : ledIds) {
            gpio.unexport(ledId);
            gpio.export(ledId);
            gpio.set_direction_out(ledId);
        }

        /* MQTT */
        startMqtt();

        /* BUTTONS */
        mButtonHandler = new Handler();
        mButtons = new Buttons(gpio);
        mButtons.addButtonEventListener(new ButtonEventListener() {
            @Override
            public void onButtonPressed(Buttons object, final int buttonNumber) {
                mButtonHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mqttHelper.isConnected()) {
                            try {
                                mqttHelper.sendMessage(MQTT_TOPIC_BUTTONS + String.valueOf(buttonNumber+1), "1");
                            } catch (MqttException ex) {
                                System.err.println("Exception whilst publishing");
                                ex.printStackTrace();
                            }
                        }
                    }
                });
            }

            @Override
            public void onButtonReleased(Buttons object, final int buttonNumber) {
                mButtonHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mqttHelper.isConnected()) {
                            try {
                                mqttHelper.sendMessage(MQTT_TOPIC_BUTTONS + String.valueOf(buttonNumber+1), "0");
                            } catch (MqttException ex) {
                                System.err.println("Exception whilst publishing");
                                ex.printStackTrace();
                            }
                        }
                    }
                });
            }
        });

        //Log.i("Thread onCreate:",Thread.currentThread().getName());

        /* POTI */
        mPoti = new Poti();
        mPoti.getAdcValue_adc();
        mPotiHandler = new Handler();
        mPotiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mqttHelper.isConnected()) {
                    try {
                        mqttHelper.sendMessage(MQTT_TOPIC_POTI, Integer.toString(mPoti.getAdcValue_adc()));
                    } catch (MqttException ex) {
                        System.err.println("Exception whilst publishing");
                        ex.printStackTrace();
                    }
                }
                mPotiHandler.postDelayed(this,200);
                Log.i("MQTT","message sent");
            }
        });


//        try {
//            Log.d("Mqtt","Sending a message to topic " + MQTT_TOPIC_LUMINANCE + "=" +  String.valueOf((int)luminance));
//            int aa = (int)luminance;
//            //mqttHelper.sendMessage(MQTT_TOPIC_LUMINANCE, "AAAAA");
//        } catch (MqttException ex) {
//            System.err.println("Exception whilst publishing");
//            ex.printStackTrace();
//        }
    }

    protected void onStop() {
        stopLuminanceUpdateThread();

        android.os.Process.killProcess(android.os.Process.myPid());
        finish();
        super.onStop();
    }

    @Override
    public void onPause()
    {
        super.onPause();
        stopLuminanceUpdateThread();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        startLuminanceUpdateThread();
    }

    private void startMqtt(){
        mqttHelper = new MqttHelper(getApplicationContext());
        mqttHelper.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean b, String s) {
                Log.w("Mqtt","Connection complete");

            }

            @Override
            public void connectionLost(Throwable throwable) {
                Log.w("Mqtt","Connection lost");

            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) {
                String text = topic + "=" + mqttMessage.toString();

                if (topic.startsWith(MQTT_TOPIC_LEDS)) {
                    int ledId = Character.getNumericValue(topic.charAt(MQTT_TOPIC_LEDS.length()));
                    --ledId;
                    if (mqttMessage.toString().length() == 1 && ledId >= 0 && ledId < ledIds.length) {
                        char value = mqttMessage.toString().charAt(0);
                        Log.d("Mqtt", "Changing the LED" + String.valueOf(ledId));
                        gpio.write_value(ledIds[ledId], value);
                    }
                }

                //dataReceived.setText(text);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

            }
        });
    }

    private void startLuminanceUpdateThread() {
        Log.i("Activity","Start updating the luminance value");
        mLuminanceValueUpdaterThread.start();
    }

    void stopLuminanceUpdateThread() {
        Log.i("Activity","Stop updating the luminance value");
        mStopUpdateThread = true;
        mSensorHandler.removeCallbacks(mLuminanceValueUpdaterThread);
    }

    private double getLuminanceValue() {
        //Log.i("Activity","Reading the value from the sensor");
        /* I2C Communication buffer and file handle */
        int[] i2cCommBuffer = new int[16];
        int fileHandle;

        /* Light and conversion variable */
        int exponent;
        int mantissa;
        double luminance;

        /* Open the i2c device, get the file handle */
        fileHandle = i2c.open(MAX44009_FILE_NAME);

        /* Set the i2c slave address for all subsequent I2C device transfers */
        i2c.SetSlaveAddress(fileHandle, MAX44009_I2C_ADDR);

        /* Setup i2c buffer for the configuration register an write it to the MAX44009 device */
        /* Continuous mode, Integration time = 800 ms(0x40)	                                  */
        i2cCommBuffer[0] = MAX44009_CONFIG;
        i2cCommBuffer[1] = 0x40;
        i2c.write(fileHandle, i2cCommBuffer, 2);

        /* Setup the MAX44009 register to read the ambient light value */
        i2cCommBuffer[0] = 0x03;
        i2c.write(fileHandle, i2cCommBuffer, 1);

        /* Read the current ambient light value from the MAX44009 device */
        i2c.read(fileHandle, i2cCommBuffer, 2);

        /* Convert the ambient light value to lux, have a look at the datasheet	*/
        exponent = (i2cCommBuffer[0] & 0xF0) >> 4;
        mantissa = ((i2cCommBuffer[0] & 0x0F) << 4) | (i2cCommBuffer[1] & 0x0F);
        luminance = Math.pow(2, exponent) * mantissa * 0.045;

        //Log.i("Activity","Luminance value is " + String.valueOf(luminance));

        /* Close the i2c file */
        i2c.close(fileHandle);

        return luminance;
    }
}
