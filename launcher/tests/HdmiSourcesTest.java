package com.vibehome.launcher;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class HdmiSourcesTest {
    private static HdmiSources.Configured port(String key,String label){return new HdmiSources.Configured(key,label);}
    private static HdmiSources.Input input(String id,String label){return new HdmiSources.Input(id,label);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        String base="com.softwinner.vis/.HdmiInputService/HW";
        List<HdmiSources.Choice> choices=HdmiSources.choose(Arrays.asList(port("HDMI2","HDMI")),Arrays.asList(input(base+"1","HDMI"),input(base+"2","HDMI"),input(base+"3","HDMI")));
        check(choices.size()==1&&choices.get(0).inputId.equals(base+"2")&&choices.get(0).label.equals("HDMI"),"configured port must exclude phantom inputs");
        choices=HdmiSources.choose(Collections.emptyList(),Arrays.asList(input("vendor/b","HDMI"),input("vendor/a","HDMI")));
        check(choices.size()==2&&choices.get(0).inputId.equals("vendor/a")&&choices.get(0).label.equals("HDMI (1)")&&choices.get(1).label.equals("HDMI (2)"),"missing config must expose distinct Android inputs");
        choices=HdmiSources.choose(Arrays.asList(port("HDMI2","HDMI")),Arrays.asList(input("vendor/a","HDMI 1"),input("vendor/b","HDMI 2")));
        check(choices.size()==1&&choices.get(0).inputId.equals("vendor/b"),"unique port label must match");
        choices=HdmiSources.choose(Arrays.asList(port("HDMI2","HDMI")),Arrays.asList(input("vendor/a","HDMI"),input("vendor/b","HDMI")));
        check(choices.size()==2,"ambiguous mapping must offer inputs, not guess");
        choices=HdmiSources.choose(Arrays.asList(port("HDMI2","HDMI")),Arrays.asList(input("other/input","HDMI")));
        check(choices.size()==1&&choices.get(0).inputId.equals("other/input"),"single input must work on other firmware");
        choices=HdmiSources.choose(Arrays.asList(port("HDMI2","HDMI")),Collections.emptyList());
        check(choices.size()==1&&choices.get(0).inputId==null&&choices.get(0).key.equals("HDMI2"),"legacy fallback must retain configured key");
        System.out.println("HDMI source discovery tests passed");
    }
}
