package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import com.chi__you.cuisinejournal.recipe.WorkstationDisplay;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static com.chi__you.cuisinejournal.compat.UnificationContent.id;

public final class WorkstationDisplayTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        var mapping=json("src/main/resources/cuisinejournal/workstation_display.json");
        var zh=json("src/main/resources/assets/cuisinejournal/lang/zh_cn.json");var en=json("src/main/resources/assets/cuisinejournal/lang/en_us.json");
        var installed=new HashSet<String>();String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(directory!=null)try(var files=Files.list(Path.of(directory))){for(var file:files.filter(p->p.toString().endsWith(".jar")&&!p.getFileName().toString().startsWith("cuisinejournal-")).toList())
            try(var jar=new JarFile(file.toFile())){for(var entry:Collections.list(jar.entries())){
                var parts=entry.getName().split("/");if(parts.length==5&&parts[0].equals("assets")&&parts[2].equals("models")&&parts[3].equals("item")&&parts[4].endsWith(".json"))
                    installed.add(parts[1]+":"+parts[4].substring(0,parts[4].length()-5));
            }}
        }
        for(var entry:mapping.entrySet()){
            var method=id(entry.getKey());var candidates=WorkstationDisplay.candidates(method);
            check(!candidates.isEmpty(),"every declared method has icon candidates "+method);
            String key="cuisinejournal.method."+entry.getKey().replace(':','.');
            check(zh.has(key)&&en.has(key),"method name exists in both supported languages "+method);
            boolean hostPresent=installed.stream().anyMatch(item->item.startsWith(method.getNamespace()+":"));
            if(directory!=null&&hostPresent)check(candidates.stream().anyMatch(item->item.getNamespace().equals("minecraft")||installed.contains(item.toString())),"installed icon model available "+method);
        }
        var optional=id("test:unknown_station");check(WorkstationDisplay.candidates(optional).equals(List.of(optional)),"unknown method retains same-id fallback without loading a host");
        try(var files=Files.list(Path.of("src/main/resources/data/cuisinejournal/cuisinejournal/compat/workstations"))){
            for(var file:files.filter(p->p.toString().endsWith(".json")).toList())for(var row:json(file.toString()).getAsJsonArray("workstations")){
                var value=row.getAsJsonObject();if(value.has("method"))check(mapping.has(value.get("method").getAsString()),"all bundled workstation method declarations covered "+value.get("method"));
            }
        }
        var corrections=json("src/main/resources/data/cuisinejournal/tags/items/food_slot_overrides.json");
        var ids=new HashSet<String>();for(var row:corrections.getAsJsonArray("values")){
            var item=row.getAsJsonObject();check(!item.get("required").getAsBoolean(),"optional food override tolerates absent host");ids.add(item.get("id").getAsString());
        }
        check(ids.equals(Set.of("ice_and_fire_delight:hydra_venom_soup","exquisito:nightshade_lollipop","exquisito:ether_lollipop",
                "farmersdelight:bone_broth","kaleidoscope_cookery:pork_bone_soup","youkaishomecoming:shaved_ice_over_rice")),"only reported soups/lollipops/shaved rice override drinking animation");
        check(!ids.contains("minecraft:potion")&&!ids.contains("exquisito:nightshade_berry_parfait"),"ordinary drinks and existing parfait special policy untouched");
        System.out.println("WorkstationDisplayTest: "+checks+" checks passed");
    }
    private static JsonObject json(String path)throws Exception{return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
