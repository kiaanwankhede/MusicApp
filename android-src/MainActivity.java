package com.kiaanwankhede.tinytunes;

import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Local plugins must be registered before super.onCreate() builds the Capacitor bridge.
        registerPlugin(TinyTunesPlugin.class);
        super.onCreate(savedInstanceState);
        // Keep the screen on while the app is open.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Volume keys in this app always mean media volume (not ringer), even when nothing is playing.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // While the volume is locked (a session is running) the volume keys do nothing.
        if (event != null && TinyTunesPlugin.isVolumeLocked() && TinyTunesPlugin.isVolumeKey(event.getKeyCode())) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onTopResumedActivityChanged(boolean isTopResumedActivity) {
        super.onTopResumedActivityChanged(isTopResumedActivity);
        // Android 10+: another app / split-screen window / system dialog is on top -> release the volume lock.
        TinyTunesPlugin.onHostTopResumedChanged(isTopResumedActivity);
    }
}
