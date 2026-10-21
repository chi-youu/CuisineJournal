package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.util.*;
import static com.chi__you.cuisinejournal.compat.UnificationContent.id;

public final class UnificationExpansionTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        var rules=JsonParser.parseString(Files.readString(Path.of("src/main/resources/cuisinejournal/unification.json"))).getAsJsonObject();
        Set<ResourceLocation> all=new HashSet<>();
        for(var row:rules.entrySet())row.getValue().getAsJsonObject().getAsJsonArray("items").forEach(v->all.add(id(v.getAsString())));
        all.addAll(Set.of(id("crockpot:onion_seeds"),id("crockpot:garlic_seeds"),id("jellyfishing:pineapple_seeds"),id("pineapple_delight:pineapple_crop")));
        var plan=UnificationContent.compile(rules,true,all::contains,mod->true);
        for(String[] pair:List.of(
                new String[]{"neapolitan:banana","alexsmobs:banana"},new String[]{"youkaishomecoming:bowl_of_cream","cosmopolitan:cream"},
                new String[]{"braziliandelight:heavy_cream_bucket","cosmopolitan:cream_bucket"},new String[]{"bountifulfares:orange","fruitsdelight:orange"},
                new String[]{"jellyfishing:seanut","vintagedelight:peanut"},new String[]{"jellyfishing:roasted_seanut","vintagedelight:roasted_peanut"},
                new String[]{"crockpot:onion","farmersdelight:onion"},new String[]{"crockpot:garlic","veggiesdelight:garlic"},
                new String[]{"ubesdelight:garlic_chop","veggiesdelight:garlic_clove"},new String[]{"muffins_thaidelight:lime","collectorsreap:lime"},
                new String[]{"veggiesdelight:broccoli","smc:broccoli"},new String[]{"veggiesdelight:baked_sweet_potato","kaleidoscope_grilling:roasted_sweet_potato"},
                new String[]{"fruitsdelight:pineapple","pineapple_delight:pineapple"},new String[]{"kitchenkarrot:acorn","seeddelight:acorn"},
                new String[]{"bountifulfares:flour","kaleidoscope_cookery:flour"},new String[]{"vanillacookbook:milk_bottle","farmersdelight:milk_bottle"},
                new String[]{"cosmopolitan:condensed_milk_bottle","ubesdelight:condensed_milk_bottle"},new String[]{"neapolitan:ice_cubes","youkaishomecoming:ice_cube"},
                new String[]{"environmental:cherries","vinery:cherry"},new String[]{"wildernature:truffle","environmental:truffle"}))
            check(plan.replace(id(pair[0])).equals(id(pair[1])),"priority "+pair[0]);
        check(plan.excluded().containsAll(Set.of(id("crockpot:onion_seeds"),id("crockpot:garlic_seeds"))),"obsolete dedicated seeds hidden");
        check(!UnificationCrops.blocksPlanting(id("vintagedelight:peanut"),plan),"representative peanut planting remains usable");
        check(!plan.disabledPlants().contains("jellyfishing:seanut_bush"),"underwater peanut cultivation retained");
        check(plan.disabledPlants().stream().noneMatch(p->p.contains("orange")),"orange trees all retained");
        check(UnificationCrops.blocksPlanting(id("crockpot:onion_seeds"),plan),"obsolete onion seed cannot be planted");
        check(UnificationCrops.blocksPlanting(id("veggiesdelight:broccoli_seeds"),plan),"lower seed redirects to selected crop");
        check(UnificationCrops.blocksPlanting(id("vintagedelight:cucumber_seeds"),plan),"existing crop groups also obey selected seed planting rule");
        check(!UnificationCrops.blocksPlanting(id("smc:broccoli_seeds"),plan),"selected dedicated seed stays plantable");
        var only=Set.of(id("crockpot:garlic"),id("crockpot:garlic_seeds"));
        var fallback=UnificationContent.compile(rules,true,only::contains,mod->true);
        check(!fallback.excluded().contains(id("crockpot:garlic_seeds"))&&!UnificationCrops.blocksPlanting(id("crockpot:garlic_seeds"),fallback),"only crop retains unique seed");
        var recipes=new LinkedHashMap<ResourceLocation,JsonElement>();
        put(recipes,"youkaishomecoming:cream","{type:'farmersdelight:cooking',ingredients:[{item:'minecraft:milk_bucket'}],result:{item:'youkaishomecoming:bowl_of_cream'}}");
        put(recipes,"cosmopolitan:fill","{type:'cosmopolitan:tub_extracting',ingredient:[{item:'minecraft:glass_bottle'},{fluid:{amount:250,name:'cosmopolitan:condensed_milk'}}],result:{item:'cosmopolitan:condensed_milk_bottle'}}");
        put(recipes,"kitchenkarrot:acorn","{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:oak_sapling'}],result:{item:'kitchenkarrot:acorn'}}");
        put(recipes,"ubesdelight:reassemble","{type:'minecraft:crafting_shapeless',ingredients:[{item:'ubesdelight:garlic_chop'},{item:'ubesdelight:garlic_chop'}],result:{item:'ubesdelight:garlic'}}");
        put(recipes,"veggiesdelight:convert","{type:'minecraft:crafting_shapeless',ingredients:[{item:'ubesdelight:garlic'}],result:{item:'veggiesdelight:garlic'}}");
        put(recipes,"test:milk3","{type:'minecraft:crafting_shapeless',ingredients:[{item:'vanillacookbook:milk_bottle'},{item:'vanillacookbook:milk_bottle'},{item:'vanillacookbook:milk_bottle'},{item:'minecraft:bucket'}],result:{item:'minecraft:milk_bucket'}}");
        put(recipes,"test:milk4","{type:'minecraft:crafting_shapeless',ingredients:[{item:'farmersdelight:milk_bottle'},{item:'farmersdelight:milk_bottle'},{item:'farmersdelight:milk_bottle'},{item:'farmersdelight:milk_bottle'},{item:'minecraft:bucket'}],result:{item:'minecraft:milk_bucket'}}");
        put(recipes,"test:old_value","{type:'crockpot:food_values',items:['neapolitan:banana'],values:{FRUIT:0.5}}");
        put(recipes,"test:winner_value","{type:'crockpot:food_values',items:['alexsmobs:banana'],values:{FRUIT:1}}");
        put(recipes,"test:onion_value","{type:'crockpot:food_values',items:['crockpot:onion'],values:{VEGGIE:1}}");
        put(recipes,"test:seed","{type:'minecraft:crafting_shapeless',ingredients:[{item:'crockpot:onion'}],result:{item:'crockpot:onion_seeds'}}");
        UnificationRecipes.apply(recipes,plan);
        check(recipes.get(id("youkaishomecoming:cream")).toString().contains("cosmopolitan:cream"),"alternate cream channel retained");
        check(recipes.get(id("cosmopolitan:fill")).toString().contains("ubesdelight:condensed_milk_bottle"),"actual fluid filling preserved for selected bottle");
        check(recipes.containsKey(id("kitchenkarrot:acorn")),"special acorn crafting channel retained");
        check(!recipes.containsKey(id("ubesdelight:reassemble")),"two-piece garlic reassembly cannot duplicate three-piece representative");
        check(!recipes.containsKey(id("veggiesdelight:convert")),"new self conversion removed");
        check(!recipes.containsKey(id("test:milk3"))&&recipes.containsKey(id("test:milk4")),"milk container conservation");
        check(!recipes.containsKey(id("test:old_value"))&&recipes.containsKey(id("test:winner_value")),"CrockPot representative retains its own value");
        check(recipes.get(id("test:onion_value")).toString().contains("farmersdelight:onion"),"missing representative value inherits compatible raw-food definition");
        check(!recipes.containsKey(id("test:seed")),"removed seed has no output recipe");
        var before=new LinkedHashMap<>(recipes);UnificationRecipes.apply(recipes,plan);check(before.equals(recipes),"repeated reload is stable");
        installed(rules);
        System.out.println("UnificationExpansionTest: "+checks+" checks passed");
    }
    private static void installed(JsonObject rules)throws Exception{
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");if(directory==null)return;
        var recipes=new LinkedHashMap<ResourceLocation,JsonElement>();
        Set<ResourceLocation> items=new HashSet<>();
        try(var paths=Files.list(Path.of(directory))){
            for(var path:paths.filter(p->p.toString().endsWith(".jar")&&!p.getFileName().toString().startsWith("cuisinejournal-")).sorted().toList())
                try(var jar=new java.util.jar.JarFile(path.toFile())){
                    for(var entry:Collections.list(jar.entries())){
                        var name=entry.getName();
                        if(name.matches("assets/[^/]+/models/item/.+[.]json")){
                            var p=name.split("/",5);var item=ResourceLocation.tryParse(p[1]+":"+p[4].substring(0,p[4].length()-5));
                            if(item!=null)items.add(item);
                        }
                        if(!name.matches("data/[^/]+/recipes/.+[.]json"))continue;
                        try(var input=jar.getInputStream(entry)){
                            var p=name.split("/",4);var key=ResourceLocation.tryParse(p[1]+":"+p[3].substring(0,p[3].length()-5));
                            if(key!=null)recipes.put(key,JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)));
                        }catch(JsonParseException ignored){}
                    }
                }
        }
        var plan=UnificationContent.compile(rules,true,items::contains,mod->true);
        var originals=new LinkedHashMap<>(recipes);UnificationRecipes.apply(recipes,plan);
        for(String key:List.of("youkaishomecoming:bowl_of_cream","cosmopolitan:general/cream",
                "cosmopolitan:farmersdelight/cooking/cream_bucket","cosmopolitan:tub_extracting/cream_extract",
                "cosmopolitan:tub_injecting/cream","cosmopolitan:tub_extracting/condensed_milk_bottle_extract",
                "cosmopolitan:tub_injecting/condensed_milk_bottle","cosmopolitan:general/condensed_milk_bucket_from_bottle",
                "kitchenkarrot:acorn","minecraft:acorn_from_oak_sapling_stonecutting","onion_onion:cutting/onion"))
            if(originals.containsKey(id(key)))check(recipes.containsKey(id(key)),"installed retained production/serving channel "+key);
        for(String key:List.of("ubesdelight:minecraft/crafting/garlic_from_garlic_chop","veggiesdelight:recipes/compat/ubesdelight/garlic_convert"))
            if(originals.containsKey(id(key)))check(!recipes.containsKey(id(key)),"installed garlic reverse/self loop gone "+key);
        if(originals.containsKey(id("veggiesdelight:cutting/garlic_clove_from_cutting")))
            check(originals.get(id("veggiesdelight:cutting/garlic_clove_from_cutting")).equals(recipes.get(id("veggiesdelight:cutting/garlic_clove_from_cutting"))),"selected garlic cutting preserves three cloves and original tool");
        check(plan.replace(id("environmental:cherries")).equals(id("vinery:cherry")),"actual plural cherry item selected");
        check(plan.replace(id("youkaishomecoming:ice_cube")).equals(id("youkaishomecoming:ice_cube")),"actual singular ice cube selected");
    }
    private static void put(Map<ResourceLocation,JsonElement> recipes,String key,String json){recipes.put(id(key),JsonParser.parseString(json));}
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;}
}
