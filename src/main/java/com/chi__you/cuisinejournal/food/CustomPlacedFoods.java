package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.*;

/** Only host families with confirmed per-bite getters. */
public final class CustomPlacedFoods {
    public static final String ALEX_CAKE="io.github.rcneg.alexsmobsdelight.blocks.CommonCakeBlock";
    public static final String SPELUNKIE="net.thevaliantsquidward.cavedelight.blocks.SpelunkieCakeBlock";
    private CustomPlacedFoods() {}
    public static boolean table(Block block){return BountifulFaresFoods.table(block);}
    public static boolean known(Block block){return CosmopolitanFoods.known(block)||BountifulFaresFoods.quarter(block)||table(block)
            ||com.chi__you.cuisinejournal.compat.HostClasses.matches(block,ALEX_CAKE)||com.chi__you.cuisinejournal.compat.HostClasses.matches(block,SPELUNKIE);}
    public static ItemStack table(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,ItemStack held,boolean sneaking){
        return BountifulFaresFoods.table(level,pos,held,sneaking);
    }
    public static ItemStack serving(Block block){
        if(BountifulFaresFoods.quarter(block))return new ItemStack(block.asItem());
        if(com.chi__you.cuisinejournal.compat.HostClasses.matches(block,ALEX_CAKE)){var raw=HostReflection.field(block,"sliceItem");
            return raw instanceof java.util.function.Supplier<?> supplier&&supplier.get() instanceof Item item?new ItemStack(item):ItemStack.EMPTY;}
        Object raw=HostReflection.invoke(block,"getPieSliceItem");return raw instanceof ItemStack stack?stack.copyWithCount(1):ItemStack.EMPTY;
    }
    public static net.minecraft.world.food.FoodProperties properties(ItemStack stack,net.minecraft.world.food.FoodProperties original){
        return BountifulFaresFoods.properties(stack,original);
    }
    public static int remaining(BlockState state){
        Object raw=HostReflection.invoke(state.getBlock(),"getMaxBites");int count=raw instanceof Number n?n.intValue():4;
        if(com.chi__you.cuisinejournal.compat.HostClasses.matches(state.getBlock(),ALEX_CAKE))count=7;
        count=CosmopolitanFoods.servings(state,count);
        return Math.max(0,count-DelightPlacedFoods.value(state,"bites"));
    }
    public static ItemStack eating(Block block,BlockState state,ItemStack held){
        if(!held.isEmpty()&&(held.is(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.parse("forge:tools/knives")))
                ||held.is(block.asItem())||held.is(Items.FLINT_AND_STEEL)||held.is(net.minecraft.tags.ItemTags.CANDLES)))return ItemStack.EMPTY;
        if(CosmopolitanFoods.refilling(block,held,serving(block)))return ItemStack.EMPTY;
        return serving(block);
    }
}
