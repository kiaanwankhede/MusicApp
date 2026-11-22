package com.kiaanwankhede.tinytunes;

import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import androidx.activity.OnBackPressedCallback;
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
        // Back button / edge swipe: the web app goes back one screen (a stray swipe by the child must not
        // close music time). Only on the app's home screen does Back leave the app as usual.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (bridge == null || bridge.getWebView() == null) {
                    leave();
                    return;
                }
                bridge.getWebView().evaluateJavascript("(window.TT&&TT.back)?TT.back():false", value -> {
                    if (!"true".equals(value)) leave();
                });
            }

            private void leave() {
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
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
