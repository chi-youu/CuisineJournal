package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Shared serving protocols of Delight addons, matched by verified host inheritance. */
public final class DelightPlacedFoods {
    public static final String PANCAKE="com.phantomwing.rusticdelight.block.custom.PancakeBlock";
    public static final String EGGPLANT="com.baisylia.culturaldelights.block.custom.EggplantFeastBlock";
    public static final String DRINK="com.chefmooon.ubesdelight.common.block.DrinkableFeastBlock";
    public static final String LEAF="com.chefmooon.ubesdelight.common.block.leaf_feast.base.SimpleLeafFeastBlock";
    public static final String LARGE_LEAF="com.chefmooon.ubesdelight.common.block.leaf_feast.base.LargeLeafFeastBlock";
    public static final String UNIVERSAL_LEAF="com.chefmooon.ubesdelight.common.block.leaf_feast.base.UniversalLeafFeastBlock";
    public static final String UBE_CAKE="com.chefmooon.ubesdelight.common.block.UbesDelightCakeBlock";
    public static final String FLAN="com.chefmooon.ubesdelight.common.block.LecheFlanFeastBlock";
    public static final String BRAZIL_PIE="com.dannbrown.braziliandelight.common.content.blocks.PieBlock";
    public static final String BRAZIL_CAKE="com.dannbrown.braziliandelight.common.content.blocks.CustomCakeBlock";
    public static final String BRAZIL_CANDLE="com.dannbrown.braziliandelight.common.content.blocks.CustomCandleCakeBlock";
    public static final String BRAZIL_FEAST="com.dannbrown.braziliandelight.common.content.blocks.PlaceableFoodBlock";
    public static final String EFFECT_CAKE="net.brdle.collectorsreap.common.block.EffectCakeBlock";
    public static final String EFFECT_CANDLE="net.brdle.collectorsreap.common.block.EffectCandleCakeBlock";
    public static final String MAGMA="com.soytutta.mynethersdelight.common.block.MagmaCakeBlock";
    public static final String SPONGE="net.mcreator.oceanicdelight.block.SpongeCakeBlock";
    public static final String BREAD="com.soytutta.mynethersdelight.common.block.BreadLoafBlock";
    public static final String HOGLIN="com.soytutta.mynethersdelight.common.block.StuffedHoglinBlock";
    public static final String CAN="com.renyigesai.luncheonmeatsdelight.block.LuncheonMeatBlock";
    public static final String EGG="com.renyigesai.luncheonmeatsdelight.block.FriedEggLuncheonMeatBlock";
    public static final String RICE="com.renyigesai.luncheonmeatsdelight.block.LuncheonMeatRiceBallBlock";
    public static final String TOAST="com.renyigesai.luncheonmeatsdelight.block.CanShellSmallToastBlock";
    public static final String POPCORN="cn.mcmod.corn_delight.block.PopcornBoxBlock";
    public static final String PANETTONE="net.brdle.collectorsreap.common.block.PanettoneBlock";
    public static final String FD_FEAST="vectorwing.farmersdelight.common.block.FeastBlock";
    public static final String FD_PIE="vectorwing.farmersdelight.common.block.PieBlock";
    public static final String KING_CRAB="cloudmeow.untameddelight.block.SteamedKingCrabBlock";
    public static final String KRABBY="club.someoneice.jellyfishingdelight.block.KrabbyPatty";
    public static final String COLORFUL_KRABBY="club.someoneice.jellyfishingdelight.block.ColorfulKrabbyPattys";
    private static final java.util.Set<String> MODS=java.util.Set.of("rusticdelight","pineapple_delight","luncheonmeatsdelight","sunflowerdelight","culturaldelights","moredelight","braziliandelight","unusual_delight","oceanic_delight","oceansdelight","corn_delight","seeddelight","vintagedelight","crabbersdelight","ubesdelight","collectorsreap","jadensnetherexpansiondelight","mynethersdelight");
    private DelightPlacedFoods() {}
    public static boolean is(Object object,String... names){
        if(object==null)return false;
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())for(String name:names)if(type.getName().equals(name))return true;
        return false;
    }
    public static boolean known(Block block){return is(block,KING_CRAB,KRABBY,COLORFUL_KRABBY)||ordinaryAddonFeast(block)||FarmersSpellFoods.known(block)||UnusualServingRelations.get(block)!=null||is(block,PANCAKE,EGGPLANT,DRINK,LEAF,LARGE_LEAF,UNIVERSAL_LEAF,UBE_CAKE,FLAN,BRAZIL_PIE,BRAZIL_CAKE,BRAZIL_CANDLE,BRAZIL_FEAST,EFFECT_CAKE,EFFECT_CANDLE,MAGMA,SPONGE,BREAD,HOGLIN,CAN,EGG,RICE,TOAST,POPCORN,PANETTONE)
            ||(MODS.contains(BuiltInRegistries.BLOCK.getKey(block).getNamespace())||ModFoodCompat.hostNamespace(BuiltInRegistries.BLOCK.getKey(block).getNamespace())
                ||BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("farmers_spell"))&&is(block,FD_FEAST,FD_PIE);}
    private static boolean ordinaryAddonFeast(Block block){
        String namespace=BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        return java.util.Set.of("untameddelight","delighto_flight").contains(namespace)&&is(block,FD_FEAST,FD_PIE);
    }
    public static ItemStack serving(Block block,BlockState state){
        if(is(block,KING_CRAB))return switch(value(state,"servings")){
            case 3->item("untameddelight:steamed_king_crab_legs");
            case 2->item("untameddelight:steamed_king_crab");
            case 1->item("untameddelight:steamed_egg_with_king_crab_roe");
            default->ItemStack.EMPTY;};
        if(FarmersSpellFoods.known(block))return FarmersSpellFoods.serving(block,state);
        Object result=null;
        if(is(block,KRABBY))result=HostReflection.field(block,"mini_burger");
        else if(is(block,COLORFUL_KRABBY)){
            Object raw=HostReflection.field(block,"KRABBY_PATTYS");int color=value(state,"color");
            if(raw instanceof java.util.List<?> items&&color>0&&color<=items.size())result=items.get(color-1);
        }
        var unusual=UnusualServingRelations.get(block);if(unusual!=null)return UnusualServingRelations.item(unusual.output());
        if(is(block,PANCAKE))result=HostReflection.invoke(block,"getServingItem");
        else if(is(block,EGGPLANT,DRINK,POPCORN,FD_FEAST))result=HostReflection.invoke(block,"getServingItem",state);
        else if(is(block,CAN))result=HostReflection.invoke(block,"getSpawnLuncheonMeat");
        else if(is(block,EGG))return new ItemStack(block.asItem());
        else if(is(block,RICE))return item("luncheonmeatsdelight:luncheon_meat_rice_ball");
        else if(is(block,TOAST))return item("luncheonmeatsdelight:small_toast");
        else if(is(block,UBE_CAKE,FLAN))result=HostReflection.invoke(block,"getCakeSliceStack");
        else if(is(block,BRAZIL_PIE,MAGMA,FD_PIE))result=HostReflection.invoke(block,"getPieSliceItem");
        else if(is(block,EFFECT_CAKE))result=HostReflection.invoke(block,"getSlice");
        else if(is(block,EFFECT_CANDLE))result=HostReflection.invoke(HostReflection.invoke(block,"getCake"),"getSlice");
        else if(is(block,LEAF,LARGE_LEAF))result=HostReflection.field(block,"servingItem");
        else if(is(block,BRAZIL_CAKE,BRAZIL_FEAST))result=HostReflection.field(block,"sliceItem");
        else if(is(block,BRAZIL_CANDLE)){
            Object cake=HostReflection.field(block,"cakeBlock");
            if(cake instanceof java.util.function.Supplier<?> supplier&&supplier.get() instanceof Block base)return serving(base,base.defaultBlockState());
        }
        else if(is(block,SPONGE))return item("oceanic_delight:sponge_cake_slice");
        else if(is(block,BREAD))result=HostReflection.invoke(block,"getBreadSliceItem");
        else if(is(block,PANETTONE))result=HostReflection.invoke(block,"getSliceItem");
        else if(is(block,HOGLIN)){int n=value(state,"servings");return item("mynethersdelight:"+(n>9?"roast_ear":n==9?"plate_of_stuffed_hoglin_snout":n>4?"plate_of_stuffed_hoglin_ham":"plate_of_stuffed_hoglin"));}
        if(result instanceof java.util.function.Supplier<?> supplier)result=supplier.get();
        return result instanceof ItemStack stack?stack.copy():result instanceof Item item?new ItemStack(item):ItemStack.EMPTY;
    }
    public static int value(BlockState state,String key){
        var property=state.getBlock().getStateDefinition().getProperty(key);
        return property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty integer?state.getValue(integer):0;
    }
    public static int remaining(BlockState state){
        if(FarmersSpellFoods.known(state.getBlock()))return FarmersSpellFoods.remaining(state);
        Block block=state.getBlock();
        if(is(block,KRABBY))return Math.max(0,4-value(state,"bites"));
        if(is(block,COLORFUL_KRABBY))return value(state,"color");
        var unusual=UnusualServingRelations.get(block);if(unusual!=null)return unusual.remaining();
        if(is(block,PANCAKE)){int n=value(state,"servings");return n<6?6-n:n+1;}
        if(is(block,EGGPLANT,DRINK,LEAF,LARGE_LEAF,UNIVERSAL_LEAF,HOGLIN,POPCORN,FD_FEAST,KING_CRAB))return value(state,"servings");
        if(is(block,CAN))return 4;
        if(is(block,TOAST))return 1;
        if(is(block,RICE))return Math.max(0,value(state,"slice")-1);
        if(is(block,EGG))return value(state,"bites");
        if(is(block,EFFECT_CANDLE,BRAZIL_CANDLE))return 7;
        if(is(block,BRAZIL_FEAST))return Math.max(0,4-value(state,"uses"));
        int count=is(block,BRAZIL_PIE,SPONGE,PANETTONE,FD_PIE)?4:is(block,FLAN,BREAD)?5:7;
        if(is(block,FD_PIE)){
            Object maximum=HostReflection.invoke(block,"getMaxBites");
            if(maximum instanceof Number value)count=value.intValue();
        }
        if(is(block,MAGMA)){
            var second=state.getBlock().getStateDefinition().getProperty("second_cake");
            if(second instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag&&state.getValue(flag))count+=7;
        }
        return Math.max(0,count-value(state,"bites"));
    }
    public static boolean sameMeal(BlockState before,BlockState after){
        if(before.getBlock()==after.getBlock())return true;
        var first=UnusualServingRelations.get(before.getBlock());var next=UnusualServingRelations.get(after.getBlock());
        if(first!=null&&next!=null)return first.whole().equals(next.whole())&&next.remaining()<first.remaining();
        ItemStack source=serving(before.getBlock(),before),target=serving(after.getBlock(),after);
        return !source.isEmpty()&&!target.isEmpty()&&ItemStack.isSameItem(source,target)
                &&is(before.getBlock(),BRAZIL_CAKE,BRAZIL_CANDLE,EFFECT_CAKE,EFFECT_CANDLE)
                &&is(after.getBlock(),BRAZIL_CAKE,BRAZIL_CANDLE,EFFECT_CAKE,EFFECT_CANDLE);
    }
    public static net.minecraft.world.food.FoodProperties properties(Item item,net.minecraft.world.food.FoodProperties original){
        if(original!=null)return original;
        String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        if(id.equals("luncheonmeatsdelight:fried_egg_luncheon_meat"))return new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationMod(6).build();
        if(!id.equals("unusual_delight:glow_gummy"))return null;
        // The host directly adds 3 hunger and 0.2 saturation per bite.
        return new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationMod(.2F/6F).build();
    }
    private static ItemStack item(String id){return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);}
}
