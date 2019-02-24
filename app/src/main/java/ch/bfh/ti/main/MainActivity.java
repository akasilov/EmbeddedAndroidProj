package ch.bfh.ti.main;


import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Switch;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

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

    private Handler mButtonHandler;

    private Handler mPotiHandler;

    private Switch[] mButtonSwitches = {null,null,null,null};
    private RadioButton[] mLedCheckBoxes = {null,null,null,null};

    private LineGraphSeries<DataPoint> mGraphSeries;

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

                mGraphSeries.appendData(new DataPoint(new Date().getTime(), luminance), true, 100);

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

    private class SwitchClickListner implements CompoundButton.OnClickListener {
        public SwitchClickListner(int switchId) {
            mSwitchId = switchId;
        }

        @Override
        public void onClick(View v) {

            boolean isChecked = ((Switch)v).isChecked();
            gpio.write_value(ledIds[mSwitchId-1], isChecked ? '1': '0');

            if (mqttHelper.isConnected()) {
                try {
                    mqttHelper.sendMessage(MQTT_TOPIC_BUTTONS + String.valueOf(mSwitchId), isChecked ? "1": "0");
                } catch (MqttException ex) {
                    System.err.println("Exception whilst publishing");
                    ex.printStackTrace();
                }
            }
        }

        //private String mLed;
        private int mSwitchId;
    }

    private class LedClickListner implements CompoundButton.OnClickListener {
        public LedClickListner(int ledId) {
            mLedId = ledId;
        }

        @Override
        public void onClick(View v) {

            boolean isChecked = ((RadioButton)v).isChecked();
            if (mChecked == false) {
                ((RadioButton)v).setChecked(true);
                mChecked = true;
            }
            else if (mChecked == true) {
                ((RadioButton)v).setChecked(false);
                mChecked = false;
            }


            if (mqttHelper.isConnected()) {
                try {
                    mqttHelper.sendMessage(MQTT_TOPIC_LEDS + String.valueOf(mLedId), mChecked ? "1": "0");
                } catch (MqttException ex) {
                    System.err.println("Exception whilst publishing");
                    ex.printStackTrace();
                }
            }
        }

        //private String mLed;
        private int mLedId;
        boolean mChecked = false;
    }

    private class SwitchCheckedListner implements CompoundButton.OnCheckedChangeListener {
        public SwitchCheckedListner(RadioButton ledCheckbox) {
            mLedCheckbox = ledCheckbox;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            mLedCheckbox.setChecked(isChecked);
        }

        RadioButton mLedCheckbox;
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

        mButtonSwitches[0] = (Switch) findViewById(R.id.t1_id);
        mButtonSwitches[1] = (Switch) findViewById(R.id.t2_id);
        mButtonSwitches[2] = (Switch) findViewById(R.id.t3_id);
        mButtonSwitches[3] = (Switch) findViewById(R.id.t4_id);

        mLedCheckBoxes[0] = (RadioButton) findViewById(R.id.led1_id);
        mLedCheckBoxes[1] = (RadioButton) findViewById(R.id.led2_id);
        mLedCheckBoxes[2] = (RadioButton) findViewById(R.id.led3_id);
        mLedCheckBoxes[3] = (RadioButton) findViewById(R.id.led4_id);

        GraphView graph = (GraphView) findViewById(R.id.graph_id);
        mGraphSeries = new LineGraphSeries<>();
        graph.addSeries(mGraphSeries);

        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(0);
        graph.getViewport().setMaxY(100);

        // set date label formatter

        graph.getGridLabelRenderer().setLabelFormatter(new DateAsXAxisLabelFormatter(this, new SimpleDateFormat("HH:mm:ss")));
        graph.getGridLabelRenderer().setNumHorizontalLabels(3);

        Date d1 = new Date();
        graph.getViewport().setMinX(d1.getTime());
        graph.getViewport().setMaxX(d1.getTime() +  60000);


        for (String ledId : ledIds) {
            gpio.unexport(ledId);
            gpio.export(ledId);
            gpio.set_direction_out(ledId);
        }

        int index = 0;
        for (Switch sw : mButtonSwitches) {
            sw.setChecked(false);
            sw.setOnClickListener(new SwitchClickListner(index + 1));
            index++;
        }
        index = 0;
        for (RadioButton cb : mLedCheckBoxes) {
            cb.setChecked(false);
            cb.setOnClickListener(new LedClickListner(index + 1));
            index++;
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
                        mLedCheckBoxes[ledId].setChecked(value == '1' ? true : false);
                        gpio.write_value(ledIds[ledId], value);
                    }
                }
                if (topic.startsWith(MQTT_TOPIC_BUTTONS)) {
                    int buttonId = Character.getNumericValue(topic.charAt(MQTT_TOPIC_BUTTONS.length()));
                    --buttonId;
                    if (mqttMessage.toString().length() == 1 && buttonId >= 0 && buttonId < mButtonSwitches.length) {
                        char value = mqttMessage.toString().charAt(0);
                        Log.d("Mqtt", "Changing the Button" + String.valueOf(buttonId));
                        mButtonSwitches[buttonId].setChecked(value == '1' ? true : false);
                        gpio.write_value(ledIds[buttonId], value);
                    }
                }
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
