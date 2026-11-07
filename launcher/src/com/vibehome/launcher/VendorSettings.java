package com.vibehome.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.widget.Toast;

/** Opens installed, exported settings UI. No shell, HOME selection or hardware setters. */
final class VendorSettings {
    static final String PACKAGE="com.htc.htcsettings";
    static ComponentName page(String name){return new ComponentName(PACKAGE,"com.htc.activity."+name);}
    static boolean available(Activity activity,ComponentName component){
        try{ActivityInfo info=activity.getPackageManager().getActivityInfo(component,0);
            return info.exported&&info.enabled&&info.applicationInfo.enabled&&(info.permission==null||activity.checkSelfPermission(info.permission)==PackageManager.PERMISSION_GRANTED);
        }catch(PackageManager.NameNotFoundException|RuntimeException unavailable){return false;}
    }
    static void open(Activity activity,ComponentName requested){
        // Private firmware editors are reached through their exported parent page.
        ComponentName target=available(activity,requested)?requested:page("HomeActivity");
        if(!available(activity,target)){Toast.makeText(activity,"Projector settings are unavailable on this firmware",Toast.LENGTH_LONG).show();return;}
        try{activity.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(target));}
        catch(android.content.ActivityNotFoundException|SecurityException unavailable){Toast.makeText(activity,"Unable to open projector settings",Toast.LENGTH_LONG).show();}
    }
}
