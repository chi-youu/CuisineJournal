package com.chi__you.foodinteractions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Read-only adapters for confirmed implementation families; no simulated clicks. */
public final class FoodInteractionScanner {
    private static final String FEAST="vectorwing.farmersdelight.common.block.FeastBlock";
    private static final String PIE="vectorwing.farmersdelight.common.block.PieBlock";
    private static final String STACKED="com.renyigesai.immortalers_delight.block.StackedFoodBlock";
    private static final String SMC_FEAST="com.starmeow.smc.blocks.BaseFeastBlock";
    private static final String ZEA="com.renyigesai.immortalers_delight.block.food.ZeaPancakeBLock";
    private static final String NETHER_PIZZA="net.jadenxgamer.netherexp.registry.block.custom.NetherPizzaBlock";
    private static final String COMMON_CAKE="io.github.rcneg.legendarydelicacies.blocks.CommonCakeBlock";
    private static final String COSMIC_CAKE="com.aizistral.enigmaticlegacy.blocks.BlockCosmicCake";
    // Verified overrides preserve portion/container semantics; bamboo additionally requires an open lid.
    private static final java.util.Set<String> VERIFIED_SERVING_OVERRIDES=java.util.Set.of(
            "show.tatd.mod.block.StuffedEggSnifferEggBlock",
            "show.tatd.mod.block.SnifferEggSoupBlock",
            "show.tatd.mod.block.BambooTubeRiceBlock",
            // The frozen bucket keeps FeastBlock serving and only resets to the empty
            // bucket when the last portion is taken; its serving item is still the normal one.
            "show.tatd.mod.block.ice_cream.IceCreamBucketBlock",
            "cn.mcmod.corn_delight.block.PopcornBoxBlock");
    public record Scan(List<InteractionDisplay> entries,List<String> report) {}
    private FoodInteractionScanner() {}

    public static Scan scan(InteractionRules.Resolved rules) {
        Map<ResourceLocation,InteractionDisplay> entries=new LinkedHashMap<>();
        List<String> report=new ArrayList<>(rules.problems());
        for(Block block:BuiltInRegistries.BLOCK) {
            ResourceLocation id=BuiltInRegistries.BLOCK.getKey(block);
            if(id.toString().equals("farmersdelight:rice_roll_medley_block")) {
                report.add(id+"	skipped	sushi platter excluded"); continue;
            }
            JsonObject rule=rules.blocks().get(id);
            if(rule!=null&&rule.has("enabled")&&!rule.get("enabled").getAsBoolean()) {
                report.add(id+"	skipped	datapack exclusion");continue;
            }
            try {
                String inheritedFamily=family(block,FEAST)!=null?FEAST:family(block,SMC_FEAST)!=null?SMC_FEAST:family(block,PIE)!=null?PIE:
                        family(block,STACKED)!=null?STACKED:family(block,COMMON_CAKE)!=null?COMMON_CAKE:null;
                if((rule==null||!rule.has("output"))&&inheritedFamily!=null
                        &&overrides(block,inheritedFamily).startsWith("review override:")
                        &&!VERIFIED_SERVING_OVERRIDES.contains(block.getClass().getName())) {
                    report.add(id+"	needs_review	"+overrides(block,inheritedFamily));continue;
                }
                List<InteractionDisplay> found=rule!=null&&rule.has("output")?List.of(manual(block,rule)):
                        block.getClass().getName().equals(TrailIceCreamRelations.EMPTY)?iceBucket(block):
                        family(block,FEAST)!=null||family(block,SMC_FEAST)!=null?feast(block):family(block,PIE)!=null?pie(block):
                                family(block,STACKED)!=null?stacked(block):family(block,ZEA)!=null?zea(block):
                                        family(block,NETHER_PIZZA)!=null?netherPizza(block):
                                                family(block,COMMON_CAKE)!=null?commonCake(block):
                                                        family(block,COSMIC_CAKE)!=null?cosmicCake(block):B001InteractionAdapter.read(block);
                for(InteractionDisplay entry:found) {
                    if(!BuiltInRegistries.ITEM.containsKey(entry.output())||entry.count()<1)
                        throw new IllegalArgumentException("missing/invalid output: "+entry.output());
                    entries.putIfAbsent(entry.recipeId(),entry);
                }
                if(!found.isEmpty())report.add(id+"	candidate	"+found.get(0).evidence());
            } catch(ReflectiveOperationException|RuntimeException|LinkageError failure) {
                report.add(id+"	failed	"+failure.getClass().getSimpleName()+": "+failure.getMessage());
            }
        }
        return new Scan(List.copyOf(entries.values()),List.copyOf(report));
    }

    private static List<InteractionDisplay> feast(Block block)throws ReflectiveOperationException {
        IntegerProperty property=(IntegerProperty)invoke(block,"getServingsProperty");
        Map<String,InteractionDisplay> distinct=new LinkedHashMap<>();
        for(int remaining:property.getPossibleValues()) {
            if(remaining<=0)continue;
            BlockState state=block.defaultBlockState().setValue(property,remaining);
            Map<String,String> displayState=new LinkedHashMap<>();displayState.put(property.getName(),Integer.toString(remaining));
            if (block.getClass().getName().equals(TrailIceCreamRelations.FROZEN)) displayState.put("covered", "false");
            if(block.getClass().getName().equals("show.tatd.mod.block.BambooTubeRiceBlock")) {
                var open=(net.minecraft.world.level.block.state.properties.BooleanProperty)block.getClass().getField("OPEN").get(null);
                state=state.setValue(open,true);displayState.put(open.getName(),"true");
            }
            ItemStack output=(ItemStack)invoke(block,"getServingItem",state);
            if(output.isEmpty())continue;
            ItemStack container=output.getCraftingRemainingItem();
            JsonObject input=container.isEmpty()?anyHand():item(BuiltInRegistries.ITEM.getKey(container.getItem()));
            String base=family(block,FEAST)!=null?FEAST:SMC_FEAST;
            String evidence=base+".getServingItem; container=output crafting remainder; "+overrides(block,base);
            var display=display(block,input,output,Map.copyOf(displayState),"serving",evidence);
            distinct.putIfAbsent(output.getItem().toString()+"|"+input+"|"+output.getCount(),display);
        }
        return List.copyOf(distinct.values());
    }

    private static List<InteractionDisplay> iceBucket(Block block) throws ReflectiveOperationException {
        List<InteractionDisplay> result = new ArrayList<>();
        for (var relation : TrailIceCreamRelations.read(block)) result.add(new InteractionDisplay(
                BuiltInRegistries.BLOCK.getKey(block), relation.mix(), item(relation.mix()),
                BuiltInRegistries.ITEM.getKey(relation.frozen().asItem()), 1,
                Map.of("covered", "false", "ice_cream_type", "0"), "freezing",
                "IceBucketBlock: pour mix, close lid, age to 6 on random ticks; yields " + relation.servings() + " servings"));
        return List.copyOf(result);
    }

    private static List<InteractionDisplay> pie(Block block)throws ReflectiveOperationException {
        ItemStack output=(ItemStack)invoke(block,"getPieSliceItem");
        if(output.isEmpty())return List.of();
        Method accepts=Class.forName("vectorwing.farmersdelight.common.utility.ItemUtils").getMethod("isKnife",ItemStack.class);
        JsonElement input=tools(null,accepts);
        return List.of(display(block,input,output,Map.of("bites","0"),"cutting","PieBlock.cutSlice; "+overrides(block,PIE)));
    }

    private static List<InteractionDisplay> stacked(Block block)throws ReflectiveOperationException {
        Object supplier=block.getClass().getField("servingItem").get(block);
        ItemStack output=new ItemStack((Item)((Supplier<?>)supplier).get());
        Method predicate=block.getClass().getMethod("isCuttable",ItemStack.class);
        predicate.setAccessible(true);
        JsonElement input=tools(block,predicate);
        return List.of(display(block,input,output,Map.of("bites","0"),"cutting",
                "StackedFoodBlock.servingItem; tools="+predicate.getDeclaringClass().getName()+".isCuttable"));
    }

    private static List<InteractionDisplay> zea(Block block)throws ReflectiveOperationException {
        Class<?> items=Class.forName("com.renyigesai.immortalers_delight.init.ImmortalersDelightItems");
        Item output=(Item)((Supplier<?>)items.getField("ZEA_PANCAKE_SLICE").get(null)).get();
        Method predicate=Class.forName("com.renyigesai.immortalers_delight.util.ItemUtils").getMethod("isKnives",ItemStack.class);
        return List.of(display(block,tools(null,predicate),new ItemStack(output),Map.of("bites","0"),"cutting",
                "ZeaPancakeBLock.cut; ItemUtils.isKnives; four portions"));
    }

    private static List<InteractionDisplay> netherPizza(Block block)throws ReflectiveOperationException {
        Object supplier=Class.forName("net.jadenxgamer.netherexp.registry.item.JNEItems")
                .getField("NETHER_PIZZA_SLICE").get(null);
        Item output=(Item)((Supplier<?>)supplier).get();
        IntegerProperty property=(IntegerProperty)block.getClass().getField("SLICES").get(null);
        int remaining=block.defaultBlockState().getValue(property);
        // The host requires no tool; show the empty-hand route confirmed by the player.
        return List.of(display(block,item(ResourceLocation.parse("minecraft:air")),new ItemStack(output),
                Map.of(property.getName(),Integer.toString(remaining)),"cutting",
                "NetherPizzaBlock.use; NETHER_PIZZA_SLICE; no tool required"));
    }

    private static List<InteractionDisplay> commonCake(Block block)throws ReflectiveOperationException {
        Item output=(Item)((Supplier<?>)block.getClass().getField("sliceItem").get(block)).get();
        JsonArray knives=new JsonArray();
        for(String id:List.of("farmersdelight:tools/knives","forge:tools/knives")) {
            JsonObject tag=new JsonObject();tag.addProperty("tag",id);knives.add(tag);
        }
        return List.of(display(block,knives,new ItemStack(output),Map.of("bites","0"),"cutting",
                "CommonCakeBlock.cutSlice; sliceItem; farmersdelight OR forge knives"));
    }

    private static List<InteractionDisplay> cosmicCake(Block block)throws ReflectiveOperationException {
        // The slice is added by Enigmatic Delicacy's tool event, not by the cake block itself.
        if(!net.minecraftforge.fml.ModList.get().isLoaded("enigmaticdelicacy"))return List.of();
        Class<?> items=Class.forName("auviotre.enigmatic.delicacy.registries.EnigmaticDelightItems");
        Item tool=(Item)items.getField("ETHERIUM_MACHETE").get(null);
        Item output=(Item)items.getField("COSMIC_CAKE_SLICE").get(null);
        if(tool==null||output==null)throw new IllegalStateException("cosmic cake tool/output not registered");
        return List.of(display(block,item(BuiltInRegistries.ITEM.getKey(tool)),new ItemStack(output),
                Map.of("bites","0"),"cutting","EtheriumMachete.onCakeInteraction; only ETHERIUM_MACHETE; bites < 6"));
    }

    private static JsonElement tools(Object owner,Method predicate)throws ReflectiveOperationException {
        JsonArray alternatives=new JsonArray();
        for(Item tool:BuiltInRegistries.ITEM) {
            // The host tests both ordinary knives and special tools (e.g. axes).
            if(tool!=Items.AIR && Boolean.TRUE.equals(predicate.invoke(owner,new ItemStack(tool))))
                alternatives.add(item(BuiltInRegistries.ITEM.getKey(tool)));
        }
        if(alternatives.isEmpty())throw new IllegalArgumentException("no accepted tool in loaded registry");
        // Some stacked sweets accept any item. Avoid displaying every registered item.
        if(alternatives.size()==BuiltInRegistries.ITEM.size()-1)return anyHand();
        return alternatives.size()==1?alternatives.get(0):alternatives;
    }

    private static InteractionDisplay manual(Block block,JsonObject rule) {
        JsonElement input=rule.has("input")?rule.get("input").deepCopy():anyHand();
        Map<String,String> state=new LinkedHashMap<>();
        if(rule.has("state"))rule.getAsJsonObject("state").entrySet().forEach(e->state.put(e.getKey(),e.getValue().getAsString()));
        return new InteractionDisplay(BuiltInRegistries.BLOCK.getKey(block),source(block),input,
                ResourceLocation.parse(rule.get("output").getAsString()),rule.has("count")?rule.get("count").getAsInt():1,
                Map.copyOf(state),rule.has("kind")?rule.get("kind").getAsString():"serving","datapack override");
    }
    private static InteractionDisplay display(Block block,JsonElement input,ItemStack output,Map<String,String> state,String kind,String evidence) {
        return new InteractionDisplay(BuiltInRegistries.BLOCK.getKey(block),source(block),input,
                BuiltInRegistries.ITEM.getKey(output.getItem()),output.getCount(),state,kind,evidence);
    }
    public static ResourceLocation source(Block block) {
        var id=BuiltInRegistries.BLOCK.getKey(block);
        if(id.getNamespace().equals("unusual_delight")){
            String base=switch(id.getPath()){case "dragonloaf_2","dragonloaf_3"->"dragon_loaf";case "snowhead_2","snowhead_3","snowhead_4","snowhead_5"->"snow_head";default->null;};
            if(base!=null)return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),base);
        }
        if(block.asItem()!=Items.AIR)return BuiltInRegistries.ITEM.getKey(block.asItem());
        if(family(block,STACKED)!=null)try {
            Object raw=block.getClass().getField("pileItem").get(block);
            if(raw instanceof Supplier<?> supplier && supplier.get() instanceof Item item && item!=Items.AIR)
                return BuiltInRegistries.ITEM.getKey(item);
        }catch(ReflectiveOperationException|RuntimeException ignored){}
        return null;
    }
    private static JsonObject item(ResourceLocation id){JsonObject json=new JsonObject();json.addProperty("item",id.toString());return json;}
    private static JsonObject anyHand(){JsonObject json=new JsonObject();json.addProperty("type","lychee:always_true");return json;}
    private static Class<?> family(Block block,String name){for(Class<?> type=block.getClass();type!=null;type=type.getSuperclass())if(type.getName().equals(name))return type;return null;}
    private static Object invoke(Object object,String name,Object... args)throws ReflectiveOperationException {
        for(Method method:object.getClass().getMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length) {
            method.setAccessible(true);return method.invoke(object,args);
        }
        throw new NoSuchMethodException(name);
    }
    private static String overrides(Block block,String family) {
        List<String> classes=new ArrayList<>();
        for(Class<?> type=block.getClass();type!=null&&!type.getName().equals(family);type=type.getSuperclass())
            for(Method method:type.getDeclaredMethods())if(method.getName().equals("takeServing")||method.getName().equals("use")
                    ||method.getName().equals("m_6227_")||method.getName().equals("cutSlice")||method.getName().equals("cut"))
                classes.add(type.getName()+"."+method.getName());
        return classes.isEmpty()?"inherited interaction":"review override: "+classes;
    }
}
