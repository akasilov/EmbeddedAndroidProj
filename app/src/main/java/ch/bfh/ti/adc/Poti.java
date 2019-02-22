package ch.bfh.ti.adc;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import static java.lang.Math.pow;

public class Poti {

    private final String SYSFS_ADC_AIN2 = "/sys/bus/iio/devices/iio\\:device0/in_voltage2_raw"; //ADC AIN2 sysfs path
    private final double UREF_V = 1.8; //ADC reference voltage
    private final double NUMBER_OF_BITS = 10; //ADC resolution

    /*
     * Constructor
     */
    public Poti()
    {

    }

    /*
     * Read a Poti value (ADC-Value)
     */
    public int getAdcValue_adc() {
        int value = 0;
        try {
            value = Integer.parseInt(getAdcValue_str());
        }
        catch (Exception ex)
        {
            Log.w("ADC","could not parse string to int. Exception: " + ex.getMessage());
        }
        return value;
    }

    /*
     * Read a Poti value (Volt)
     */
    public double getAdcValue_volt() {
        return getAdcValue_adc() * UREF_V / (pow(2,NUMBER_OF_BITS) - 1);
    }

    /*
     * Read a Poti value (ADC-Value as string)
     */
    private String getAdcValue_str()
    {
        String text = "";

        try
        {
            String command = "cat /sys/bus/iio/devices/iio\\:device0/in_voltage2_raw";

            Process suProcess = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(suProcess.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(suProcess.getInputStream()));

            // Execute command with root access
            os.writeBytes(command + "\n");
            os.flush();

            //StringBuilder text = new StringBuilder();
            text = reader.readLine();
            os.writeBytes("exit\n");
            os.flush();

            //Log.i("ADC","value: " + text);

            try
            {
                int suProcessRetval = suProcess.waitFor();
                if (255 != suProcessRetval)
                {
                    // Root access granted
                }
                else
                {
                    // Root access denied
                    Log.w("ADC","no root access granted to access sysfs");
                }
            }
            catch (Exception ex)
            {
                Log.e("ADC", "Error executing root action", ex);
            }
        }
        catch (IOException ex)
        {
            Log.w("ADC", "Can't get root access", ex);
        }
        catch (SecurityException ex)
        {
            Log.w("ADC", "Can't get root access", ex);
        }
        catch (Exception ex)
        {
            Log.w("ADC", "Error executing internal operation", ex);
        }

        return text;
    }


}
