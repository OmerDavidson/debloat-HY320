package com.vibehome.launcher;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** The short-press modes exposed by the supported projector firmware. */
final class QuickWake {
    static final int SLEEP = 1;
    static final int POWER_MENU = 6;
    private static final int UNKNOWN = -1;
    private static final String POWER_BUTTON_SHORT_PRESS = "power_button_short_press";

    private final Context context;

    QuickWake(Context context) {
        this.context = context;
    }

    int behavior() {
        if (!supportsFirmware()) return UNKNOWN;
        return Settings.Global.getInt(context.getContentResolver(), POWER_BUTTON_SHORT_PRESS, UNKNOWN);
    }

    boolean canChange() {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    boolean setEnabled(boolean enabled) {
        int current = behavior();
        if (!canChange() || (current != SLEEP && current != POWER_MENU)) return false;
        int requested = enabled ? SLEEP : POWER_MENU;
        if (current == requested) return true;
        try {
            return Settings.Global.putInt(context.getContentResolver(), POWER_BUTTON_SHORT_PRESS, requested)
                    && behavior() == requested;
        } catch (SecurityException denied) {
            return false;
        }
    }

    private static boolean supportsFirmware() {
        return ("HY320".equals(Build.MODEL) && "UI_Veng.projector.20241225.164550".equals(Build.DISPLAY))
                || ("HY300Pro".equals(Build.MODEL) && "UI_Veng.projector.20240815.134535".equals(Build.DISPLAY));
    }
}
