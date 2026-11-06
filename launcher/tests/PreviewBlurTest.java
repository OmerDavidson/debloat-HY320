package com.vibehome.launcher;

import java.util.Arrays;

public final class PreviewBlurTest {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        int[] solid=new int[120*68];Arrays.fill(solid,0xFFE01735);
        int[] blurred=PreviewBlur.apply(solid,120,68,2,2);
        check(Arrays.equals(solid,blurred),"Blur must not darken a solid color, including edges");
        check(solid!=blurred,"Source image must remain owned by its caller");
        int[] impulse=new int[9*9];Arrays.fill(impulse,0xFF000000);impulse[4*9+4]=0xFFFFFFFF;
        int[] spread=PreviewBlur.apply(impulse,9,9,1,1);
        check(spread[4*9+4]!=0xFFFFFFFF,"Bright detail should soften");
        check((spread[4*9+3]&255)>0,"Blur should spread detail to neighboring pixels");
        check(spread[4*9+3]==spread[4*9+5]&&spread[3*9+4]==spread[5*9+4],"Blur should be symmetric");
        check(impulse[4*9+4]==0xFFFFFFFF,"Source must not be modified");
        for(int p:spread)check((p>>>24)==255,"Opaque images must stay opaque");
        boolean rejected=false;try{PreviewBlur.apply(new int[3],2,2,2,2);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"Inconsistent image bounds must be rejected");
        System.out.println("Preview blur contracts passed: no dimming, immutable input, softened detail, symmetry, alpha and bounds.");
    }
}
