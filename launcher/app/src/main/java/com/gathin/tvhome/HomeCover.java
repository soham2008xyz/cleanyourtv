package com.gathin.tvhome;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Fire OS 5 (Android 5.1) only. After a Home press Android holds back every activity start from an app that is not
 * in front for five seconds, and on 5.1 nothing an app can call gets around it (AppTask.moveToFront is "stopped",
 * an alarm's PendingIntent waits too). So FireHomeWatcher asks for the launcher, which Android lets through when
 * the five seconds are up, and meanwhile this plain cover hides Amazon's home and its ads.
 *
 * Fails open: it goes as soon as the launcher resumes, on Back, or after {@link #MAX_MS} whatever happens, so it
 * can never leave the screen covered. It takes the remote's keys while up, so nothing is pressed on Amazon's home
 * behind it. Needs SYSTEM_ALERT_WINDOW, granted at install on Android 5; if the window is refused, nothing shows.
 */
final class HomeCover {
    /** Longest the cover stays: Android's five seconds plus the launcher's start. */
    static final long MAX_MS = 8000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static View cover;
    private static final Runnable TIMEOUT = HomeCover::hide;

    private HomeCover() {
    }

    /** Shows the cover (main thread). A second call while it is up only restarts the timeout. */
    static void show(Context context) {
        MAIN.removeCallbacks(TIMEOUT);
        MAIN.postDelayed(TIMEOUT, MAX_MS);
        if (cover != null) return;
        Context app = context.getApplicationContext();
        FrameLayout view = new FrameLayout(app) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) hide();
                return true;
            }
        };
        view.setBackgroundColor(0xFF000000);
        view.setFocusable(true);
        @SuppressWarnings("deprecation")
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        try {
            ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).addView(view, lp);
            cover = view;
            view.requestFocus();
        } catch (RuntimeException refused) {
            // No overlay permission on this firmware: the launcher still comes, just with Amazon's home first.
            MAIN.removeCallbacks(TIMEOUT);
        }
    }

    /** Removes the cover if it is up (any thread). */
    static void hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(HomeCover::hide);
            return;
        }
        MAIN.removeCallbacks(TIMEOUT);
        View view = cover;
        cover = null;
        if (view == null) return;
        try {
            ((WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(view);
        } catch (RuntimeException ignored) {
        }
    }
}
