package com.vibehome.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native projection preview/editor. This version deliberately contains no hardware setters. */
public final class ProjectionActivity extends Activity {
    private static final int BG = Ui.BG, PANEL = Ui.PANEL;
    private static final int INK = Ui.INK, MUTED = Ui.MUTED, MINT = Ui.ACCENT;
    private static final String[] MODES = {"Front · table", "Rear · table", "Front · ceiling", "Rear · ceiling"};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ProjectionState state;
    private LinearLayout body;
    private TextView status, review;
    private Picture preview;
    private String tab = "zoom";
    private int zoom = 100, mode = -1;
    private int[] corners;
    private boolean destroyed;
    private final java.util.List<TextView> tabButtons=new java.util.ArrayList<>();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout loading = column(); loading.setPadding(dp(36),dp(30),dp(36),dp(30)); loading.setBackgroundColor(BG);
        loading.addView(text("Reading projector status…", 22, INK)); setContentView(loading);
        worker.execute(() -> {
            try {
                ProjectionState result = ProjectionState.read();
                runOnUiThread(() -> { if (!destroyed) { state=result; corners=result.geometry == null ? null : result.geometry.clone(); draw(); } });
            } catch (Exception error) {
                runOnUiThread(() -> { if (!destroyed) { loading.removeAllViews(); loading.addView(text("Projector status is unavailable. Back returns home.",18,INK)); } });
            }
        });
    }

    private void draw() {
        LinearLayout root = column(); root.setPadding(dp(36),dp(22),dp(36),dp(18)); root.setBackgroundColor(BG);
        LinearLayout heading = row(); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("Projector",28,INK),new LinearLayout.LayoutParams(0,dp(48),1));
        TextView back = button("Back to home",this::finish); heading.addView(back); root.addView(heading);
        root.addView(text("Find your picture. Preview every adjustment first.",13,MUTED)); gap(root,18);
        LinearLayout tabs = row();
        for (String item : new String[]{"mode","zoom","keystone"}) {
            String title = item.equals("mode") ? "Projection mode" : item.equals("zoom") ? "Screen size" : "Keystone";
            TextView choice = button(title, () -> { tab=item; renderControls(); });
            choice.setTag(item);tabButtons.add(choice);
            tabs.addView(choice,new LinearLayout.LayoutParams(0,dp(40),1));
        }
        root.addView(tabs); gap(root,14);
        LinearLayout split = row();
        LinearLayout illustration = column(); illustration.setPadding(0,0,dp(24),0);
        preview = new Picture(); illustration.addView(preview,new LinearLayout.LayoutParams(-1,dp(250)));
        status=text("",12,MUTED); illustration.addView(status);
        gap(illustration,8);
        illustration.addView(text("Preview only · your projected image stays unchanged",12,MINT));
        split.addView(illustration,new LinearLayout.LayoutParams(0,-1,1));
        ScrollView controls = new ScrollView(this); controls.setFillViewport(false);
        body=column(); body.setPadding(dp(8),0,dp(6),dp(8)); controls.addView(body);
        split.addView(controls,new LinearLayout.LayoutParams(0,-1,1));
        root.addView(split,new LinearLayout.LayoutParams(-1,0,1));
        gap(root,10);
        LinearLayout bottom=row();
        review=button("Review draft",this::reviewDraft); bottom.addView(review,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView reset=button("Discard preview",() -> { zoom=100;mode=-1;corners=state.geometry==null?null:state.geometry.clone();renderControls(); });
        bottom.addView(reset,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView vendor=button("Original controls ↗",this::originalControls);
        bottom.addView(vendor,new LinearLayout.LayoutParams(0,dp(42),1));root.addView(bottom);
        setContentView(root);renderControls();back.requestFocus();
    }

    private void renderControls() {
        for(TextView choice:tabButtons)choice.setTextColor(Ui.ink(tab.equals(choice.getTag())?MINT:INK));
        body.removeAllViews();
        if (!state.compatible()) {
            body.addView(text("This firmware has not been verified for native control.",18,INK));
            review.setEnabled(false);status.setText("Device information available; hardware changes unavailable.");return;
        }
        if (tab.equals("mode")) {
            body.addView(text("Where is your projector?",19,INK));gap(body,8);
            String reported=state.panelMode>=0?MODES[state.panelMode]:state.value("persist.sys.panelvalue");
            body.addView(text(reported.isEmpty()?"Current mode is unavailable to this app.":"Current: "+reported,12,MUTED));
            for (int i=0;i<MODES.length;i++) {
                final int index=i;
                TextView option=button((mode==i?"●  ":"○  ")+MODES[i],() -> {mode=index;renderControls();});
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));p.topMargin=dp(7);body.addView(option,p);
            }
            gap(body,10);body.addView(text("Selecting a position changes this preview only.",12,MUTED));
        } else if (state.geometry==null) {
            body.addView(text("Current corner geometry is unavailable. No default picture is assumed.",18,INK));
        } else if (tab.equals("zoom")) {
            body.addView(text("Size without losing your corners",19,INK));gap(body,8);
            body.addView(text("100% means your current picture size. Scaling keeps its existing shape.",13,MUTED));gap(body,14);
            slider("Relative size",80,120,zoom,value -> {zoom=value;update();});
            gap(body,12);body.addView(text(deviceSizeLabel(),12,MUTED));
        } else {
            body.addView(text("Four-corner correction",19,INK));
            body.addView(text("Offsets move each edge inward. Scroll for the lower corners.",12,MUTED));gap(body,10);
            String[] names={"Top left", "Top right", "Bottom right", "Bottom left"};int[] indexes={2,4,6,0};
            for(int n=0;n<names.length;n++) {
                body.addView(text(names[n],15,MINT));final int index=indexes[n];
                slider("Horizontal inset",0,450,corners[index],v -> {corners[index]=v;update();});
                slider("Vertical inset",0,450,corners[index+1],v -> {corners[index+1]=v;update();});gap(body,6);
            }
        }
        update();
    }

    private interface Changed { void value(int value); }
    private void slider(String title,int min,int max,int initial,Changed changed) {
        TextView label=text(sliderLabel(title,initial),13,INK);body.addView(label);
        SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(initial-min);bar.setKeyProgressIncrement(1);
        body.addView(bar,new LinearLayout.LayoutParams(-1,dp(36)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int progress,boolean user){if(user){int v=progress+min;label.setText(sliderLabel(title,v));changed.value(v);}}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });
    }
    private String sliderLabel(String title,int value){
        return title+": "+(title.equals("Relative size")?value+"%":String.format(java.util.Locale.getDefault(),"%.1f%%",value/10f));
    }
    private String deviceSizeLabel(){
        String raw=state.value("persist.vendor.overscan.main");String[] parts=raw.replace("overscan ","").split(",");
        if(parts.length==4&&parts[0].matches("[0-9]+")&&parts[0].equals(parts[1])&&parts[0].equals(parts[2])&&parts[0].equals(parts[3]))
            return "Current device size setting: "+parts[0]+"%. This preview starts from the picture you already see.";
        return "Your current picture uses individual edge adjustments. The preview keeps its shape.";
    }

    private int[] desired() {
        if (state.geometry==null)return null;
        if (tab.equals("keystone")){ProjectionGeometry.validate(corners);return corners.clone();}
        return ProjectionGeometry.scale(state.geometry,tab.equals("zoom")?zoom:100);
    }
    private void update() {
        boolean changed=false;
        try {
            int[] wanted=desired();
            preview.shape=wanted;preview.mirror=mode;preview.invalidate();
            changed=tab.equals("mode")?mode>=0:wanted!=null&&!Arrays.equals(wanted,state.geometry);
            status.setText("HY320 · 1920 × 1080\n"+(tab.equals("mode")&&mode>=0?MODES[mode]:"Solid outline: preview · faint outline: current"));
        } catch(IllegalArgumentException error){status.setText(error.getMessage());}
        review.setEnabled(changed&&state.compatible());review.setAlpha(review.isEnabled()?1:0.45f);
    }

    private void reviewDraft() {
        try {
            JSONObject draft=new JSONObject();draft.put("schema",1);draft.put("kind",tab);draft.put("created_ms",System.currentTimeMillis());
            draft.put("baseline",state.json());
            if(tab.equals("mode"))draft.put("mode",mode);else draft.put("corners",new JSONArray(desired()));
            String description=tab.equals("mode")?MODES[mode]:tab.equals("zoom")?"Size: "+zoom+"% of the current picture":"New four-corner shape";
            new AlertDialog.Builder(this).setTitle("Review projection draft").setMessage(description+"\n\nThis saves a draft only. Your picture will not change. Applying hardware changes needs the connected computer and your approval.")
                .setNegativeButton("Keep editing",null).setPositiveButton("Save draft",(dialog,which)->{
                    getSharedPreferences("projection_drafts",MODE_PRIVATE).edit().putString("pending",draft.toString()).apply();
                    Toast.makeText(this,"Draft saved. Projected picture unchanged.",Toast.LENGTH_LONG).show();
                }).show();
        } catch(Exception error){Toast.makeText(this,"Unable to prepare this draft.",Toast.LENGTH_LONG).show();}
    }

    private void originalControls(){
        try{startActivity(new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName("com.htc.htcsettings","com.htc.activity.ProjectionActivity3")));}
        catch(android.content.ActivityNotFoundException|SecurityException error){Toast.makeText(this,"Original controls unavailable",Toast.LENGTH_LONG).show();}
    }

    private final class Picture extends View {
        int[] shape;int mirror=-1;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Picture(){super(ProjectionActivity.this);setContentDescription("Draft picture outline; no hardware changes");}
        @Override protected void onDraw(Canvas canvas){
            float w=Math.min(getWidth()-dp(36),(getHeight()-dp(36))*16/9f),h=w*9/16f;
            float left=(getWidth()-w)/2,top=(getHeight()-h)/2;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(Color.rgb(68,88,96));canvas.drawRect(left,top,left+w,top+h,paint);
            if(state.geometry!=null)polygon(canvas,state.geometry,left,top,w,h,Color.rgb(81,107,110),1);
            if(shape!=null)polygon(canvas,shape,left,top,w,h,MINT,2);
            paint.setStyle(Paint.Style.FILL);paint.setColor(INK);paint.setTextSize(dp(15));paint.setTextAlign(Paint.Align.CENTER);
            canvas.save();
            if(mirror==1||mirror==3)canvas.scale(-1,1,left+w/2,top+h/2);
            if(mirror==2||mirror==3)canvas.scale(1,-1,left+w/2,top+h/2);
            canvas.drawText("VIBE",left+w/2,top+h/2+dp(5),paint);canvas.restore();
        }
        private void polygon(Canvas c,int[] values,float x,float y,float w,float h,int color,int stroke){
            int[][] p=ProjectionGeometry.points(values);Path path=new Path();
            for(int i=0;i<4;i++){float px=x+w*p[i][0]/1000f,py=y+h*p[i][1]/1000f;if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}path.close();
            paint.setStyle(Paint.Style.STROKE);paint.setColor(color);paint.setStrokeWidth(dp(stroke));c.drawPath(path,paint);
        }
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);return v;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void gap(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private TextView button(String label,Runnable action){
        TextView b=text(label,13,INK);b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setClickable(true);
        Ui.button(b,PANEL);b.setOnClickListener(v->action.run());return b;
    }
    @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();super.onDestroy();}
}
