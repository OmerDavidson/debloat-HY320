package com.vibehome.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.json.JSONObject;

/** On-demand source menu. Delegates selection to the installed vendor apps. */
final class InputMenu {
    private static final ComponentName HDMI = new ComponentName("com.softwinner.awlivetv","com.softwinner.awlivetv.MainActivity");
    private static final ComponentName FILES = new ComponentName("com.softwinner.TvdFileManager","com.softwinner.TvdFileManager.MainUI");
    private final Activity activity;
    private final ExecutorService worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final TvInputManager tv;
    private PopupWindow popup;
    private LinearLayout rows;
    private View anchor;
    private boolean loading, dirty, observing;
    private int generation;
    private final TvInputManager.TvInputCallback tvCallback = new TvInputManager.TvInputCallback(){
        @Override public void onInputStateChanged(String id,int state){refresh();}
        @Override public void onInputAdded(String id){refresh();}
        @Override public void onInputRemoved(String id){refresh();}
    };
    private final BroadcastReceiver media = new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){refresh();}
    };
    private static final class Source {
        final String key, label, detail, glyph;
        final boolean connected, available;
        Source(String key,String label,String detail,String glyph,boolean connected,boolean available){
            this.key=key;this.label=label;this.detail=detail;this.glyph=glyph;this.connected=connected;this.available=available;
        }
    }
    InputMenu(Activity activity,ExecutorService worker){
        this.activity=activity;this.worker=worker;
        tv=(TvInputManager)activity.getSystemService(Context.TV_INPUT_SERVICE);
    }
    boolean isShowing(){return popup!=null&&popup.isShowing();}
    void toggle(View view){if(isShowing())dismiss();else show(view);}
    void dismiss(){if(popup!=null)popup.dismiss();}
    private int dp(int n){return Ui.dp(activity,n);}
    private void show(View view){
        anchor=view;rows=new LinearLayout(activity);rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(6),dp(6),dp(6),dp(6));
        rows.setBackground(Ui.surface(activity,0xF2242529,0xFF45464A,20));
        TextView loadingLabel=new TextView(activity);loadingLabel.setText("Checking inputs…");loadingLabel.setTextColor(Ui.MUTED);
        loadingLabel.setGravity(Gravity.CENTER);rows.addView(loadingLabel,new LinearLayout.LayoutParams(-1,dp(58)));
        PopupWindow current=new PopupWindow(rows,dp(286),ViewGroup.LayoutParams.WRAP_CONTENT,true);popup=current;
        current.setBackgroundDrawable(Ui.surface(activity,0xF2242529,0,20));current.setElevation(dp(14));
        current.setOutsideTouchable(true);current.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);current.setAnimationStyle(0);
        current.setOnDismissListener(()->{
            generation++;loading=false;dirty=false;
            if(observing){activity.unregisterReceiver(media);if(tv!=null)tv.unregisterCallback(tvCallback);observing=false;}
            popup=null;anchor.setActivated(false);anchor.setContentDescription("Inputs, collapsed");
            if(anchor.isAttachedToWindow())anchor.requestFocusFromTouch();
        });
        int[] location=new int[2];view.getLocationOnScreen(location);
        int offset=Math.min(0,activity.getResources().getDisplayMetrics().widthPixels-dp(38)-location[0]-dp(286));
        current.showAsDropDown(view,offset,dp(8),Gravity.START);
        view.setActivated(true);view.setContentDescription("Inputs, expanded");
        IntentFilter filter=new IntentFilter();
        for(String action:new String[]{Intent.ACTION_MEDIA_MOUNTED,Intent.ACTION_MEDIA_UNMOUNTED,Intent.ACTION_MEDIA_REMOVED,Intent.ACTION_MEDIA_BAD_REMOVAL,Intent.ACTION_MEDIA_CHECKING})filter.addAction(action);
        filter.addDataScheme("file");activity.registerReceiver(media,filter);
        if(tv!=null)tv.registerCallback(tvCallback,main);observing=true;
        refresh();
    }
    private void refresh(){
        if(!isShowing())return;
        if(loading){dirty=true;return;}loading=true;final int request=generation;
        worker.execute(()->{
            List<Source> sources=discover();
            main.post(()->{
                if(request!=generation||!isShowing())return;
                loading=false;render(sources);if(dirty){dirty=false;refresh();}
            });
        });
    }
    private boolean launchable(ComponentName component){
        try{ActivityInfo info=activity.getPackageManager().getActivityInfo(component,0);
            return info.exported&&info.enabled&&info.applicationInfo.enabled&&(info.permission==null||activity.checkSelfPermission(info.permission)==android.content.pm.PackageManager.PERMISSION_GRANTED);
        }catch(android.content.pm.PackageManager.NameNotFoundException|RuntimeException error){return false;}
    }
    private List<Source> discover(){
        List<Source> sources=new ArrayList<>();
        // The HAL advertises unused HDMI/AV ports too. Use the same configured
        // source list as the stock launcher, rather than inventing physical ports.
        try(BufferedReader reader=new BufferedReader(new FileReader("/product/etc/config.ini"))){
            StringBuilder raw=new StringBuilder();String line;
            while((line=reader.readLine())!=null)if(!line.trim().startsWith("#"))raw.append(line).append('\n');
            JSONObject config=new JSONObject(raw.toString());
            String[] keys=config.optString("sourceList","").split(","),labels=config.optString("sourceListTitle","").split(",");
            List<TvInputInfo> inputs=tv==null?new ArrayList<>():tv.getTvInputList();
            boolean canOpen=launchable(HDMI);
            for(int i=0;i<keys.length;i++){
                String key=keys[i].trim();if(!key.matches("HDMI[1-3]|CVBS[1-2]|VGA"))continue;
                String label=i<labels.length&&!labels[i].trim().isEmpty()?labels[i].trim():key;
                String status="Status unavailable";boolean connected=false;
                // These IDs and the HDMI2 mapping were checked against this firmware.
                String id=key.startsWith("HDMI")?"com.softwinner.vis/.HdmiInputService/HW"+key.substring(4):null;
                for(TvInputInfo input:inputs)if(input.getId().equals(id)){
                    int state=tv.getInputState(id);connected=state==TvInputManager.INPUT_STATE_CONNECTED;
                    status=connected?"Connected":state==TvInputManager.INPUT_STATE_CONNECTED_STANDBY?"Standby":"Not connected";break;
                }
                sources.add(new Source(key,label,canOpen?status:"Unavailable",key.startsWith("HDMI")?"hdmi":"inputs",connected,canOpen));
            }
        }catch(Exception error){
            sources.add(new Source("unavailable","Projector inputs","Unable to read input configuration","hdmi",false,false));
        }
        boolean mounted=false,known=true;List<String> names=new ArrayList<>();
        try{
            StorageManager storage=(StorageManager)activity.getSystemService(Context.STORAGE_SERVICE);
            if(storage==null)throw new IllegalStateException();
            for(StorageVolume volume:storage.getStorageVolumes())if(volume.isRemovable()&&(Environment.MEDIA_MOUNTED.equals(volume.getState())||Environment.MEDIA_MOUNTED_READ_ONLY.equals(volume.getState()))){
                mounted=true;names.add(volume.getDescription(activity));
            }
        }catch(RuntimeException error){known=false;}
        boolean browser=launchable(FILES);
        sources.add(new Source("usb","USB",!browser?"Media browser unavailable":mounted?String.join(" · ",names):known?"No drive connected":"Status unavailable","usb",mounted,browser));
        return sources;
    }
    private void render(List<Source> sources){
        String selected="";View focus=rows.findFocus();if(focus!=null&&focus.getTag() instanceof String)selected=(String)focus.getTag();
        rows.removeAllViews();View target=null;
        for(Source source:sources){
            if(source.key.equals("usb")&&rows.getChildCount()>0){View divider=new View(activity);divider.setBackgroundColor(0xFF424347);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.setMargins(dp(12),dp(6),dp(12),dp(6));rows.addView(divider,p);}
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(12),0);row.setTag(source.key);
            Ui.Glyph icon=new Ui.Glyph(activity,source.glyph);row.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));
            TextView title=new TextView(activity);title.setText(source.label);title.setTextSize(15);title.setTextColor(Ui.INK);title.setSingleLine(true);
            LinearLayout.LayoutParams label=new LinearLayout.LayoutParams(0,-2,1);label.setMarginStart(dp(12));row.addView(title,label);
            TextView status=new TextView(activity);status.setText((source.connected?"●  ":"")+source.detail);status.setTextSize(10);status.setTextColor(source.connected?0xFF51CB80:Ui.MUTED);status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(status,new LinearLayout.LayoutParams(dp(132),-2));
            row.setFocusable(true);row.setFocusableInTouchMode(true);row.setClickable(true);row.setBackground(Ui.focus(activity,android.graphics.Color.TRANSPARENT));
            row.setContentDescription(source.label+", "+source.detail);
            row.setOnFocusChangeListener((v,on)->{icon.active(on);title.setTextColor(on?Ui.DARK:Ui.INK);status.setTextColor(on?0xFF4B5550:source.connected?0xFF51CB80:Ui.MUTED);});
            Ui.hoverFocus(row);
            row.setOnClickListener(v->{
                if(!source.available){Toast.makeText(activity,source.detail,Toast.LENGTH_SHORT).show();return;}
                // USB is a media browser, not a video signal. Allow browsing even
                // without a drive; the vendor browser owns mounts and file access.
                Intent intent=new Intent(Intent.ACTION_MAIN).setComponent(source.key.equals("usb")?FILES:HDMI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                if(!source.key.equals("usb"))intent.putExtra("input_source",source.key);
                dismiss();try{activity.startActivity(intent);}catch(android.content.ActivityNotFoundException|SecurityException error){Toast.makeText(activity,"Input unavailable on this firmware",Toast.LENGTH_SHORT).show();}
            });
            // Touch focus must not consume the first tap on a different source.
            row.setOnTouchListener((v,event)->{if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP){v.setPressed(false);v.performClick();return true;}return false;});
            row.setOnKeyListener((v,key,event)->{
                if(event.getAction()==KeyEvent.ACTION_DOWN&&((key==KeyEvent.KEYCODE_DPAD_UP&&v==rows.getChildAt(0))||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)){
                    dismiss();if(key!=KeyEvent.KEYCODE_DPAD_UP){View next=anchor.focusSearch(key==KeyEvent.KEYCODE_DPAD_LEFT?View.FOCUS_LEFT:View.FOCUS_RIGHT);if(next!=null)next.requestFocusFromTouch();}return true;
                }return false;
            });
            rows.addView(row,new LinearLayout.LayoutParams(-1,dp(52)));if(target==null||source.key.equals(selected))target=row;
        }
        popup.update(dp(286),-2);
        final View restore=target;final PopupWindow current=popup;
        if(restore!=null)rows.post(()->{if(popup==current&&current.isShowing()&&restore.getParent()==rows)restore.requestFocusFromTouch();});
    }
}
