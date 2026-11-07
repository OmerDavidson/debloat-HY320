package com.vibehome.launcher;

import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Locale;

/** Firmware-dependent locale adapter. Caller must hold CHANGE_CONFIGURATION.
 * Does not disable hidden-API restrictions; reflection/permission failures propagate.
 */
final class DeviceLanguage {
    static boolean adapterAvailable(){
        try{Class.forName("android.app.ActivityManager").getMethod("getService");
            Class.forName("android.app.IActivityManager").getMethod("updatePersistentConfiguration",Configuration.class);
            Configuration.class.getField("userSetLocale");return true;
        }catch(ReflectiveOperationException|RuntimeException unavailable){return false;}
    }
    static void request(Configuration baseline,Locale locale) throws ReflectiveOperationException {
        Configuration next=new Configuration(baseline);next.setLocales(new LocaleList(locale));
        Configuration.class.getField("userSetLocale").setBoolean(next,true);
        Object manager=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Class.forName("android.app.IActivityManager").getMethod("updatePersistentConfiguration",Configuration.class).invoke(manager,next);
    }
}
