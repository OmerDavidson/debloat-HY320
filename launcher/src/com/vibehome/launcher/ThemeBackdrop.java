package com.vibehome.launcher;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Focus-triggered GPU gradients, with no wallpaper bitmap, animation or timer. */
final class ThemeBackdrop extends Drawable {
    private static final int BASE=Color.rgb(7,10,22);
    private final Paint glow=new Paint(), shade=new Paint();
    private int accent=Color.rgb(90,55,130);
    void theme(int value){if(value!=accent){accent=value;configure();invalidateSelf();}}
    @Override protected void onBoundsChange(Rect bounds){configure();}
    private void configure(){
        Rect r=getBounds();if(r.width()==0||r.height()==0)return;
        glow.setShader(new RadialGradient(r.width()*.32f,r.height()*.24f,r.width()*.66f,
            new int[]{mix(accent,.90f),mix(accent,.46f),BASE},new float[]{0,.43f,1},Shader.TileMode.CLAMP));
        shade.setShader(new LinearGradient(0,0,0,r.height(),new int[]{0x00070A16,0x18070A16,0xD9070A16},new float[]{0,.43f,1},Shader.TileMode.CLAMP));
    }
    private static int mix(int color,float amount){return Color.rgb((int)(Color.red(color)*amount+7*(1-amount)),(int)(Color.green(color)*amount+10*(1-amount)),(int)(Color.blue(color)*amount+22*(1-amount)));}
    @Override public void draw(Canvas c){c.drawColor(BASE);c.drawRect(getBounds(),glow);c.drawRect(getBounds(),shade);}
    @Override public void setAlpha(int alpha){} @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
