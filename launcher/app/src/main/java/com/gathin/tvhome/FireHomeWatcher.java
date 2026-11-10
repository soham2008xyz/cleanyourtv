package com.gathin.tvhome;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Home on Fire OS 6, which ignores the default-home setting, refuses to disable Amazon's launcher (a protected
 * package) and, on 6.7, never binds a third-party accessibility service, so HomeKeyService can't help there.
 *
 * Every Home press makes the system log "ActivityManager: START ... cat=[android.intent.category.HOME] ...
 * cmp=com.amazon.tv.launcher/... from uid 1000". This waits on that line (a blocking read, no polling) and brings
 * the launcher back over Amazon's home. Settings also passes through Amazon's home on its way to the Settings menu,
 * but from the settings app's uid, so it is left alone.
 *
 * Only runs when READ_LOGS has been granted over adb, which build-and-install.sh does with FIRE_OS_HOME_WATCHER=1:
 *   pm grant com.gathin.tvhome android.permission.READ_LOGS
 * On every other TV it never starts.
 */
public class FireHomeWatcher extends Service {

    private static final String TAG = "FireHomeWatcher";
    private static final String HOME_CATEGORY = "cat=[android.intent.category.HOME]";
    private static final String AMAZON_HOME = "cmp=com.amazon.tv.launcher/";
    private static final String FROM_SYSTEM = "from uid 1000 ";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private Thread thread;

    static void startIfGranted(Context ctx) {
        if (!Compat.granted(ctx, android.Manifest.permission.READ_LOGS)) return;
        ctx.startService(new Intent(ctx, FireHomeWatcher.class));
    }

    /** After a reboot: the watcher, and the launcher over Amazon's home. */
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (!Compat.granted(ctx, android.Manifest.permission.READ_LOGS)) return;
            startIfGranted(ctx);
            ctx.startActivity(new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(this::watch, TAG);
            thread.start();
        }
        return START_STICKY;
    }

    private void watch() {
        while (!Thread.currentThread().isInterrupted()) {
            Process p = null;
            try {
                // -T 1: start at the newest line, so an old Home press doesn't fire on start-up.
                p = Runtime.getRuntime().exec(new String[]{"logcat", "-T", "1", "-v", "brief", "ActivityManager:I", "*:S"});
                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.contains(HOME_CATEGORY) && line.contains(AMAZON_HOME) && line.contains(FROM_SYSTEM)) {
                        bringBack();
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "logcat stopped", e);
            } finally {
                if (p != null) p.destroy();
            }
            SystemClock.sleep(2000);   // logcat exited (log buffer cleared, say): start it again
        }
    }

    /**
     * After a Home press Android 7 holds back activity starts from background apps for five seconds. Moving the
     * launcher's existing task to the front through AppTask runs under the system's identity and isn't held back,
     * so the launcher is up in about half a second; a plain start is the fallback when the task is gone.
     */
    private void bringBack() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            bringBackFromSystem();
            return;
        }
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.AppTask task : am.getAppTasks()) {
            try {
                task.moveToFront();
                return;
            } catch (RuntimeException e) {
                Log.w(TAG, "moveToFront failed", e);
            }
        }
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    /**
     * Android 5 and 6 (Fire OS 5): AppTask.moveToFront is held back after Home too ("Task to front request ...
     * stopped"), and so is every other start, an alarm's PendingIntent included. Ask for the launcher, which
     * Android starts when its five seconds are up, and cover Amazon's home until then (HomeCover).
     */
    private void bringBackFromSystem() {
        MAIN.post(() -> HomeCover.show(this));
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    @Override
    public void onDestroy() {
        if (thread != null) thread.interrupt();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
