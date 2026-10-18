package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/** Local preferences and opt-in control for Fire TV's Home redirect. */
final class HomeRedirect {
    static final String ENABLED = "enabled";
    static final String BOOT_PENDING = "boot_pending";
    static final String AMAZON = "com.amazon.tv.launcher";

    private HomeRedirect() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("fire_tv_home", Context.MODE_PRIVATE);
    }

    static boolean isFireTv(Context context) {
        if (!"Amazon".equalsIgnoreCase(Build.MANUFACTURER)) return false;
        try {
            context.getPackageManager().getApplicationInfo(AMAZON, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static boolean requested(Context context) {
        return prefs(context).getBoolean(ENABLED, false);
    }

    private static ComponentName component(Context context) {
        return new ComponentName(context, HomeRedirectService.class);
    }

    private static Set<String> enabledServices(Context context) {
        Set<String> result = new LinkedHashSet<>();
        String raw = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (raw != null && !raw.isEmpty()) {
            for (String entry : raw.split(":")) {
                if (!entry.isEmpty()) result.add(entry);
            }
        }
        return result;
    }

    static boolean serviceEnabled(Context context) {
        ComponentName ours = component(context);
        for (String entry : enabledServices(context)) {
            if (ours.equals(ComponentName.unflattenFromString(entry))) return true;
        }
        return false;
    }

    /** Changes only our entry; services belonging to other apps are preserved. */
    static void setEnabled(Context context, boolean enabled) {
        if (enabled && !isFireTv(context)) throw new IllegalStateException("Not a Fire TV");
        boolean previous = requested(context);
        prefs(context).edit().putBoolean(ENABLED, enabled).apply();
        Set<String> services = enabledServices(context);
        ComponentName ours = component(context);
        services.removeIf(entry -> ours.equals(ComponentName.unflattenFromString(entry)));
        if (enabled) services.add(ours.flattenToShortString());
        try {
            if (!Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, TextUtils.join(":", services))) {
                throw new SecurityException("Could not update accessibility services");
            }
            if (enabled || services.isEmpty()) {
                if (!Settings.Secure.putInt(context.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_ENABLED, enabled ? 1 : 0)) {
                    throw new SecurityException("Could not update accessibility state");
                }
            }
        } catch (SecurityException e) {
            // An off switch must stop redirects even if a secure-settings grant was revoked.
            if (enabled) prefs(context).edit().putBoolean(ENABLED, previous).apply();
            throw e;
        }
        if (!enabled) prefs(context).edit().putBoolean(BOOT_PENDING, false).apply();
    }

    static void markHomeVisible(Context context) {
        if (prefs(context).getBoolean(BOOT_PENDING, false)) {
            prefs(context).edit().putBoolean(BOOT_PENDING, false).apply();
        }
    }
}