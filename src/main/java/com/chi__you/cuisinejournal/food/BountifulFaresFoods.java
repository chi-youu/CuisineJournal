package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostClasses;
import com.chi__you.cuisinejournal.tracking.VerifiedWorkstations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Bountiful Fares ceramic dishes and quarter pastries. */
public final class BountifulFaresFoods {
    private static final String QUARTER="net.hecco.bountifulfares.block.custom.QuarterPastryBlock";
    private static final String DISH="net.hecco.bountifulfares.block.custom.CeramicDishBlock";
    private BountifulFaresFoods() {}

    public static boolean table(Block block) { return HostClasses.matches(block,DISH); }
    public static boolean quarter(Block block) { return HostClasses.matches(block,QUARTER); }
    public static ItemStack table(Level level,BlockPos pos,ItemStack held,boolean sneaking) {
        if(sneaking&&held.isEmpty())return ItemStack.EMPTY;
        if(!held.isEmpty()&&net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals("bountifulfares:artisan_brush"))return ItemStack.EMPTY;
        var food=VerifiedWorkstations.stack(level.getBlockEntity(pos),0);
        if(food.isEmpty())return ItemStack.EMPTY;
        try {
            var raw=Class.forName(DISH).getMethod("canEatOnDish",ItemStack.class).invoke(null,food);
            return Boolean.TRUE.equals(raw)?food.copyWithCount(1):ItemStack.EMPTY;
        } catch(ReflectiveOperationException|LinkageError failure) {
            throw new IllegalStateException("Cannot resolve native ceramic serving",failure);
        }
    }
    public static FoodProperties properties(ItemStack stack,FoodProperties original) {
        if(stack.getItem() instanceof BlockItem item&&quarter(item.getBlock()))
            return new FoodProperties.Builder().nutrition(4).saturationMod(.3F).build();
        return original;
    }
}
