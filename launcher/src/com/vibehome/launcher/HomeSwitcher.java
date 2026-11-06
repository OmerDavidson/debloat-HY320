package com.vibehome.launcher;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** Public Android home-selection UI only. Never changes another package or calls a shell. */
final class HomeSwitcher {
    static final int REQUEST=814;
    private static final String STOCK="com.htc.htclauncherhighenglishd08";
    private final Activity activity;
    private final SharedPreferences prefs;
    private final ComponentName entry;
    HomeSwitcher(Activity owner){activity=owner;prefs=owner.getSharedPreferences("home_switch",0);entry=new ComponentName(owner,"com.vibehome.launcher.HomeEntry");}
    String currentPackage(){
        ResolveInfo value=activity.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY);
        return value==null||value.activityInfo==null?"":value.activityInfo.packageName;
    }
    boolean isVibe(){
        if(Build.VERSION.SDK_INT>=29)try{RoleManager roles=activity.getSystemService(RoleManager.class);if(roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_HOME)&&roles.isRoleHeld(RoleManager.ROLE_HOME))return true;}catch(RuntimeException ignored){}
        return activity.getPackageName().equals(currentPackage());
    }
    String status(){return isVibe()?"Vibe Home":STOCK.equals(currentPackage())?"Original launcher":"Choose a default";}
    private boolean entryEnabled(){return activity.getPackageManager().getComponentEnabledSetting(entry)==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;}
    private void enabled(boolean value){activity.getPackageManager().setComponentEnabledSetting(entry,value?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);}
    void useVibe(){
        if(isVibe()){openVibe();return;}
        if(!begin("vibe"))return;
        try{
            enabled(true);
            if(Build.VERSION.SDK_INT>=29){RoleManager roles=activity.getSystemService(RoleManager.class);
                if(roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_HOME)){activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME),REQUEST);return;}}
            picker();
        }catch(RuntimeException error){finish();message("Default launcher selection is unavailable.");}
    }
    void useOriginal(){
        if(STOCK.equals(currentPackage())){if(entryEnabled())enabled(false);openOriginal();return;}
        if(!begin("stock"))return;
        try{picker();}catch(RuntimeException error){finish();message("Default launcher selection is unavailable.");}
    }
    private boolean begin(String target){
        // Persist before enabling HOME, so cancellation also recovers after process recreation.
        boolean ok=prefs.edit().putString("pending",target).putBoolean("prior_enabled",entryEnabled()).commit();
        if(!ok)message("Unable to prepare launcher selection.");return ok;
    }
    private void picker(){activity.startActivityForResult(new Intent(Settings.ACTION_HOME_SETTINGS),REQUEST);}
    /** Called after returning from Android's UI; also recovers a process killed while it was open. */
    void finish(){
        String target=prefs.getString("pending","");if(target.isEmpty())return;
        boolean own=isVibe(),original=STOCK.equals(currentPackage()),wasEnabled=prefs.getBoolean("prior_enabled",false);
        if(!own&&(original||!wasEnabled))enabled(false);
        prefs.edit().remove("pending").remove("prior_enabled").apply();
        if(target.equals("stock")&&original)openOriginal();else if(target.equals("vibe")&&own)openVibe();
    }
    private void openOriginal(){
        try{activity.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(STOCK,"com.htc.activity.HomeActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException error){message("Original launcher unavailable.");}
    }
    private void openVibe(){activity.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(entry).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private void message(String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
}
