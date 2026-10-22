package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.recipe.*;
import com.chi__you.cuisinejournal.network.ExportCompletion;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class FlavorRepairTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var definition=JsonParser.parseString("{}").getAsJsonObject();
        check(CuisineFoodDefinitions.readFlavorSources(definition)==null,"omitted source field means automatic");
        definition.add("flavor_sources",new com.google.gson.JsonArray());
        check(CuisineFoodDefinitions.readFlavorSources(definition).isEmpty(),"explicit empty sources disable automatic inference");
        var implicit=new CuisineFoodDefinitions.Values(null,null,null,null,null,false,null);
        check(implicit.flavorSources()==null,"legacy constructor retains automatic semantics");
        var effect=new CuisineFoodDefinitions.Effect(id("effect"),1,0.5F,0);
        check(effect.activeTicks(12000)==6000,"proportional flavor lives five minutes of a ten minute food");
        check(effect.activeTicks(2400)==1200,"proportional flavor scales to shorter food");
        check(new CuisineFoodDefinitions.Effect(id("effect"),1,0.5F,600).activeTicks(12000)==600,
                "explicit non-flavor duration cap still works");
        check(new CuisineFoodDefinitions.Effect(id("effect"),1,0,0).activeTicks(12000)==0,"zero proportion has no lifetime");
        traceTests();
        storageCycleTests();
        availabilityTests();
        sourceFilterTests();
        drinkBonusTests();
        dishSearchPacketTests();
        check(CuisineFlavorEffects.flavorStars(false,null)==2,"untracked ingredients use two flavor stars without a stamp");
        check(CuisineFlavorEffects.flavorStars(true,null)==0,"unstamped dishes retain zero flavor stars");
        check(CuisineFlavorEffects.flavorStars(false,new com.chi__you.cuisinejournal.culinary.MealStamp(5,false,5,null,""))==2,
                "ingredient flavor baseline is fixed even if an old stamp exists");
        check(CuisineFlavorEffects.flavorStars(true,new com.chi__you.cuisinejournal.culinary.MealStamp(3,true,5,null,""))==4,
                "dishes retain their true stars and perfect bonus");
        exportTests();
        durationModeTests();
        contractTests();
        System.out.println("FlavorRepairTest: "+checks+" checks passed");
    }
    private static void traceTests() {
        var dish=id("sandwich");var bread=id("bread");var dough=id("dough");var wheat=id("wheat");
        var salt=id("salt");var tag=id("any_meat");var powder=id("powder");var cocoa=id("cocoa");
        Map<ResourceLocation,List<RecipeEntry.IngredientSlot>> graph=new LinkedHashMap<>();
        graph.put(dish,List.of(slot(bread),slot(powder),new RecipeEntry.IngredientSlot(List.of(id("beef"),id("pork")),List.of(),List.of(tag))));
        graph.put(bread,List.of(slot(dough)));graph.put(dough,List.of(slot(wheat),slot(salt)));
        graph.put(powder,List.of(slot(cocoa)));graph.put(cocoa,List.of(slot(powder)));
        var emptyTags=(java.util.function.Function<ResourceLocation,List<ResourceLocation>>)item->List.of();
        var automatic=(java.util.function.Function<ResourceLocation,List<String>>)item->null;
        var trace=FlavorTrace.trace(dish,item->graph.getOrDefault(item,List.of()),emptyTags,emptyTags,ref->ref.equals(bread.toString()),automatic);
        check(trace.sources().contains(bread.toString())&&!trace.sources().contains(dough.toString())&&!trace.sources().contains(wheat.toString()),
                "intermediate flavor source is a true stop point");
        check(trace.sources().contains(powder.toString())&&trace.sources().contains(cocoa.toString())&&!trace.truncated(),
                "multi-level processing and cycles terminate without losing other branches");
        check(trace.sources().contains("#"+tag)&&!trace.sources().contains(id("beef").toString())
                &&!trace.sources().contains(id("pork").toString()),"tag branch never unions member-specific flavors");
        var full=FlavorTrace.trace(dish,item->graph.getOrDefault(item,List.of()),emptyTags,emptyTags,ref->false,automatic);
        check(full.sources().contains(wheat.toString())&&full.sources().contains(salt.toString()),"unmarked intermediates recurse through all recipe levels");
        var explicit=FlavorTrace.trace(dish,item->graph.getOrDefault(item,List.of()),emptyTags,emptyTags,ref->false,
                item->item.equals(dish)?List.of():null);
        check(explicit.sources().isEmpty(),"explicit empty root blocks every auto source");
        var override=FlavorTrace.trace(dish,item->graph.getOrDefault(item,List.of()),emptyTags,emptyTags,ref->false,
                item->item.equals(bread)?List.of(salt.toString()):null);
        check(override.sources().contains(salt.toString())&&!override.sources().contains(wheat.toString()),"explicit intermediate sources replace its recipe branch");
        var inherited=FlavorTrace.trace(dish,item->graph.getOrDefault(item,List.of()),item->item.equals(bread)?List.of(id("grain")):List.of(),
                emptyTags,ref->ref.equals(bread.toString()),automatic);
        check(inherited.sources().contains("#"+id("grain")),"stop-source item still carries its tag flavor");
        Map<ResourceLocation,List<RecipeEntry.IngredientSlot>> large=new HashMap<>();
        for(int i=0;i<4100;i++)large.put(id("node"+i),List.of(slot(id("node"+(i+1)))));
        check(FlavorTrace.trace(id("node0"),item->large.getOrDefault(item,List.of()),emptyTags,emptyTags,ref->false,automatic).truncated(),
                "oversized graphs return diagnostic instead of hanging");
        singletonTagTests();
        fruitCauldronTests();
    }
    private static void fruitCauldronTests() {
        var water=id("water_state");var lemonWater=id("lemon_state");var fruit=id("fruit_state");
        var jam=id("jam_state");var jellyState=id("jelly_state");
        var lemon=id("lemon_slice");var peach=id("peach");var sugar=id("sugar");var slime=id("slime");
        var jamItem=id("jam");var jelly=id("jelly");var bottle=ResourceLocation.parse("minecraft:glass_bottle");
        List<FruitCauldronBridge.Step> steps=List.of(
                new FruitCauldronBridge.Step(water,lemon,1,lemonWater,null,0),
                new FruitCauldronBridge.Step(lemonWater,peach,3,fruit,null,0),
                new FruitCauldronBridge.Step(water,jamItem,3,fruit,null,0),
                new FruitCauldronBridge.Step(fruit,sugar,1,jam,null,0),
                new FruitCauldronBridge.Step(jam,slime,1,jellyState,null,0),
                new FruitCauldronBridge.Step(jam,bottle,3,null,jamItem,3),
                new FruitCauldronBridge.Step(jellyState,bottle,3,null,jelly,3));
        var recipes=FruitCauldronBridge.recipes(steps);
        check(recipes.size()==2,"cauldron table exposes both bottled jam and jelly preparations");
        var jellyRecipe=recipes.stream().filter(recipe->recipe.masteryTarget().equals(jelly)).findFirst().orElseThrow();
        List<ResourceLocation> inputs=jellyRecipe.ingredients().stream().flatMap(slot->slot.items().stream()).toList();
        check(inputs.containsAll(List.of(lemon,peach,sugar,slime)),"jelly traces full lemon-fruit-sugar-slime cauldron chain");
        check(Collections.frequency(inputs,peach)==3,"cauldron recipe preserves the host batch fruit amount");
        check(!inputs.contains(jamItem)&&!inputs.contains(bottle),"refilling with own jam and serving bottles are not flavor ingredients");
        check(jellyRecipe.outputCount()==3&&jellyRecipe.method().id().equals(FruitCauldronBridge.TYPE),
                "cauldron output amount and preparation method match the host table");
        var cycle=FruitCauldronBridge.recipes(List.of(
                new FruitCauldronBridge.Step(jam,sugar,1,jellyState,null,0),
                new FruitCauldronBridge.Step(jellyState,slime,1,jam,null,0),
                new FruitCauldronBridge.Step(jam,bottle,3,null,jamItem,3)));
        check(cycle.isEmpty(),"unknown cyclic cauldron transformations are skipped instead of inventing a source");
        var ambiguous=new ArrayList<>(steps);
        ambiguous.add(new FruitCauldronBridge.Step(lemonWater,id("other_fruit"),3,fruit,null,0));
        check(FruitCauldronBridge.recipes(ambiguous).isEmpty(),"ambiguous cauldron branches are not combined into one imaginary recipe");
        var root=new net.minecraft.nbt.CompoundTag();var fruits=new net.minecraft.nbt.ListTag();
        for(String name:List.of("PEACH","APPLE","PEACH","../invalid"))fruits.add(net.minecraft.nbt.StringTag.valueOf(name));
        root.put(FruitJellyFlavorBridge.KEY,fruits);
        check(FruitJellyFlavorBridge.fruits(root).equals(List.of("APPLE","PEACH")),"host fruit list deduplicates and orders variants without writing NBT");
        check(fruits.size()==4,"reading fruit flavor leaves the original host list untouched");
        var request=new CuisineFoodInfoPackets.FruitRequest(id("bread"),3,List.of("APPLE","PEACH"));
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            request.write(buffer);
            check(CuisineFoodInfoPackets.FruitRequest.read(buffer).equals(request),"variant tooltip request preserves fruit identities and stars");
        } finally {buffer.release();}
        var info=new CuisineFoodInfoPackets.Response(id("bread"),false,12,0.3F,6000,1,false,32,0,
                List.of(new CuisineFoodInfoPackets.Effect(id("heal"),1,6000)),3);
        var response=new CuisineFoodInfoPackets.FruitResponse(request.fruits(),info);
        buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            response.write(buffer);
            check(CuisineFoodInfoPackets.FruitResponse.read(buffer).equals(response),"variant tooltip response preserves numeric and potion effect values");
        } finally {buffer.release();}
    }
    private static void dishSearchPacketTests() {
        var entry=new com.chi__you.cuisinejournal.network.DishSearchPackets.Entry(id("meal"),List.of(id("flavor")),
                List.of(ResourceLocation.parse("minecraft:strength")));
        var packet=new com.chi__you.cuisinejournal.network.DishSearchPackets.Sync(true,List.of(entry));
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            packet.write(buffer);
            check(com.chi__you.cuisinejournal.network.DishSearchPackets.Sync.read(buffer).equals(packet),
                    "dish search sync retains flavors and all-stage effect IDs");
            com.chi__you.cuisinejournal.client.DishSearchState.accept(packet);
            check(com.chi__you.cuisinejournal.client.DishSearchState.get(entry.item()).equals(entry),"search metadata arrives before any detail request");
            com.chi__you.cuisinejournal.client.DishSearchState.accept(new com.chi__you.cuisinejournal.network.DishSearchPackets.Sync(true,List.of()));
            check(com.chi__you.cuisinejournal.client.DishSearchState.get(entry.item())==null,"empty catalogue clears prior search metadata");
        } finally { buffer.release(); }
    }

    private static void drinkBonusTests() {
        var five=FlavorModifiers.read(JsonParser.parseString("{\"drink_bonus_multiplier\":0.05}").getAsJsonObject());
        var ten=new FlavorModifiers(0,0,0,0,0,0,0.1);
        check(Math.abs(five.drinkBonus(0.2)-0.25)<1e-9,"drink flavor adds five percentage points to twenty percent");
        check(Math.abs(five.drinkBonus(0)-0.05)<1e-9,"additive flavor works with zero base drink bonus");
        check(Math.abs(five.plus(ten).drinkBonus(0.2)-0.35)<1e-9,"multiple drink flavor contributions add together");
        check(new FlavorModifiers(0,0,0,0,0,0,-0.3).drinkBonus(0.2)==0,"negative drink flavor clamps final bonus at zero");
        check(FlavorModifiers.read(five.toJson()).equals(five),"existing preset key and values round trip unchanged");
    }

    private static void sourceFilterTests() {
        var config=com.chi__you.cuisinejournal.config.ConfigManager.server();
        boolean enabled=config.flavors.enabled;
        var source=new com.chi__you.cuisinejournal.network.Packets.FlavorInfo(true,List.of(),false,true,List.of(),List.of());
        try {
            config.flavors.enabled=false;
            check(com.chi__you.cuisinejournal.client.journal.FlavorUi.hasListedFlavor(source),"source filter retains configured sources while gameplay flavors are off");
            check(!com.chi__you.cuisinejournal.client.journal.FlavorUi.hasVisibleFlavor(source),"gameplay display still obeys the disabled flavor switch");
            check(!com.chi__you.cuisinejournal.client.journal.FlavorUi.hasListedFlavor(com.chi__you.cuisinejournal.network.Packets.FlavorInfo.NONE),"ordinary ingredients do not become flavor sources");
            var flavor=id("catalogue_numeric");
            var preset=new FlavorPresets.Preset(flavor,"Numeric","",true,List.of(new FlavorStage(0,List.of(),
                    new FlavorModifiers(1,0,0,0,0,0),FlavorModifiers.NONE,FlavorModifiers.NONE)));
            com.chi__you.cuisinejournal.client.FlavorClientState.accept(new com.chi__you.cuisinejournal.network.Packets.SyncFlavors(
                    List.of(new com.chi__you.cuisinejournal.network.Packets.FlavorPresetView(flavor,preset.toJson().toString(),(byte)2,1)),true,1,1,false));
            var own=new com.chi__you.cuisinejournal.network.Packets.FlavorInfo(true,List.of(flavor),true,false,List.of(),List.of());
            var inherited=new com.chi__you.cuisinejournal.network.Packets.FlavorInfo(false,List.of(),false,false,
                    List.of(new com.chi__you.cuisinejournal.network.Packets.InheritedFlavor(id("fruit_tag"),List.of(flavor))),List.of());
            check(com.chi__you.cuisinejournal.client.journal.FlavorUi.hasListedFlavor(own),"synced preset source remains listed with flavors disabled");
            check(com.chi__you.cuisinejournal.client.journal.FlavorUi.hasListedFlavor(inherited),"tag-inherited source remains listed with flavors disabled");
            var unknown=new com.chi__you.cuisinejournal.network.Packets.FlavorInfo(true,List.of(id("unknown")),false,false,List.of(),List.of());
            check(!com.chi__you.cuisinejournal.client.journal.FlavorUi.hasListedFlavor(unknown),"unavailable preset is not mistaken for visible content");
            config.flavors.enabled=true;
            check(com.chi__you.cuisinejournal.client.journal.FlavorUi.hasVisibleFlavor(source),"active flavors keep their existing display behavior");
        } finally {config.flavors.enabled=enabled;com.chi__you.cuisinejournal.client.FlavorClientState.clear();}
    }
    private static void availabilityTests() {
        var missing = new FlavorStage.Effect(id("missing_effect"),1,30,1);
        var present = new FlavorStage.Effect(id("present_effect"),1,30,1);
        var exists = (java.util.function.Predicate<ResourceLocation>)effect -> effect.equals(present.id());
        var empty = new FlavorPresets.Preset(id("empty"),"Empty","",true,List.of(new FlavorStage(0,List.of(missing))));
        check(!FlavorAvailability.hasContent(empty,exists),"missing-only flavor is hidden without deleting its definition");
        check(empty.stages().get(0).effects().equals(List.of(missing)),"availability never removes missing effect configuration");
        var mixed = new FlavorPresets.Preset(id("mixed"),"Mixed","",true,List.of(new FlavorStage(0,List.of(missing,present))));
        check(FlavorAvailability.hasContent(mixed,exists),"one remaining available effect keeps flavor visible");
        var locked = new FlavorPresets.Preset(id("locked"),"Locked","",true,List.of(new FlavorStage(5,List.of(present))));
        check(FlavorAvailability.hasContent(locked,exists),"high-star-only effects remain visible before unlocking");
        var numeric = new FlavorPresets.Preset(id("numeric"),"Numeric","",true,List.of(new FlavorStage(0,List.of(missing),
                FlavorModifiers.NONE,FlavorModifiers.NONE,new FlavorModifiers(0,0,0,0,-30,0))));
        check(FlavorAvailability.hasContent(numeric,exists),"drink-specific numeric modifier keeps missing-effect flavor visible");
        check(FlavorAvailability.hasContent(empty,effect -> true),"restored mod makes the original flavor visible again");
        var zero = new FlavorPresets.Preset(id("zero"),"Zero","",true,List.of(new FlavorStage(0,
                List.of(new FlavorStage.Effect(present.id(),1,30,0)))));
        check(!FlavorAvailability.hasContent(zero,exists),"zero-duration effect without modifiers is empty");
    }

    private static void storageCycleTests() {
        var food=id("jelly");var block=id("jelly_block");var bottle=ResourceLocation.parse("minecraft:glass_bottle");
        var packing=new IngredientStorageCycles.Production(true,1,List.of(slot(food),slot(food),slot(food)));
        var unpacking=new IngredientStorageCycles.Production(true,3,List.of(slot(block),slot(bottle),slot(bottle),slot(bottle)));
        var production=new LinkedHashMap<ResourceLocation,List<IngredientStorageCycles.Production>>();
        production.put(block,List.of(packing));production.put(food,List.of(unpacking));
        var uses=List.of(new IngredientJournalIndex.Input(food,slot(block)));
        check(IngredientStorageCycles.hidden(production,uses,Set.of()).equals(Set.of(block)),
                "jelly block solely packed from jelly and unpacked to jelly is hidden as an ingredient");
        check(IngredientStorageCycles.hidden(production,uses,Set.of(block)).isEmpty(),
                "unreadable production prevents unsafe storage-only exclusion");
        var otherUses=new ArrayList<>(uses);otherUses.add(new IngredientJournalIndex.Input(id("cake"),slot(block)));
        check(IngredientStorageCycles.hidden(production,otherUses,Set.of()).isEmpty(),
                "storage block used in another actual dish remains an ingredient");
        production.put(block,List.of(packing,new IngredientStorageCycles.Production(false,1,List.of(slot(id("fruit"))))));
        check(IngredientStorageCycles.hidden(production,uses,Set.of()).isEmpty(),
                "independently produced blocks are not classified as storage-only");
        production.put(block,List.of(new IngredientStorageCycles.Production(true,1,List.of(slot(food),slot(id("sugar"))))));
        check(IngredientStorageCycles.hidden(production,uses,Set.of()).isEmpty(),
                "transformations using additional ingredients are not pure packing");
        production.put(block,List.of(packing));production.put(food,List.of(new IngredientStorageCycles.Production(true,1,List.of(slot(block)))));
        check(IngredientStorageCycles.hidden(production,uses,Set.of()).isEmpty(),
                "single-output conversions are not assumed to be storage unpacking");
    }
    private static void singletonTagTests() {
        var jelly=ResourceLocation.parse("fruitsdelight:peach_jelly");
        var peach=ResourceLocation.parse("fruitsdelight:peach");
        var peachTag=ResourceLocation.parse("forge:fruits/peach");
        var anyFruit=id("any_fruit");var apple=id("apple");var seed=id("seed");
        var peachSlot=new RecipeEntry.IngredientSlot(List.of(peach),List.of(),List.of(peachTag));
        Map<ResourceLocation,List<RecipeEntry.IngredientSlot>> graph=Map.of(
                jelly,List.of(peachSlot,peachSlot,
                        new RecipeEntry.IngredientSlot(List.of(peach,apple),List.of(),List.of(anyFruit))),
                peach,List.of(slot(seed)));
        var emptyTags=(java.util.function.Function<ResourceLocation,List<ResourceLocation>>)item->List.of();
        var automatic=(java.util.function.Function<ResourceLocation,List<String>>)item->null;
        var members=(java.util.function.Function<ResourceLocation,List<ResourceLocation>>)tag->
                tag.equals(peachTag)?List.of(peach):tag.equals(anyFruit)?List.of(peach,apple):List.of();
        var trace=FlavorTrace.trace(jelly,item->graph.getOrDefault(item,List.of()),emptyTags,members,
                ref->ref.equals(peach.toString()),automatic);
        check(trace.sources().contains(peach.toString()),
                "peach jelly resolves its sole tag member to the bound peach source");
        check(trace.sources().contains("#"+peachTag),
                "single-member tag retains its own flavor binding");
        check(Collections.frequency(trace.sources(),peach.toString())==1,
                "repeated tag ingredients only contribute the peach source once");
        check(!trace.sources().contains(apple.toString())&&trace.sources().contains("#"+anyFruit),
                "multi-member tags keep tag flavors without collecting alternative item flavors");
        check(!trace.sources().contains(seed.toString()),
                "single-member item source stops further processing ancestry");
        var recursive=FlavorTrace.trace(jelly,item->graph.getOrDefault(item,List.of()),
                item->item.equals(peach)?List.of(peachTag):List.of(),members,ref->false,automatic);
        check(recursive.sources().contains(seed.toString())&&!recursive.truncated(),
                "single-member tag follows processing ancestry and terminates item-tag cycles");
        var disabled=FlavorTrace.trace(jelly,item->graph.getOrDefault(item,List.of()),emptyTags,members,ref->false,
                item->item.equals(jelly)?List.of():null);
        check(disabled.sources().isEmpty(),
                "explicit empty sources still disable single-member tag inference");
    }
    private static void exportTests() {
        AtomicInteger clears=new AtomicInteger();
        CompletableFuture<Void> pending=new CompletableFuture<>();
        var result=ExportCompletion.afterReload(pending,Runnable::run,3,()->{clears.incrementAndGet();return true;});
        check(!result.isDone()&&clears.get()==0,"export stays pending and local layer stays until reload completes");
        pending.complete(null);
        check(result.join()==3&&clears.get()==1,"successful reload clears once and then reports success");
        var failed=ExportCompletion.afterReload(CompletableFuture.failedFuture(new IllegalStateException("bad pack")),Runnable::run,3,
                ()->{clears.incrementAndGet();return true;});
        check(failed.isCompletedExceptionally()&&clears.get()==1,"failed reload never runs local cleanup");
        var cleanupFailure=ExportCompletion.afterReload(CompletableFuture.completedFuture(null),Runnable::run,3,()->false);
        check(cleanupFailure.isCompletedExceptionally(),"cleanup persistence/conflict failure is not reported as export success");
    }
    private static void durationModeTests() throws Exception {
        var directory=java.nio.file.Files.createTempDirectory("flavor-duration-mode");
        try {
            java.nio.file.Files.writeString(directory.resolve("cuisinejournal-ingredients.json"),"""
                {"flavor":{"test:source":{"source":true,"stages":[{"min_stars":0,
                 "effects":[{"id":"minecraft:speed","duration_seconds":30,"active_fraction":0.5}]}]}}}
                """);
            IngredientCatalogueConfig.load(directory);
            var valheim=CuisineFlavorEffects.resolveSources(List.of("test:source"),0,false,Map.of(),false).effects().get(0);
            var vanilla=CuisineFlavorEffects.resolveSources(List.of("test:source"),0,false,Map.of(),true).effects().get(0);
            check(valheim.durationTicks()==0&&valheim.activeTicks(12000)==6000,"Valheim ignores retained vanilla seconds");
            check(vanilla.durationTicks()==600,"vanilla retains fixed thirty seconds");
        }finally{
            java.nio.file.Files.delete(directory.resolve("cuisinejournal-ingredients.json"));java.nio.file.Files.delete(directory);
        }
    }
    private static RecipeEntry.IngredientSlot slot(ResourceLocation item){return new RecipeEntry.IngredientSlot(List.of(item));}
    private static void contractTests() throws Exception {
        String protocol=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/chi__you/cuisinejournal/forge/ForgeNetwork.java"));
        check(protocol.contains("PROTOCOL = "+(char)34+"47"+(char)34),"settings and regeneration sync reject older clients");
        for(String type:List.of("food/CuisineFoodEffects","food/CuisineFoodCatalogueSync","network/FlavorEditHandler")) {
            var node=compiled(type);boolean shared=false;
            for(var method:node.methods)for(var instruction:method.instructions)
                if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.name.equals("activeTicks"))shared=true;
            check(shared,"runtime, tooltip and preview share effect lifetime: "+type);
        }
        var registry=compiled("recipe/CulinaryRegistry");boolean graph=false;
        for(var method:registry.methods)if(method.name.equals("build"))for(var instruction:method.instructions)
            if(instruction instanceof org.objectweb.asm.tree.FieldInsnNode field&&field.name.equals("flavorInputs")&&field.getOpcode()==181)graph=true;
        check(graph,"reload builds preserved item/tag recipe graph for runtime tracing");
        var handler=compiled("network/FlavorEditHandler");
        var export=handler.methods.stream().filter(method->method.name.equals("export")).findFirst().orElseThrow();
        boolean waits=false,earlyClear=false;
        for(var instruction:export.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
            if(call.owner.endsWith("/ExportCompletion")&&call.name.equals("afterReload"))waits=true;
            if(List.of("clearLocal","clearLocalFlavors","reset").contains(call.name))earlyClear=true;
        }
        check(waits&&!earlyClear,"export method defers every local cleanup until reload completion");
    }
    private static org.objectweb.asm.tree.ClassNode compiled(String path) throws Exception {
        var result=new org.objectweb.asm.tree.ClassNode();
        try(var stream=FlavorRepairTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/"+path+".class")) {
            new org.objectweb.asm.ClassReader(Objects.requireNonNull(stream)).accept(result,0);
        }
        return result;
    }
    private static ResourceLocation id(String path){return ResourceLocation.parse("test:"+path);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
