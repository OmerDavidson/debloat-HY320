package com.vibehome.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

/** Quick links to Android settings, the projector's original settings, and Vibe. */
final class SettingsMenu {
    private static final String ANDROID_TV = "com.android.tv.settings";
    private final Activity activity;
    private final Runnable openQuickWake;
    private final Runnable openAbout;
    private PopupWindow popup;

    SettingsMenu(Activity activity, Runnable openQuickWake, Runnable openAbout) {
        this.activity = activity;
        this.openQuickWake = openQuickWake;
        this.openAbout = openAbout;
    }

    void dismiss() {
        if (popup != null) popup.dismiss();
    }

    void toggle(View anchor) {
        if (popup != null) {
            dismiss();
            return;
        }

        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(6), dp(6), dp(6), dp(6));
        PopupWindow window = new PopupWindow(rows, dp(252), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup = window;
        window.setBackgroundDrawable(Ui.surface(activity, 0xF2242529, 0xFF45464A, 20));
        window.setElevation(dp(14));
        window.setOutsideTouchable(true);
        window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        window.setAnimationStyle(0);
        window.setOnDismissListener(() -> {
            popup = null;
            anchor.setActivated(false);
            anchor.setContentDescription("Settings, collapsed");
            if (anchor.isAttachedToWindow()) anchor.requestFocusFromTouch();
        });

        addRow(rows, anchor, "Wi-Fi", R.drawable.ic_menu_wifi, () -> openAndroid(
                Settings.ACTION_WIFI_SETTINGS, "connectivity.NetworkActivity", "Wi-Fi"));
        addRow(rows, anchor, "Bluetooth", R.drawable.ic_menu_bluetooth, this::openBluetooth);
        addRow(rows, anchor, "Projector settings", R.drawable.ic_menu_settings,
                () -> VendorSettings.open(activity, VendorSettings.page("HomeActivity")));
        View divider = new View(activity);
        divider.setBackgroundColor(0xFF424347);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(1));
        line.setMargins(dp(12), dp(5), dp(12), dp(5));
        rows.addView(divider, line);
        addRow(rows, anchor, "Quick Wake", R.drawable.ic_menu_power, openQuickWake);
        addRow(rows, anchor, "About", R.drawable.ic_menu_info, openAbout);

        window.showAsDropDown(anchor, 0, dp(8), Gravity.END);
        anchor.setActivated(true);
        anchor.setContentDescription("Settings, expanded");
        rows.post(() -> {
            if (popup == window && window.isShowing()) rows.getChildAt(0).requestFocusFromTouch();
        });
    }

    private void addRow(LinearLayout rows, View anchor, String title, int icon, Runnable action) {
        TextView row = new TextView(activity);
        row.setText(title);
        row.setTextSize(15);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setBackground(Ui.focus(activity, Color.TRANSPARENT));
        row.setFocusableInTouchMode(true);
        Ui.menuIcon(row, icon);
        Ui.hoverFocus(row);
        row.setOnClickListener(v -> {
            dismiss();
            action.run();
        });
        // A tap on a different row should open it in one press, even in touch mode.
        row.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_UP) return false;
            v.setPressed(false);
            if (event.getX() >= 0 && event.getX() < v.getWidth()
                    && event.getY() >= 0 && event.getY() < v.getHeight()) v.performClick();
            return true;
        });
        row.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key == KeyEvent.KEYCODE_DPAD_UP && v == rows.getChildAt(0)) {
                dismiss();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                dismiss();
                View next = anchor.focusSearch(key == KeyEvent.KEYCODE_DPAD_LEFT
                        ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
                if (next != null) next.requestFocusFromTouch();
                return true;
            }
            return false;
        });
        rows.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void openBluetooth() {
        if (start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).setPackage(ANDROID_TV))) return;
        // On this firmware Bluetooth is an internal page of Android TV Settings.
        if (start(new Intent(Settings.ACTION_SETTINGS).setComponent(
                new ComponentName(ANDROID_TV, ANDROID_TV + ".MainSettings")))) return;
        if (start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).setPackage("com.android.settings"))) return;
        Toast.makeText(activity, "Android Bluetooth settings are unavailable", Toast.LENGTH_SHORT).show();
    }

    private void openAndroid(String action, String tvActivity, String label) {
        if (start(new Intent(action).setPackage(ANDROID_TV))) return;
        // This firmware exposes the TV pages as activities without the standard action filters.
        ComponentName component = new ComponentName(ANDROID_TV, ANDROID_TV + "." + tvActivity);
        if (VendorSettings.available(activity, component) && start(new Intent().setComponent(component))) return;
        if (start(new Intent(action).setPackage("com.android.settings"))) return;
        Toast.makeText(activity, label + " settings are unavailable", Toast.LENGTH_SHORT).show();
    }

    private boolean start(Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return false;
        }
    }

    private int dp(int value) {
        return Ui.dp(activity, value);
    }
}
