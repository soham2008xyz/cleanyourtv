package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * Outfit on Android 7 (Fire OS 6). There android:fontFamily="@string/font_*" names a font XML the framework
 * can't load, so every label would fall back to Roboto. Below Android 8 this factory builds the layouts'
 * TextView and TextClock tags itself and sets the typeface UiFont picks for their fontFamily (from the tag or its
 * style). Other tags, and every view on Android 8 and later, are left to the normal inflater.
 */
final class LegacyFonts implements LayoutInflater.Factory2 {

    private static final int[] FONT_FAMILY = {android.R.attr.fontFamily};

    /** Installs the factory in every activity, before its setContentView. No-op on Android 8 and later. */
    static void install(Application app) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return;
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {
                LayoutInflater inflater = a.getLayoutInflater();
                if (inflater.getFactory2() == null) inflater.setFactory2(new LegacyFonts());
            }
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    @Override
    @SuppressLint("ResourceType")   // FONT_FAMILY is a one-attribute styleable built in code
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        TextView view;
        if ("TextView".equals(name)) view = new TextView(context, attrs);
        else if ("TextClock".equals(name)) view = new TextClock(context, attrs);
        else return null;
        TypedArray a = context.obtainStyledAttributes(attrs, FONT_FAMILY);
        int font = a.getResourceId(0, 0);
        a.recycle();
        if (font != 0) {
            try {
                Typeface current = view.getTypeface();
                view.setTypeface(UiFont.get(context, font), current != null && current.isBold() ? Typeface.BOLD : Typeface.NORMAL);
            } catch (RuntimeException ignored) {
                // fontFamily named something other than a font_* string: keep what the framework chose.
            }
        }
        return view;
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }
}
