package com.vibehome.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline, remote-first home. Native recycled app grid and bounded, on-demand icons. */
public final class MainActivity extends Activity {
    private static final String VENDOR = "com.htc.htcsettings", STOCK_HOME = "com.htc.htclauncherhighenglishd08";
    private static final int MAX_FAVORITES = 4;
    private static final int PIN_HEADROOM_DP = 24;
    private static final String FAVORITES_FULL = "4 favorites pinned. Unpin one to add another.";
    private static final String[] DEFAULTS = {"com.google.android.youtube.tv", "com.netflix.mediaclient",
        "org.jellyfin.androidtv", "org.videolan.vlc", "com.disneyplus.mea", "com.valvesoftware.steamlink"};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    // 72px ARGB icons: ~20 KiB each. Home holds only visible/recycled card views.
    private final LruCache<String, Bitmap> icons = new LruCache<String, Bitmap>(384 * 1024) {
        @Override protected int sizeOf(String key, Bitmap image) { return image.getAllocationByteCount(); }
    };
    // Accessed only on the main thread. Weak targets never keep discarded pages alive.
    private final Map<String, List<WeakReference<ImageView>>> pendingIcons = new HashMap<>();
    private List<App> apps = new ArrayList<>();
    private String fingerprint = "", lastApp = "";
    private SharedPreferences prefs;
    private LinearLayout root, body;
    private TextView hero, editTab, editHint;
    private boolean editing;
    private View firstTile;
    private ThemeBackdrop backdrop;
    private HomeSwitcher homeSwitcher;
    private InputMenu inputs;
    private SettingsMenu settingsMenu;
    private TextView aboutLauncherStatus;
    private final Map<String,Integer> appColors=new HashMap<>();
    private final Set<String> noBanner=new LinkedHashSet<>();
    private final LruCache<String,Bitmap> banners=new LruCache<String,Bitmap>(1024*1024){
        @Override protected int sizeOf(String key,Bitmap image){return image.getAllocationByteCount();}
    };
    private final LruCache<String,Bitmap> previewImages=new LruCache<String,Bitmap>(256*1024){
        @Override protected int sizeOf(String key,Bitmap image){return image.getAllocationByteCount();}
    };
    private boolean disposed, refreshing;
    private Dialog sheet;

    private static final class App {
        final String label;
        final ComponentName component;
        final ResolveInfo info;
        App(ResolveInfo value, PackageManager pm) {
            info=value;component=new ComponentName(value.activityInfo.packageName,value.activityInfo.name);
            String name=value.loadLabel(pm).toString().trim();
            label=VENDOR.equals(component.getPackageName())?"Projector settings":
                "com.android.settings".equals(component.getPackageName())?"Android settings":name.isEmpty()?component.getPackageName():name;
        }
        String pkg(){return component.getPackageName();}
        String key(){return component.flattenToString();}
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);prefs=getSharedPreferences("home",MODE_PRIVATE);homeSwitcher=new HomeSwitcher(this);
        inputs=new InputMenu(this,worker);
        settingsMenu=new SettingsMenu(this,this::showAbout);
        if(saved!=null)lastApp=saved.getString("last_app","");
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        drawPage();
    }
    @Override protected void onResume(){super.onResume();homeSwitcher.finish();refreshLauncherStatus();refreshApps();}
    @Override protected void onPause(){inputs.dismiss();settingsMenu.dismiss();if(editing)finishEdit();super.onPause();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==HomeSwitcher.REQUEST){homeSwitcher.finish();refreshLauncherStatus();}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.hasCategory(Intent.CATEGORY_HOME)||intent.getComponent()!=null&&intent.getComponent().getClassName().endsWith(".HomeEntry"))showHome();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("last_app",lastApp);super.onSaveInstanceState(out);}

    private void refreshApps() {
        if(refreshing||disposed)return;refreshing=true;
        worker.execute(()->{
            List<App> next=new ArrayList<>();String signature;
            try {
                PackageManager pm=getPackageManager();LinkedHashMap<String,App> found=new LinkedHashMap<>();
                for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}) {
                    for(ResolveInfo value:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)) {
                        ActivityInfo a=value.activityInfo;
                        if(a==null||!a.exported||!a.enabled||!a.applicationInfo.enabled)continue;
                        if(getPackageName().equals(a.packageName)||STOCK_HOME.equals(a.packageName))continue;
                        if(a.permission!=null&&pm.checkPermission(a.permission,getPackageName())!=PackageManager.PERMISSION_GRANTED)continue;
                        if(!found.containsKey(a.packageName))found.put(a.packageName,new App(value,pm));
                    }
                }
                next.addAll(found.values());Collections.sort(next,Comparator.comparing(a->a.label.toLowerCase(Locale.ROOT)));
                StringBuilder s=new StringBuilder();for(App app:next)s.append(app.key()).append(app.label).append('\n');signature=s.toString();
            } catch(RuntimeException error) {
                runOnUiThread(()->{refreshing=false;if(!disposed)toast("Unable to refresh apps.");});return;
            }
            runOnUiThread(()->{
                refreshing=false;if(disposed||isFinishing())return;
                if(!signature.equals(fingerprint)){fingerprint=signature;apps=next;if(sheet!=null)sheet.dismiss();drawPage();}
            });
        });
    }

    private Set<String> pinnedPackages(){return prefs.getStringSet("favorites",new LinkedHashSet<>(Arrays.asList(DEFAULTS)));}
    private boolean isHomeApp(App app){
        return !VENDOR.equals(app.pkg())&&!"com.android.settings".equals(app.pkg());
    }
    private List<App> favorites() {
        Set<String> wanted=pinnedPackages();
        Map<String,App> remaining=new LinkedHashMap<>();
        for(App app:apps)if(isHomeApp(app)&&wanted.contains(app.pkg()))remaining.put(app.pkg(),app);
        List<App> result=new ArrayList<>(MAX_FAVORITES);
        String[] order=prefs.getString("favorite_order",String.join("|",DEFAULTS)).split("\\|");
        for(String pkg:order){
            App app=remaining.remove(pkg);
            if(app!=null)result.add(app);
            if(result.size()==MAX_FAVORITES)return result;
        }
        for(App app:remaining.values()){
            if(result.size()==MAX_FAVORITES)break;
            result.add(app);
        }
        return result;
    }

    private void showHome(){
        if(sheet!=null)sheet.dismiss();
        drawPage();
    }
    private void drawPage() {
        inputs.dismiss();
        settingsMenu.dismiss();
        editing=false;
        root=column();root.setPadding(dp(38),dp(24),dp(38),dp(16));
        backdrop=new ThemeBackdrop();root.setBackground(backdrop);
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);
        {
            LinearLayout dateBlock=column();
            int hour=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            TextView greeting=text(hour<12?"GOOD MORNING":hour<18?"GOOD AFTERNOON":"GOOD EVENING",9,Ui.INK);greeting.setLetterSpacing(.16f);dateBlock.addView(greeting);gap(dateBlock,8);
            LinearLayout clocks=row();clocks.setGravity(Gravity.BOTTOM);
            TextClock time=new TextClock(this);time.setFormat12Hour("h:mm a");time.setFormat24Hour("HH:mm");time.setTextSize(31);time.setTextColor(Ui.INK);time.setIncludeFontPadding(true);time.setSingleLine(true);time.setFontFeatureSettings("tnum");clocks.addView(time);
            TextClock date=new TextClock(this);date.setFormat12Hour("EEE, d MMMM");date.setFormat24Hour("EEE, d MMMM");date.setTextSize(10);date.setTextColor(Ui.INK);date.setLetterSpacing(.10f);
            LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(-2,dp(26));d.setMarginStart(dp(18));clocks.addView(date,d);dateBlock.addView(clocks);
            top.addView(dateBlock,new LinearLayout.LayoutParams(0,-2,1));
        }
        TextView inputTab = menuTab("Inputs", R.drawable.ic_menu_input, inputs::toggle);
        firstTile = inputTab;
        addNavigation(top, inputTab);
        editTab = text("Edit", 13, Ui.INK);
        editTab.setGravity(Gravity.CENTER);
        editTab.setTypeface(Typeface.create("sans-serif-medium", 0));
        Ui.menuPill(editTab, R.drawable.ic_menu_edit);
        editTab.setOnClickListener(v -> toggleEdit());
        addNavigation(top, editTab);
        addNavigation(top, menuTab("Settings", R.drawable.ic_menu_settings, settingsMenu::toggle));
        top.setMinimumHeight(dp(65));root.addView(top,new LinearLayout.LayoutParams(-1,-2));gap(root,16);
        body=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        drawHome();
        setContentView(root);firstTile.requestFocusFromTouch();
    }

    private void addNavigation(LinearLayout top, View tab) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
        params.setMarginStart(dp(8));
        top.addView(tab, params);
    }

    private TextView menuTab(String label, int icon, java.util.function.Consumer<View> toggle) {
        TextView tab = text(label, 13, Ui.INK);
        tab.setGravity(Gravity.CENTER);
        tab.setTypeface(Typeface.create("sans-serif-medium", 0));
        Ui.menuPill(tab, icon);
        tab.setContentDescription(label + ", collapsed");
        tab.setOnClickListener(toggle::accept);
        tab.setOnKeyListener((view, key, event) -> {
            if (key != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) toggle.accept(view);
            return true;
        });
        return tab;
    }

    private List<App> homeApps(List<App> pinned,String order){
        Map<String,App> remaining = new LinkedHashMap<>();
        for(App app:apps)if(isHomeApp(app))remaining.put(app.pkg(),app);
        for(App app:pinned)remaining.remove(app.pkg());
        List<App> result = new ArrayList<>(pinned);
        for(String pkg:order.split("\\|")){
            App app = remaining.remove(pkg);
            if(app!=null)result.add(app);
        }
        result.addAll(remaining.values());
        return result;
    }
    private void saveHomeOrder(List<App> display){
        Set<String> visible = new LinkedHashSet<>();
        for(App app:display)visible.add(app.pkg());
        List<String> order = new ArrayList<>();
        int next = 0;
        boolean hasAbsentApps = false;
        // Keep entries for temporarily unavailable apps in their saved slots.
        for(String pkg:prefs.getString("home_order","").split("\\|")){
            if(pkg.isEmpty())continue;
            if(!visible.contains(pkg)){
                order.add(pkg);
                hasAbsentApps = true;
            }else if(next<display.size()){
                order.add(display.get(next++).pkg());
            }
        }
        while(next<display.size())order.add(display.get(next++).pkg());
        SharedPreferences.Editor update = prefs.edit();
        if(!hasAbsentApps&&display.equals(homeApps(favorites(),"")))update.remove("home_order");
        else update.putString("home_order",String.join("|",order));
        update.apply();
    }
    private void toggleEdit(){
        if(editing){
            finishEdit();
        }else if(firstTile instanceof HomeRowsGrid){
            editing=true;
            updateEditUi();
            HomeRowsGrid grid=(HomeRowsGrid)firstTile;
            grid.requestFocusFromTouch();
            grid.selectLogical(grid.logicalSelection);
        }
    }
    private void finishEdit(){
        HomeRowsGrid grid=(HomeRowsGrid)firstTile;
        grid.cancelPickup();
        editing=false;
        updateEditUi();
        grid.requestFocusFromTouch();
        grid.selectLogical(grid.logicalSelection);
    }
    private void updateEditUi(){
        editTab.setText(editing?"Done":"Edit");
        editTab.setActivated(editing);
        editHint.setVisibility(editing?View.VISIBLE:View.GONE);
        HomeRowsGrid grid=(HomeRowsGrid)firstTile;
        grid.setEditingAnimation(editing);
        if(!editing)return;
        editHint.setText(grid.picked==null
                ?"EDIT APPS   ·   OK to pick up   ·   Pinned apps are locked   ·   Back to finish"
                :"MOVING APP   ·   Arrows to move   ·   OK to drop   ·   Back to cancel");
    }
    private void drawHome(){
        body.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        editHint=text("",12,Ui.INK);
        editHint.setGravity(Gravity.CENTER_VERTICAL);
        editHint.setVisibility(View.GONE);
        body.addView(editHint,fill(28));
        hero=text("Your apps",33,Ui.INK);hero.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));hero.setSingleLine(true);hero.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(hero,new LinearLayout.LayoutParams(-1,dp(48)));gap(body,10);
        List<App> pinned=favorites();
        List<App> display=homeApps(pinned,prefs.getString("home_order",""));
        if(display.isEmpty())return;
        GridView grid=new HomeRowsGrid(display,pinned.size());
        // Space for the pins, one sharp row, one gap, and half of the preview row.
        body.addView(grid,new LinearLayout.LayoutParams(-1,dp(PIN_HEADROOM_DP)+dp(118)+dp(14)+dp(59)));
        firstTile=grid;
    }

    private void trackGridFocus(GridView grid){
        grid.setOnFocusChangeListener((v,focused)->refreshGridFocus(grid));
    }
    private void refreshGridFocus(GridView grid){
        for(int i=0;i<grid.getChildCount();i++)((BannerCard)grid.getChildAt(i)).paintSelection(grid.getChildAt(i).isSelected());
    }

    /** Keep the active logical row at the top; native pixel scrolling must not crop it. */
    private final class HomeRowsGrid extends GridView {
        final List<App> display;
        final BaseAdapter adapter;
        final int pinnedCount;
        App picked;
        List<App> beforePickup;
        int windowStart=0,logicalSelection=0;
        int rowDirection;
        FrameLayout departing;
        ValueAnimator editWiggle;
        float touchX,touchY;boolean swiping;
        HomeRowsGrid(List<App> values,int pinnedCount){
            super(MainActivity.this);display=values;this.pinnedCount=pinnedCount;
            trackGridFocus(this);
            setNumColumns(4);setHorizontalSpacing(dp(14));setVerticalSpacing(dp(14));setStretchMode(STRETCH_COLUMN_WIDTH);
            setVerticalScrollBarEnabled(false);setSelector(android.R.color.transparent);setOverScrollMode(OVER_SCROLL_NEVER);
            setVerticalFadingEdgeEnabled(false);setFadingEdgeLength(0);setPadding(0,0,0,0);
            for(int i=0;i<display.size();i++)if(display.get(i).pkg().equals(lastApp)){logicalSelection=i;break;}
            windowStart=logicalSelection/4*4;
            adapter=new BaseAdapter(){
                public int getCount(){return Math.min(8,display.size()-windowStart);}
                public App getItem(int position){return display.get(windowStart+position);}
                public long getItemId(int position){return windowStart+position;}
                public View getView(int position,View old,ViewGroup parent){
                    BannerCard card=old instanceof BannerCard?(BannerCard)old:new BannerCard();
                    App app = getItem(position);
                    card.bind(app, windowStart+position<pinnedCount, null);
                    card.setPreview(position>=4);return card;
                }
            };
            setAdapter(adapter);
            setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                    refreshGridFocus(HomeRowsGrid.this);
                    if(picked!=null)return;
                    int absolute=windowStart+position;
                    if(position>=4){post(()->{if(picked==null&&isAttachedToWindow())selectLogical(absolute);});return;}
                    if(absolute<display.size()){logicalSelection=absolute;focusApp(display.get(absolute));}
                }
                public void onNothingSelected(android.widget.AdapterView<?> parent){}
            });
            setOnItemClickListener((parent,view,position,id)->{
                int absolute=windowStart+position;
                if(editing){
                    if(picked!=null&&display.get(absolute)!=picked)movePicked(absolute);
                    else{selectLogical(absolute);togglePickup();}
                    return;
                }
                if(position>=4)selectLogical(absolute);else openComponent(display.get(absolute).component);
            });
            setOnItemLongClickListener((parent,view,position,id)->{if(!editing)showAppOptions(display.get(windowStart+position));return true;});
            post(()->{if(!disposed&&isAttachedToWindow()){requestFocusFromTouch();selectLogical(logicalSelection);}});
        }
        void setEditingAnimation(boolean enabled){
            if(enabled&&editWiggle!=null)return;
            if(editWiggle!=null){editWiggle.cancel();editWiggle=null;}
            for(int i=0;i<getChildCount();i++)((BannerCard)getChildAt(i)).tile.setRotation(0);
            if(!enabled||!isAttachedToWindow()||!ValueAnimator.areAnimatorsEnabled())return;
            // One shared clock keeps the small visible window moving only while editing.
            editWiggle=ValueAnimator.ofFloat(0f,1f);
            editWiggle.setDuration(700);
            editWiggle.setInterpolator(new android.view.animation.LinearInterpolator());
            editWiggle.setRepeatCount(ValueAnimator.INFINITE);
            editWiggle.addUpdateListener(animation->{
                float phase=(float)animation.getAnimatedValue();
                for(int i=0;i<getChildCount();i++){
                    float offset=((windowStart+i)%4)*.19f;
                    ((BannerCard)getChildAt(i)).tile.setRotation((float)(1.15*Math.sin((phase+offset)*2*Math.PI)));
                }
            });
            editWiggle.start();
        }
        void selectLogical(int position){
            selectLogical(position,false);
        }
        private void selectLogical(int position,boolean orderChanged){
            if(display.isEmpty())return;
            position=Math.max(0,Math.min(display.size()-1,position));
            logicalSelection=position;int start=position/4*4;
            if(start!=windowStart){
                int direction=Integer.signum(start-windowStart);
                finishRowMotion();
                if(ValueAnimator.areAnimatorsEnabled()&&isLaidOut()){
                    rowDirection=direction;
                    // Retain only the departing sharp row. Artwork bitmaps are shared;
                    // no screenshot, per-frame blur, or second full grid is created.
                    if(direction>0){
                        departing=new FrameLayout(MainActivity.this);
                        departing.layout(0,0,getWidth(),getHeight());
                        for(int i=0;i<Math.min(4,getChildCount());i++){
                            BannerCard source=(BannerCard)getChildAt(i),copy=new BannerCard();
                            copy.bind(source.app, source.pin.getVisibility() == VISIBLE, source);
                            copy.setPreview(false);
                            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(source.getWidth(),source.getHeight());
                            lp.leftMargin=source.getLeft();lp.topMargin=source.getTop();departing.addView(copy,lp);
                        }
                        departing.measure(View.MeasureSpec.makeMeasureSpec(getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(getHeight(),View.MeasureSpec.EXACTLY));
                        departing.layout(0,0,getWidth(),getHeight());getOverlay().add(departing);
                    }
                }
                windowStart=start;adapter.notifyDataSetChanged();
            }else if(orderChanged){
                finishRowMotion();
                adapter.notifyDataSetChanged();
            }
            if(isInTouchMode())requestFocusFromTouch();
            super.setSelection(position-windowStart);focusApp(display.get(position));
        }
        private void togglePickup(){
            if(picked==null){
                if(logicalSelection<pinnedCount){
                    toast("Pinned apps stay in the first row.");
                    return;
                }
                picked=display.get(logicalSelection);
                beforePickup=new ArrayList<>(display);
            }else{
                if(!display.equals(beforePickup))saveHomeOrder(display);
                picked=null;
                beforePickup=null;
            }
            updateEditUi();
        }
        void cancelPickup(){
            if(picked==null)return;
            int original=beforePickup.indexOf(picked);
            display.clear();
            display.addAll(beforePickup);
            picked=null;
            beforePickup=null;
            selectLogical(original,true);
            updateEditUi();
        }
        private void movePicked(int destination){
            int current=display.indexOf(picked);
            if(destination==current||destination<pinnedCount||destination>=display.size())return;
            display.remove(current);
            display.add(destination,picked);
            selectLogical(destination,true);
        }
        private boolean isOk(int code){
            return code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_NUMPAD_ENTER;
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            super.onLayout(changed,l,t,r,b);
            refreshGridFocus(this);
            if(rowDirection==0)return;
            int direction=rowDirection;rowDirection=0;
            for(int i=0;i<getChildCount();i++)Ui.glide(getChildAt(i),0,dp(132)*direction);
            if(departing!=null){
                FrameLayout old=departing;
                old.animate().translationY(-dp(132)).setDuration(Ui.TRAVEL_MS).setInterpolator(Ui.EASE).withEndAction(()->{
                    getOverlay().remove(old);if(departing==old)departing=null;
                }).start();
            }
        }
        private void finishRowMotion(){
            rowDirection=0;
            if(departing!=null){departing.animate().cancel();getOverlay().remove(departing);departing=null;}
            for(int i=0;i<getChildCount();i++){View child=getChildAt(i);child.animate().cancel();child.setTranslationY(0);}
        }
        @Override protected void onDetachedFromWindow(){setEditingAnimation(false);finishRowMotion();super.onDetachedFromWindow();}
        private void moveRow(int direction){
            int row=logicalSelection/4+direction;
            if(row<0||row>(display.size()-1)/4)return;
            int destination=Math.min(row*4+logicalSelection%4,display.size()-1);
            if(picked==null)selectLogical(destination);else movePicked(destination);
        }
        @Override public boolean onKeyDown(int code,KeyEvent event){
            if(editing&&isOk(code))return true;
            if(picked!=null){
                int position=display.indexOf(picked);
                switch(code){
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if(position%4>0)movePicked(position-1);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if(position%4<3)movePicked(position+1);
                        return true;
                    case KeyEvent.KEYCODE_MOVE_HOME:
                    case KeyEvent.KEYCODE_MOVE_END:
                        return true;
                    default:
                        break;
                }
            }
            if(code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_PAGE_DOWN){moveRow(1);return true;}
            if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_PAGE_UP){
                if(logicalSelection>=4||picked!=null){moveRow(-1);return true;}
            }
            if(code==KeyEvent.KEYCODE_MOVE_HOME){selectLogical(0);return true;}
            if(code==KeyEvent.KEYCODE_MOVE_END){selectLogical(display.size()-1);return true;}
            return super.onKeyDown(code,event);
        }
        @Override public boolean onKeyUp(int code,KeyEvent event){
            if(editing&&isOk(code)){
                if(!event.isCanceled())togglePickup();
                return true;
            }
            return super.onKeyUp(code,event);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event){
            int action=event.getActionMasked();
            if(action==android.view.MotionEvent.ACTION_DOWN){touchX=event.getX();touchY=event.getY();swiping=false;}
            if(action==android.view.MotionEvent.ACTION_MOVE&&Math.abs(event.getY()-touchY)>dp(12)&&Math.abs(event.getY()-touchY)>Math.abs(event.getX()-touchX)){
                if(!swiping){android.view.MotionEvent cancel=android.view.MotionEvent.obtain(event);cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);super.onTouchEvent(cancel);cancel.recycle();swiping=true;}
                return true;
            }
            if(action==android.view.MotionEvent.ACTION_UP&&swiping){if(Math.abs(event.getY()-touchY)>dp(28))moveRow(event.getY()<touchY?1:-1);swiping=false;return true;}
            if(action==android.view.MotionEvent.ACTION_CANCEL)swiping=false;
            return swiping||super.onTouchEvent(event);
        }
        @Override public boolean onGenericMotionEvent(android.view.MotionEvent event){
            if(event.getActionMasked()==android.view.MotionEvent.ACTION_SCROLL){float scroll=event.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL);if(scroll!=0){moveRow(scroll<0?1:-1);return true;}}
            return super.onGenericMotionEvent(event);
        }
    }

    private final class BannerCard extends FrameLayout {
        private static final float REST_SCALE=.90f, FOCUSED_SCALE=1.10f;
        final FrameLayout tile,face;final ImageView banner,icon,preview,pin;final TextView label;
        App app;boolean previewRow,previewPosted,lit;int previewToken;
        BannerCard(){
            super(MainActivity.this);setClipChildren(false);
            tile=new FrameLayout(MainActivity.this);
            addView(tile,new FrameLayout.LayoutParams(-1,-1));
            face=new FrameLayout(MainActivity.this);face.setClipToOutline(true);
            // Leave headroom for the enlarged, raised face even at the viewport edges.
            FrameLayout.LayoutParams faceParams=new FrameLayout.LayoutParams(-1,-1);faceParams.setMargins(dp(11),dp(14+PIN_HEADROOM_DP),dp(11),dp(4));
            tile.addView(face,faceParams);
            LinearLayout fallback=row();fallback.setGravity(Gravity.CENTER);fallback.setPadding(dp(14),0,dp(14),0);
            icon=new ImageView(MainActivity.this);icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);fallback.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));
            label=text("",24,Ui.INK);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);label.setTypeface(Typeface.create("sans-serif-medium",0));
            LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(-2,-2);l.setMarginStart(dp(10));fallback.addView(label,l);face.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
            banner=new ImageView(MainActivity.this);banner.setScaleType(ImageView.ScaleType.CENTER_CROP);banner.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);face.addView(banner,new FrameLayout.LayoutParams(-1,-1));
            preview=new ImageView(MainActivity.this);preview.setScaleType(ImageView.ScaleType.FIT_XY);preview.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);preview.setVisibility(INVISIBLE);face.addView(preview,new FrameLayout.LayoutParams(-1,-1));
            pin = new ImageView(MainActivity.this);
            pin.setImageResource(R.drawable.ic_favorite_pin);
            pin.setContentDescription("Pinned");
            pin.setVisibility(INVISIBLE);
            FrameLayout.LayoutParams pinParams = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP | Gravity.LEFT);
            pinParams.leftMargin = dp(13);
            pinParams.topMargin = dp(14+PIN_HEADROOM_DP-21);
            tile.addView(pin, pinParams);
            setOnHoverListener((v,event)->{
                if(event.getActionMasked()==android.view.MotionEvent.ACTION_HOVER_ENTER&&!previewRow&&getParent() instanceof HomeRowsGrid){
                    HomeRowsGrid grid=(HomeRowsGrid)getParent();
                    if(grid.picked==null){grid.requestFocusFromTouch();grid.setSelection(grid.getPositionForView(this));}
                }
                return false;
            });
        }
        private void bind(App value,boolean pinned,BannerCard source){
            boolean changed=app==null||!app.key().equals(value.key());
            app=value;if(changed){previewToken++;preview.setImageDrawable(null);preview.setVisibility(INVISIBLE);tile.animate().cancel();lit=false;settleLift();}
            setTag(value.pkg());setContentDescription(value.label);label.setText(value.pkg().equals("com.netflix.mediaclient")?"NETFLIX":value.label);
            pin.setVisibility(pinned ? VISIBLE : INVISIBLE);
            boolean netflix=value.pkg().equals("com.netflix.mediaclient");
            icon.setVisibility(netflix?GONE:VISIBLE);label.setTextColor(netflix?Color.rgb(218,8,27):Ui.INK);
            label.setTypeface(Typeface.create(netflix?"sans-serif-condensed":"sans-serif-medium",netflix?Typeface.BOLD:Typeface.NORMAL));label.setTextSize(netflix?36:24);
            int fill=netflix?Color.rgb(247,246,244):tileColor(value);
            face.setBackground(Ui.surface(MainActivity.this,fill,Color.TRANSPARENT,12));
            if(source==null){loadIcon(value,icon);loadBanner(value,banner);}else copyArtwork(source);
            setFocusable(false);setClickable(false);setLongClickable(false);
            if(getLayoutParams()==null)setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(118+PIN_HEADROOM_DP)));
            paintSelection(isSelected());refreshPreview();
        }
        void setPreview(boolean value){
            if(value!=previewRow){
                previewRow=value;
                previewToken++;
                int headroom=value?0:PIN_HEADROOM_DP;
                ViewGroup.LayoutParams cardParams=getLayoutParams();
                cardParams.height=dp(118+headroom);
                setLayoutParams(cardParams);
                FrameLayout.LayoutParams faceParams=(FrameLayout.LayoutParams)face.getLayoutParams();
                faceParams.topMargin=dp(14+headroom);
                face.setLayoutParams(faceParams);
            }
            if(!value){preview.setVisibility(INVISIBLE);preview.setImageDrawable(null);}else refreshPreview();
            paintSelection(isSelected());
        }
        private void paintSelection(boolean selected){
            if(tile==null)return;
            boolean active=selected&&!previewRow&&getParent() instanceof GridView&&((GridView)getParent()).hasFocus();
            if(lit!=active){lit=active;Ui.lift(tile,active?FOCUSED_SCALE:REST_SCALE,active?-dp(8):0);}
            else if(!tile.isLaidOut())settleLift();
        }
        private void settleLift(){tile.setScaleX(lit?FOCUSED_SCALE:REST_SCALE);tile.setScaleY(lit?FOCUSED_SCALE:REST_SCALE);tile.setTranslationY(lit?-dp(8):0);}
        void copyArtwork(BannerCard source){
            // Duplicate Drawable state, while sharing its immutable decoded bitmap.
            for(ImageView[] pair:new ImageView[][]{{banner,source.banner},{icon,source.icon}}){
                android.graphics.drawable.Drawable d=pair[1].getDrawable();
                if(d!=null&&d.getConstantState()!=null)pair[0].setImageDrawable(d.getConstantState().newDrawable(getResources()));
            }
        }
        @Override public void setSelected(boolean selected){super.setSelected(selected);paintSelection(selected);}
        @Override protected void onDetachedFromWindow(){
            // GridView can reattach a selected child without another layout or
            // setSelected call. Preserve its settled focus state while recycling.
            tile.animate().cancel();settleLift();
            super.onDetachedFromWindow();
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();paintSelection(isSelected());}
        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
            super.onLayout(changed,left,top,right,bottom);
            // Scale the artwork and pin together around the artwork's original center.
            tile.setPivotX(face.getLeft()+face.getWidth()/2f);
            tile.setPivotY(face.getTop()+face.getHeight()/2f);
            paintSelection(isSelected());
            refreshPreview();
        }
        void refreshPreview(){
            if(!previewRow||app==null||previewPosted||disposed)return;
            previewPosted=true;
            post(()->{
                previewPosted=false;if(disposed||!previewRow||app==null||face.getWidth()==0||face.getHeight()==0)return;
                String key=app.key();Bitmap cached=previewImages.get(key);
                if(cached!=null){preview.setImageBitmap(cached);preview.setVisibility(VISIBLE);return;}
                android.graphics.drawable.Drawable art=banner.getDrawable();
                Bitmap sharp=art instanceof android.graphics.drawable.BitmapDrawable?((android.graphics.drawable.BitmapDrawable)art).getBitmap():null;
                boolean ready=sharp!=null||(noBanner.contains(key+"#banner")&&(icon.getDrawable()!=null||icon.getVisibility()==GONE));
                if(sharp==null&&(face.isLayoutRequested()||label.isLayoutRequested()||(icon.getVisibility()!=GONE&&icon.isLayoutRequested())))return;
                final Bitmap source;final boolean ownsSource;
                if(sharp!=null){source=sharp;ownsSource=false;}
                else{
                    source=Bitmap.createBitmap(120,68,Bitmap.Config.ARGB_8888);ownsSource=true;
                    Canvas canvas=new Canvas(source);canvas.scale(120f/face.getWidth(),68f/face.getHeight());
                    int visibility=preview.getVisibility();
                    preview.setVisibility(INVISIBLE);
                    face.draw(canvas);
                    preview.setVisibility(visibility);
                }
                final int token=++previewToken;WeakReference<BannerCard> owner=new WeakReference<>(this);
                worker.execute(()->{
                    Bitmap thumbnail=Bitmap.createScaledBitmap(source,120,68,true);
                    int[] pixels=new int[120*68];thumbnail.getPixels(pixels,0,120,0,0,120,68);
                    if(thumbnail!=source)thumbnail.recycle();if(ownsSource)source.recycle();
                    int[] blurred=PreviewBlur.apply(pixels,120,68,2,2);Bitmap image=Bitmap.createBitmap(blurred,120,68,Bitmap.Config.ARGB_8888);
                    runOnUiThread(()->{
                        if(disposed)return;if(ready)previewImages.put(key,image);
                        BannerCard card=owner.get();if(card!=null&&card.previewRow&&card.previewToken==token&&card.app!=null&&key.equals(card.app.key())){card.preview.setImageBitmap(image);card.preview.setVisibility(VISIBLE);}
                    });
                });
            });
        }
    }
    private void imageReady(ImageView target){
        android.view.ViewParent parent=target.getParent();
        while(parent instanceof View){if(parent instanceof BannerCard){((BannerCard)parent).refreshPreview();return;}parent=parent.getParent();}
    }
    private int tileColor(App app){int color=themeColor(app);return Color.rgb(Color.red(color)*2/3,Color.green(color)*2/3,Color.blue(color)*2/3);}
    private int themeColor(App app){
        String pkg=app.pkg();
        if(pkg.contains("netflix"))return 0xFFCB0024;
        if(pkg.contains("youtube"))return 0xFFCE132C;
        if(pkg.contains("videolan"))return 0xFFE67E24;
        if(pkg.contains("jellyfin"))return 0xFF7253C8;
        if(pkg.contains("disney"))return 0xFF1553B6;
        if(pkg.contains("steam"))return 0xFF176A94;
        if(pkg.contains("amazon"))return 0xFF087ABF;
        return appColors.getOrDefault(pkg,0xFF475E91);
    }
    private void focusApp(App app){lastApp=app.pkg();if(hero!=null)hero.setText(app.label);if(backdrop!=null)backdrop.theme(themeColor(app));}
    private void loadBanner(App app,ImageView target){
        String key=app.key()+"#banner";if(key.equals(target.getTag())&&target.getDrawable()!=null)return;target.setTag(key);Bitmap cached=banners.get(key);target.setImageBitmap(cached);
        if(cached!=null||disposed||noBanner.contains(key))return;
        List<WeakReference<ImageView>> waiting=pendingIcons.get(key);if(waiting!=null){waiting.add(new WeakReference<>(target));return;}
        waiting=new ArrayList<>();waiting.add(new WeakReference<>(target));pendingIcons.put(key,waiting);
        worker.execute(()->{
            Bitmap result=null;
            try{android.graphics.drawable.Drawable d=app.info.activityInfo.loadBanner(getPackageManager());
                if(d==null)d=app.info.activityInfo.applicationInfo.loadBanner(getPackageManager());
                if(d!=null){result=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);d.setBounds(0,0,320,180);d.draw(new Canvas(result));}}
            catch(RuntimeException ignored){}
            final Bitmap ready=result;
            runOnUiThread(()->{
                List<WeakReference<ImageView>> targets=pendingIcons.remove(key);if(disposed)return;
                if(ready==null)noBanner.add(key);else banners.put(key,ready);
                if(targets!=null)for(WeakReference<ImageView> ref:targets){ImageView v=ref.get();if(v!=null&&key.equals(v.getTag())){v.setImageBitmap(ready);imageReady(v);}}
            });
        });
    }
    private void loadIcon(App app,ImageView target) {
        String key=app.key();if(key.equals(target.getTag())&&target.getDrawable()!=null)return;target.setTag(key);Bitmap cached=icons.get(key);target.setImageBitmap(cached);
        if(cached!=null||disposed)return;
        List<WeakReference<ImageView>> targets=pendingIcons.get(key);
        if(targets!=null){targets.add(new WeakReference<>(target));return;}
        targets=new ArrayList<>();targets.add(new WeakReference<>(target));pendingIcons.put(key,targets);
        worker.execute(()->{
            Bitmap image=null;
            try{
                android.graphics.drawable.Drawable d=app.info.loadIcon(getPackageManager());
                image=Bitmap.createBitmap(72,72,Bitmap.Config.ARGB_8888);d.setBounds(0,0,72,72);d.draw(new Canvas(image));
            }catch(RuntimeException ignored){/* The app label remains useful if an APK disappears. */}
            final Bitmap ready=image;
            runOnUiThread(()->{
                List<WeakReference<ImageView>> waiting=pendingIcons.remove(key);if(disposed)return;
                if(ready!=null){
                    icons.put(key,ready);int r=0,g=0,b=0,n=0;
                    for(int y=6;y<72;y+=12)for(int x=6;x<72;x+=12){int color=ready.getPixel(x,y);int hi=Math.max(Color.red(color),Math.max(Color.green(color),Color.blue(color))),lo=Math.min(Color.red(color),Math.min(Color.green(color),Color.blue(color)));
                        if(Color.alpha(color)>160&&hi-lo>55&&hi>100){r+=Color.red(color);g+=Color.green(color);b+=Color.blue(color);n++;}}
                    if(n>0)appColors.put(app.pkg(),Color.rgb(r/n,g/n,b/n));
                    if(app.pkg().equals(lastApp)&&backdrop!=null)backdrop.theme(themeColor(app));
                }
                if(waiting!=null)for(WeakReference<ImageView> ref:waiting){ImageView v=ref.get();if(v!=null&&key.equals(v.getTag())){v.setImageBitmap(ready);imageReady(v);}}
            });
        });
    }

    private void showAppOptions(App app) {
        List<App> favorites=favorites();
        boolean pinned=favorites.contains(app);
        LinearLayout menu=sheet(app.label,"APP OPTIONS");
        menu.addView(menuAction("play","Open app",()->openComponent(app.component)),fill(54));gap(menu,8);
        if(!pinned&&favorites.size()==MAX_FAVORITES){
            TextView limit=text(FAVORITES_FULL,12,Ui.MUTED);
            limit.setGravity(Gravity.CENTER_VERTICAL);
            limit.setPadding(dp(14),0,dp(12),0);
            menu.addView(limit,fill(46));
        }else{
            menu.addView(menuAction("pin",pinned?"Remove from favorites":"Add to favorites",()->toggleFavorite(app)),fill(54));
        }
        gap(menu,8);menu.addView(menuAction("settings","App information",()->{
            try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+app.pkg())));}
            catch(android.content.ActivityNotFoundException|SecurityException error){toast("App information unavailable");}
        }),fill(54));gap(menu,18);menu.addView(text("BACK to close",11,Ui.MUTED));showSheet(menu);
    }
    private View menuAction(String icon,String title,Runnable action){return shortcut(icon,title,null,()->{if(sheet!=null)sheet.dismiss();action.run();});}
    private LinearLayout sheet(String title,String eyebrow){
        if(sheet!=null)sheet.dismiss();
        sheet=new Dialog(this);sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout menu=column();menu.setPadding(dp(26),dp(24),dp(26),dp(24));menu.setBackground(Ui.surface(this,Ui.PANEL,Ui.LINE,22));
        TextView tag=text(eyebrow,10,Ui.ACCENT);tag.setLetterSpacing(0.13f);menu.addView(tag);gap(menu,8);
        TextView heading=text(title,28,Ui.INK);heading.setMaxLines(2);menu.addView(heading);gap(menu,22);return menu;
    }
    private void showSheet(LinearLayout content){
        Dialog current=sheet;current.setContentView(content);Window w=current.getWindow();
        w.setWindowAnimations(0);w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setDimAmount(0.65f);
        current.setOnDismissListener(d->{
            if(sheet==d){sheet=null;aboutLauncherStatus=null;}
        });current.show();
        w.setLayout(dp(360),-2);w.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        android.view.WindowManager.LayoutParams p=w.getAttributes();p.x=dp(28);w.setAttributes(p);
        for(int i=0;i<content.getChildCount();i++)if(content.getChildAt(i).isFocusable()){content.getChildAt(i).requestFocus();break;}
    }
    private void showAbout(){
        LinearLayout info=sheet("About Vibe Home","VERSION 0.14.8");
        aboutLauncherStatus=text("",14,Ui.MUTED);
        refreshLauncherStatus();
        info.addView(aboutLauncherStatus);
        gap(info,14);
        info.addView(shortcut("play","Use Vibe Home",null,homeSwitcher::useVibe),fill(54));
        gap(info,8);
        info.addView(shortcut("back","Use original launcher",null,homeSwitcher::useOriginal),fill(54));
        gap(info,18);
        info.addView(text("Inspired by CleanYourTV and Tellyscope.",11,Ui.MUTED));
        gap(info,12);
        info.addView(button("Back",()->{if(sheet!=null)sheet.dismiss();},Ui.RAISED),fill(42));
        showSheet(info);
    }
    private void refreshLauncherStatus(){
        if(aboutLauncherStatus!=null)aboutLauncherStatus.setText("Default launcher  ·  "+homeSwitcher.status());
    }
    private void toggleFavorite(App app){
        List<App> active=favorites();
        boolean removed=active.remove(app);
        if(!removed){
            if(active.size()>=MAX_FAVORITES){toast(FAVORITES_FULL);return;}
            active.add(app);
        }
        List<String> order=new ArrayList<>();
        for(App favorite:active)order.add(favorite.pkg());
        // Preserve unavailable preferences, but release any installed overflow from an older build.
        Set<String> selected=new LinkedHashSet<>(pinnedPackages());
        for(App installed:apps)if(isHomeApp(installed))selected.remove(installed.pkg());
        selected.addAll(order);
        prefs.edit().putStringSet("favorites",selected).putString("favorite_order",String.join("|",order)).apply();
        lastApp=app.pkg();
        drawPage();
        toast(removed?"Removed from favorites":"Added to favorites");
    }

    private LinearLayout shortcut(String glyph,String title,String subtitle,Runnable action){
        LinearLayout card=row();card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(14),dp(8),dp(12),dp(8));
        Ui.Glyph icon=new Ui.Glyph(this,glyph);card.addView(icon,new LinearLayout.LayoutParams(dp(26),dp(28)));
        LinearLayout words=column();words.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(0,-1,1);wp.setMarginStart(dp(10));card.addView(words,wp);
        TextView name=text(title,14,Ui.INK);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);words.addView(name);
        TextView sub=null;if(subtitle!=null){sub=text(subtitle,10,Ui.MUTED);sub.setSingleLine(true);sub.setEllipsize(TextUtils.TruncateAt.END);words.addView(sub);}
        makeCard(card,icon,sub==null?new TextView[]{name}:new TextView[]{name,sub},action);return card;
    }
    private void makeCard(View card,Ui.Glyph icon,TextView[] words,Runnable action){
        card.setFocusable(true);card.setClickable(true);card.setBackground(Ui.focus(this,Ui.PANEL));card.setOnClickListener(v->action.run());
        int[] colors=new int[words.length];for(int i=0;i<words.length;i++)colors[i]=words[i].getCurrentTextColor();
        card.setOnFocusChangeListener((v,f)->{Ui.lift(icon,f?1.08f:1f);icon.active(f);for(int i=0;i<words.length;i++)words[i].setTextColor(f?Ui.DARK:colors[i]);});
        Ui.hoverFocus(card);
    }
    private void openComponent(ComponentName c){openComponent(c,true);}
    private void openComponent(ComponentName c,boolean task){
        try{Intent intent=new Intent(Intent.ACTION_MAIN).setComponent(c);if(task)intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);}
        catch(android.content.ActivityNotFoundException|SecurityException error){toast("Unable to open this app. It may have been removed or restricted.");}
    }

    private TextView button(String title,Runnable action,int rest){TextView b=text(title,13,Ui.INK);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(6),dp(12),dp(6));Ui.button(b,rest);b.setOnClickListener(v->action.run());return b;}
    private TextView text(String title,int size,int color){TextView v=new TextView(this);v.setText(title);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);return v;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);return v;}
    private int dp(int n){return Ui.dp(this,n);}
    private void gap(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private LinearLayout.LayoutParams fill(int height){return new LinearLayout.LayoutParams(-1,dp(height));}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}

    @Override public boolean onKeyDown(int keyCode,KeyEvent event){
        if(keyCode==KeyEvent.KEYCODE_MENU){
            if(editing||event.getRepeatCount()>0)return true;
            View focused=getCurrentFocus();
            if(focused instanceof GridView){Object selected=((GridView)focused).getSelectedItem();if(selected instanceof App)showAppOptions((App)selected);}
            return true;
        }
        return super.onKeyDown(keyCode,event);
    }
    @Override public void onBackPressed(){
        if(editing){
            HomeRowsGrid grid=(HomeRowsGrid)firstTile;
            if(grid.picked!=null)grid.cancelPickup();else finishEdit();
        }else if(firstTile!=null)firstTile.requestFocusFromTouch();
    }
    @Override protected void onDestroy(){disposed=true;inputs.dismiss();settingsMenu.dismiss();if(sheet!=null)sheet.dismiss();worker.shutdownNow();pendingIcons.clear();icons.evictAll();banners.evictAll();previewImages.evictAll();super.onDestroy();}
}
