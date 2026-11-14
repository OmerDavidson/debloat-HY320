package com.vibehome.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resolves configured ports without guessing when a firmware advertises extra inputs. */
final class HdmiSources {
    static final class Configured {
        final String key, label;
        Configured(String key,String label){this.key=key;this.label=label;}
    }
    static final class Input {
        final String id, label;
        Input(String id,String label){this.id=id;this.label=label;}
    }
    static final class Choice {
        final String key, label, inputId;
        Choice(String key,String label,String inputId){this.key=key;this.label=label;this.inputId=inputId;}
    }
    private static String normalized(String text){return text==null?"":text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");}
    static List<Choice> choose(List<Configured> configured,List<Input> discovered){
        List<Input> inputs=new ArrayList<>(discovered);
        Collections.sort(inputs,Comparator.comparing(input->input.id));
        List<Choice> matches=new ArrayList<>();Set<String> used=new HashSet<>();boolean complete=!configured.isEmpty();
        for(Configured port:configured){
            Input match=null;
            // This mapping is firmware-specific and only used if the exact input exists.
            String known="com.softwinner.vis/.HdmiInputService/HW"+port.key.substring(4);
            for(Input input:inputs)if(input.id.equals(known)){match=input;break;}
            if(match==null){
                for(Input input:inputs)if(normalized(input.label).equals(normalized(port.key))){
                    if(match!=null){match=null;break;}match=input;
                }
            }
            if(match==null&&configured.size()==1&&inputs.size()==1)match=inputs.get(0);
            if(match==null||!used.add(match.id)){complete=false;break;}
            matches.add(new Choice(port.key,port.label,match.id));
        }
        if(complete)return matches;
        if(inputs.isEmpty()){
            for(Configured port:configured)matches.add(new Choice(port.key,port.label,null));
            return matches;
        }
        // Where the mapping is ambiguous, let the user choose an Android-reported
        // input instead of silently mapping a physical port to the wrong source.
        List<Choice> choices=new ArrayList<>();Map<String,Integer> counts=new HashMap<>();
        for(Input input:inputs){String label=input.label==null?"":input.label.trim();if(label.isEmpty())label="HDMI";
            counts.put(label,counts.containsKey(label)?counts.get(label)+1:1);}
        Map<String,Integer> seen=new HashMap<>();
        for(Input input:inputs){String label=input.label==null?"":input.label.trim();if(label.isEmpty())label="HDMI";
            int number=seen.containsKey(label)?seen.get(label)+1:1;seen.put(label,number);
            if(counts.get(label)>1)label=label+" ("+number+")";
            choices.add(new Choice(input.id,label,input.id));
        }
        return choices;
    }
    private HdmiSources(){}
}
