package ch.bfh.ti.gpio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/*
 * This class provides gpio access via the sysfs.
 * Remember: All sysfs commands are executed by ASCII character
 */
public class SysfsFileGPIO
{
    public static final String LED_L1 = "124";
    public static final String LED_L2 = "125";
    public static final String LED_L3 = "126";
    public static final String LED_L4 = "127";

    public static final String BUTTON_T1 = "120";
    public static final String BUTTON_T2 = "121";
    public static final String BUTTON_T3 = "122";
    public static final String BUTTON_T4 = "123";

    /* Define some useful constants */
    static final String GPIO_OUT = "out";
    static final String GPIO_IN = "in";
    static final String GPIO_HIGH = "1";
    static final String GPIO_LOW = "0";

    /* Define path names for the sysfs */
    static final String SYSFS_GPIO = "/sys/class/gpio/gpio";
    static final String SYSFS_GPIO_DIRECTION = "/direction";
    static final String SYSFS_GPIO_VALUE = "/value";
    static final String SYSFS_GPIO_EXPORT = "/sys/class/gpio/unexport";
    static final String SYSFS_GPIO_UNEXPORT = "/sys/class/gpio/unexport";

    /*
     * Define some useful constants
     */
    public static final char ON = '1';
    public static final char OFF = '0';

    /*
     *  Export a gpio number
     */
    public boolean export(String gpio)
    {
        try {
            /*
             *  Open file handle to export a GPIO number
             */
            FileWriter unexportFile = new FileWriter(SYSFS_GPIO_UNEXPORT);
            FileWriter exportFile =  new FileWriter(SYSFS_GPIO_EXPORT);

            /*
             *  Clear the port, if needed
             */
            File exportFileCheck = new File(SYSFS_GPIO + gpio);

            if (exportFileCheck.exists())
            {
                unexportFile.write(gpio);
                unexportFile.flush();
                unexportFile.close();
            }

            /*
             *  Set the port for use
             */
            exportFile.write(gpio);
            exportFile.flush();
            exportFile.close();
            return true;
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
            return false;
        }
    }

    /*
     * Unexport a gpio
     */
    public boolean unexport(String gpio)
    {
        try {
            /*
             *  Open file handle to unexport a GPIO number
             */
            FileWriter unexportFile = new FileWriter(SYSFS_GPIO_UNEXPORT);
            unexportFile.write(gpio);
            unexportFile.flush();
            unexportFile.close();
            return true;
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
            return false;
        }
    }

    /*
     * Set gpio direction to output
     */
    public boolean set_direction_out(String gpio)
    {
        try
        {
            /*
             *  Open file handle to set the GPIO direction as output
             */
            FileWriter directionFile = new FileWriter(SYSFS_GPIO + gpio + SYSFS_GPIO_DIRECTION);

            /*
             *  Set port for output
             */
            directionFile.write(GPIO_OUT);
            directionFile.flush();
            directionFile.close();
            return true;
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
            return false;
        }
    }

    /*
     * Set gpio direction to input
     */
    public boolean set_direction_in(String gpio)
    {
        try
        {
            /*
             *  Open file handle to set the GPIO direction as input
             */
            FileWriter directionFile = new FileWriter(SYSFS_GPIO + gpio + SYSFS_GPIO_DIRECTION);

            /*
             *  Set port for output
             */
            directionFile.write(GPIO_IN);
            directionFile.flush();
            directionFile.close();
            return true;
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
            return false;
        }
    }

    /*
     * Write a gpio value
     */
    public boolean write_value(String gpio, char value)
    {
        try
        {
            /*
             *  Set up File I/O to write a value to the GPIO
             */
            FileWriter gpioNumber = new FileWriter(SYSFS_GPIO + gpio + SYSFS_GPIO_VALUE);

            gpioNumber.write(value);
            gpioNumber.flush();
            gpioNumber.close();
            return true;
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
            return false;
        }
    }

    /*
     * Write a gpio value
     */
    public FileWriter get_value_writer(String gpio)
    {
        try
        {
            /*
             *  Set up File I/O to write a value to the GPIO
             */
            return  new FileWriter(SYSFS_GPIO + gpio + SYSFS_GPIO_VALUE);
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
            return null;
        }
    }
    /*
     * Read a gpio value
     */
    public String read_value(String gpio)
    {
        /*
         *  Set up File I/O read a value from the GPIO
         */
        String value;
        try
        {
            BufferedReader br = new BufferedReader(new FileReader(SYSFS_GPIO + gpio + SYSFS_GPIO_VALUE));
            value = br.readLine();
            br.close();
        }
        catch (IOException ex)
        {
            return "-1";
        }
        return value;
    }
}
