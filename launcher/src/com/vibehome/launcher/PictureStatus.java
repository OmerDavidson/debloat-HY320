package com.vibehome.launcher;

import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only H713 picture-service snapshot. No setters or fallback values. */
final class PictureStatus {
    private static final String SERVICE="vendor.aw.homlet.tvsystem.tvserver@1.0::ITvServer";
    static Map<String,String> read(){
        Map<String,String> values=new LinkedHashMap<>();
        try{
            Class<?> parcel=Class.forName("android.os.HwParcel");
            Object binder=Class.forName("android.os.HwBinder").getMethod("getService",String.class,String.class,boolean.class).invoke(null,SERVICE,"default",false);
            if(binder==null)return values;
            String[] names={"Brightness","Contrast","Saturation","Hue","Sharpness"};
            for(int i=0;i<names.length;i++){
                Object request=parcel.getConstructor().newInstance(),reply=parcel.getConstructor().newInstance();
                try{
                    parcel.getMethod("writeInterfaceToken",String.class).invoke(request,SERVICE);
                    parcel.getMethod("writeInt32",int.class).invoke(request,i+1);
                    Class.forName("android.os.IHwBinder").getMethod("transact",int.class,parcel,parcel,int.class).invoke(binder,98,request,reply,0);
                    parcel.getMethod("verifySuccess").invoke(reply);
                    int value=(Integer)parcel.getMethod("readInt32").invoke(reply);
                    if(value>=0&&value<=100)values.put(names[i],String.valueOf(value));
                }finally{parcel.getMethod("releaseTemporaryStorage").invoke(request);parcel.getMethod("release").invoke(reply);}
            }
        }catch(Exception unavailable){/* A denied read is unavailable, never a presumed default. */}
        return values;
    }
}
