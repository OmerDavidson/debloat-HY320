package com.vibehome.launcher;

public final class WifiSecurityTest {
    private static void expect(boolean condition,String detail){if(!condition)throw new AssertionError(detail);}
    public static void main(String[] args){
        expect(WifiSecurity.fromCapabilities("[WPA2-PSK-CCMP][RSN-SAE-CCMP][ESS]")==WifiSecurity.WPA2,"Transition network must retain a supported personal-security choice");
        expect(WifiSecurity.fromCapabilities("[RSN-SAE-CCMP][ESS]")==WifiSecurity.WPA3,"SAE must not become open");
        expect(WifiSecurity.fromCapabilities("[RSN-OWE-CCMP][ESS]")==WifiSecurity.OWE,"OWE must not become plain open");
        for(String caps:new String[]{"[WEP][ESS]","[RSN-EAP-CCMP]","[WAPI-PSK]","[RSN-UNKNOWN]"})
            expect(WifiSecurity.fromCapabilities(caps)==WifiSecurity.UNSUPPORTED,"Unsupported authentication must not be downgraded: "+caps);
        expect(WifiSecurity.fromCapabilities("[ESS]")==WifiSecurity.OPEN,"Open network");
        expect(WifiSecurity.error("1234567890123456789012345678901é","",WifiSecurity.OPEN,true,true)!=null,"SSID length must count UTF-8 bytes");
        expect(WifiSecurity.error("123456789012345678901234567890é","",WifiSecurity.OPEN,true,true)==null,"32-byte SSID must be accepted");
        expect(WifiSecurity.error("network","1234567",WifiSecurity.WPA2,true,true)!=null,"WPA2 requires eight characters");
        expect(WifiSecurity.error("network","12345678",WifiSecurity.WPA2,false,false)==null,"WPA2 is independent of WPA3 hardware support");
        expect(WifiSecurity.error("network","12345678",WifiSecurity.WPA3,false,true)!=null,"Unsupported WPA3 must be rejected before submission");
        expect(WifiSecurity.error("network","",WifiSecurity.OWE,true,false)!=null,"Unsupported OWE must be rejected before submission");
        expect(WifiSecurity.error("network","",WifiSecurity.OWE,false,true)==null,"OWE does not use a password");
        expect(WifiSecurity.error("network","1234567é",WifiSecurity.WPA2,true,true)!=null,"Builder-incompatible password must be rejected");
        expect(WifiSecurity.error("network","\npassword",WifiSecurity.WPA2,true,true)!=null,"Control characters must be rejected");
        System.out.println("WifiSecurityTest passed");
    }
}
