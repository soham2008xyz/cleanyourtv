package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;

import androidx.core.content.res.ResourcesCompat;

/**
 * The typeface behind one of the font_* strings in res/values/fonts.xml, for views built in code. The string
 * points at a bundled font, or, in locales Outfit can't draw (values-vi), names a system family (#833).
 * Android 7 can't read font XML or variable-font weights, so there each font_* maps to a static cut of Outfit
 * (res/font/outfit_w*.ttf); LegacyFonts applies the same mapping to layouts.
 */
final class UiFont {
    private UiFont() {}

    static Typeface get(Context context, int fontString) {
        Resources res = context.getResources();
        TypedValue value = new TypedValue();
        res.getValue(fontString, value, true);
        String name = String.valueOf(value.string);
        if (!name.startsWith("res/")) return Typeface.create(name, Typeface.NORMAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return res.getFont(value.resourceId);
        return ResourcesCompat.getFont(context, staticCut(res.getResourceEntryName(value.resourceId)));
    }

    /** The static Outfit file for a font resource name such as outfit_medium. */
    static int staticCut(String fontName) {
        switch (fontName) {
            case "outfit_extralight": return R.font.outfit_w200;
            case "outfit_light": return R.font.outfit_w300;
            case "outfit_medium": return R.font.outfit_w500;
            default: return R.font.outfit_w400;
        }
    }
}
