package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.Context;
import android.provider.Settings;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** A small remote-friendly settings dialog, shown only on Amazon Fire TV. */
final class HomeSettings {
    private HomeSettings() {}

    static void show(Activity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.Theme_TvHome_Dialog);
        Context context = builder.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * density);
        content.setPadding(padding, Math.round(8 * density), padding, Math.round(12 * density));

        TextView explanation = new TextView(context);
        explanation.setText(R.string.home_redirect_explanation);
        explanation.setTextSize(18);
        explanation.setTextColor(0xFFDDDDDD);
        content.addView(explanation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        CheckBox enabled = new CheckBox(context);
        enabled.setText(R.string.home_redirect_toggle);
        enabled.setTextSize(20);
        enabled.setFocusableInTouchMode(true);
        enabled.setTypeface(context.getResources().getFont(R.font.outfit));
        enabled.setMinHeight(Math.round(56 * density));
        enabled.setPadding(Math.round(16 * density), 0, Math.round(16 * density), 0);
        enabled.setBackgroundResource(R.drawable.pill_bg);
        enabled.setTextColor(context.getColorStateList(R.color.home_redirect_text));
        enabled.setButtonTintList(context.getColorStateList(R.color.home_redirect_text));
        enabled.setChecked(HomeRedirect.requested(activity) && HomeRedirect.serviceEnabled(activity));
        LinearLayout.LayoutParams checkboxParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        checkboxParams.topMargin = Math.round(20 * density);
        content.addView(enabled, checkboxParams);
        boolean[] updating = {false};
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (updating[0]) return;
            try {
                HomeRedirect.setEnabled(activity, checked);
            } catch (SecurityException | IllegalStateException e) {
                if (!checked) return; // The local preference already stopped redirects.
                updating[0] = true;
                enabled.setChecked(false);
                updating[0] = false;
                new AlertDialog.Builder(activity, R.style.Theme_TvHome_Dialog)
                        .setTitle(R.string.home_redirect_permission_title)
                        .setMessage(R.string.home_redirect_permission_message)
                        .setPositiveButton(R.string.home_redirect_accessibility,
                                (dialog, which) -> open(activity,
                                        new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                        .setNegativeButton(R.string.home_redirect_close, null)
                        .show();
            }
        });

        AlertDialog dialog = builder.setTitle(R.string.home_redirect_title)
                .setView(content)
                .setNeutralButton(R.string.home_redirect_system_settings,
                        (ignored, which) -> open(activity, new Intent(Settings.ACTION_SETTINGS)))
                .setNegativeButton(R.string.home_redirect_close, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window != null) {
                android.graphics.drawable.GradientDrawable surface =
                        new android.graphics.drawable.GradientDrawable();
                surface.setColor(0xFF171719);
                surface.setCornerRadius(20 * density);
                window.setBackgroundDrawable(surface);
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                android.view.WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.dimAmount = 0.72f;
                window.setAttributes(attributes);
                int screen = context.getResources().getDisplayMetrics().widthPixels;
                window.setLayout(Math.min(Math.round(640 * density),
                        screen - Math.round(112 * density)), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            enabled.requestFocus();
        });
        dialog.show();
    }

    private static void open(Activity activity, Intent intent) {
        if (Settings.ACTION_SETTINGS.equals(intent.getAction()) && HomeRedirect.isFireTv(activity)) {
            // The general settings entry belongs to Amazon Home. Device settings
            // is a public Android action handled by the separate Fire TV settings app.
            intent = new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                    .setPackage("com.amazon.tv.settings.v2");
        }
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }
}