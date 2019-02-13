/*
 ***************************************************************************
 * \brief   Embedded Android I2C Exercise 4.2
 *	        Native basic i2c communication interface
 *	        Only a minimal error handling is implemented.
 *
 * \file    I2C.java
 * \version 1.0
 * \date    06.03.2014
 * \author  Martin Aebersold
 *
 * \remark  Last Modifications:
 * \remark  V1.0, AOM1, 06.03.2014
 ***************************************************************************
 */

package ch.bfh.ti.i2c;

import android.util.Log;

/***************************************************************************
 * This is an I2C operation class
 **************************************************************************/

public class I2C
{
    /**
     * @brief open the special device file
     *
     * @param deviceName
     *
     * @return return file handler else return <0 on fail
     */
    public native int open(String deviceName);


    /**
     * @brief Set the i2c slave address
     *
     * @param fileHandler
     * @param i2c_adr
     *
     * @return return file handler else return <0 on fail
     */
    public native int SetSlaveAddress(int fileHandler, int i2c_adr);

    /**
     * @brief Read a defined number of bytes from the i2c device
     *
     * @param fileHandler
     * @param buffer
     * @param length
     *
     * @return Number of bytes read
     */
    public native int read(int fileHandler, int buffer[], int length);

    /**
     * @brief Write a defined number of bytes to the i2c device
     *
     * @param fileHandler
     * @param buffer
     * @param length
     *
     * @return Number of bytes written
     */
    public native int write(int fileHandler, int buffer[], int length);


    /**
     * @brief Close the special device file
     *
     * @param fileHandler
     *
     * @return -
     */
    public native void close(int fileHandler);

    /* The static block is run once on application startup. */
    static
    {
        try
        {
            Log.i("JNI", "Trying to load libi2c_interface.so");
            System.loadLibrary("i2c_interface");
        }
        catch (final UnsatisfiedLinkError e)
        {
            Log.e("JNI", "WARNING: Could not load libi2c_interface.so" + Log.getStackTraceString(e));
        }
    }
}
