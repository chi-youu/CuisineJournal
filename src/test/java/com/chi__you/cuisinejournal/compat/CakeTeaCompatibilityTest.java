package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import static com.chi__you.cuisinejournal.compat.UnificationContent.id;

public final class CakeTeaCompatibilityTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        var rules=JsonParser.parseString(Files.readString(Path.of("src/main/resources/cuisinejournal/unification.json"))).getAsJsonObject();
        Set<ResourceLocation> all=new HashSet<>();
        rules.entrySet().forEach(e->e.getValue().getAsJsonObject().getAsJsonArray("items").forEach(v->all.add(id(v.getAsString()))));
        var plan=UnificationContent.compile(rules,true,all::contains,mod->true);
        var available=new HashSet<ResourceLocation>(List.of(id("oceanic_delight:squid_tentacles"),id("dumplings_delight:calamari"),
                id("kaleidoscope_grilling:squid_tentacle"),id("crabbersdelight:raw_squid_tentacles")));
        for(String winner:List.of("oceanic_delight:squid_tentacles","dumplings_delight:calamari","kaleidoscope_grilling:squid_tentacle","crabbersdelight:raw_squid_tentacles")){
            var fallback=UnificationContent.compile(rules,true,available::contains,mod->true);
            check(fallback.winner("cuisinejournal:tentacle").equals(id(winner)),"confirmed new raw tentacle priority "+winner);available.remove(id(winner));
        }
        check(calls(own("food/PlacedFoodConsumption"),"food/CakeFoodCompat","eating"),"direct eating dispatches cake identity");
        check(calls(own("mixin/food/VanillaFoodPropertiesMixin"),"food/CakeFoodCompat","properties"),"catalogue and hand tooltip can inspect cake-only properties");
        check(calls(own("recipe/PlacedServingRelations"),"food/CakeFoodCompat","serving"),"whole-to-real-slice relation uses same resolver");
        var serving=own("food/CakeFoodCompat").methods.stream().filter(m->m.name.equals("serving")).findFirst().orElseThrow();
        int exact=-1,vanilla=-1;
        for(int i=0;i<serving.instructions.size();i++){
            var insn=serving.instructions.get(i);
            if(insn instanceof FieldInsnNode field&&field.owner.equals("net/minecraft/world/level/block/Blocks")&&field.name.equals("CAKE"))exact=i;
            if(insn instanceof MethodInsnNode call&&call.owner.endsWith("/CuisineBlockFoods")&&call.name.equals("cake"))vanilla=i;
        }
        check(exact>=0&&vanilla>exact,"only exact vanilla cake uses farmer vanilla slice fallback");
        boolean guarded=false;
        for(var insn:serving.instructions)if(insn instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IF_ACMPNE)guarded=true;
        check(guarded,"vanilla fallback guarded by object identity, not CakeBlock superclass");
        var recipes=new LinkedHashMap<ResourceLocation,JsonElement>();
        var leaves=recipe("herbalbrews:green_tea_leaf");var dry=recipe("herbalbrews:dried_green_tea");
        var mixed=JsonParser.parseString("{type:'minecraft:crafting_shaped',pattern:['XXX','X B','BBB'],key:{X:{item:'herbalbrews:dried_green_tea'},B:{item:'herbalbrews:dried_black_tea'}},result:{item:'herbalbrews:mixed_tea_leaf_block'}}").getAsJsonObject();
        recipes.put(id("herbalbrews:green_tea_leaf_block"),leaves);recipes.put(id("herbalbrews:dried_green_tea_leaf_block"),dry);
        recipes.put(id("herbalbrews:mixed_tea_leaf_block"),mixed);
        UnificationRecipes.apply(recipes,plan);
        for(String name:List.of("green_tea_leaf_block","dried_green_tea_leaf_block")){
            var actual=recipes.get(id("herbalbrews:"+name)).getAsJsonObject();
            check(actual.getAsJsonArray("pattern").get(1).getAsString().equals("X X"),"eight-leaf process block avoids nine-leaf bag "+name);
        }
        check(recipes.get(id("herbalbrews:mixed_tea_leaf_block")).getAsJsonObject().get("pattern").equals(mixed.get("pattern")),"mixed block remains four green plus four black");
        var loot=new LinkedHashMap<ResourceLocation,JsonElement>();
        loot.put(id("herbalbrews:blocks/green_tea_leaf_block"),loot("herbalbrews:green_tea_leaf",9));
        loot.put(id("herbalbrews:blocks/mixed_tea_leaf_block"),loot("herbalbrews:green_tea_leaf",4));
        for(String name:List.of("black_tea_leaf_block","dried_out_green_tea_leaf_block","oolong_tea_leaf_block"))
            loot.put(id("herbalbrews:blocks/"+name),loot("herbalbrews:dried_black_tea",name.startsWith("black")?2:3));
        var originals=new LinkedHashMap<>(loot);
        check(UnificationTeaBlocks.loot(loot,plan)==2,"only raw and mixed block drops changed");
        check(loot.get(id("herbalbrews:blocks/green_tea_leaf_block")).toString().contains("8"),"unprocessed leaves return eight not nine");
        check(loot.get(id("herbalbrews:blocks/mixed_tea_leaf_block")).toString().contains("herbalbrews:dried_green_tea"),"mixed block never refunds fresh instead of dried green");
        for(String name:List.of("black_tea_leaf_block","dried_out_green_tea_leaf_block","oolong_tea_leaf_block"))
            check(originals.get(id("herbalbrews:blocks/"+name)).equals(loot.get(id("herbalbrews:blocks/"+name))),"native processing loss retained "+name);
        check(UnificationTeaBlocks.loot(loot,plan)==0,"tea drop repair idempotent");
        check(UnificationTeaBlocks.loot(new LinkedHashMap<>(originals),UnificationContent.EMPTY)==0,"default mode retains native tea blocks");
        loot.clear();
        var mixedLoot=JsonParser.parseString("{type:'minecraft:block',pools:[{rolls:1,conditions:[{condition:'minecraft:survives_explosion'}],entries:[{type:'minecraft:item',name:'herbalbrews:green_tea_leaf',functions:[{function:'minecraft:set_count',count:4}]},{type:'minecraft:item',name:'herbalbrews:dried_black_tea',functions:[{function:'minecraft:set_count',count:4}]}]}]}");
        loot.put(id("herbalbrews:blocks/mixed_tea_leaf_block"),mixedLoot);
        check(UnificationTeaBlocks.loot(loot,plan)==1,"real mixed block alternatives repaired");
        var pools=loot.get(id("herbalbrews:blocks/mixed_tea_leaf_block")).getAsJsonObject().getAsJsonArray("pools");
        check(pools.size()==2&&pools.get(0).getAsJsonObject().getAsJsonArray("entries").size()==1&&pools.get(1).getAsJsonObject().getAsJsonArray("entries").size()==1,"mixed refund returns both leaf types, not random either/or");
        check(pools.get(0).getAsJsonObject().get("conditions").equals(pools.get(1).getAsJsonObject().get("conditions")),"refund keeps native explosion condition for both ingredients");
        check(UnificationTeaBlocks.loot(loot,plan)==0,"mixed pool split idempotent");
        cakeSliceHealing();
        installed();
        System.out.println("CakeTeaCompatibilityTest: "+checks+" checks passed");
    }
    private static void cakeSliceHealing()throws Exception{
        var tag=JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/cuisinejournal/tags/items/instant_healing_cake_slices.json"))).getAsJsonObject();
        check(!tag.get("replace").getAsBoolean(),"cake healing tag remains extensible by datapacks");
        var ids=new HashSet<String>();
        for(var value:tag.getAsJsonArray("values")){
            var entry=value.getAsJsonObject();
            check(!entry.get("required").getAsBoolean(),"missing cake mod is optional "+entry.get("id"));
            check(ids.add(entry.get("id").getAsString()),"cake healing declaration is unique "+entry.get("id"));
        }
        check(ids.contains("#forge:cake_slices"),"third-party standard cake slices receive healing without mod dependency");
        for(String item:List.of("farmersdelight:cake_slice","farmersdelight:sweet_berry_cheesecake_slice",
                "veggiesdelight:carrot_cake_slice","vanillacookbook:chorus_cake_slice","farmersrespite:coffee_cake_slice",
                "bakery:chocolate_gateau_slice","bakery:linzer_tart_slice","cavedelight:slice_of_tectonic_cheesecake",
                "slime_delight:slice_of_grass_block_cake","exquisito:beloved_battenberg_cake_slice",
                "braziliandelight:carrot_cake_with_chocolate_slice","youkaishomecoming:red_velvet_cake_slice"))
            check(ids.contains(item),"audited cake slice included "+item);
        check(ids.stream().noneMatch(id->id.startsWith("displaydelight:")),"decorative plated cake models are not new edible slices");
        check(!ids.contains("minecraft:cake")&&!ids.contains("vanillacookbook:cheesecake")
                &&!ids.contains("natures_spirit:cheese_block")&&!ids.contains("farmersdelight:apple_pie_slice")
                &&!ids.contains("immortalers_delight:zea_pancake_slice"),"whole cakes, cheese, pies and pancakes do not receive cake-slice bonus");
        check(com.chi__you.cuisinejournal.food.CakeSliceHealing.HEALTH_POINTS==2F,"one heart equals two health points regardless of stars");
        check(calls(own("food/CakeSliceHealing"),"food/CuisineFoodRuntime","activeForCurrentThread"),
                "server consumption and client tooltip require the active Valheim runtime");
        check(calls(own("event/CulinaryHooks"),"food/CakeSliceHealing","onEaten"),"successful common eating callback awards slice healing");
        check(calls(own("food/DirectFoodHealing"),"food/CakeSliceHealing","points"),"tooltip and runtime share same tag and amount");
        check(calls(own("food/CuisineFoodTooltip"),"food/DirectFoodHealing","total"),"Valheim panel includes additive instant healing");
        check(calls(own("client/tooltip/NativeFoodEffectTooltip"),"food/DirectFoodHealing","total"),"Vanilla text tooltip includes additive instant healing");
        int awards=0,heals=0;
        for(var method:own("event/CulinaryHooks").methods)if(method.name.equals("onItemEaten"))for(var insn:method.instructions)
            if(insn instanceof MethodInsnNode call&&call.owner.endsWith("/CakeSliceHealing")&&call.name.equals("onEaten"))awards++;
        var bonus=own("food/CakeSliceHealing");
        for(var method:bonus.methods)for(var insn:method.instructions)if(insn instanceof MethodInsnNode call){
            if(call.name.equals("heal")&&call.desc.equals("(F)V"))heals++;
            check(!call.owner.endsWith("/CuisineGameplayRules")&&!call.owner.endsWith("/CuisineFoodQuality"),
                    "Valheim bonus does not depend on native effects or stars");
        }
        check(awards==1&&heals==1,"one success callback performs one extra direct heal, never one per stack count");
    }
    private static JsonObject recipe(String ingredient){return JsonParser.parseString("{type:'minecraft:crafting_shaped',pattern:['XXX','XXX','XXX'],key:{X:{item:'"+ingredient+"'}},result:{item:'herbalbrews:green_tea_leaf_block'}}").getAsJsonObject();}
    private static JsonElement loot(String item,int count){return JsonParser.parseString("{type:'minecraft:block',pools:[{rolls:1,entries:[{type:'minecraft:item',name:'"+item+"',functions:[{function:'minecraft:set_count',count:"+count+"}]}]}]}");}
    private static void installed()throws Exception{
        String dir=System.getenv("CUISINEJOURNAL_COMPAT_MODS");if(dir==null)return;
        Map<String,ClassNode> classes=new HashMap<>();
        Map<String,String> parents=new HashMap<>();
        var inspected=List.of("com/morallenplay/vanillacookbook/block/CheesecakeBlock","net/mehdinoui/veggiesdelight/common/block/CarrotCakeBlock",
                "dev/xkmc/twilightdelight/content/block/TwilightCakeBlock","net/hibiscus/naturespirit/blocks/CheeseBlock",
                "umpaz/farmersrespite/common/block/CoffeeCakeBlock");
        try(var files=Files.list(Path.of(dir))){for(var p:files.filter(f->f.toString().endsWith(".jar")&&!f.getFileName().toString().startsWith("cuisinejournal-")).toList())
            try(var jar=new JarFile(p.toFile())){for(var entry:Collections.list(jar.entries()))if(entry.getName().endsWith(".class")){
                try(var input=jar.getInputStream(entry)){
                    var reader=new ClassReader(input);parents.put(reader.getClassName(),reader.getSuperName());
                    if(inspected.contains(reader.getClassName())){
                        var node=new ClassNode();reader.accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);classes.put(node.name,node);
                    }
                }
            }}
        }
        for(String name:inspected){
            var host=classes.get(name);if(host==null)continue;
            check(host.superName.equals("net/minecraft/world/level/block/CakeBlock"),"verified misleading vanilla superclass "+name);
            boolean nutrition=false;for(var method:host.methods)for(var insn:method.instructions)
                if(insn instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/world/food/FoodData")&&call.name.equals("m_38707_"))nutrition=true;
            check(nutrition,"per-bite food signal remains observable "+name);
        }
        int cakes=0;
        for(var parent:parents.values()){
            var next=parent;var visited=new HashSet<String>();
            while(next!=null&&visited.add(next)){
                if(next.equals("net/minecraft/world/level/block/CakeBlock")){cakes++;break;}
                next=parents.get(next);
            }
        }
        check(cakes>=20,"installed cake subclass audit covers whole family, not two screenshots");
    }
    private static ClassNode own(String name)throws Exception{
        var node=new ClassNode();try(var input=CakeTeaCompatibilityTest.class.getResourceAsStream("/com/chi__you/cuisinejournal/"+name+".class")){
            new ClassReader(Objects.requireNonNull(input)).accept(node,0);
        }return node;
    }
    private static boolean calls(ClassNode node,String owner,String name){
        for(var m:node.methods)for(var instruction:m.instructions)
            if(instruction instanceof MethodInsnNode call&&call.owner.equals("com/chi__you/cuisinejournal/"+owner)&&call.name.equals(name))return true;return false;
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
