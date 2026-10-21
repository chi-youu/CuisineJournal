package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

public final class SquidRecipeCompatTest {
    private static int checks;
    private static final String RAW = "forge:raw_fishes/calamari";
    private static final String RAW_TENTACLES = "cuisinejournal:raw_squid_tentacles";
    private static final ResourceLocation OLD = id("minersdelight:cooking/takoyaki");
    private static final ResourceLocation CANONICAL = id("miners_delight:cooking/takoyaki");

    public static void main(String[] args) throws Exception {
        Map<ResourceLocation, JsonElement> recipes = new LinkedHashMap<>();
        JsonElement old = json("""
            {"type":"farmersdelight:cooking","ingredients":[{"tag":"forge:foods/tentacles"},
             {"tag":"forge:foods/tentacles"},{"tag":"forge:dough"},{"tag":"forge:crops/onion"}],
             "result":{"item":"miners_delight:takoyaki"},"container":{"item":"minecraft:bowl"},
             "cookingtime":200,"experience":1.0}
            """);
        recipes.put(OLD, old);
        check(SquidRecipeCompat.apply(recipes, item -> true) == 2 && !recipes.containsKey(OLD)
                && recipes.containsKey(CANONICAL), "native-only installation gets one stable recipe id");
        var repaired = recipes.get(CANONICAL);
        check(countTags(repaired, "forge:tentacles") == 2 && countTags(repaired,"forge:dough") == 1
                && repaired.getAsJsonObject().get("container").equals(old.getAsJsonObject().get("container")),
                "takoyaki has two broad tentacle inputs and keeps its dough and bowl");
        check(old.toString().contains("forge:foods/tentacles"), "native source object stays unchanged");
        check(SquidRecipeCompat.apply(recipes,item->true)==0,"repeat application is idempotent");
        JsonElement conditional = json("""
            {"type":"forge:conditional","recipes":[{"conditions":[{"type":"forge:item_exists",
             "item":"miners_delight:stuffed_squid"},{"type":"forge:not","value":{"type":"forge:tag_empty",
             "tag":"forge:raw_fishes/squid/tentacles"}}],"recipe":{"type":"farmersdelight:cooking",
             "ingredients":[{"tag":"forge:raw_fishes/squid/tentacles"},{"tag":"forge:raw_fishes/squid/tentacles"}],
             "result":{"item":"miners_delight:takoyaki"},"cookingtime":200}}]}
            """);
        recipes.clear(); recipes.put(OLD,old); recipes.put(CANONICAL,conditional);
        SquidRecipeCompat.apply(recipes,item->true);
        check(recipes.size()==1&&countTags(recipes.get(CANONICAL),"forge:tentacles")==3,
                "Delightful duplicate merged, including the tag-empty guard");
        check(recipes.get(CANONICAL).toString().contains("miners_delight:stuffed_squid"),
                "unrelated optional-host condition retained");
        recipes.clear(); recipes.put(OLD,old);
        SquidRecipeCompat.apply(recipes,item->false);
        check(recipes.get(OLD)==old && recipes.size()==1,"no miner item does not invent cooking content");
        testRollsAndSkewer();
        testCalamariDumplings();
        testCooking();
        testCookedAndGlowing();
        testTags();
        installed();
        System.out.println("SquidRecipeCompatTest: "+checks+" checks passed");
    }

    private static void testRollsAndSkewer() {
        var rustic=id("rusticdelight:calamari_roll"); var cultural=id("culturaldelights:calamari_roll");
        var skewer=id("oceansdelight:tentacle_on_a_stick");
        var recipes=new LinkedHashMap<ResourceLocation,JsonElement>();
        var original=json("""
            {"type":"minecraft:crafting_shapeless","conditions":[{"type":"rusticdelight:config_boolean",
             "settingId":"squids_drop_calamari"}],"ingredients":[{"tag":"rusticdelight:calamari_roll_ingredients"},
             {"tag":"rusticdelight:calamari_roll_ingredients"},{"item":"farmersdelight:cooked_rice"}],
             "result":{"item":"rusticdelight:calamari_roll","count":2}}
            """);
        recipes.put(rustic,original);
        recipes.put(cultural,json("""
            {"ingredients":[{"item":"culturaldelights:raw_calamari"},{"item":"culturaldelights:raw_calamari"},
             {"item":"farmersdelight:cooked_rice"},{"item":"minecraft:dried_kelp"}],
             "result":{"item":"culturaldelights:calamari_roll","count":2}}
            """));
        recipes.put(skewer,json("""
            {"ingredients":[{"item":"minecraft:stick"},{"item":"oceansdelight:tentacles"}],
             "result":{"item":"oceansdelight:tentacle_on_a_stick"}}
            """));
        CompatRecipePathPatch.repair(recipes,true,id->false);
        SquidRecipeCompat.apply(recipes,item->true);
        for(var roll:List.of(rustic,cultural)) {
            check(countTags(recipes.get(roll),RAW)==2,"both sushi inputs use the broad raw squid tag: "+roll);
            check(recipes.get(roll).toString().contains("kaleidoscope_cookery:cooked_rice"),
                    "unified rice path repair survives squid repair: "+roll);
            check(recipes.get(roll).getAsJsonObject().getAsJsonObject("result").get("count").getAsInt()==2,
                    "roll output remains two: "+roll);
        }
        check(recipes.get(rustic).getAsJsonObject().get("conditions").equals(original.getAsJsonObject().get("conditions")),
                "rustic config gate untouched");
        check(countTags(recipes.get(skewer),RAW_TENTACLES)==1&&recipes.get(skewer).toString().contains("minecraft:stick"),
                "raw skewer uses new raw-only tentacle tag with same stick");
        var other=id("test:unrelated"); recipes.put(other,original);
        check(SquidRecipeCompat.apply(recipes,item->true)==0&&recipes.get(other)==original,
                "unrelated recipe and second pass stay intact");
    }

    private static void testCalamariDumplings() {
        var inputs = List.of("{\"tag\":\"forge:raw_squid\"}",
                "{\"item\":\"oceansdelight:tentacles\"}",
                "{\"item\":\"miners_delight:tentacles\"}",
                "{\"item\":\"culturaldelights:raw_calamari\"}");
        for (int installed = 1; installed < 16; installed++) {
            var recipes = new LinkedHashMap<ResourceLocation,JsonElement>();
            for (int i = 0; i < 4; i++) if ((installed & (1 << i)) != 0) {
                var key = id("dumplings_delight:dumpling/calamari_boiled_dumpling_recipe_" + (i + 1));
                recipes.put(key,json("{\"type\":\"farmersdelight:cooking\",\"ingredients\":["
                        + "{\"item\":\"farmersdelight:wheat_dough\"}," + inputs.get(i)
                        + ",{\"item\":\"minecraft:brown_mushroom\"},{\"tag\":\"dumplings_delight:greenonion\"}],"
                        + "\"result\":{\"item\":\"dumplings_delight:calamari_boiled_dumpling\",\"count\":2},"
                        + "\"experience\":0.35,\"cookingtime\":200}"));
            }
            var first = recipes.keySet().iterator().next();
            var original = recipes.get(first).deepCopy();
            var unrelated = id("test:unrelated_dumpling");
            recipes.put(unrelated,original);
            SquidRecipeCompat.apply(recipes,item -> false);
            check(recipes.get(first).equals(original),"absent dumpling host leaves recipes alone: " + installed);
            SquidRecipeCompat.apply(recipes,item -> true);
            check(recipes.size() == 2 && recipes.containsKey(first),"one available dumpling variant retained: " + installed);
            var result = recipes.get(first).getAsJsonObject();
            var expected = original.deepCopy().getAsJsonObject();
            expected.getAsJsonArray("ingredients").set(1,json("{\"tag\":\"" + RAW + "\"}"));
            check(result.equals(expected),"only squid ingredient changes, output/time/XP kept: " + installed);
            check(recipes.get(unrelated).equals(original),"other recipe IDs stay untouched: " + installed);
            check(SquidRecipeCompat.apply(recipes,item -> true) == 0,"dumpling collapse idempotent: " + installed);
            if (installed == 15) {
                var alternative = expected.deepCopy();alternative.addProperty("cookingtime",400);
                var extra = id("dumplings_delight:dumpling/calamari_boiled_dumpling_recipe_4");
                recipes.put(extra,alternative);
                check(SquidRecipeCompat.apply(recipes,item -> true)==0 && recipes.get(extra)==alternative,
                        "external recipe with different time is not incorrectly deduplicated");
            }
        }
    }

    private static void testCooking() {
        var recipes=new LinkedHashMap<ResourceLocation,JsonElement>();
        for(var method:List.of("campfire","smelting","smoking")) {
            int time=method.equals("campfire")?600:method.equals("smoking")?100:200;
            recipes.put(id("oceanic_delight:squid_tentacles_"+method),json(
                    "{\"type\":\"minecraft:"+(method.equals("campfire")?"campfire_cooking":method)
                    +"\",\"ingredient\":{\"tag\":\"oceanic_delight:squid_tentacles\"},\"cookingtime\":"+time
                    +",\"experience\":1,\"result\":\"oceanic_delight:grilled_squid_tentacles\"}"));
        }
        check(SquidRecipeCompat.apply(recipes,item->true)==6&&recipes.size()==6,"three tagged cooking methods become six explicit recipes");
        for(var method:List.of("campfire","smelting","smoking")) {
            var normal=recipes.get(id("oceanic_delight:squid_tentacles_"+method)).getAsJsonObject();
            var glow=recipes.get(SquidRecipeCompat.glowRecipe(method)).getAsJsonObject();
            check(normal.getAsJsonObject("ingredient").get("item").getAsString().equals("oceanic_delight:squid_tentacles")
                    &&glow.getAsJsonObject("ingredient").get("item").getAsString().equals("oceanic_delight:glow_squid_tentacles"),
                    "ordinary and glowing inputs remain distinct: "+method);
            check(normal.get("result").equals(glow.get("result"))&&normal.get("cookingtime").equals(glow.get("cookingtime"))
                    &&normal.get("experience").equals(glow.get("experience")),"native output, time, XP retained: "+method);
        }
        check(SquidRecipeCompat.apply(recipes,item->true)==0,"explicit cooking split is idempotent");
        var empty=new HashMap<ResourceLocation,JsonElement>();
        check(SquidRecipeCompat.apply(empty,item->false)==0&&empty.isEmpty(),"missing optional mods produce no recipes");
    }

    private static void testCookedAndGlowing() {
        String cooked="forge:cooked_fishes/squid";
        String glow="cuisinejournal:glow_squid";
        var recipes=new LinkedHashMap<ResourceLocation,JsonElement>();
        var burger=id("miners_delight:cave_hamburger");
        var original=json("""
            {"type":"minecraft:crafting_shapeless","ingredients":[{"tag":"forge:bread"},
             [{"item":"miners_delight:crunchy_bar"},{"item":"miners_delight:vegan_patty"}],
             {"item":"miners_delight:smoked_bat_wing"},
             [{"item":"miners_delight:baked_tentacles"},{"item":"minecraft:brown_mushroom"}],
             [{"item":"miners_delight:baked_tentacles"},{"item":"minecraft:brown_mushroom"}]],
             "result":{"item":"miners_delight:cave_hamburger"}}
            """);
        recipes.put(burger,original);
        var skewer=id("oceanic_delight:seafood_skewer");
        recipes.put(skewer,json("""
            {"type":"minecraft:crafting_shaped","key":{"a":{"item":"oceanic_delight:fried_shrimp"},
             "b":{"item":"oceanic_delight:grilled_squid_tentacles"},
             "c":{"tag":"forge:sea_pickles"},"d":{"item":"minecraft:stick"}},
             "pattern":["abc","d  "],"result":{"item":"oceanic_delight:seafood_skewer","count":1}}
            """));
        var stew=id("oceanic_delight:glow_squid_stew");
        recipes.put(stew,json("""
            {"type":"farmersdelight:cooking","ingredients":[{"item":"oceanic_delight:glow_squid_tentacles"},
             {"item":"farmersdelight:tomato_sauce"},{"tag":"forge:crops/onion"},{"item":"minecraft:brown_mushroom"}],
             "result":{"item":"oceanic_delight:glow_squid_stew"},"cookingtime":200,"experience":1,
             "container":{"item":"minecraft:bowl"}}
            """));
        check(SquidRecipeCompat.apply(recipes,item->true)==3,"cooked and glowing paths patched");
        var ingredients=recipes.get(burger).getAsJsonObject().getAsJsonArray("ingredients");
        check(countTags(recipes.get(burger),cooked)==2,"burger keeps two squid slots");
        for(int slot:List.of(3,4)) {
            var alternatives=ingredients.get(slot).getAsJsonArray();
            check(alternatives.size()==2&&alternatives.get(0).getAsJsonObject().get("tag").getAsString().equals(cooked)
                    &&alternatives.get(1).getAsJsonObject().get("item").getAsString().equals("minecraft:brown_mushroom"),
                    "each burger slot preserves the mushroom OR branch");
        }
        check(ingredients.get(0).equals(original.getAsJsonObject().getAsJsonArray("ingredients").get(0))
                &&ingredients.get(1).equals(original.getAsJsonObject().getAsJsonArray("ingredients").get(1)),
                "bread and patty alternatives unchanged");
        check(countTags(recipes.get(skewer),cooked)==1&&recipes.get(skewer).getAsJsonObject().getAsJsonArray("pattern")
                .equals(json("[\"abc\",\"d  \"]")),"shaped seafood skewer pattern retained");
        check(countTags(recipes.get(stew),glow)==1&&recipes.get(stew).toString().contains("minecraft:brown_mushroom")
                &&recipes.get(stew).getAsJsonObject().get("cookingtime").getAsInt()==200,
                "glowing stew changes only squid input");
        check(SquidRecipeCompat.apply(recipes,item->true)==0,"cooked and glowing patch idempotent");
    }

    private static void testTags() throws Exception {
        var broad=members("forge:tentacles");var raw=members(RAW);var tentacles=members(RAW_TENTACLES);
        check(broad.size()==14 && raw.size()==15 && tentacles.size()==9,"explicit additions contain all audited candidates including grilling/dumplings");
        check(!raw.contains("oceansdelight:cut_tentacles"),"cut tentacles stay outside the broad raw squid tag");
        check(tentacles.stream().allMatch(raw::contains)&&tentacles.stream().allMatch(broad::contains),"raw tentacles included in both broad tags");
        check(!tentacles.contains("oceansdelight:cut_tentacles")&&!tentacles.contains("rusticdelight:calamari_slice")
                &&!tentacles.contains("miners_delight:squid"),"raw skewer tag excludes cut products and whole squid");
        check(raw.stream().noneMatch(item->item.contains("cooked")||item.contains("baked")||item.contains("grilled")),
                "raw squid tag never adds cooked food");
        var cooked=members("forge:cooked_fishes/squid");
        var glow=members("cuisinejournal:glow_squid");
        check(cooked.size()==9&&cooked.contains("miners_delight:baked_squid")&&cooked.contains("miners_delight:baked_tentacles"),
                "cooked tag includes whole, tentacles and slices including Miner's own food");
        check(cooked.stream().noneMatch(raw::contains),"cooked tag never adds raw ingredients");
        check(glow.size()==5&&glow.stream().allMatch(item->item.contains("glow_squid")),
                "glow tag includes only distinct glowing squid ingredients");
        check(glow.contains("crabbersdelight:cooked_glow_squid_tentacles")
                &&!glow.contains("minecraft:glow_ink_sac")&&!glow.contains("oceanic_delight:glow_squid_stew"),
                "all glowing ingredient states included, ink and finished meals excluded");
        var mods=List.of("miners_delight","culturaldelights","oceansdelight","oceanic_delight","crabbersdelight","rusticdelight");
        for(int combination=0;combination<64;combination++) {
            var loaded=new HashSet<String>();
            for(int i=0;i<6;i++)if((combination&(1<<i))!=0)loaded.add(mods.get(i));
            var available=tentacles.stream().filter(item->loaded.contains(item.split(":")[0])).toList();
            check(available.stream().allMatch(item->raw.contains(item)&&broad.contains(item)),"partial install retains raw tentacle tag relationship: "+combination);
        }
    }
    private static Set<String> members(String tag) throws Exception {
        var parts=tag.split(":",2);
        try(var input=SquidRecipeCompatTest.class.getResourceAsStream("/data/"+parts[0]+"/tags/items/"+parts[1]+".json")) {
            var root=JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(input))).getAsJsonObject();
            check(!root.get("replace").getAsBoolean(),"tag additions preserve other mods: "+tag);
            var result=new HashSet<String>();
            for(var value:root.getAsJsonArray("values")) {
                check(value.isJsonObject()&&!value.getAsJsonObject().get("required").getAsBoolean(),"missing item optional: "+value);
                result.add(value.getAsJsonObject().get("id").getAsString());
            }
            return result;
        }
    }

    private static void installed() throws Exception {
        String mods=System.getenv("CUISINEJOURNAL_COMPAT_MODS");if(mods==null)return;
        try(var paths=Files.list(Path.of(mods))) {
            for(var path:paths.filter(p->p.toString().endsWith(".jar")).toList())try(var jar=new JarFile(path.toFile())) {
                var dumplings=new LinkedHashMap<ResourceLocation,JsonElement>();
                for(int i=1;i<=4;i++) {
                    String recipe="dumpling/calamari_boiled_dumpling_recipe_"+i;
                    var resource=jar.getJarEntry("data/dumplings_delight/recipes/"+recipe+".json");
                    if(resource==null)continue;
                    try(var reader=new java.io.InputStreamReader(jar.getInputStream(resource),java.nio.charset.StandardCharsets.UTF_8)) {
                        dumplings.put(id("dumplings_delight:"+recipe),JsonParser.parseReader(reader));
                    }
                }
                if(!dumplings.isEmpty()) {
                    var first=dumplings.keySet().iterator().next();
                    var expected=dumplings.get(first).deepCopy().getAsJsonObject();
                    expected.getAsJsonArray("ingredients").set(1,json("{\"tag\":\""+RAW+"\"}"));
                    SquidRecipeCompat.apply(dumplings,item->true);
                    check(dumplings.size()==1 && dumplings.get(first).equals(expected),
                            "installed dumpling recipes collapse to raw squid tag without other changes");
                    check(SquidRecipeCompat.apply(dumplings,item->true)==0,"installed dumpling collapse is idempotent");
                }
                for(String resource:List.of("data/minersdelight/recipes/cooking/takoyaki.json",
                        "data/miners_delight/recipes/cooking/takoyaki.json","data/rusticdelight/recipes/calamari_roll.json",
                        "data/culturaldelights/recipes/calamari_roll.json","data/oceansdelight/recipes/tentacle_on_a_stick.json",
                        "data/crabbersdelight/recipes/squid_kebob.json","data/oceanic_delight/recipes/squid_and_pickles.json",
                        "data/oceanic_delight/recipes/seafood_skewer.json","data/miners_delight/recipes/cave_hamburger.json",
                        "data/miners_delight/recipes/tentacles_on_a_stick.json","data/oceanic_delight/recipes/glow_squid_stew.json",
                        "packs/soup/data/farmersdelight/recipes/stockpot/glow_squid_stew.json",
                        "data/oceanic_delight/recipes/squid_tentacles_campfire.json","data/oceanic_delight/recipes/squid_tentacles_smelting.json",
                        "data/oceanic_delight/recipes/squid_tentacles_smoking.json")) {
                    var entry=jar.getJarEntry(resource);if(entry==null)continue;
                    JsonElement original;try(var reader=new java.io.InputStreamReader(jar.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)){original=JsonParser.parseReader(reader);}
                    String standard=resource.startsWith("packs/")?resource.substring(resource.indexOf("data/")):resource;
                    String[] parts=standard.split("/",4);var recipeId=id(parts[1]+":"+parts[3].replace(".json",""));
                    var actual=new HashMap<ResourceLocation,JsonElement>();actual.put(recipeId,original);
                    check(SquidRecipeCompat.apply(actual,item->true)>0,"installed host recipe repaired: "+path.getFileName()+" / "+recipeId);
                    check(SquidRecipeCompat.apply(actual,item->true)==0,"installed repair idempotent: "+recipeId);
                }
            }
        }
    }
    private static int countTags(JsonElement element,String tag) {
        int count=0;
        if(element.isJsonObject())for(var entry:element.getAsJsonObject().entrySet())
            count+=entry.getKey().equals("tag")&&entry.getValue().isJsonPrimitive()&&entry.getValue().getAsString().equals(tag)?1:countTags(entry.getValue(),tag);
        else if(element.isJsonArray())for(var child:element.getAsJsonArray())count+=countTags(child,tag);
        return count;
    }
    private static JsonElement json(String text){return JsonParser.parseString(text);}
    private static ResourceLocation id(String text){return ResourceLocation.parse(text);}
    private static void check(boolean passed,String message){if(!passed)throw new AssertionError(message);checks++;}
}
