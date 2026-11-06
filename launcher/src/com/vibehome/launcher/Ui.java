package com.vibehome.launcher;

import android.content.Context;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/** Shared native design tokens and short, interruptible property animations. */
final class Ui {
    static final int BG = Color.rgb(16, 21, 23);
    static final int PANEL = Color.rgb(28, 35, 38);
    static final int RAISED = Color.rgb(38, 47, 49);
    static final int LINE = Color.rgb(56, 67, 68);
    static final int INK = Color.rgb(243, 244, 233);
    static final int MUTED = Color.rgb(171, 184, 182);
    static final int ACCENT = Color.rgb(213, 237, 174);
    static final int DARK = Color.rgb(27, 38, 24);
    static final int FOCUS = Color.rgb(247, 249, 252);
    static final PathInterpolator EASE = new PathInterpolator(.22f, .8f, .25f, 1f);
    static final int TRAVEL_MS = 220, FOCUS_MS = 140;
    private Ui() {}

    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable surface(Context c, int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(c, radius));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(c, 1), stroke);
        return d;
    }
    static StateListDrawable focus(Context c, int rest) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, surface(c, ACCENT, ACCENT, 14));
        d.addState(new int[]{android.R.attr.state_focused}, surface(c, FOCUS, Color.TRANSPARENT, 14));
        d.addState(new int[]{android.R.attr.state_selected}, surface(c, ACCENT, ACCENT, 14));
        d.addState(new int[]{}, surface(c, rest, Color.TRANSPARENT, 14));
        return d;
    }
    static ColorStateList ink(int normal) {
        return new ColorStateList(new int[][]{
            {android.R.attr.state_pressed}, {android.R.attr.state_focused}, {}
        }, new int[]{DARK, DARK, normal});
    }
    static void button(TextView view, int rest) {
        view.setFocusable(true); view.setClickable(true); view.setTextColor(ink(INK));
        view.setBackground(focus(view.getContext(), rest));
        view.setOnFocusChangeListener((v, focused)->lift(v, focused?1.035f:1f));
        hoverFocus(view);
    }

    static void hoverFocus(View view) {
        view.setOnHoverListener((v,event)->{if(event.getActionMasked()==android.view.MotionEvent.ACTION_HOVER_ENTER)v.requestFocusFromTouch();return false;});
    }
    static void lift(View view, float scale) {
        lift(view,scale,0);
    }
    static void lift(View view, float scale, float rise) {
        view.animate().cancel();
        if(!ValueAnimator.areAnimatorsEnabled()||!view.isLaidOut()) {view.setScaleX(scale);view.setScaleY(scale);view.setTranslationY(rise);return;}
        view.animate().scaleX(scale).scaleY(scale).translationY(rise).setDuration(FOCUS_MS).setInterpolator(EASE).start();
    }
    static void glide(View view, float x, float y) {
        view.animate().cancel();
        view.setTranslationX(0);view.setTranslationY(0);
        if(!ValueAnimator.areAnimatorsEnabled())return;
        view.setTranslationX(x);view.setTranslationY(y);
        view.animate().translationX(0).translationY(0).setDuration(TRAVEL_MS).setInterpolator(EASE).start();
    }

    /** Tiny independently drawn line icons. Drawing objects are allocated once per view. */
    static final class Glyph extends View {
        private final String kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private boolean active;
        Glyph(Context context, String value) { super(context); kind=value; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void active(boolean value) { if(active!=value){active=value;invalidate();} }
        @Override protected void onDraw(Canvas c) {
            c.save(); c.translate((getWidth()-Ui.dp(getContext(),24))/2f,(getHeight()-Ui.dp(getContext(),24))/2f);
            float s=Ui.dp(getContext(),24)/24f;c.scale(s,s);
            paint.setColor(active?DARK:ACCENT);paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            if(kind.equals("apps")) {
                for(int y=4;y<=14;y+=10)for(int x=4;x<=14;x+=10){rect.set(x,y,x+6,y+6);c.drawRoundRect(rect,1,1,paint);}
            } else if(kind.equals("wifi")) {
                rect.set(1,4,23,26);c.drawArc(rect,222,96,false,paint);
                rect.set(6,9,18,21);c.drawArc(rect,222,96,false,paint);
                paint.setStyle(Paint.Style.FILL);c.drawCircle(12,19,1.5f,paint);
            } else if(kind.equals("bluetooth")) {
                path.reset();path.moveTo(7,7);path.lineTo(17,17);path.lineTo(12,21);path.lineTo(12,3);path.lineTo(17,7);path.lineTo(7,17);c.drawPath(path,paint);
            } else if(kind.equals("projection")) {
                path.reset();path.moveTo(5,5);path.lineTo(19,3);path.lineTo(21,20);path.lineTo(3,18);path.close();c.drawPath(path,paint);
                c.drawLine(9,11,15,11,paint);c.drawLine(12,8,12,14,paint);
            } else if(kind.equals("back")) {
                c.drawLine(19,12,5,12,paint);c.drawLine(5,12,11,6,paint);c.drawLine(5,12,11,18,paint);
            } else if(kind.equals("play")) {
                path.reset();path.moveTo(8,5);path.lineTo(19,12);path.lineTo(8,19);path.close();c.drawPath(path,paint);
            } else if(kind.equals("pin")) {
                c.drawLine(12,15,12,22,paint);path.reset();path.moveTo(8,3);path.lineTo(16,3);path.lineTo(15,10);path.lineTo(18,15);path.lineTo(6,15);path.lineTo(9,10);path.close();c.drawPath(path,paint);
            } else {
                for(int i=0;i<3;i++){int y=5+i*7,x=i==1?15:8;c.drawLine(3,y,21,y,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,3,paint);paint.setStyle(Paint.Style.STROKE);}
            }
            c.restore();
        }
    }
}
