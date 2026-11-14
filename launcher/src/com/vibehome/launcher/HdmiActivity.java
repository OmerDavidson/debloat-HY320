package com.vibehome.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

/** Displays a passthrough input through the public Android TV API. */
public final class HdmiActivity extends Activity {
    static final String INPUT_ID="input_id";
    private TvView video;
    private String inputId;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        inputId=getIntent().getStringExtra(INPUT_ID);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        video=new TvView(this);root.addView(video,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
    }
    @Override protected void onStart(){
        super.onStart();
        TvInputManager manager=(TvInputManager)getSystemService(TV_INPUT_SERVICE);
        if(inputId==null||manager==null||manager.getTvInputInfo(inputId)==null){unavailable();return;}
        try{video.tune(inputId,TvContract.buildChannelUriForPassthroughInput(inputId));}
        catch(IllegalArgumentException|SecurityException error){unavailable();}
    }
    private void unavailable(){Toast.makeText(this,"HDMI input unavailable",Toast.LENGTH_SHORT).show();finish();}
    @Override protected void onStop(){video.reset();super.onStop();}
}
