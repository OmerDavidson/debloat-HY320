package com.vibehome.launcher;

import java.util.Arrays;

/** Small separable box passes for cached preview thumbnails. No dependencies or render loop. */
final class PreviewBlur {
    private PreviewBlur() {}
    static int[] apply(int[] source,int width,int height,int radius,int passes){
        if(width<1||height<1||source.length!=width*height||radius<1||radius>16||passes<1||passes>4)throw new IllegalArgumentException("Invalid blur bounds");
        int[] pixels=Arrays.copyOf(source,source.length),scratch=new int[pixels.length];
        for(int pass=0;pass<passes;pass++){
            sweep(pixels,scratch,width,height,radius,false);
            sweep(scratch,pixels,width,height,radius,true);
        }
        return pixels;
    }
    private static void sweep(int[] input,int[] output,int width,int height,int radius,boolean vertical){
        int lines=vertical?width:height,length=vertical?height:width,step=vertical?width:1,window=radius*2+1;
        for(int line=0;line<lines;line++){
            int base=vertical?line:line*width,a=0,r=0,g=0,b=0;
            for(int offset=-radius;offset<=radius;offset++){
                int p=input[base+Math.max(0,Math.min(length-1,offset))*step];a+=p>>>24;r+=(p>>>16)&255;g+=(p>>>8)&255;b+=p&255;
            }
            for(int index=0;index<length;index++){
                output[base+index*step]=((a/window)<<24)|((r/window)<<16)|((g/window)<<8)|(b/window);
                int remove=input[base+Math.max(0,index-radius)*step],add=input[base+Math.min(length-1,index+radius+1)*step];
                a+=(add>>>24)-(remove>>>24);r+=((add>>>16)&255)-((remove>>>16)&255);g+=((add>>>8)&255)-((remove>>>8)&255);b+=(add&255)-(remove&255);
            }
        }
    }
}
