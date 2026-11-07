package com.vibehome.launcher;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;

/** Reports actual ordinary-app access. Does not request/grant permissions or call setters. */
final class SettingsAccess {
    static boolean has(Activity activity,String name){return activity.checkSelfPermission("android.permission."+name)==PackageManager.PERMISSION_GRANTED;}
    static String describe(Activity activity){
        StringBuilder text=new StringBuilder("Vibe UID: ").append(activity.getApplicationInfo().uid);
        PackageManager pm=activity.getPackageManager();
        try{text.append("\nVendor settings UID: ").append(pm.getApplicationInfo("com.htc.htcsettings",0).uid);
            text.append("\nVendor signing certificate matches Vibe: ").append(pm.checkSignatures(activity.getPackageName(),"com.htc.htcsettings")==PackageManager.SIGNATURE_MATCH?"yes":"no");
        }catch(PackageManager.NameNotFoundException unavailable){text.append("\nVendor settings unavailable");}
        text.append("\nNative locale adapter available: ").append(DeviceLanguage.adapterAvailable());
        text.append("\nModify system settings consent: ").append(Settings.System.canWrite(activity)?"allowed":"not allowed");
        for(String name:new String[]{"WRITE_SECURE_SETTINGS","CHANGE_CONFIGURATION","SET_TIME","SET_TIME_ZONE","NETWORK_SETTINGS","TETHER_PRIVILEGED","BLUETOOTH_PRIVILEGED","MASTER_CLEAR","RECOVERY"}){
            String permission="android.permission."+name;text.append("\n\n").append(name).append(": ")
                .append(activity.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED?"granted":"denied");
            try{int protection=pm.getPermissionInfo(permission,0).protectionLevel;
                int base=protection&PermissionInfo.PROTECTION_MASK_BASE;
                text.append("\nProtection: ").append(base==PermissionInfo.PROTECTION_SIGNATURE?"signature":base==PermissionInfo.PROTECTION_DANGEROUS?"runtime":base==PermissionInfo.PROTECTION_NORMAL?"normal":"other ("+base+")");
                if((protection&PermissionInfo.PROTECTION_FLAG_PRIVILEGED)!=0)text.append(" | privileged");
                if((protection&PermissionInfo.PROTECTION_FLAG_DEVELOPMENT)!=0)text.append(" | development (potential ADB grant)");
            }catch(PackageManager.NameNotFoundException unavailable){text.append("\nPermission not defined on this firmware");}
        }
        WifiManager wifi=(WifiManager)activity.getApplicationContext().getSystemService(Activity.WIFI_SERVICE);
        if(wifi!=null&&android.os.Build.VERSION.SDK_INT>=29){try{text.append("\n\nWPA3 personal supported: ").append(wifi.isWpa3SaeSupported()).append("\nEnhanced Open supported: ").append(wifi.isEnhancedOpenSupported());}catch(SecurityException denied){text.append("\nWi-Fi capability query denied");}}
        return text.append("\n\nNo access was granted and no setting was changed by this check.").toString();
    }
}
