package com.kiaanwankhede.tinytunes;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Tiny Tunes native helper (Capacitor 6).
 *
 * Called from the web page ONLY as
 *   window.Capacitor.nativePromise('TinyTunes', method, options)
 * Every method resolves with a plain object or rejects with {message, code}; none of them may crash the app
 * (an exception escaping a @PluginMethod is re-thrown by the Capacitor bridge as a RuntimeException).
 *
 * Volume lock: while locked, MainActivity swallows the volume keys and a Settings.System ContentObserver
 * (plus the system VOLUME_CHANGED broadcast) puts STREAM_MUSIC straight back to the locked level.
 * The lock is released whenever the app is paused, loses the top position (split screen, another app, a
 * system dialog) or is destroyed, so it can never hold down the volume in YouTube or any other app.
 * If the lock was released by a pause / loss of focus (not by unlockVolume()), it is put back when this
 * app becomes the top resumed activity again.
 */
@CapacitorPlugin(name = "TinyTunes")
public class TinyTunesPlugin extends Plugin {

    private static final String TAG = "TinyTunes";
    private static final int STREAM = AudioManager.STREAM_MUSIC;

    private static final int MAX_FILES = 12;
    private static final long MAX_FILE_BYTES = 40L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 80L * 1024 * 1024;
    private static final long EXPORT_MAX_AGE_MS = 12L * 60 * 60 * 1000;
    private static final String EXPORT_DIR = "exports";
    private static final String DOWNLOADS_SUBDIR = "TinyTunes";
    private static final Pattern MIME_RE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*/[A-Za-z0-9*][A-Za-z0-9!#$&^_.+*-]*$");

    // ---- volume lock state (static: MainActivity reads it on the UI thread; plugin calls run on the
    // Capacitor plugin thread). All writes happen under LOCK.
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean volumeLocked = false;
    private static volatile int lockedLevel = -1;
    private static int suspendedLevel = -1; // lock to restore when the app is top again
    private static int stuckCount = 0; // consecutive restores the system refused
    private static boolean topResumedForwarded = false; // MainActivity forwards onTopResumedActivityChanged
    private static boolean hostTop = true; // false while the app is paused or another window is on top
    private static Context appContext;
    private static ContentObserver volumeObserver;
    private static BroadcastReceiver volumeReceiver;

    // =====================================================================================
    // Static hooks used by MainActivity
    // =====================================================================================

    /** True while lockVolume() is in force. Read by MainActivity.dispatchKeyEvent. */
    public static boolean isVolumeLocked() {
        return volumeLocked;
    }

    public static boolean isVolumeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    /** MainActivity.onTopResumedActivityChanged (Android 10+): split screen, other app, permission dialog. */
    public static void onHostTopResumedChanged(boolean isTop) {
        synchronized (LOCK) {
            topResumedForwarded = true;
            hostTop = isTop;
        }
        if (isTop) {
            resumeSuspendedLock();
        } else {
            suspendLock();
        }
    }

    // =====================================================================================
    // Lifecycle
    // =====================================================================================

    @Override
    public void load() {
        synchronized (LOCK) {
            appContext = getContext().getApplicationContext();
            hostTop = true; // a new bridge: its activity is being created and will be on top
        }
    }

    @Override
    protected void handleOnPause() {
        synchronized (LOCK) {
            hostTop = false;
        }
        suspendLock();
    }

    @Override
    protected void handleOnResume() {
        boolean viaTop;
        synchronized (LOCK) {
            viaTop = Build.VERSION.SDK_INT >= 29 && topResumedForwarded;
        }
        // On Android 10+ the lock comes back in onTopResumedActivityChanged(true), which always follows
        // onResume when this app really is on top (and does not come when another window is on top).
        if (!viaTop) {
            synchronized (LOCK) {
                hostTop = true;
            }
            resumeSuspendedLock();
        }
    }

    @Override
    protected void handleOnDestroy() {
        synchronized (LOCK) {
            suspendedLevel = -1;
            releaseLockLocked();
        }
    }

    // =====================================================================================
    // Plugin methods
    // =====================================================================================

    /** → {level, max, bluetooth, outputs:[type names], locked, pinned, lockedLevel, fixed, pinMode, api} */
    @PluginMethod
    public void info(PluginCall call) {
        try {
            call.resolve(buildInfo());
        } catch (Throwable t) {
            fail(call, "Could not read the tablet's audio state", "INFO_FAILED", t);
        }
    }

    /** setVolume({level}) — STREAM_MUSIC, clamped to 0..max, no system volume panel. → info */
    @PluginMethod
    public void setVolume(PluginCall call) {
        try {
            Integer level = readLevel(call);
            if (level == null) {
                call.reject("setVolume needs a number: {level: <step>}", "BAD_LEVEL");
                return;
            }
            AudioManager am = audio();
            if (am == null) {
                call.reject("The tablet's audio service is not available", "NO_AUDIO");
                return;
            }
            int lvl = clamp(level, 0, am.getStreamMaxVolume(STREAM));
            synchronized (LOCK) {
                // While locked the app itself is the authority: moving the level moves the lock with it,
                // otherwise the guard would immediately put the old level back.
                if (volumeLocked) {
                    lockedLevel = lvl;
                    stuckCount = 0;
                }
            }
            am.setStreamVolume(STREAM, lvl, 0);
            call.resolve(buildInfo());
        } catch (IllegalArgumentException e) {
            call.reject(e.getMessage(), "BAD_LEVEL");
        } catch (Throwable t) {
            fail(call, "Could not change the media volume", "SET_FAILED", t);
        }
    }

    /** lockVolume({level}) — set STREAM_MUSIC to level and hold it there (keys swallowed, changes undone). → info */
    @PluginMethod
    public void lockVolume(PluginCall call) {
        try {
            Integer level = readLevel(call);
            AudioManager am = audio();
            if (am == null) {
                call.reject("The tablet's audio service is not available", "NO_AUDIO");
                return;
            }
            if (level == null) {
                level = am.getStreamVolume(STREAM); // no level given: hold the current one
            }
            lockOrDefer(getContext(), level);
            call.resolve(buildInfo());
        } catch (IllegalArgumentException e) {
            call.reject(e.getMessage(), "BAD_LEVEL");
        } catch (Throwable t) {
            fail(call, "Could not lock the media volume", "LOCK_FAILED", t);
        }
    }

    /** unlockVolume() — volume keys and other apps work normally again. The level is left where it is. → info */
    @PluginMethod
    public void unlockVolume(PluginCall call) {
        try {
            synchronized (LOCK) {
                suspendedLevel = -1;
                releaseLockLocked();
            }
            call.resolve(buildInfo());
        } catch (Throwable t) {
            fail(call, "Could not unlock the media volume", "UNLOCK_FAILED", t);
        }
    }

    /** pin() — screen pinning (Android shows its own "Pin this app?" confirmation). → info + {requested} */
    @PluginMethod
    public void pin(PluginCall call) {
        final Activity activity = getActivity();
        if (activity == null) {
            call.reject("The app window is not ready yet", "NO_ACTIVITY");
            return;
        }
        try {
            activity.runOnUiThread(() -> {
                try {
                    boolean already = isPinned();
                    if (!already) {
                        activity.startLockTask();
                    }
                    JSObject r = buildInfo();
                    r.put("requested", !already);
                    call.resolve(r);
                } catch (Throwable t) {
                    fail(call, "Android did not allow screen pinning here (turn on Settings → Security → App pinning)", "PIN_FAILED", t);
                }
            });
        } catch (Throwable t) {
            fail(call, "Could not pin the screen", "PIN_FAILED", t);
        }
    }

    /** unpin() — leave screen pinning. → info */
    @PluginMethod
    public void unpin(PluginCall call) {
        final Activity activity = getActivity();
        if (activity == null) {
            call.reject("The app window is not ready yet", "NO_ACTIVITY");
            return;
        }
        try {
            activity.runOnUiThread(() -> {
                try {
                    if (isPinned()) {
                        activity.stopLockTask();
                    }
                    call.resolve(buildInfo());
                } catch (Throwable t) {
                    fail(call, "Could not stop screen pinning (hold Back and Overview together)", "UNPIN_FAILED", t);
                }
            });
        } catch (Throwable t) {
            fail(call, "Could not stop screen pinning", "UNPIN_FAILED", t);
        }
    }

    /** share({files:[{name, mime, base64}], title}) — Android share sheet. → {shared:true, files, pinned} */
    @PluginMethod
    public void share(PluginCall call) {
        try {
            JSONArray arr = call.getData().optJSONArray("files");
            if (arr == null || arr.length() == 0) {
                call.reject("share needs {files:[{name, mime, base64}]} with at least one file", "BAD_FILES");
                return;
            }
            if (arr.length() > MAX_FILES) {
                call.reject("Too many files to share at once (at most " + MAX_FILES + ")", "BAD_FILES");
                return;
            }
            String title = cleanTitle(call.getData().optString("title", ""), "Share from Tiny Tunes");

            ArrayList<Payload> payloads = new ArrayList<>();
            Set<String> names = new HashSet<>();
            long total = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject f = arr.optJSONObject(i);
                if (f == null) {
                    call.reject("files[" + i + "] must be an object {name, mime, base64}", "BAD_FILES");
                    return;
                }
                Payload p = decodePayload(f, "files[" + i + "]");
                if (!names.add(p.name.toLowerCase(Locale.ROOT))) {
                    call.reject("Two files are both called \"" + p.name + "\"", "BAD_FILES");
                    return;
                }
                total += p.bytes.length;
                if (total > MAX_TOTAL_BYTES) {
                    call.reject("The files are too big to share together (over " + (MAX_TOTAL_BYTES >> 20) + " MB)", "TOO_BIG");
                    return;
                }
                payloads.add(p);
            }

            Context ctx = getContext();
            File dir = new File(ctx.getCacheDir(), EXPORT_DIR);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                call.reject("Could not create the export folder", "WRITE_FAILED");
                return;
            }
            cleanOldExports(dir);

            String authority = ctx.getPackageName() + ".fileprovider";
            ArrayList<Uri> uris = new ArrayList<>();
            ArrayList<String> mimes = new ArrayList<>();
            for (Payload p : payloads) {
                File out = new File(dir, p.name);
                try (FileOutputStream fos = new FileOutputStream(out, false)) {
                    fos.write(p.bytes);
                    fos.flush();
                }
                uris.add(FileProvider.getUriForFile(ctx, authority, out));
                mimes.add(p.mime);
            }

            final Intent send;
            if (uris.size() == 1) {
                send = new Intent(Intent.ACTION_SEND);
                send.setType(mimes.get(0));
                send.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            } else {
                send = new Intent(Intent.ACTION_SEND_MULTIPLE);
                send.setType(commonMime(mimes));
                send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            send.putExtra(Intent.EXTRA_SUBJECT, title);
            send.putExtra(Intent.EXTRA_TITLE, title);
            ClipData clip = ClipData.newRawUri(title, uris.get(0));
            for (int i = 1; i < uris.size(); i++) {
                clip.addItem(new ClipData.Item(uris.get(i)));
            }
            send.setClipData(clip);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            final Intent chooser = Intent.createChooser(send, title);
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            final int count = uris.size();
            final Activity activity = getActivity();
            if (activity == null) {
                call.reject("The app window is not ready yet", "NO_ACTIVITY");
                return;
            }
            activity.runOnUiThread(() -> {
                try {
                    activity.startActivity(chooser);
                    JSObject r = new JSObject();
                    r.put("shared", true);
                    r.put("files", count);
                    r.put("pinned", isPinned());
                    call.resolve(r);
                } catch (android.content.ActivityNotFoundException e) {
                    call.reject("No app on this tablet can receive these files", "NO_TARGET");
                } catch (Throwable t) {
                    fail(call, "Could not open the share sheet (if the screen is pinned, unpin it first)", "SHARE_FAILED", t);
                }
            });
        } catch (IllegalArgumentException e) {
            call.reject(e.getMessage(), "BAD_FILES");
        } catch (OutOfMemoryError e) {
            call.reject("The files are too big for this tablet's memory; share them one at a time", "TOO_BIG");
        } catch (Throwable t) {
            fail(call, "Could not prepare the files to share", "SHARE_FAILED", t);
        }
    }

    /** saveToDownloads({name, mime, base64}) — Android 10+: Download/TinyTunes/<name>. → {uri, name, path} */
    @PluginMethod
    public void saveToDownloads(PluginCall call) {
        if (Build.VERSION.SDK_INT < 29) {
            call.reject("Saving to Downloads needs Android 10 or newer; use Share instead", "UNSUPPORTED");
            return;
        }
        Uri item = null;
        ContentResolver cr = null;
        try {
            Payload p = decodePayload(call.getData(), "file");
            cr = getContext().getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, p.name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, p.mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOADS_SUBDIR);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            item = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (item == null) {
                call.reject("Android refused to create the file in Downloads", "WRITE_FAILED");
                return;
            }
            try (OutputStream os = cr.openOutputStream(item, "w")) {
                if (os == null) {
                    throw new java.io.IOException("no output stream");
                }
                os.write(p.bytes);
                os.flush();
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(item, done, null, null);

            String finalName = p.name;
            try (Cursor c = cr.query(item, new String[] { MediaStore.MediaColumns.DISPLAY_NAME }, null, null, null)) {
                if (c != null && c.moveToFirst() && !c.isNull(0)) {
                    finalName = c.getString(0); // Android adds " (1)" if the name was taken
                }
            } catch (Throwable ignored) {}

            JSObject r = new JSObject();
            r.put("uri", item.toString());
            r.put("name", finalName);
            r.put("path", Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOADS_SUBDIR + "/" + finalName);
            call.resolve(r);
        } catch (IllegalArgumentException e) {
            deleteQuietly(cr, item);
            call.reject(e.getMessage(), "BAD_FILE");
        } catch (OutOfMemoryError e) {
            deleteQuietly(cr, item);
            call.reject("The file is too big for this tablet's memory", "TOO_BIG");
        } catch (Throwable t) {
            deleteQuietly(cr, item);
            fail(call, "Could not save the file to Downloads", "WRITE_FAILED", t);
        }
    }

    // =====================================================================================
    // Volume lock internals
    // =====================================================================================

    private static void applyLock(Context ctx, int level) {
        synchronized (LOCK) {
            if (appContext == null && ctx != null) {
                appContext = ctx.getApplicationContext();
            }
            AudioManager am = audioOf(appContext);
            if (am == null) {
                throw new IllegalStateException("audio service unavailable");
            }
            int lvl = clamp(level, 0, am.getStreamMaxVolume(STREAM));
            lockedLevel = lvl;
            volumeLocked = true;
            suspendedLevel = -1;
            stuckCount = 0;
            try {
                am.setStreamVolume(STREAM, lvl, 0);
            } catch (Throwable t) {
                Log.w(TAG, "setStreamVolume failed while locking", t);
            }
            registerGuardsLocked();
        }
    }

    /**
     * A lockVolume call can still be queued on the plugin thread when the app is paused or loses the top
     * position (UI thread). Then it must not hold the volume while another window is in front: it is only
     * remembered, and applied when the app is on top again (resolves with locked:false).
     */
    private static void lockOrDefer(Context ctx, int level) {
        synchronized (LOCK) {
            if (!hostTop) {
                if (appContext == null && ctx != null) {
                    appContext = ctx.getApplicationContext();
                }
                AudioManager am = audioOf(appContext);
                if (am == null) {
                    throw new IllegalStateException("audio service unavailable");
                }
                suspendedLevel = clamp(level, 0, am.getStreamMaxVolume(STREAM));
                return;
            }
            applyLock(ctx, level);
        }
    }

    /** Pause / lost focus: drop the lock now, remember it for when the app is on top again. */
    private static void suspendLock() {
        synchronized (LOCK) {
            if (volumeLocked) {
                suspendedLevel = lockedLevel;
            }
            releaseLockLocked();
        }
    }

    private static void resumeSuspendedLock() {
        int lvl;
        Context ctx;
        synchronized (LOCK) {
            lvl = suspendedLevel;
            ctx = appContext;
            suspendedLevel = -1;
        }
        if (lvl >= 0 && ctx != null) {
            try {
                applyLock(ctx, lvl);
            } catch (Throwable t) {
                Log.w(TAG, "could not restore the volume lock", t);
            }
        }
    }

    private static void releaseLockLocked() {
        volumeLocked = false;
        lockedLevel = -1;
        stuckCount = 0;
        Context ctx = appContext;
        if (ctx == null) {
            volumeObserver = null;
            volumeReceiver = null;
            return;
        }
        if (volumeObserver != null) {
            try {
                ctx.getContentResolver().unregisterContentObserver(volumeObserver);
            } catch (Throwable t) {
                Log.w(TAG, "unregisterContentObserver failed", t);
            }
            volumeObserver = null;
        }
        if (volumeReceiver != null) {
            try {
                ctx.unregisterReceiver(volumeReceiver);
            } catch (Throwable t) {
                Log.w(TAG, "unregisterReceiver failed", t);
            }
            volumeReceiver = null;
        }
    }

    private static void registerGuardsLocked() {
        Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        if (volumeObserver == null) {
            ContentObserver obs = new ContentObserver(MAIN) {
                @Override
                public void onChange(boolean selfChange) {
                    enforceLockedLevel();
                }
            };
            try {
                // AudioService persists every volume change to Settings.System (volume_music_*).
                ctx.getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, obs);
                volumeObserver = obs;
            } catch (Throwable t) {
                Log.w(TAG, "registerContentObserver failed", t);
            }
        }
        if (volumeReceiver == null) {
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    enforceLockedLevel();
                }
            };
            // Second, faster signal (sent by the system's AudioService on every volume change).
            IntentFilter f = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    ctx.registerReceiver(r, f, null, MAIN, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    ctx.registerReceiver(r, f, null, MAIN);
                }
                volumeReceiver = r;
            } catch (Throwable t) {
                Log.w(TAG, "registerReceiver failed", t);
            }
        }
    }

    /** Runs on the main thread: put STREAM_MUSIC back to the locked level if anything moved it. */
    private static void enforceLockedLevel() {
        if (!volumeLocked) {
            return;
        }
        Context ctx;
        int want;
        synchronized (LOCK) {
            if (!volumeLocked || stuckCount >= 3) {
                return; // give up after 3 refused restores (e.g. fixed volume) instead of looping
            }
            ctx = appContext;
            want = lockedLevel;
        }
        AudioManager am = audioOf(ctx);
        if (am == null || want < 0) {
            return;
        }
        try {
            boolean muted = Build.VERSION.SDK_INT >= 23 && am.isStreamMute(STREAM);
            if (muted && want > 0) {
                am.adjustStreamVolume(STREAM, AudioManager.ADJUST_UNMUTE, 0);
            }
            if (am.getStreamVolume(STREAM) != want) {
                am.setStreamVolume(STREAM, want, 0);
                boolean ok = am.getStreamVolume(STREAM) == want;
                synchronized (LOCK) {
                    stuckCount = ok ? 0 : stuckCount + 1;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "could not restore the locked volume", t);
        }
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private JSObject buildInfo() {
        JSObject o = new JSObject();
        AudioManager am = audio();
        int level = -1;
        int max = -1;
        boolean fixed = false;
        boolean bluetooth = false;
        JSArray outputs = new JSArray();
        if (am != null) {
            level = am.getStreamVolume(STREAM);
            max = am.getStreamMaxVolume(STREAM);
            try {
                fixed = am.isVolumeFixed();
            } catch (Throwable ignored) {}
            if (Build.VERSION.SDK_INT >= 23) {
                Set<String> names = new LinkedHashSet<>();
                try {
                    AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                    if (devs != null) {
                        for (AudioDeviceInfo d : devs) {
                            int type = d.getType();
                            if (isBluetoothOutput(type)) {
                                bluetooth = true;
                            }
                            names.add(typeName(type));
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "getDevices failed", t);
                }
                for (String n : names) {
                    outputs.put(n);
                }
            }
        }
        o.put("level", level);
        o.put("max", max);
        o.put("bluetooth", bluetooth);
        o.put("outputs", outputs);
        o.put("locked", volumeLocked);
        o.put("lockedLevel", volumeLocked ? lockedLevel : -1);
        o.put("fixed", fixed);
        String mode = pinMode();
        o.put("pinned", !"none".equals(mode));
        o.put("pinMode", mode);
        o.put("api", Build.VERSION.SDK_INT);
        return o;
    }

    private boolean isPinned() {
        return !"none".equals(pinMode());
    }

    private String pinMode() {
        try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return "none";
            }
            if (Build.VERSION.SDK_INT >= 23) {
                int s = am.getLockTaskModeState();
                if (s == ActivityManager.LOCK_TASK_MODE_PINNED) return "pinned";
                if (s == ActivityManager.LOCK_TASK_MODE_LOCKED) return "locked";
                return "none";
            }
            return am.isInLockTaskMode() ? "pinned" : "none";
        } catch (Throwable t) {
            return "none";
        }
    }

    private static boolean isBluetoothOutput(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
            case AudioDeviceInfo.TYPE_BLE_BROADCAST:
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return true;
            default:
                return false;
        }
    }

    private static String typeName(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return "builtin_earpiece";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "builtin_speaker";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE:
                return "builtin_speaker_safe";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "wired_headset";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return "wired_headphones";
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
                return "line_analog";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return "line_digital";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "bluetooth_sco";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "bluetooth_a2dp";
            case AudioDeviceInfo.TYPE_HDMI:
                return "hdmi";
            case AudioDeviceInfo.TYPE_HDMI_ARC:
                return "hdmi_arc";
            case AudioDeviceInfo.TYPE_HDMI_EARC:
                return "hdmi_earc";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return "usb_device";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return "usb_accessory";
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return "usb_headset";
            case AudioDeviceInfo.TYPE_DOCK:
                return "dock";
            case AudioDeviceInfo.TYPE_FM:
                return "fm";
            case AudioDeviceInfo.TYPE_TELEPHONY:
                return "telephony";
            case AudioDeviceInfo.TYPE_AUX_LINE:
                return "aux_line";
            case AudioDeviceInfo.TYPE_IP:
                return "ip";
            case AudioDeviceInfo.TYPE_BUS:
                return "bus";
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return "hearing_aid";
            case AudioDeviceInfo.TYPE_REMOTE_SUBMIX:
                return "remote_submix";
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
                return "ble_headset";
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                return "ble_speaker";
            case AudioDeviceInfo.TYPE_BLE_BROADCAST:
                return "ble_broadcast";
            default:
                return "type_" + type;
        }
    }

    /** level may arrive as int, double ("7.0") or a numeric string. null = not given. */
    private static Integer readLevel(PluginCall call) {
        Object v = call.getData().opt("level");
        if (v == null || v == JSONObject.NULL) {
            return null;
        }
        double d;
        if (v instanceof Number) {
            d = ((Number) v).doubleValue();
        } else if (v instanceof String) {
            try {
                d = Double.parseDouble(((String) v).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("level must be a number, got \"" + v + "\"");
            }
        } else {
            throw new IllegalArgumentException("level must be a number");
        }
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("level must be a finite number");
        }
        if (d > 10000) d = 10000;
        if (d < -10000) d = -10000;
        return (int) Math.round(d);
    }

    private static final class Payload {

        String name;
        String mime;
        byte[] bytes;
    }

    private static Payload decodePayload(JSONObject f, String label) {
        Payload p = new Payload();
        p.name = cleanName(f.optString("name", ""));
        if (p.name.isEmpty()) {
            throw new IllegalArgumentException(label + ": name is missing or not a usable file name");
        }
        String mime = f.optString("mime", "").trim().toLowerCase(Locale.ROOT);
        if (mime.isEmpty()) {
            mime = guessMime(p.name);
        } else if (!MIME_RE.matcher(mime).matches()) {
            throw new IllegalArgumentException(label + ": mime \"" + mime + "\" is not a valid type like audio/wav");
        }
        p.mime = mime;
        String b64 = f.optString("base64", "");
        int comma = b64.indexOf(',');
        if (b64.startsWith("data:") && comma > 0) {
            b64 = b64.substring(comma + 1); // tolerate a data: URL
        }
        if (b64.isEmpty()) {
            throw new IllegalArgumentException(label + " (" + p.name + "): base64 content is missing");
        }
        if ((long) b64.length() * 3 / 4 > MAX_FILE_BYTES + 3) {
            throw new IllegalArgumentException(label + " (" + p.name + ") is too big (over " + (MAX_FILE_BYTES >> 20) + " MB)");
        }
        try {
            p.bytes = Base64.decode(b64, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " (" + p.name + "): base64 content is not valid base64");
        }
        if (p.bytes == null || p.bytes.length == 0) {
            throw new IllegalArgumentException(label + " (" + p.name + "): file is empty");
        }
        return p;
    }

    private static String cleanName(String raw) {
        if (raw == null) return "";
        String n = raw.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        if (slash >= 0) n = n.substring(slash + 1);
        n = n.replaceAll("[^A-Za-z0-9._ ()+-]", "_").trim();
        while (n.startsWith(".")) n = n.substring(1);
        if (n.length() > 100) {
            int dot = n.lastIndexOf('.');
            String ext = (dot > 0 && n.length() - dot <= 10) ? n.substring(dot) : "";
            n = n.substring(0, 100 - ext.length()) + ext;
        }
        return n.replaceAll("^[_ ]+$", "");
    }

    private static String cleanTitle(String raw, String fallback) {
        if (raw == null) return fallback;
        String t = raw.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (t.isEmpty()) return fallback;
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    private static String guessMime(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.equals("wav")) return "audio/wav";
            if (ext.equals("json")) return "application/json";
            if (ext.equals("txt")) return "text/plain";
            try {
                String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (m != null) return m;
            } catch (Throwable ignored) {}
        }
        return "application/octet-stream";
    }

    private static String commonMime(ArrayList<String> mimes) {
        String first = mimes.get(0);
        boolean same = true;
        String top = first.substring(0, first.indexOf('/'));
        boolean sameTop = true;
        for (String m : mimes) {
            if (!m.equals(first)) same = false;
            if (!m.startsWith(top + "/")) sameTop = false;
        }
        if (same) return first;
        if (sameTop) return top + "/*";
        return "*/*";
    }

    private static void cleanOldExports(File dir) {
        try {
            File[] old = dir.listFiles();
            if (old == null) return;
            long cutoff = System.currentTimeMillis() - EXPORT_MAX_AGE_MS;
            for (File f : old) {
                if (f.isFile() && f.lastModified() < cutoff) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void deleteQuietly(ContentResolver cr, Uri item) {
        if (cr == null || item == null) return;
        try {
            cr.delete(item, null, null);
        } catch (Throwable ignored) {}
    }

    private AudioManager audio() {
        return audioOf(getContext());
    }

    private static AudioManager audioOf(Context ctx) {
        if (ctx == null) return null;
        try {
            return (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        if (hi < lo) hi = lo;
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static void fail(PluginCall call, String msg, String code, Throwable t) {
        Log.w(TAG, msg, t);
        String detail = t == null ? null : t.getMessage();
        try {
            call.reject(detail == null || detail.isEmpty() ? msg : msg + ": " + detail, code);
        } catch (Throwable ignored) {}
    }
}
