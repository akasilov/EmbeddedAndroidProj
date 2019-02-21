package ch.bfh.ti.gpio;

public interface ButtonEventListener
{
    void onButtonPressed(Buttons object, int buttonNumber);
    void onButtonReleased(Buttons object, int buttonNumber);
}