package com.gathin.tvhome;

import android.app.Application;

/** Process start: Outfit for Android 7's layouts (LegacyFonts). */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LegacyFonts.install(this);
    }
}
