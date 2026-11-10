package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Android 5 only (API 22): android:drawableTint (API 23) is ignored there, so the top pills' white icons stayed
 * white on the white focused pill. LegacyFonts inflates this instead of a TextView whose style asks for a
 * drawable tint; it puts the tint on every compound drawable, including ones set later from code.
 */
final class TintedTextView extends TextView {
    private ColorStateList tint;

    TintedTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    ColorStateList tint() {
        return tint;
    }

    void setTint(ColorStateList tint) {
        this.tint = tint;
        Compat.tintDrawables(this, tint);
    }

    @Override
    public void setCompoundDrawables(Drawable left, Drawable top, Drawable right, Drawable bottom) {
        super.setCompoundDrawables(left, top, right, bottom);
        if (tint != null) Compat.tintDrawables(this, tint);
    }

    @Override
    public void setCompoundDrawablesRelative(Drawable start, Drawable top, Drawable end, Drawable bottom) {
        super.setCompoundDrawablesRelative(start, top, end, bottom);
        if (tint != null) Compat.tintDrawables(this, tint);
    }
}
