package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.recipe.*;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FlavorSystemTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("flavor-system");
        try {
            Files.writeString(directory.resolve("cuisinejournal-ingredients.json"),"""
                {"flavor":{"test:sugar":{"source":true,"flavors":["test:sweet","test:rich","test:sweet"]},
                 "test:other":{"source":true,"flavors":["test:rich"]},
                 "test:disabled_source":{"source":false,"flavors":["test:rich"]},
                 "test:unknown":{"source":true,"flavors":["test:missing"]},
                 "test:inline":{"source":true,"stages":[{"min_stars":0,"modifiers":{"health_points":1}}]}},
                 "tagFlavor":{"forge:sugar":{"source":true,"flavors":["test:sweet"]}}}
                """);
            IngredientCatalogueConfig.load(directory);
            levelBonusTests();
            IngredientCatalogueConfig.load(directory);
            check(IngredientCatalogueConfig.current().flavor(id("sugar")).flavors().size()==2,"binding removes duplicate preset ids");
            var encoded=IngredientCatalogueConfig.current().flavor(id("sugar")).toJson();
            check(IngredientCatalogueConfig.Flavor.fromJson(encoded).flavors().size()==2,"binding serialization preserves multiple ids");
            IngredientCatalogueConfig.load(directory);
            check(IngredientCatalogueConfig.current().flavor(id("sugar")).flavors().size()==2
                    &&IngredientCatalogueConfig.current().flavor(id("inline")).stages().get(0).modifiers().healthPoints()==1,
                    "existing config rewrite and reload preserves ids and numeric stages");
            var sweet=FlavorPresets.read(id("sweet"),json("""
                {"name":"Sweet","stages":[{"min_stars":0,"food_modifiers":{"duration_multiplier":-0.6},
                    "drink_modifiers":{"duration_multiplier":-0.2}},
                    {"min_stars":4,"food_modifiers":{"duration_multiplier":-0.4}}]}
                """));
            var rich=FlavorPresets.read(id("rich"),json("""
                {"name":"Rich","stages":[{"min_stars":2,"modifiers":{"health_points":2,"health_multiplier":0.1,
                    "regen_per_second":0.2,"regen_multiplier":0.25},
                    "effects":[{"id":"minecraft:strength","level":2,"duration_seconds":30}]}]}
                """));
            var presets=Map.of(sweet.id(),sweet,rich.id(),rich);
            for (String target : List.of("all","food","drink")) {
                var scoped = FlavorPresets.read(sweet.id(),json("""
                    {"name":"Scoped","stages":[{"min_stars":0,"effect_target":"%s",
                     "effects":[{"id":"minecraft:speed"}],
                     "effect_level_bonuses":[{"id":"minecraft:strength","levels":1}]}]}
                    """.formatted(target)));
                check(FlavorPresets.read(scoped.id(),scoped.toJson()).equals(scoped),
                        "effect target survives preset serialization: "+target);
                for (boolean isDrink : new boolean[]{false,true}) {
                    var applied = CuisineFlavorEffects.resolveSources(List.of("test:sugar"),2,isDrink,Map.of(sweet.id(),scoped));
                    boolean expected = target.equals("all") || target.equals(isDrink ? "drink" : "food");
                    check((!applied.effects().isEmpty()) == expected && (!applied.effectLevelBonuses().isEmpty()) == expected,
                            "effects and level bonuses obey target "+target+" for drink="+isDrink);
                }
            }
            check(rich.stages().get(0).effectTarget().equals("all"),
                    "legacy presets default to common effects without guessing numeric target");
            var refs=List.of("test:sugar","test:other","#forge:sugar","#c:sugar","test:sugar");
            var low=CuisineFlavorEffects.resolveSources(refs,0,false,presets);
            check(low.effects().isEmpty()&&close(low.modifiers().duration(120),48),"low-star numeric-only flavor works and rich is locked");
            var resolved=CuisineFlavorEffects.resolveSources(refs,2,false,presets);
            check(resolved.flavors().size()==2&&resolved.effects().size()==1,"same flavor via several items/tags applies once");
            boolean previousEffects=ConfigManager.server().foodEffects.enabled;
            try {
                ConfigManager.server().foodEffects.enabled=false;
                var disabled=CuisineFlavorEffects.resolveSources(refs,2,false,presets);
                check(disabled.effects().equals(resolved.effects())&&disabled.effectLevelBonuses().equals(resolved.effectLevelBonuses())
                        &&disabled.modifiers().equals(resolved.modifiers()),"native effect switch preserves flavor potions and numeric bonuses");
                var bonusPreset=FlavorPresets.read(sweet.id(),json("""
                    {"name":"Bonus","stages":[{"min_stars":0,"effects":[{"id":"minecraft:speed"}],
                    "effect_level_bonuses":[{"id":"minecraft:strength","levels":2}]}]}
                    """));
                var independent=CuisineFlavorEffects.resolveSources(List.of("test:sugar"),2,false,Map.of(sweet.id(),bonusPreset));
                check(independent.effects().size()==1
                        &&independent.effectLevelBonuses().get(ResourceLocation.parse("minecraft:strength"))==2,
                        "native off leaves flavor potion and level bonus enabled");
            } finally {ConfigManager.server().foodEffects.enabled=previousEffects;}
            boolean previousFlavors=ConfigManager.server().flavors.enabled;
            try {
                ConfigManager.server().flavors.enabled=false;
                var disabled=CuisineFlavorEffects.resolveSources(refs,2,false,presets);
                check(disabled.effects().isEmpty()&&disabled.effectLevelBonuses().isEmpty()
                        &&disabled.modifiers().equals(FlavorModifiers.NONE)&&disabled.flavors().isEmpty(),
                        "flavor master switch stops all inherited content without deleting presets");
                check(FlavorPresets.read(sweet.id(),sweet.toJson()).equals(sweet),"disabled flavor preset remains serializable");
            } finally {ConfigManager.server().flavors.enabled=previousFlavors;}
            for (boolean youkai : new boolean[]{false, true}) {
                CuisineEffectIds.configure(youkai);
                try {
                    var maturity = new FlavorStage(0, List.of(
                            new FlavorStage.Effect(CuisineEffectIds.MATURE,1,30,1),
                            new FlavorStage.Effect(CuisineEffectIds.YOUKAI_MATURE,2,30,1)),
                            FlavorModifiers.NONE,FlavorModifiers.NONE,FlavorModifiers.NONE,List.of(
                            new FlavorStage.EffectLevelBonus(CuisineEffectIds.MATURE,1),
                            new FlavorStage.EffectLevelBonus(CuisineEffectIds.YOUKAI_MATURE,2)));
                    var maturePreset = new FlavorPresets.Preset(sweet.id(),"Mature","",true,List.of(maturity));
                    var adapted = CuisineFlavorEffects.resolveSources(List.of("test:sugar"),2,false,
                            Map.of(sweet.id(),maturePreset));
                    var effectId = CuisineEffectIds.resolve(CuisineEffectIds.MATURE);
                    check(adapted.effects().size()==1&&adapted.effects().get(0).id().equals(effectId)
                            &&adapted.effects().get(0).level()==2,"provider aliases resolve and deduplicate actual effects");
                    check(adapted.effectLevelBonuses().get(effectId)==3,
                            "level bonuses combine using the active provider id");
                } finally { CuisineEffectIds.configure(false); }
            }
            check(close(resolved.modifiers().health(10),13.2)&&close(resolved.modifiers().regen(0.4),0.75),"flat additions precede multiplier bonus");
            check(close(resolved.modifiers().duration(120),48),"repeated sweet flavor cannot repeatedly shorten food");
            check(resolved.modifiers().durationTicks(2400)==960,"runtime duration rounds to ticks rather than whole seconds");
            var drink=CuisineFlavorEffects.resolveSources(refs,2,true,presets);
            check(close(drink.modifiers().duration(600),480),"drink-specific duration is distinct from food");
            var high=CuisineFlavorEffects.resolveSources(refs,5,false,presets);
            check(close(high.modifiers().duration(120),72),"higher stage replaces previous numeric stage");
            check(CuisineFlavorEffects.resolveSources(List.of("test:disabled_source"),5,false,presets).modifiers().equals(FlavorModifiers.NONE),"source false blocks presets");
            check(CuisineFlavorEffects.resolveSources(List.of("test:unknown"),5,false,presets).problems().size()==1,"unknown preset produces diagnostic without invented values");
            var disabled=new FlavorPresets.Preset(rich.id(),rich.name(),rich.description(),false,rich.stages());
            check(CuisineFlavorEffects.resolveSources(List.of("test:other"),5,false,Map.of(rich.id(),disabled)).effects().isEmpty(),"disabled preset has no runtime effect");
            check(FlavorPresets.read(sweet.id(),sweet.toJson()).equals(sweet),"full preset round trip preserves target-specific modifiers");
            var first=FlavorModifiers.read(json("""
                {"health_multiplier":0.2}
                """));
            var second=FlavorModifiers.read(json("""
                {"health_multiplier":0.3}
                """));
            check(close(first.plus(second).health(10),15),"different flavor multipliers add instead of exponential multiplication");
            var negative=FlavorModifiers.read(json("""
                {"health_points":-30,"regen_multiplier":-2,"duration_seconds":-1000}
                """));
            check(negative.health(10)==0&&negative.regen(1)==0&&negative.duration(60)==0,"negative modifiers cannot create negative runtime stats");
            check(negative.durationTicks(1200)==20,"negative duration retains one-second technical minimum");
            check(new FlavorModifiers(0,0,0,0,2592000,100).durationTicks(Integer.MAX_VALUE)==Integer.MAX_VALUE,
                    "huge duration saturates instead of overflowing");
            var forward=CuisineFlavorEffects.resolveSources(List.of("test:inline","test:sugar"),2,false,presets);
            var reverse=CuisineFlavorEffects.resolveSources(List.of("test:sugar","test:inline"),2,false,presets);
            check(forward.modifiers().equals(reverse.modifiers()),"inline legacy stages and presets combine independent of reference order");
            check(CuisineFlavorEffects.resolveSources(List.of("test:inline"),0,false,presets).modifiers().healthPoints()==1,
                    "legacy inline numeric stages need no named preset");
            var compatibility=IngredientCompatRules.resolve(List.of(new IngredientCompatRules.RuleFile(
                    id("cuisinejournal/compat/flavors.json"),"test_pack",json("""
                    {"item_flavor":{"test:ingredient":{"source":true,"flavors":["test:sweet","test:rich"]}}}
                    """))),mod->true,null);
            check(compatibility.itemFlavor(id("ingredient")).flavors().size()==2,"compat datapacks share the new multi-flavor binding parser");
            invalid("""
                {"health_points":"3"}
                """);invalid("""
                {"duration_multiplier":1e100}
                """);invalid("""
                {"regen_per_second":1e1000}
                """);
            var mixed=FlavorStage.read(JsonParser.parseString("""
                [{"min_stars":0,"modifiers":{"health_points":1},"food_modifiers":{"health_points":2}}]
                """).getAsJsonArray());
            check(mixed.get(0).modifiersFor(false).healthPoints()==3&&mixed.get(0).modifiersFor(true).healthPoints()==1,"common and target-specific modifiers combine");
            testLoading(directory,sweet,rich);
            var tagRules=json("""
                {"compatOverrides":{"merge_tags":[{"id":"test:sugar_group","tags":["forge:sugar","test:sugars"],
                 "flavor":{"source":true,"flavors":["test:sweet","test:rich"]}}],
                 "item_aliases":[{"item":"test:sugar_piece","into":"test:sugar"}]},
                 "flavor":{"test:sugar":{"source":true,"flavors":["test:sweet","test:rich"]}}}
                """);
            Files.writeString(directory.resolve("cuisinejournal-ingredients.json"),tagRules.toString());
            IngredientCatalogueConfig.load(directory);
            check(IngredientCatalogueConfig.setLocalFlavor(false,id("cocoa"),IngredientCatalogueConfig.Flavor.of(true,List.of(id("rich")),null))
                    &&IngredientCatalogueConfig.current().flavor(id("cocoa")).flavors().equals(List.of(id("rich"))),"in-game binding is saved and read back");
            check(IngredientCatalogueConfig.current().localFlavors(false).containsKey(id("cocoa")),"local bindings are listed for export");
            check(IngredientCatalogueConfig.setLocalFlavor(false,id("cocoa"),null)&&IngredientCatalogueConfig.current().flavor(id("cocoa"))==null,"reverting a binding removes it");
            ResourceManager empty=(ResourceManager)java.lang.reflect.Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                    new Class[]{ResourceManager.class},(proxy,method,arguments)->method.getName().equals("listResources")?Map.of():null);
            IngredientCompatRules.load(empty);
            var grouped=CuisineFlavorEffects.resolveSources(List.of("#test:sugars","#forge:sugar","#test:sugar_group",
                    "test:sugar_piece","test:sugar"),2,false,presets);
            check(grouped.flavors().size()==2&&grouped.effects().size()==1&&close(grouped.modifiers().health(10),13.2),
                    "merged tags and aliased ingredient sources retain preset-level deduplication");
            checkRuntimeContract();
        } finally {
            FlavorPresets.clear();IngredientCompatRules.clear();
            try(var files=Files.list(directory)){for(Path file:files.toList())Files.delete(file);}
            Files.delete(directory);
        }
        System.out.println("FlavorSystemTest: "+checks+" checks passed");
    }
    private static void testLoading(Path directory,FlavorPresets.Preset sweet,FlavorPresets.Preset rich) throws Exception {
        Map<ResourceLocation,Resource> resources=new LinkedHashMap<>();
        resources.put(ResourceLocation.parse("test:cuisinejournal/flavors/sweet.json"),resource(sweet.toJson().toString()));
        resources.put(ResourceLocation.parse("test:cuisinejournal/flavors/rich.json"),resource(rich.toJson().toString()));
        ResourceManager manager=(ResourceManager)java.lang.reflect.Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class[]{ResourceManager.class},(proxy,method,args)->method.getName().equals("listResources")?resources:method.getName().equals("listPacks")?java.util.stream.Stream.empty():null);
        Files.writeString(directory.resolve("cuisinejournal-flavors.json"),"""
            {"presets":{"test:sweet":{"name":"Local Sweet","stages":[{"min_stars":0,"modifiers":{"duration_multiplier":-0.5}}]},
             "test:broken":{"stages":[{"min_stars":9}]}}}
            """);
        FlavorPresets.reload(manager,directory);
        check(FlavorPresets.get(id("sweet")).name().equals("Local Sweet")&&FlavorPresets.get(id("rich"))!=null,"local preset overrides pack while unrelated packs survive malformed local entry");
        check(FlavorPresets.defaults().get(id("sweet")).equals(sweet)&&FlavorPresets.overrides().size()==1,"defaults and local layers available for future restore editor");
        Files.delete(directory.resolve("cuisinejournal-flavors.json"));
        FlavorPresets.reload(manager,directory);
        check(FlavorPresets.get(id("sweet")).equals(sweet),"removing local override restores pack after reload");
        // In-game editor: local copies, origins, generated ids and icons.
        var edited=new FlavorPresets.Preset(sweet.id(),"Edited",sweet.description(),false,sweet.stages(),ResourceLocation.parse("minecraft:sugar"));
        check(FlavorPresets.putLocal(edited)&&FlavorPresets.origin(sweet.id())==1&&!FlavorPresets.get(sweet.id()).enabled(),"editing a pack preset makes a local copy");
        check(FlavorPresets.read(edited.id(),edited.toJson()).equals(edited),"icon survives the JSON round trip");
        var created=FlavorPresets.newId("Bitter Note!");
        check(created.toString().equals("cuisinejournal_custom:bitter_note"),"new ids come from the name in the custom namespace");
        check(FlavorPresets.putLocal(new FlavorPresets.Preset(created,"Bitter",null,true,List.of()))&&FlavorPresets.origin(created)==2,"created presets are origin 2");
        FlavorPresets.reload(manager,directory);
        check(FlavorPresets.get(created)!=null&&FlavorPresets.get(sweet.id()).name().equals("Edited"),"local edits are written to disk");
        check(FlavorPresets.removeLocal(sweet.id())&&FlavorPresets.get(sweet.id()).equals(sweet),"reverting restores the pack preset");
        check(FlavorPresets.clearLocal()&&FlavorPresets.get(created)==null,"clearing removes created presets");
        Files.deleteIfExists(directory.resolve("cuisinejournal-flavors.json"));
        FlavorPresets.reload(manager,directory);
    }
    private static Resource resource(String json) {
        var pack=(net.minecraft.server.packs.PackResources)java.lang.reflect.Proxy.newProxyInstance(
                net.minecraft.server.packs.PackResources.class.getClassLoader(),new Class[]{net.minecraft.server.packs.PackResources.class},
                (proxy,method,args)->method.getName().equals("packId")?"test_pack":null);
        return new Resource(pack,()->new java.io.ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
    private static void levelBonusTests() throws Exception {
        var stage=FlavorStage.read(JsonParser.parseString("""
            [{"min_stars":2,"effect_level_bonuses":[{"id":"minecraft:strength","levels":2}]}]
            """).getAsJsonArray()).get(0);
        check(stage.effectLevelBonuses().get(0).levels()==2,"stage reads selectable effect level increment");
        var array=new JsonArray();array.add(stage.toJson());
        check(FlavorStage.read(array).get(0).equals(stage),"effect level bonuses survive stage serialization");
        check(new FlavorStage(0,List.of()).effectLevelBonuses().isEmpty(),"legacy stage constructors remain compatible");
        var strength=ResourceLocation.parse("minecraft:strength");
        var speed=ResourceLocation.parse("minecraft:speed");
        var original=List.of(new CuisineFoodDefinitions.Effect(strength,1,0.5F,600),
                new CuisineFoodDefinitions.Effect(speed,2,1,100));
        var enhanced=FlavorEffectLevels.apply(original,Map.of(strength,2));
        check(enhanced.get(0).level()==3&&enhanced.get(0).activeFraction()==0.5F&&enhanced.get(0).durationTicks()==600,
                "existing strength gains two levels without changing duration");
        check(enhanced.get(1).equals(original.get(1)),"other effect levels are unaffected");
        var granted=FlavorEffectLevels.apply(List.of(),Map.of(strength,2));
        check(granted.size()==1&&granted.get(0).level()==2&&granted.get(0).activeFraction()==1&&granted.get(0).durationTicks()==0,
                "missing effect starts at bonus level and lasts full Valheim food time");
        check(FlavorEffectLevels.apply(List.of(),Map.of(strength,1),600).get(0).durationTicks()==600,
                "new vanilla bonus effect receives thirty second default duration");
        check(FlavorEffectLevels.apply(original,Map.of(strength,2,speed,1)).size()==2,
                "existing effects are enhanced once without duplicate grant");
        check(FlavorEffectLevels.level(250,255)==256&&FlavorEffectLevels.level(1,Integer.MAX_VALUE)==256,
                "final effect level cannot overflow Minecraft amplifier byte");
        var dir=Files.createTempDirectory("flavor-level-bonuses");
        try {
            Files.writeString(dir.resolve("cuisinejournal-ingredients.json"),"""
                {"flavor":{"test:source":{"source":true,"flavors":["test:a","test:b","test:a"]}}}
                """);
            IngredientCatalogueConfig.load(dir);
            var a=new FlavorPresets.Preset(id("a"),"A","",true,List.of(stage));
            var b=new FlavorPresets.Preset(id("b"),"B","",true,List.of(new FlavorStage(0,List.of(),
                    FlavorModifiers.NONE,FlavorModifiers.NONE,FlavorModifiers.NONE,List.of(new FlavorStage.EffectLevelBonus(strength,1)))));
            var resolved=CuisineFlavorEffects.resolveSources(List.of("test:source","test:source"),2,false,Map.of(a.id(),a,b.id(),b));
            check(resolved.effectLevelBonuses().get(strength)==3,"different flavors add increments while repeated flavor sources deduplicate");
            check(FlavorEffectLevels.apply(resolved.effects(),resolved.effectLevelBonuses()).get(0).level()==3,
                    "bonus-only flavors grant combined levels without implicit level one");
            var low=CuisineFlavorEffects.resolveSources(List.of("test:source"),0,false,Map.of(a.id(),a,b.id(),b));
            check(low.effectLevelBonuses().get(strength)==1,"level increments obey downstream star stage selection");
        }finally{Files.delete(dir.resolve("cuisinejournal-ingredients.json"));Files.delete(dir);}
        for(String invalid:List.of("0","-1","256","1.5")) {
            boolean rejected=false;
            try {FlavorStage.read(JsonParser.parseString("[{\"min_stars\":0,\"effect_level_bonuses\":[{\"id\":\"minecraft:strength\",\"levels\":"+invalid+"}]}]").getAsJsonArray());}
            catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"invalid effect level increment rejected: "+invalid);
        }
    }

    private static void checkRuntimeContract() throws Exception {
        var node=new org.objectweb.asm.tree.ClassNode();
        try(var input=FlavorSystemTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/food/CuisineFoodDefinition.class")) {
            new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(node,0);
        }
        var from=node.methods.stream().filter(method->method.name.equals("from")).findFirst().orElseThrow();
        List<String> calls=new ArrayList<>();
        for(var instruction:from.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call)calls.add(call.owner+"."+call.name);
        String root="com/chi__you/cuisinejournal/";
        int slots=calls.indexOf(root+"food/CuisineFoodConfig.slotsFor");
        int resolve=calls.indexOf(root+"food/CuisineFlavorEffects.resolveStack");
        int health=calls.indexOf(root+"recipe/FlavorModifiers.health");
        check(slots>=0&&resolve>slots&&calls.indexOf(root+"food/CuisineFoodMath.foodHealthPoints")<health,
                "runtime computes fixed slots and reduced star baseline before applying flavors");
        check(resolve<health&&health<calls.indexOf(root+"food/CuisineFoodMath.roundedHealthPoints"),
                "flavor health is applied before final half-heart rounding");
        check(resolve<calls.indexOf(root+"recipe/FlavorModifiers.durationTicks"),"final numeric duration is routed through flavor minimum/overflow guard");
    }
    private static void invalid(String json) {try{FlavorModifiers.read(json(json));}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError("Accepted invalid modifier");}
    private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    private static ResourceLocation id(String path){return ResourceLocation.parse("test:"+path);}
    private static boolean close(double a,double b){return Math.abs(a-b)<1e-6;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
