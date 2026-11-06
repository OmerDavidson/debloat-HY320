package com.vibehome.launcher;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Read-only snapshot. No setters, broadcasts, calibration writes or device service transactions. */
final class ProjectionState {
    static final String EXPECTED_FIRMWARE = "UI_Veng.projector.20241225.164550";
    static final String[] KEYS = {
        "ro.product.model", "ro.build.display.id", "ro.hardware", "persist.sys.panelvalue",
        "persist.sys.prj.type", "persist.sys.projection.position", "persist.sys.tpryauto",
        "persist.sys.tpryxcrt", "persist.sys.camok", "persist.vendor.overscan.main", "persist.sys.zoom.value",
        "persist.display.keystone_lbx", "persist.display.keystone_lby", "persist.display.keystone_ltx",
        "persist.display.keystone_lty", "persist.display.keystone_rtx", "persist.display.keystone_rty",
        "persist.display.keystone_rbx", "persist.display.keystone_rby"
    };
    final Map<String,String> properties = new LinkedHashMap<>();
    int[] geometry;
    String geometryError;
    int panelMode = -1;

    static ProjectionState read() throws Exception {
        Process process = new ProcessBuilder("/system/bin/getprop").redirectErrorStream(true).start();
        try {
            if (!process.waitFor(4, TimeUnit.SECONDS)) throw new IllegalStateException("Device status read timed out");
            if (process.exitValue() != 0) throw new IllegalStateException("Device status unavailable");
            Map<String,String> all = new LinkedHashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line; int total = 0;
                while ((line = reader.readLine()) != null) {
                    total += line.length();
                    if (total > 131072) throw new IllegalStateException("Unexpected device status size");
                    int separator = line.indexOf("]: [");
                    if (line.startsWith("[") && separator > 1 && line.endsWith("]"))
                        all.put(line.substring(1, separator), line.substring(separator+4, line.length()-1));
                }
            }
            ProjectionState state = new ProjectionState();
            for (String key : KEYS) state.properties.put(key, all.getOrDefault(key, ""));
            state.readVendorConfig();
            try { int current=HardwareProbe.readPanelMode(); if(current>=0&&current<=3)state.panelMode=current; }
            catch (Exception unavailable) { /* Ordinary-app access may be restricted; do not bypass it. */ }
            try { state.geometry = ProjectionGeometry.parse(state.value("persist.sys.zoom.value")); }
            catch (IllegalArgumentException e) { state.geometryError = "Current picture geometry is unavailable"; }
            return state;
        } finally { process.destroy(); }
    }

    String value(String key) { return properties.getOrDefault(key, ""); }
    private void readVendorConfig() {
        for(String path:new String[]{"/oem/config.ini","/sdcard/config.ini","/product/etc/config.ini"}) {
            File file=new File(path);if(!file.isFile())continue;
            try(FileInputStream input=new FileInputStream(file)) {
                if(file.length()>32768)throw new IllegalStateException("Unexpected config size");
                byte[] bytes=new byte[(int)file.length()];int offset=0,read;
                while(offset<bytes.length&&(read=input.read(bytes,offset,bytes.length-offset))>0)offset+=read;
                if(offset!=bytes.length)throw new IllegalStateException("Short config read");
                String raw=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
                JSONObject config=new JSONObject(raw.replaceAll("(?m)^\\s*##[^\\r\\n]*", ""));
                properties.put("vibe.config.path",path);
                byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder hex=new StringBuilder();
                for(byte b:hash)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                properties.put("vibe.config.sha256",hex.toString());
                properties.put("vibe.config.autoCheckCamera",String.valueOf(config.optBoolean("autoCheckCamera",false)));
                properties.put("vibe.config.autoFourCorner",String.valueOf(config.optBoolean("autoFourCorner",false)));
            } catch(Exception unavailable) {properties.put("vibe.config.error","unreadable");}
            return;
        }
    }
    boolean previewConfigVerified() {
        return "/product/etc/config.ini".equals(value("vibe.config.path"))
            && "false".equals(value("vibe.config.autoCheckCamera"))
            && "false".equals(value("vibe.config.autoFourCorner")) && !"1".equals(value("persist.sys.camok"));
    }
    boolean compatible() {
        return "HY320".equals(value("ro.product.model")) && EXPECTED_FIRMWARE.equals(value("ro.build.display.id"))
            && "sun50iw12p1".equals(value("ro.hardware"));
    }
    JSONObject json() { return new JSONObject(properties); }
}
