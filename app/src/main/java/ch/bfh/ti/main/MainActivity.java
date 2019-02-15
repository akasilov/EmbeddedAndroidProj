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


import android.content.res.ColorStateList;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import ch.bfh.ti.i2c.I2C;
import ch.bfh.ti.proj.R;
import ch.bfh.ti.mqtt.MqttHelper;
import ch.bfh.ti.gpio.SysfsFileGPIO;

public class MainActivity extends AppCompatActivity {

    /* MAX44009 Register pointers */
    private static final char MAX44009_CONFIG = 0x02;    /* Sensor Configuration Register */

    /* I2C Address of the MAX44009 device */
    private static final char MAX44009_I2C_ADDR = 0x4A;

    /* I2C device file name */
    private static final String MAX44009_FILE_NAME = "/dev/i2c-4";

    private static final String MQTT_TOPIC_LEDS ="firefly/leds/led";

    private static final String[] ledIds = {SysfsFileGPIO.LED_L1, SysfsFileGPIO.LED_L2,
                                            SysfsFileGPIO.LED_L3, SysfsFileGPIO.LED_L4};

    private MqttHelper mqttHelper;

    /* I2C object variable */
    private final I2C i2c = new I2C();

    private final SysfsFileGPIO gpio = new SysfsFileGPIO();

    /* I2C Communication buffer and file handle */
    private int[] i2cCommBuffer = new int[16];
    private int fileHandle;

    /* Light and conversion variable */
    static int exponent;
    static int mantissa;
    static double luminance;

    /* Variable for TextView widgets */
    TextView textViewAmbientLight;
    TextView dataReceived;

    /* Temperature Degrees Celsius text symbol */
    private static final String DEGREE_SYMBOL = "\u2103";

    /* Method is run at app startup */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_i2c);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);

        textViewAmbientLight = (TextView) findViewById(R.id.textViewAmbientLight);
        dataReceived = (TextView) findViewById(R.id.dataReceived);

        for (String ledId : ledIds) {
            gpio.unexport(ledId);
            gpio.export(ledId);
            gpio.set_direction_out(ledId);
        }

        startMqtt();
	    /* Instantiate the new i2c device */

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


        /* Display actual ambient light value in lux */
        textViewAmbientLight.setTextColor(Color.WHITE);
        dataReceived.setTextColor(Color.WHITE);
        textViewAmbientLight.setText("Lux: " + String.format("%3.2f", luminance));

	    /* Close the i2c file */
        i2c.close(fileHandle);
    }

    /*
     * 	(non-Javadoc)
     * @see android.app.Activity#onStop()
     */
    protected void onStop()
    {
        android.os.Process.killProcess(android.os.Process.myPid());
        finish();
        super.onStop();
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
            public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
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

                dataReceived.setText(text);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

            }
        });
    }
}
