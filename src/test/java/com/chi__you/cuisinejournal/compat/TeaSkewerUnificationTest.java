package com.chi__you.cuisinejournal.compat;

import com.chi__you.cuisinejournal.recipe.GrillingRecipes;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.util.*;
import static com.chi__you.cuisinejournal.compat.UnificationContent.id;

/** Configured priorities, one-to-many substitutions and installed native skewering contracts. */
public final class TeaSkewerUnificationTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        var document=JsonParser.parseString(Files.readString(Path.of("src/main/resources/cuisinejournal/unification.json"))).getAsJsonObject();
        Set<ResourceLocation> all=new HashSet<>();
        document.entrySet().forEach(e->e.getValue().getAsJsonObject().getAsJsonArray("items").forEach(v->all.add(id(v.getAsString()))));
        for(String item:List.of("veggiesdelight:bellpepper","veggiesdelight:bellpepper_seeds","veggiesdelight:bellpepper_crate",
                "rusticdelight:bell_pepper_green","rusticdelight:bell_pepper_seeds","crockpot:cooked_eggplant",
                "culturaldelights:smoked_eggplant","culturaldelights:smoked_cut_eggplant"))all.add(id(item));
        var plan=UnificationContent.compile(document,true,all::contains,mod->true);
        for(String[] pair:List.of(new String[]{"farmersrespite:coffee_beans","youkaishomecoming:green_coffee_bean"},
                new String[]{"farmersrespite:coffee_berries","youkaishomecoming:coffee_berries"},
                new String[]{"herbalbrews:dried_black_tea","youkaishomecoming:black_tea_leaves"},
                new String[]{"farmersrespite:yellow_tea_leaves","youkaishomecoming:yellow_tea_leaves"},
                new String[]{"delightful:green_tea_leaf","youkaishomecoming:green_tea_leaves"},
                new String[]{"herbalbrews:green_tea_leaf","youkaishomecoming:tea_leaves"},
                new String[]{"bountifulfares:dried_tea_leaves","kaleidoscope_cookery:dried_tea_leaves"},
                new String[]{"herbalbrews:dried_oolong_tea","youkaishomecoming:oolong_tea_leaves"},
                new String[]{"crockpot:eggplant","kaleidoscope_chinesefood:eggplant"},
                new String[]{"neapolitan:adzuki_beans","youkaishomecoming:redbean"},
                new String[]{"bountifulfares:lemon","fruitsdelight:lemon"},new String[]{"beachparty:coconut","crabbersdelight:coconut"},
                new String[]{"bountifulfares:passion_fruit","atmospheric:passion_fruit"},new String[]{"bountifulfares:plum","environmental:plum"},
                new String[]{"ltc2:pepper_red","kaleidoscope_cookery:red_chili"},new String[]{"neapolitan:chocolate_bar","smc:chocolate"}))
            check(plan.replace(id(pair[0])).equals(id(pair[1])),"priority "+pair[0]);
        check(!plan.replace(id("herbalbrews:green_tea_leaf")).equals(plan.replace(id("herbalbrews:dried_green_tea"))),"fresh versus processed tea cannot self-convert");
        for(String keep:List.of("ltc2:salt","neapolitan:roasted_adzuki_beans","papercraft_magic_decoration:coffee_fruit","kaleidoscope_cookery:green_chili"))
            check(plan.replace(id(keep)).equals(id(keep)),"independent native ingredient "+keep);
        check(plan.disabledPlants().stream().noneMatch(p->p.equals("ltc2:wild_pepper_red")||p.equals("muffins_thaidelight:wild_pepper")),"wild red pepper sources deliberately retained");
        check(plan.disabledPlants().stream().noneMatch(p->p.contains("tea")),"tea cultivation not silently unified");
        check(UnificationCrops.blocksPlanting(id("neapolitan:adzuki_beans"),plan),"lower red bean planting follows selected native crop");
        check(UnificationCrops.blocksPlanting(id("herbalbrews:coffee_beans"),plan),"lower coffee planting follows selected native crop");
        var tags=new LinkedHashMap<ResourceLocation,List<net.minecraft.tags.TagLoader.EntryWithSource>>();
        UnificationTags.apply(tags,plan);var size=tags.get(id("forge:crops/bellpepper")).size();
        UnificationTags.apply(tags,plan);check(tags.get(id("forge:crops/bellpepper")).size()==size,"one-to-many tag forwarding is idempotent");
        var fallback=UnificationContent.compile(document,true,item->all.contains(item)&&!Set.of("corn_delight","farm_and_charm").contains(item.getNamespace()),mod->true);
        check(fallback.winner("cuisinejournal:corn").equals(id("culturaldelights:corn_cob")),"corn older prefix retained");
        fallback=UnificationContent.compile(document,true,item->all.contains(item)&&Set.of("crockpot","braziliandelight","bountifulfares").contains(item.getNamespace()),mod->true);
        check(fallback.winner("cuisinejournal:corn").equals(id("crockpot:corn")),"CrockPot corn precedes Brazil and Bountiful");
        var recipes=new LinkedHashMap<ResourceLocation,JsonElement>();
        put(recipes,"veggiesdelight:dish","{type:'farmersdelight:cooking',ingredients:[{item:'veggiesdelight:bellpepper'},{item:'minecraft:beef'}],result:{item:'test:dish'}}");
        put(recipes,"crockpot:values","{type:'crockpot:food_values',items:['crockpot:cooked_eggplant'],values:{VEGGIE:1}}");
        put(recipes,"test:tea","{type:'herbalbrews:leaf_drying',ingredient:{item:'herbalbrews:green_tea_leaf'},result:{item:'herbalbrews:dried_green_tea',count:9}}");
        put(recipes,"test:bag","{type:'minecraft:crafting_shapeless',ingredients:[{item:'herbalbrews:tea_leaf_crate'}],result:{item:'herbalbrews:green_tea_leaf',count:6}}");
        put(recipes,"veggiesdelight:crate","{type:'minecraft:crafting_shaped',key:{x:{item:'veggiesdelight:bellpepper'}},result:{item:'veggiesdelight:bellpepper_crate'}}");
        UnificationRecipes.apply(recipes,plan);
        check(recipes.get(id("veggiesdelight:dish")).toString().contains("forge:crops/bell_pepper"),"all rustic colors accepted through existing whole-pepper tag");
        check(!recipes.containsKey(id("veggiesdelight:crate")),"obsolete pepper packaging removed");
        var cooked=recipes.get(id("crockpot:values")).getAsJsonObject().getAsJsonArray("items");
        check(cooked.size()==2&&cooked.toString().contains("smoked_cut_eggplant")&&cooked.toString().contains("smoked_eggplant"),"CrockPot accepts both smoked forms without merging them");
        check(recipes.get(id("test:tea")).toString().contains("green_tea_leaves")&&recipes.get(id("test:tea")).toString().contains("count"),"native tea processing preserved");
        check(recipes.get(id("test:bag")).getAsJsonObject().getAsJsonObject("result").get("count").getAsInt()==6,"six-leaf package never becomes nine");
        var twice=new LinkedHashMap<>(recipes);UnificationRecipes.apply(recipes,plan);check(recipes.equals(twice),"aliases and tea resource edits idempotent");
        var root=JsonParser.parseString("{skewers:[{id:'kaleidoscope_grilling:raw_squid_tentacle_skewer',cooked_result:'kaleidoscope_grilling:grilled_squid_tentacle_skewer',ingredients:[['#kaleidoscope_grilling:ingredients/squid_tentacles'],['kaleidoscope_grilling:squid_tentacle']]},{id:'test:other',ingredients:[['test:ingredient']]}]}").getAsJsonObject();
        var original=root.deepCopy();GrillingRecipes.repairSquid(root);
        check(root.getAsJsonArray("skewers").get(0).toString().contains("#cuisinejournal:raw_squid_tentacles"),"actual native fixed skewer selectors repaired");
        check(root.getAsJsonArray("skewers").get(1).equals(original.getAsJsonArray("skewers").get(1)),"other fixed skewers untouched");
        check(root.getAsJsonArray("skewers").get(0).getAsJsonObject().get("cooked_result").equals(original.getAsJsonArray("skewers").get(0).getAsJsonObject().get("cooked_result")),"native cooking output untouched");
        var repaired=root.deepCopy();GrillingRecipes.repairSquid(root);check(root.equals(repaired),"native selector fix idempotent");
        var catalogue=new org.objectweb.asm.tree.ClassNode();
        try(var input=TeaSkewerUnificationTest.class.getResourceAsStream("/com/chi__you/cuisinejournal/recipe/CulinaryRegistry.class")){
            new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(catalogue,0);
        }
        boolean connected=false;
        for(var method:catalogue.methods)for(var instruction:method.instructions)
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.owner.equals("com/chi__you/cuisinejournal/recipe/GrillingRecipes")&&call.name.equals("preparations"))connected=true;
        check(connected,"native fixed skewers are real catalogue preparations, not just JEI displays");
        recipes.clear();
        put(recipes,"test:nested_schema","{type:'test:other',ingredients:{item:{item:'test:nonstandard'},tag:{key:'test:tag'}},result:{item:'test:result'}}");
        check(UnificationRecipes.apply(recipes,plan)==0,"nonprimitive third-party ingredient metadata remains readable");
        installed();
        System.out.println("TeaSkewerUnificationTest: "+checks+" checks passed");
    }
    private static void installed()throws Exception{
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");if(directory==null)return;
        try(var files=Files.list(Path.of(directory))){for(var path:files.filter(p->p.toString().endsWith(".jar")).toList())
            try(var jar=new java.util.jar.JarFile(path.toFile())){
                var entry=jar.getJarEntry("data/kaleidoscope_grilling/grilling/skewers.json");if(entry==null)continue;
                JsonObject root;try(var stream=jar.getInputStream(entry)){root=JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
                var original=root.deepCopy();GrillingRecipes.repairSquid(root);int rows=0;
                for(var raw:root.getAsJsonArray("skewers")){
                    var row=raw.getAsJsonObject();if(!row.get("id").getAsString().equals("kaleidoscope_grilling:raw_squid_tentacle_skewer"))continue;
                    check(row.getAsJsonArray("ingredients").size()==3,"actual squid skewer retains three ingredient slots");
                    for(var slot:row.getAsJsonArray("ingredients"))check(slot.getAsJsonArray().get(0).getAsString().equals("#cuisinejournal:raw_squid_tentacles"),"each real threading slot accepts custom raw tag");rows++;
                }
                check(rows==1&&root.getAsJsonArray("skewers").size()==original.getAsJsonArray("skewers").size(),"no duplicated native fixed recipe");
            }
        }
    }
    private static void put(Map<ResourceLocation,JsonElement> recipes,String key,String text){recipes.put(id(key),JsonParser.parseString(text));}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
