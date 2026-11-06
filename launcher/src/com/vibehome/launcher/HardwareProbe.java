package com.vibehome.launcher;

import android.os.IBinder;
import android.os.Parcel;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** One-shot ADB-shell entry point; never started by the launcher. Probe is strictly read-only.
 * A separately approved preview changes only the live transform, restores it, and writes no properties.
 */
public final class HardwareProbe {
    private static final String TV = "vendor.aw.homlet.tvsystem.tvserver@1.0::ITvServer";
    static int readPanelMode() throws Exception {
        Class<?> hw = Class.forName("android.os.HwBinder"), parcel = Class.forName("android.os.HwParcel");
        Object binder=hw.getMethod("getService",String.class,String.class,boolean.class).invoke(null,TV,"default",false);
        if(binder==null)throw new IllegalStateException("Panel service unavailable");
        Object request=parcel.getConstructor().newInstance(),reply=parcel.getConstructor().newInstance();
        try {
            parcel.getMethod("writeInterfaceToken",String.class).invoke(request,TV);
            parcel.getMethod("writeInt32",int.class).invoke(request,3); // MIRROR configuration, getter only.
            Class.forName("android.os.IHwBinder").getMethod("transact",int.class,parcel,parcel,int.class).invoke(binder,128,request,reply,0);
            parcel.getMethod("verifySuccess").invoke(reply);
            return (Integer)parcel.getMethod("readInt32").invoke(reply);
        } finally {
            parcel.getMethod("releaseTemporaryStorage").invoke(request);
            parcel.getMethod("release").invoke(reply);
        }
    }
    private static IBinder surface() throws Exception {
        return (IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"SurfaceFlinger");
    }
    private static void transform(IBinder surface,int[] values) throws Exception {
        ProjectionGeometry.validate(values);Parcel request=Parcel.obtain();
        try {
            request.writeInterfaceToken("android.ui.ISurfaceComposer");
            for(int v:values)request.writeFloat(v/1000f);
            if(!surface.transact(1050,request,null,0))throw new IllegalStateException("Display transaction unsupported");
        } finally {request.recycle();}
    }
    private static String token(String before,String after) throws Exception {
        byte[] data=MessageDigest.getInstance("SHA-256").digest((ProjectionState.EXPECTED_FIRMWARE+"\n"+before+"\n"+after+"\n10").getBytes(StandardCharsets.UTF_8));
        StringBuilder s=new StringBuilder();for(byte b:data)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();
    }
    public static void main(String[] args) throws Exception {
        if(android.os.Process.myUid()!=2000)throw new SecurityException("Authorized ADB shell only");
        ProjectionState state=ProjectionState.read();
        if(args.length==1&&args[0].equals("probe")) {
            JSONObject result=new JSONObject();result.put("properties",state.json());
            try{result.put("panel_mode",readPanelMode());}catch(Exception e){result.put("panel_error",e.toString());}
            result.put("display_service_present",surface()!=null);
            System.out.println("VIBE_RESULT "+result);return;
        }
        if(args.length!=4||!args[0].equals("preview-geometry"))throw new IllegalArgumentException("probe, or explicitly approved preview-geometry TOKEN BEFORE AFTER");
        if(!state.compatible()||state.geometry==null)throw new SecurityException("Exact firmware and readable geometry required");
        if(!"0".equals(state.value("persist.sys.tpryauto")))throw new SecurityException("Preview requires automatic correction already off; it is never disabled here");
        if(!state.previewConfigVerified())throw new SecurityException("Vendor camera/automatic four-corner configuration is not verified");
        String before=ProjectionGeometry.encode(ProjectionGeometry.parse(args[2])),after=ProjectionGeometry.encode(ProjectionGeometry.parse(args[3]));
        if(!before.equals(state.value("persist.sys.zoom.value")))throw new SecurityException("Picture changed since review");
        if(!token(before,after).equals(args[1]))throw new SecurityException("Exact preview approval token required");
        int[] original=ProjectionGeometry.parse(before),desired=ProjectionGeometry.parse(after);
        IBinder binder=surface();if(binder==null)throw new IllegalStateException("Display service unavailable");
        Thread restore=new Thread(()->{try{transform(binder,original);}catch(Exception e){System.err.println("RESTORE_FAILED "+e);}},"vibe-preview-restore");
        Runtime.getRuntime().addShutdownHook(restore);
        try {
            System.out.println("PREVIEW_BEGIN "+after);System.out.flush();
            transform(binder,desired);Thread.sleep(10000);
        } finally {
            transform(binder,original);
            Runtime.getRuntime().removeShutdownHook(restore);
            System.out.println("PREVIEW_RESTORED "+before);System.out.flush();
        }
    }
}
