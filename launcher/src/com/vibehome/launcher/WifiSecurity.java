package com.vibehome.launcher;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Pure validation for native Wi-Fi forms; no credentials are persisted here. */
final class WifiSecurity {
    static final int OPEN=0, WPA2=1, WPA3=2, OWE=3, UNSUPPORTED=-1;
    static final String[] LABELS={"Open","WPA2 personal","WPA3 personal","Enhanced Open"};
    static int fromCapabilities(String raw){
        String value=raw==null?"":raw.toUpperCase(Locale.ROOT);
        if(value.contains("EAP")||value.contains("WEP")||value.contains("WAPI")||value.contains("DPP"))return UNSUPPORTED;
        if(value.contains("PSK"))return WPA2; // Explicit WPA2 choice on personal transition networks.
        if(value.contains("SAE"))return WPA3;
        if(value.contains("OWE"))return OWE;
        if(value.contains("RSN")||value.contains("WPA"))return UNSUPPORTED;
        return OPEN;
    }
    static boolean needsPassword(int security){return security==WPA2||security==WPA3;}
    static String error(String ssid,String password,int security,boolean sae,boolean owe){
        if(ssid.isEmpty()||ssid.getBytes(StandardCharsets.UTF_8).length>32)return "Use a network name of 1–32 UTF-8 bytes";
        if(security<OPEN||security>OWE)return "Unsupported network security";
        if(security==WPA3&&!sae)return "This projector does not report WPA3 personal support";
        if(security==OWE&&!owe)return "This projector does not report Enhanced Open support";
        if(needsPassword(security)){
            int minimum=security==WPA2?8:1;
            if(password.length()<minimum||password.length()>63)return "Use a "+minimum+"–63 character password";
            for(int i=0;i<password.length();i++)if(password.charAt(i)<32||password.charAt(i)>126)return "Use printable ASCII characters for this network password";
        }
        return null;
    }
}
