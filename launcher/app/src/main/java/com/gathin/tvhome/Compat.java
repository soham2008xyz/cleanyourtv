package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.UserManager;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

/**
 * The few calls the launcher makes that Android 5.1 (API 22, Fire OS 5) lacks, each with the closest older
 * equivalent. On Android 7 and later every method here is the plain call it wraps.
 */
final class Compat {
    private Compat() {
    }

    /** Context#checkSelfPermission (API 23); checkCallingOrSelfPermission gives the same answer for our own process. */
    static boolean granted(Context context, String permission) {
        return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** UserManager#isUserUnlocked (API 24). Before Android 7 there is no file-based encryption: always unlocked. */
    static boolean userUnlocked(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true;
        UserManager users = (UserManager) context.getSystemService(Context.USER_SERVICE);
        return users == null || users.isUserUnlocked();
    }

    /** View#forceHasOverlappingRendering (API 24): a hint only, so older Android just skips it. */
    static void noOverlappingRendering(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) view.forceHasOverlappingRendering(false);
    }

    /** The app's first locale: Configuration#getLocales (API 24), or Configuration#locale before it. */
    @SuppressWarnings("deprecation")
    static Locale locale(Context context) {
        android.content.res.Configuration config = context.getResources().getConfiguration();
        Locale locale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? config.getLocales().get(0) : config.locale;
        return locale != null ? locale : Locale.getDefault();
    }

    /** Context#getColorStateList (API 23). */
    @SuppressWarnings("deprecation")
    static ColorStateList colorStateList(Context context, int id) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? context.getColorStateList(id) : context.getResources().getColorStateList(id);
    }

    /** TvInputInfo#isHidden (API 24): no hidden inputs before Android 7. */
    static boolean inputHidden(TvInputInfo info, Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && info.isHidden(context);
    }

    /** TvInputInfo#loadCustomLabel (API 24): no custom labels before Android 7. */
    static CharSequence inputCustomLabel(TvInputInfo info, Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? info.loadCustomLabel(context) : null;
    }

    /** Whether the active network is up. Before Android 6 there is no validation flag, so "connected" counts. */
    @SuppressWarnings("deprecation")
    static boolean activeNetworkConnected(ConnectivityManager cm) {
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    /** NET_CAPABILITY_VALIDATED (API 23). Before Android 6 nothing validates a network, so an up network counts. */
    static boolean validated(android.net.NetworkCapabilities caps) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /** ConnectivityManager#registerDefaultNetworkCallback (API 24); before it, any network with internet. */
    static void registerDefaultNetworkCallback(ConnectivityManager cm, ConnectivityManager.NetworkCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(callback);
            return;
        }
        cm.registerNetworkCallback(new android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback);
    }

    /** AccessibilityService#disableSelf (API 24); false where the service cannot leave by itself. */
    static boolean disableSelf(AccessibilityService service) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        service.disableSelf();
        return true;
    }

    /** TextView#getCompoundDrawableTintList (API 23); below it, what {@link #setCompoundTint} last set. */
    static ColorStateList compoundTint(TextView view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return view.getCompoundDrawableTintList();
        return view instanceof TintedTextView ? ((TintedTextView) view).tint() : null;
    }

    /** TextView#setCompoundDrawableTintList (API 23). Below it the tint goes on each compound drawable, and a
     *  TintedTextView (what LegacyFonts inflates on Android 5) keeps it for drawables set later. */
    static void setCompoundTint(TextView view, ColorStateList tint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.setCompoundDrawableTintList(tint);
            return;
        }
        if (view instanceof TintedTextView) {
            ((TintedTextView) view).setTint(tint);
            return;
        }
        tintDrawables(view, tint);
    }

    static void tintDrawables(TextView view, ColorStateList tint) {
        Drawable[] all = view.getCompoundDrawablesRelative();
        Drawable[] plain = view.getCompoundDrawables();
        for (Drawable[] set : new Drawable[][]{all, plain}) {
            for (Drawable d : set) {
                if (d == null) continue;
                d.mutate();
                d.setTintList(tint);
                d.setState(view.getDrawableState());
            }
        }
    }

    /** Context#getSystemService(Class) is API 23; these name the service instead. */
    static ConnectivityManager connectivity(Context context) {
        return (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }
}
