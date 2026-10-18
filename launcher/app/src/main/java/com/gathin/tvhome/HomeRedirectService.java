package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/**
 * Fire TV keeps Amazon's launcher as system Home. Watch window metadata and
 * bring this app forward when Amazon's Home appears. No key filtering, screen
 * content retrieval, polling, or network access is used.
 */
public final class HomeRedirectService extends AccessibilityService {
    private static final String TAG = "TvHomeRedirect";
    private static WeakReference<HomeRedirectService> instance = new WeakReference<>(null);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean pending;
    private boolean amazonInFront;
    private boolean amazonOtherScreen;
    private int homeWindow = -1;
    private long lastAttempt;
    private View loading;
    private PendingIntent scheduledHome;
    private SharedPreferences preferences;

    private final Runnable retry = () -> {
        if (pending && canRedirect()) {
            Log.i(TAG, "Retrying deferred Home launch");
            launchHome();
        }
    };
    private final Runnable timeout = () -> {
        if (pending) Log.w(TAG, "Home did not become visible before timeout");
        finishPending();
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (prefs, key) -> {
                if (HomeRedirect.ENABLED.equals(key) && !HomeRedirect.requested(this)) {
                    finishPending();
                }
                if (HomeRedirect.BOOT_PENDING.equals(key)) handleBoot();
            };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = new WeakReference<>(this);
        preferences = HomeRedirect.prefs(this);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        Log.i(TAG, "Accessibility service connected");
        handleBoot();
    }

    private boolean canRedirect() {
        return HomeRedirect.isFireTv(this) && HomeRedirect.requested(this);
    }

    private void handleBoot() {
        if (canRedirect() && preferences != null
                && preferences.getBoolean(HomeRedirect.BOOT_PENDING, false)) {
            preferences.edit().putBoolean(HomeRedirect.BOOT_PENDING, false).apply();
            requestHome("boot");
        }
    }

    static void onBootRequested() {
        HomeRedirectService active = instance.get();
        if (active != null) active.handleBoot();
    }

    static void onHomeVisible() {
        HomeRedirectService active = instance.get();
        if (active != null) {
            if (active.pending) {
                Log.i(TAG, "Home visible after "
                        + (SystemClock.uptimeMillis() - active.lastAttempt) + " ms");
            }
            active.finishPending();
            active.amazonInFront = false;
            active.amazonOtherScreen = false;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!canRedirect() || event.getPackageName() == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_FOCUSED) return;
        String pkg = event.getPackageName().toString();
        String cls = event.getClassName() != null ? event.getClassName().toString() : "";

        if (getPackageName().equals(pkg)) {
            if (MainActivity.class.getName().equals(cls)) onHomeVisible();
            return; // Our dialog/loading overlay is not another foreground app.
        }
        if (!HomeRedirect.AMAZON.equals(pkg)) {
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && !"android".equals(pkg) && !pkg.startsWith("com.android.systemui")) {
                amazonInFront = false;
                amazonOtherScreen = false;
            }
            return;
        }

        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 && type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            Log.d(TAG, "Amazon window: class=" + cls + ", id=" + event.getWindowId());
        }
        boolean homeActivity = cls.endsWith(".HomeActivity_vNext") || cls.endsWith(".HomeActivity");
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && cls.startsWith(HomeRedirect.AMAZON + ".") && !homeActivity) {
            // SettingsActivity belongs to the launcher package too; it must remain usable.
            amazonOtherScreen = true;
            amazonInFront = true;
            return;
        }

        // Fire OS 7 sometimes reports the returning Home tab as a focus event
        // with no activity class. Its window id and the preceding app transition
        // identify that return without reading UI text or the accessibility tree.
        boolean returningFocus = type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                && ((amazonOtherScreen && event.getWindowId() == homeWindow)
                    || (!amazonInFront && !amazonOtherScreen
                        && (event.getWindowId() == homeWindow || cls.isEmpty()
                            || cls.startsWith("android.widget."))));
        if (!homeActivity && !returningFocus) return;

        boolean justArrived = !amazonInFront || amazonOtherScreen;
        amazonOtherScreen = false;
        amazonInFront = true;
        homeWindow = event.getWindowId();
        if (justArrived) requestHome(homeActivity ? "Home activity" : "Home focus");
    }

    private void requestHome(String reason) {
        long now = SystemClock.uptimeMillis();
        if (!canRedirect() || pending || now - lastAttempt < 1200) return;
        pending = true;
        lastAttempt = now;
        Log.i(TAG, "Redirect requested: " + reason);
        showLoading();
        launchHome();
        if (pending) {
            // Android 9 defers app switches after Home. Retry once after that lock.
            handler.postDelayed(retry, 5500);
            handler.postDelayed(timeout, 10000);
        }
    }

    private void launchHome() {
        try {
            Intent homeIntent = new Intent(this, MainActivity.class)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            // AlarmManager delivery avoids the post-Home activity switch delay on Fire OS 7.
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                scheduledHome = PendingIntent.getActivity(this, 0, homeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                getSystemService(AlarmManager.class).setExact(AlarmManager.ELAPSED_REALTIME,
                        SystemClock.elapsedRealtime() + 50, scheduledHome);
            } else {
                startActivity(homeIntent);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not open Home", e);
            finishPending();
        }
    }

    private void showLoading() {
        if (loading != null || Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(0xFF000000);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        panel.addView(new ProgressBar(this),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView label = new TextView(this);
        label.setText(R.string.home_redirect_loading);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(22);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(24);
        panel.addView(label, textParams);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        try {
            getSystemService(WindowManager.class).addView(panel, params);
            loading = panel;
        } catch (RuntimeException e) {
            Log.w(TAG, "Loading cover unavailable; continuing without it");
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void finishPending() {
        pending = false;
        if (scheduledHome != null) {
            getSystemService(AlarmManager.class).cancel(scheduledHome);
            scheduledHome.cancel();
            scheduledHome = null;
        }
        handler.removeCallbacks(retry);
        handler.removeCallbacks(timeout);
        if (loading != null) {
            try {
                getSystemService(WindowManager.class).removeViewImmediate(loading);
            } catch (RuntimeException ignored) {
                // The system may already have removed the accessibility overlay.
            }
            loading = null;
        }
    }

    @Override
    public void onInterrupt() {
        finishPending();
    }

    @Override
    public void onDestroy() {
        finishPending();
        handler.removeCallbacksAndMessages(null);
        if (preferences != null) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        }
        if (instance.get() == this) instance.clear();
        super.onDestroy();
    }
}