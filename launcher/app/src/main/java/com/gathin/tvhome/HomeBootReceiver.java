package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Lets the system-bound accessibility service open Home once after boot. */
public final class HomeBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && HomeRedirect.isFireTv(context) && HomeRedirect.requested(context)) {
            HomeRedirect.prefs(context).edit()
                    .putBoolean(HomeRedirect.BOOT_PENDING, true).apply();
            HomeRedirectService.onBootRequested();
        }
    }
}