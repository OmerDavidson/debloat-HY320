package com.vibehome.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;

/** Lightweight hybrid settings. Vendor UI owns protected controls; no helper service. */
final class NativeSettings {
    static final String[] SECTIONS={"Picture","Connections","Sound","Language & input","Date & time","System"};
    static final class State {String section="Picture",action="";int scroll;}
    private final Activity activity;
    private final ExecutorService worker;
    private final State state;
    private final Runnable projection;
    private LinearLayout body,sidebar;
    private ScrollView scroll;
    private AudioManager audio;
    private SeekBar volume;
    private TextView volumeLabel;
    private AlertDialog dialog;
    private boolean closed,observing;
    private int generation;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){if(!closed)updateVolume();}
    };
    NativeSettings(Activity activity,ExecutorService worker,State state,Runnable projection){this.activity=activity;this.worker=worker;this.state=state;this.projection=projection;}
    View create(){
        LinearLayout layout=row();layout.setBaselineAligned(false);layout.setClipChildren(false);
        sidebar=column();sidebar.setPadding(0,dp(4),dp(16),0);sidebar.setClipChildren(false);
        for(String name:SECTIONS){TextView button=button(name,()->choose(name));button.setTag("section:"+name);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(34));p.bottomMargin=dp(4);sidebar.addView(button,p);}
        layout.addView(sidebar,new LinearLayout.LayoutParams(dp(200),-1));
        scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setClipToPadding(false);
        body=column();body.setPadding(dp(12),dp(4),dp(10),dp(14));scroll.addView(body);
        layout.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));render();
        final int position=state.scroll;scroll.post(()->{if(!closed)scroll.scrollTo(0,position);});return layout;
    }
    View first(){View target=body.findViewWithTag(state.action);return target!=null?target:sidebar.getChildAt(index());}
    private int index(){for(int i=0;i<SECTIONS.length;i++)if(SECTIONS[i].equals(state.section))return i;return 0;}
    void resume(){observeSound();}
    private void remember(){
        if(scroll==null)return;state.scroll=scroll.getScrollY();View focus=body.findFocus();
        state.action=focus!=null&&focus.getTag() instanceof String?(String)focus.getTag():"";
    }
    void close(){if(closed)return;remember();closed=true;generation++;if(dialog!=null)dialog.dismiss();stopObserving();}
    private void stopObserving(){if(observing){activity.unregisterReceiver(receiver);observing=false;}}
    private void observeSound(){
        stopObserving();if(closed||!"Sound".equals(state.section))return;
        activity.registerReceiver(receiver,new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));observing=true;
    }
    private void choose(String value){
        if(state.section.equals(value))return;
        state.section=value;state.action="";state.scroll=0;generation++;render();scroll.scrollTo(0,0);observeSound();
    }
    private void render(){
        body.removeAllViews();volume=null;volumeLabel=null;
        for(int i=0;i<sidebar.getChildCount();i++)((TextView)sidebar.getChildAt(i)).setTextColor(Ui.ink(i==index()?Ui.ACCENT:Ui.INK));
        switch(state.section){case "Connections":connections();break;case "Sound":sound();break;case "Language & input":language();break;case "Date & time":dateTime();break;case "System":system();break;default:picture();}
    }
    private void picture(){
        heading("Picture","Live controls open the projector’s own settings.");
        vendor("Projection & keystone","Screen size, orientation and corner correction","ProjectionActivity");
        vendor("Brightness & color","Picture mode, brightness, contrast and color","PictureModeActivity");
        vendor("Screen saver","Open Projection settings, then Screen Saver","ProjectionActivity");
        gap(6);action("Projection preview","Native draft editor · does not change the picture",projection);
    }
    private void connections(){
        heading("Connections","Complete connection controls, provided by the projector.");
        vendor("Wi-Fi","Networks, connection settings and Wi-Fi power","WIFIActivity");
        vendor("Bluetooth","Pair, reconnect and manage accessories","BluetoothSettingActivity");
        vendor("Wi-Fi hotspot","Internet sharing and hotspot configuration","HotspotActivity");
    }
    private void sound(){
        heading("Sound","Projector media volume · native Vibe control.");
        audio=(AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);if(audio==null){value("Volume","Not available");return;}
        volumeLabel=text("",15,Ui.INK);body.addView(volumeLabel);volume=new SeekBar(activity);volume.setTag("volume");volume.setContentDescription("Media volume");
        volume.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));volume.setKeyProgressIncrement(1);body.addView(volume,new LinearLayout.LayoutParams(-1,dp(48)));updateVolume();
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){if(user){try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,progress,0);}catch(SecurityException denied){toast("Volume change was denied");}updateVolume();}}
            public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
        });note("Use Left and Right to adjust. Volume applies immediately.");
    }
    private void updateVolume(){if(volume==null||audio==null)return;int value=audio.getStreamVolume(AudioManager.STREAM_MUSIC),max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);volume.setProgress(value);volumeLabel.setText("Media volume · "+Math.round(100f*value/Math.max(1,max))+"%");}
    private void language(){
        heading("Language & input","Device language and keyboard.");
        if(SettingsAccess.has(activity,"CHANGE_CONFIGURATION")&&DeviceLanguage.adapterAvailable())action("Device language",Locale.getDefault().getDisplayName()+" · native",this::chooseLanguage);
        else vendor("Device language",Locale.getDefault().getDisplayName(),"LanguageListActivity");
        if(SettingsAccess.has(activity,"WRITE_SECURE_SETTINGS"))action("Choose keyboard","Select an enabled keyboard · native",this::chooseKeyboard);
        else vendor("Keyboard settings","Keyboard selection and input settings","LanguageActivity");
    }
    private void chooseKeyboard(){
        InputMethodManager manager=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);if(manager==null)return;
        List<InputMethodInfo> enabled=manager.getEnabledInputMethodList();String active=Settings.Secure.getString(activity.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD);
        String[] names=new String[enabled.size()];int checked=-1;for(int i=0;i<names.length;i++){names[i]=enabled.get(i).loadLabel(activity.getPackageManager()).toString();if(enabled.get(i).getId().equals(active))checked=i;}
        choice("Choose keyboard",names,checked,index->{try{if(!Settings.Secure.putString(activity.getContentResolver(),Settings.Secure.DEFAULT_INPUT_METHOD,enabled.get(index).getId()))toast("Android refused the keyboard change");}catch(SecurityException denied){toast("Keyboard access was denied");}});
    }
    private void chooseLanguage(){
        List<Locale> locales=new ArrayList<>();java.util.Set<String> tags=new java.util.LinkedHashSet<>();
        for(String asset:android.content.res.Resources.getSystem().getAssets().getLocales()){
            Locale locale=Locale.forLanguageTag(asset.replace('_','-'));if(locale.getLanguage().isEmpty()||locale.getLanguage().equals("und")||asset.equals("en-XA")||asset.equals("ar-XB"))continue;
            if(tags.add(locale.toLanguageTag()))locales.add(locale);
        }
        Collections.sort(locales,(a,b)->a.getDisplayName().compareToIgnoreCase(b.getDisplayName()));String[] labels=new String[locales.size()];int checked=-1;
        for(int i=0;i<labels.length;i++){labels[i]=locales.get(i).getDisplayName()+" · "+locales.get(i).getDisplayName(locales.get(i));if(locales.get(i).equals(Locale.getDefault()))checked=i;}
        choice("Device language",labels,checked,index->{try{DeviceLanguage.request(activity.getResources().getConfiguration(),locales.get(index));toast("Device language change requested");}catch(ReflectiveOperationException|RuntimeException denied){toast("The firmware refused the language change");}});
    }
    private void dateTime(){
        heading("Date & time","Current time and the projector’s clock controls.");
        vendor("Date & time settings","Automatic time, date, time zone and formats","DateTimeActivity");
        value("Current date & time",java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()));value("Time zone",TimeZone.getDefault().getDisplayName());
        if(SettingsAccess.has(activity,"WRITE_SECURE_SETTINGS"))action("Automatic date & time",Settings.Global.getInt(activity.getContentResolver(),Settings.Global.AUTO_TIME,0)==1?"On · native":"Off · native",()->{
            choice("Automatic date & time",new String[]{"Off","On"},Settings.Global.getInt(activity.getContentResolver(),Settings.Global.AUTO_TIME,0)==1?1:0,index->{try{if(!Settings.Global.putInt(activity.getContentResolver(),Settings.Global.AUTO_TIME,index))toast("Android refused automatic time change");}catch(SecurityException denied){toast("Automatic time access was denied");}});
        });
        if(Settings.System.canWrite(activity)){
            action("Time format",DateFormat.is24HourFormat(activity)?"24 hour · native":"12 hour · native",()->choice("Time format",new String[]{"12 hour","24 hour"},DateFormat.is24HourFormat(activity)?1:0,i->writeSystem(Settings.System.TIME_12_24,i==0?"12":"24")));
            String pattern=Settings.System.getString(activity.getContentResolver(),Settings.System.DATE_FORMAT);
            action("Date format",pattern==null?"Device default · native":pattern+" · native",()->{String[] formats={"M/d/yy","dd/MM/yy","yy/MM/dd","yyyy/MM/dd"};int index=-1;for(int i=0;i<formats.length;i++)if(formats[i].equals(pattern))index=i;choice("Date format",formats,index,i->writeSystem(Settings.System.DATE_FORMAT,formats[i]));});
        }
    }
    private void writeSystem(String key,String value){try{if(!Settings.System.putString(activity.getContentResolver(),key,value))toast("The setting was not saved");}catch(SecurityException denied){toast("Settings access was denied");}}
    private void system(){
        heading("System","Device information and advanced controls.");
        vendor("More device settings","Complete projector settings menu","HomeActivity");
        action("Advanced Android settings","Opens Android TV Settings ↗",()->VendorSettings.open(activity,new ComponentName("com.android.tv.settings","com.android.tv.settings.MainSettings")));
        vendor("System updates","Open About device for local or online update options","AboutDeviceActivity");
        vendor("Developer & USB settings","Open Other Setting, then Developer Opinions","OtherSettingsActivity");
        action("Factory reset","Open the projector’s reset confirmation ↗",()->{
            dialog=new AlertDialog.Builder(activity).setTitle("Open factory reset settings?").setMessage("The next screen belongs to the projector. Confirming a reset there can erase device data and affect calibration.").setNegativeButton("Cancel",null).setPositiveButton("Open settings",(d,w)->VendorSettings.open(activity,VendorSettings.page("FactoryResetActivity"))).show();dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
        });
        gap(6);value("Model",Build.MODEL+" · Android "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT);value("Firmware",Build.DISPLAY);value("Hardware",Build.HARDWARE+" · kernel "+System.getProperty("os.version","Unavailable"));
        TextView storage=value("Internal storage","Reading…"),memory=value("Memory snapshot","Reading…");final int request=generation;
        worker.execute(()->{
            String diskText="Unavailable",memoryText="Unavailable";
            try{android.os.StatFs disk=new android.os.StatFs(activity.getFilesDir().getAbsolutePath());diskText=android.text.format.Formatter.formatFileSize(activity,disk.getAvailableBytes())+" available of "+android.text.format.Formatter.formatFileSize(activity,disk.getTotalBytes());
                android.app.ActivityManager manager=(android.app.ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);if(manager!=null){android.app.ActivityManager.MemoryInfo info=new android.app.ActivityManager.MemoryInfo();manager.getMemoryInfo(info);memoryText=android.text.format.Formatter.formatFileSize(activity,info.availMem)+" available of "+android.text.format.Formatter.formatFileSize(activity,info.totalMem);}
            }catch(RuntimeException ignored){}
            final String disk=diskText,ram=memoryText;activity.runOnUiThread(()->{if(!closed&&request==generation){storage.setText(disk);memory.setText(ram);}});
        });
        value("Vibe Home","0.8.0");action("Control availability","Inspect Vibe’s native access",this::showAccess);
    }
    private void showAccess(){
        ScrollView details=new ScrollView(activity);TextView access=text("Reading native access…",12,Ui.INK);access.setPadding(dp(20),dp(12),dp(20),dp(12));access.setTextIsSelectable(true);details.addView(access);
        AlertDialog current=new AlertDialog.Builder(activity).setTitle("Control availability").setView(details).setPositiveButton("Back",null).show();dialog=current;
        worker.execute(()->{String result=SettingsAccess.describe(activity);activity.runOnUiThread(()->{if(!closed&&dialog==current&&current.isShowing())access.setText(result);});});
    }
    private void vendor(String title,String detail,String page){action(title,detail+" ↗",()->VendorSettings.open(activity,VendorSettings.page(page)));}
    private void choice(String title,String[] choices,int checked,java.util.function.IntConsumer action){dialog=new AlertDialog.Builder(activity).setTitle(title).setSingleChoiceItems(choices,checked,(d,w)->{action.accept(w);d.dismiss();}).setNegativeButton("Cancel",null).show();}
    private void heading(String title,String subtitle){body.addView(text(title,24,Ui.INK));body.addView(text(subtitle,12,Ui.MUTED));gap(12);}
    private void note(String copy){TextView note=text(copy,12,Ui.MUTED);note.setPadding(0,dp(6),0,dp(12));body.addView(note);}
    private TextView value(String title,String value){LinearLayout row=column();row.setPadding(dp(14),dp(10),dp(14),dp(10));row.addView(text(title,14,Ui.INK));TextView detail=text(value,12,Ui.MUTED);row.addView(detail);body.addView(row);return detail;}
    private void action(String title,String detail,Runnable action){
        LinearLayout item=column();item.setPadding(dp(14),dp(10),dp(14),dp(10));item.setTag(title);item.setFocusable(true);item.setClickable(true);item.setBackground(Ui.focus(activity,Ui.PANEL));
        TextView name=text(title,14,Ui.INK),sub=text(detail,11,Ui.MUTED);item.addView(name);item.addView(sub);
        item.setOnFocusChangeListener((v,on)->{name.setTextColor(on?Ui.DARK:Ui.INK);sub.setTextColor(on?Ui.DARK:Ui.MUTED);if(on)state.action=title;});Ui.hoverFocus(item);
        item.setOnClickListener(v->{item.requestFocusFromTouch();remember();action.run();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(6);body.addView(item,p);
    }
    private TextView button(String title,Runnable action){TextView view=text(title,13,Ui.INK);view.setGravity(Gravity.CENTER_VERTICAL);view.setPadding(dp(14),0,dp(12),0);Ui.button(view,Ui.PANEL);view.setOnClickListener(v->{view.requestFocusFromTouch();action.run();});return view;}
    private TextView text(String value,int size,int color){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setIncludeFontPadding(false);return view;}
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout row(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.HORIZONTAL);return view;}
    private int dp(int value){return Ui.dp(activity,value);}
    private void gap(int value){body.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(value)));}
    private void toast(String copy){Toast.makeText(activity,copy,Toast.LENGTH_LONG).show();}
}
