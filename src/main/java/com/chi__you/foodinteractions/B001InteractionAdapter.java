package com.chi__you.foodinteractions;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

/** Display-only host readers; deliberately independent of the journal module. */
final class B001InteractionAdapter {
    private static final Map<String,String> METHODS=Map.ofEntries(
            Map.entry("com.phantomwing.rusticdelight.block.custom.PancakeBlock","getServingItem"),
            Map.entry("com.baisylia.culturaldelights.block.custom.EggplantFeastBlock","getServingItem"),
            Map.entry("com.chefmooon.ubesdelight.common.block.DrinkableFeastBlock","getServingItem"),
            Map.entry("com.chefmooon.ubesdelight.common.block.UbesDelightCakeBlock","getCakeSliceStack"),
            Map.entry("com.chefmooon.ubesdelight.common.block.LecheFlanFeastBlock","getCakeSliceStack"),
            Map.entry("com.dannbrown.braziliandelight.common.content.blocks.PieBlock","getPieSliceItem"),
            Map.entry("net.brdle.collectorsreap.common.block.EffectCakeBlock","getSlice"),
            Map.entry("net.brdle.collectorsreap.common.block.PanettoneBlock","getSliceItem"),
            Map.entry("com.soytutta.mynethersdelight.common.block.MagmaCakeBlock","getPieSliceItem"),
            Map.entry("com.soytutta.mynethersdelight.common.block.BreadLoafBlock","getBreadSliceItem"));
    private B001InteractionAdapter() {}
    static List<InteractionDisplay> read(Block block)throws ReflectiveOperationException {
        var exquisito=ExquisitoServingRelations.displays(block);if(!exquisito.isEmpty())return exquisito;
        var milkshake=NeapolitanCauldronDisplays.read(block);if(!milkshake.isEmpty())return milkshake;
        String name=block.getClass().getName();var id=BuiltInRegistries.BLOCK.getKey(block);
        for(Class<?> type=block.getClass();type!=null;type=type.getSuperclass())if(METHODS.containsKey(type.getName())
                ||type.getName().equals("com.dannbrown.braziliandelight.common.content.blocks.PlaceableFoodBlock")
                ||type.getName().equals("com.chefmooon.ubesdelight.common.block.leaf_feast.base.SimpleLeafFeastBlock")
                ||type.getName().equals("com.chefmooon.ubesdelight.common.block.leaf_feast.base.LargeLeafFeastBlock")){name=type.getName();break;}
        if(id.getNamespace().equals("unusual_delight")){
            String output=switch(id.getPath()){
                case "dragon_loaf","dragonloaf_2","dragonloaf_3"->"bowl_of_dragon_loaf";
                case "snow_head"->"pumpkin_mousse_bowl";
                case "snowhead_2","snowhead_3","snowhead_4","snowhead_5"->"bowl_of_snow_head";
                default->null;
            };
            if(output!=null)return List.of(display(block,ResourceLocation.fromNamespaceAndPath("unusual_delight",output),"minecraft:bowl",Map.of()));
        }
        if(name.equals("net.ribs.vintagedelight.block.custom.CheeseMoldBlock"))
            return List.of(display(block,ResourceLocation.parse("vintagedelight:cheese_wheel"),"minecraft:air",Map.of("level","5")));
        if(name.equals("com.soytutta.mynethersdelight.common.block.StuffedHoglinBlock")){
            List<InteractionDisplay> result=new ArrayList<>();
            for(var entry:Map.of("roast_ear",11,"plate_of_stuffed_hoglin_snout",9,"plate_of_stuffed_hoglin_ham",8,"plate_of_stuffed_hoglin",4).entrySet())
                result.add(display(block,ResourceLocation.fromNamespaceAndPath("mynethersdelight",entry.getKey()),entry.getValue()>9?"#farmersdelight:tools/knives":"minecraft:bowl",Map.of("servings",entry.getValue().toString())));
            return result;
        }
        Object output=null;String input="#farmersdelight:tools/knives";
        if(name.equals("com.renyigesai.luncheonmeatsdelight.block.LuncheonMeatBlock")){
            output=block.getClass().getMethod("getSpawnLuncheonMeat").invoke(block);
            ItemStack serving=stack(output).copyWithCount(4);
            var base=display(block,BuiltInRegistries.ITEM.getKey(serving.getItem()),input,Map.of("pile","1","cut","1"));
            return List.of(new InteractionDisplay(base.block(),base.source(),base.input(),base.output(),4,base.state(),base.kind(),base.evidence()));
        }
        if(name.equals("com.renyigesai.luncheonmeatsdelight.block.CanShellSmallToastBlock"))
            return List.of(display(block,ResourceLocation.parse("luncheonmeatsdelight:small_toast"),input,Map.of()));
        if(name.equals("com.renyigesai.luncheonmeatsdelight.block.LuncheonMeatRiceBallBlock"))
            return List.of(display(block,ResourceLocation.parse("luncheonmeatsdelight:luncheon_meat_rice_ball"),"minecraft:air",Map.of("slice","3")));
        String getter=METHODS.get(name);
        if(getter!=null){
            for(Method method:block.getClass().getMethods())if(method.getName().equals(getter)){
                output=method.getParameterCount()==0?method.invoke(block):method.invoke(block,block.defaultBlockState());break;
            }
            if(name.endsWith("PancakeBlock"))input="minecraft:air";
            if(name.endsWith("EggplantFeastBlock")||name.endsWith("DrinkableFeastBlock")){
                ItemStack stack=stack(output);var container=stack.getCraftingRemainingItem();
                input=container.isEmpty()?"minecraft:air":BuiltInRegistries.ITEM.getKey(container.getItem()).toString();
            }
        }else if(name.equals("com.dannbrown.braziliandelight.common.content.blocks.PlaceableFoodBlock")){
            var slice=Class.forName(name).getDeclaredField("sliceItem");slice.setAccessible(true);output=slice.get(block);
            var requires=Class.forName(name).getDeclaredField("requireServing");requires.setAccessible(true);
            if(requires.getBoolean(block)){var container=Class.forName(name).getDeclaredField("servingItem");container.setAccessible(true);var held=stack(container.get(block));if(!held.isEmpty())input=BuiltInRegistries.ITEM.getKey(held.getItem()).toString();}
        }else if(name.equals("com.dannbrown.braziliandelight.common.content.blocks.CustomCakeBlock")
                ||name.equals("com.chefmooon.ubesdelight.common.block.leaf_feast.base.SimpleLeafFeastBlock")
                ||name.equals("com.chefmooon.ubesdelight.common.block.leaf_feast.base.LargeLeafFeastBlock")){
            var field=Class.forName(name).getDeclaredField(name.endsWith("CustomCakeBlock")?"sliceItem":"servingItem");field.setAccessible(true);output=field.get(block);
            if(name.endsWith("LeafFeastBlock")){var container=stack(output).getCraftingRemainingItem();input=container.isEmpty()?"minecraft:air":BuiltInRegistries.ITEM.getKey(container.getItem()).toString();}
        }else if(name.equals("net.mcreator.oceanicdelight.block.SpongeCakeBlock"))
            output=BuiltInRegistries.ITEM.get(ResourceLocation.parse("oceanic_delight:sponge_cake_slice"));
        ItemStack stack=stack(output);if(stack.isEmpty())return List.of();
        Map<String,String> state=new LinkedHashMap<>();
        for(var property:block.defaultBlockState().getProperties())if(property.getName().equals("bites")||property.getName().equals("servings")||property.getName().equals("uses"))
            state.put(property.getName(),block.defaultBlockState().getValue(property).toString());
        return List.of(display(block,BuiltInRegistries.ITEM.getKey(stack.getItem()),input,state));
    }
    private static ItemStack stack(Object value){
        if(value instanceof Supplier<?> supplier)value=supplier.get();
        return value instanceof ItemStack stack?stack:value instanceof Item item?new ItemStack(item):ItemStack.EMPTY;
    }
    private static InteractionDisplay display(Block block,ResourceLocation output,String input,Map<String,String> state){
        JsonObject hand=new JsonObject();hand.addProperty(input.startsWith("#")?"tag":"item",input.startsWith("#")?input.substring(1):input);
        return new InteractionDisplay(BuiltInRegistries.BLOCK.getKey(block),FoodInteractionScanner.source(block),hand,output,1,Map.copyOf(state),input.startsWith("#")?"cutting":"serving","B001 audited host serving interaction");
    }
}
