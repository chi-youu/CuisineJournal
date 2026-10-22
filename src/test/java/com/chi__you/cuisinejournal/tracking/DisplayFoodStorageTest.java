package com.chi__you.cuisinejournal.tracking;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import java.util.*;

public final class DisplayFoodStorageTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        SharedConstants.tryDetectVersion();
        var boot=Bootstrap.class.getDeclaredField("isBootstrapped");boot.setAccessible(true);boot.setBoolean(null,true);BuiltInRegistries.bootStrap();
        ItemStack low=food(1),high=food(4),plain=new ItemStack(Items.BREAD);
        var servings=List.of(low,high);
        var last=PlacedFoodVariants.preview(List.of(plain),servings,low,false,stack->false);
        check(last.size()==1&&stars(last.get(0))==4,"single take uses last insertion's identity");
        var all=PlacedFoodVariants.preview(List.of(new ItemStack(Items.BREAD,2)),servings,low,false,stack->false);
        check(all.size()==2&&stars(all.get(0))==4&&stars(all.get(1))==1,"whole plate separates incompatible qualities");
        var drops=PlacedFoodVariants.preview(List.of(new ItemStack(Items.BOWL),new ItemStack(Items.BREAD,2)),servings,low,false,stack->false);
        check(!drops.get(0).hasTag()&&stars(drops.get(1))==4&&stars(drops.get(2))==1,"plate item never consumes a stored food identity");
        var same=PlacedFoodVariants.preview(List.of(new ItemStack(Items.BREAD,2)),List.of(high,high),high,false,stack->false);
        check(same.size()==1&&same.get(0).getCount()==2,"identical servings may merge without changing count");
        var rows=new net.minecraft.nbt.ListTag();for(var item:servings)rows.add(item.save(new net.minecraft.nbt.CompoundTag()));
        check(stars(ItemStack.of(rows.getCompound(0)))==1&&stars(ItemStack.of(rows.getCompound(1)))==4,"decorative block payload preserves ordered physical servings after save/load");
        check(!plain.hasTag()&&stars(low)==1&&stars(high)==4,"drop preview cannot mutate stored or native templates");
        System.out.println("DisplayFoodStorageTest: "+checks+" checks passed");
    }
    private static ItemStack food(int stars){var item=new ItemStack(Items.BREAD);item.getOrCreateTag().putInt("test:stars",stars);return item;}
    private static int stars(ItemStack item){return item.hasTag()?item.getTag().getInt("test:stars"):0;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
