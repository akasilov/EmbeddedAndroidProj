package ch.bfh.ti.gpio;

import android.util.Log;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class Buttons {

    private Buttons ownRef = this;

    /* button events */
    private List<ButtonEventListener> mButtonEventListeners = new ArrayList<ButtonEventListener>();

    private final int mNumberOfButtons = 4;
    private final int mUpdateThread_HoldOffTimerTicks = 5; //must be >0. number of mUpdateThread_Interval_ms expired (ignoring state changes on button-gpio for amount of ticks for debouncing purposes)
    private final long mUpdateThread_Interval_ms = 20;

    /* debug/statistics */
    public AtomicInteger debug_counter_ButtonReleasedEvents;
    public AtomicInteger debug_counter_ButtonPressedEvents;
    public AtomicInteger debug_counter_UpdateThreadTicks;
    public AtomicInteger[] debug_timer_Holdoff;

    private class ButtonUpdateThread extends Thread
    {
        @Override
        public void run() {

            boolean[] isNowPressed = new boolean[mNumberOfButtons];

            while (mUpdateThread_IsTerminate == false) {
                try
                {
                    debug_counter_UpdateThreadTicks.addAndGet(1);

                    /* get gpio/button states (ignore while hold off is active) */
                    if (mButtonStates[0].isHoldoffActive == false) { isNowPressed[0] = mGPIO.read_value(SysfsFileGPIO.BUTTON_T1).contains("0"); } // "0" -> button is pressed
                    if (mButtonStates[1].isHoldoffActive == false) { isNowPressed[1] = mGPIO.read_value(SysfsFileGPIO.BUTTON_T2).contains("0"); }
                    if (mButtonStates[2].isHoldoffActive == false) { isNowPressed[2] = mGPIO.read_value(SysfsFileGPIO.BUTTON_T3).contains("0"); }
                    if (mButtonStates[3].isHoldoffActive == false) { isNowPressed[3] = mGPIO.read_value(SysfsFileGPIO.BUTTON_T4).contains("0"); }

                    for (int i=0; i<mNumberOfButtons; i++)
                    {
                        debug_timer_Holdoff[i].set(mButtonStates[i].holdOffTimer);
                    }

                    /* check for state change */
                    for (int i=0; i<mNumberOfButtons; i++)
                    {
                        if (isNowPressed[i] != mButtonStates[i].isPressed)
                        {
                            /* state change occurred */

                            /* reset (start) holdoff counter */
                            mButtonStates[i].holdOffTimer = mUpdateThread_HoldOffTimerTicks;
                            mButtonStates[i].isHoldoffActive = true;
                        }
                        else
                        {
                            /* no state change */

                            /* check if holdoff timer expired */
                            if ((mButtonStates[i].holdOffTimer == 0) && (mButtonStates[i].isHoldoffActive))
                            {
                                /* holdoff is expired */

                                /* deactivate holdoff */
                                mButtonStates[i].isHoldoffActive = false;

                                /* generate button events */
                                if (mButtonStates[i].isPressed == true)
                                {
                                    /* button pressed event */
                                    debug_counter_ButtonPressedEvents.addAndGet(1);
                                    for (ButtonEventListener bpl : mButtonEventListeners)
                                    {
                                       bpl.onButtonPressed(ownRef, i);
                                    }
                                }
                                else
                                {
                                    /* button released event */
                                    debug_counter_ButtonReleasedEvents.addAndGet(1);
                                    for (ButtonEventListener bpl : mButtonEventListeners)
                                    {
                                        bpl.onButtonReleased(ownRef, i);
                                    }
                                }
                            }

                            /* decrement holdoff counter */
                            if (mButtonStates[i].holdOffTimer > 0)
                            {
                                mButtonStates[i].holdOffTimer--;
                            }
                        }
                    }

                    /* update states */
                    for (int i=0; i<mNumberOfButtons; i++)
                    {
                        mButtonStates[i].isPressed = isNowPressed[i];
                    }

                    /* sleep */
                    Thread.sleep(mUpdateThread_Interval_ms);
                }
                catch (InterruptedException ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    }

    private boolean mUpdateThread_IsTerminate;

    private class ButtonState
    {
        public boolean isPressed;
        public int holdOffTimer;
        public boolean isHoldoffActive;
    }

    private SysfsFileGPIO mGPIO;
    private ButtonUpdateThread mUpdateThread;
    private ButtonState[] mButtonStates;

    /* Constructor */
    public Buttons(SysfsFileGPIO sysfs_gpio)
    {
        mGPIO = sysfs_gpio;
        if (mGPIO == null)
        {
            Log.w("class SysfsFileGPIO", "Nullpointer");
        }

        /* set gpio direction to "in" */
        //setDirection(false);

        /* initialize button state array */
        mButtonStates = new ButtonState[mNumberOfButtons];
        for (int i=0;i<mNumberOfButtons;i++)
        {
            mButtonStates[i] = new ButtonState();
            mButtonStates[i].holdOffTimer = 0;
            mButtonStates[i].isPressed = false;
            mButtonStates[i].isHoldoffActive = false;
        }

        /* reset event counters */
        debug_counter_ButtonReleasedEvents = new AtomicInteger(0);
        debug_counter_ButtonPressedEvents = new AtomicInteger(0);
        debug_counter_UpdateThreadTicks = new AtomicInteger(0);
        debug_timer_Holdoff = new AtomicInteger[mNumberOfButtons];
        for (int i=0; i<mNumberOfButtons; i++)
        {
            debug_timer_Holdoff[i] = new AtomicInteger(0);
        }

        /* set up Update Thread */
        mUpdateThread_IsTerminate = false;
        mUpdateThread = new ButtonUpdateThread();
        mUpdateThread.start();
    }

    /* Destructor */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        mUpdateThread_IsTerminate = true;
    }

    /*
    private boolean setDirection(boolean dir_IN_out)
    {
        boolean isSuccessful;
        if (dir_IN_out == true)
        {
            isSuccessful = (
                mGPIO.set_direction_in(SysfsFileGPIO.BUTTON_T1) &&
                mGPIO.set_direction_in(SysfsFileGPIO.BUTTON_T2) &&
                mGPIO.set_direction_in(SysfsFileGPIO.BUTTON_T3) &&
                mGPIO.set_direction_in(SysfsFileGPIO.BUTTON_T4) );
        }
        else
        {
            isSuccessful = (
                mGPIO.set_direction_out(SysfsFileGPIO.BUTTON_T1) &&
                mGPIO.set_direction_out(SysfsFileGPIO.BUTTON_T2) &&
                mGPIO.set_direction_out(SysfsFileGPIO.BUTTON_T3) &&
                mGPIO.set_direction_out(SysfsFileGPIO.BUTTON_T4) );
        }
        return isSuccessful;
    }

    public boolean export()
    {
        if (    mGPIO.export(SysfsFileGPIO.BUTTON_T1) &&
                mGPIO.export(SysfsFileGPIO.BUTTON_T2) &&
                mGPIO.export(SysfsFileGPIO.BUTTON_T3) &&
                mGPIO.export(SysfsFileGPIO.BUTTON_T4) )
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public boolean unexport()
    {
        if (    mGPIO.unexport(SysfsFileGPIO.BUTTON_T1) &&
                mGPIO.unexport(SysfsFileGPIO.BUTTON_T2) &&
                mGPIO.unexport(SysfsFileGPIO.BUTTON_T3) &&
                mGPIO.unexport(SysfsFileGPIO.BUTTON_T4) )
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public boolean open()
    {
        return true;
    }

    public boolean close()
    {
        return true;
    }
    */

    public void addButtonEventListener(ButtonEventListener listener) {
        mButtonEventListeners.add(listener);
    }

    public void debug()
    {
        Log.i("debug()",String.format("ticks: %d, holdoff T1: %d, pressed event: %d, released event %d", debug_counter_UpdateThreadTicks.get(), debug_timer_Holdoff[0].get(), debug_counter_ButtonPressedEvents.get(), debug_counter_ButtonReleasedEvents.get()));
    }
}
