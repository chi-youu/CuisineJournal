package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.food.FoodVariantIdentity;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import java.util.*;

public final class PlacedFoodDropsTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        SharedConstants.tryDetectVersion();
        var boot=Bootstrap.class.getDeclaredField("isBootstrapped");boot.setAccessible(true);boot.setBoolean(null,true);BuiltInRegistries.bootStrap();
        var stamp=new MealStamp(4,true,5,new UUID(17,23),"Original Cook");
        for(int remaining=1;remaining<=7;remaining++){
            var identity=new PlacedFoodDrops.DropIdentity(stamp,Map.of(id(Items.BREAD),remaining));
            for(int i=0;i<remaining;i++){
                var piece=new ItemStack(Items.BREAD);identity.apply(piece);
                check(stamp.equals(MealStamp.read(piece)),"each remaining native slice preserves full quality and original maker");
                check(piece.getCount()==1&&!piece.getTag().contains(FoodVariantIdentity.PENDING_PRODUCTION),"inheritance neither changes count nor creates pending cooking credit");
            }
            var extra=new ItemStack(Items.BREAD);identity.apply(extra);check(!extra.hasTag(),"exhausted source cannot stamp unrelated extra outputs");
        }
        var missing=new PlacedFoodDrops.DropIdentity(null,Map.of(id(Items.BREAD),4));
        var plain=new ItemStack(Items.BREAD,4);missing.apply(plain);check(!plain.hasTag(),"untracked old placed food never invents a maker or stars");
        var platter=new PlacedFoodDrops.DropIdentity(stamp,Map.of(id(Items.APPLE),1,id(Items.BREAD),1));
        var apple=new ItemStack(Items.APPLE);var bread=new ItemStack(Items.BREAD);var bowl=new ItemStack(Items.BOWL);
        platter.apply(apple);platter.apply(bread);platter.apply(bowl);
        check(stamp.equals(MealStamp.read(apple))&&stamp.equals(MealStamp.read(bread))&&!bowl.hasTag(),"mixed native platter inherits only the remaining food identities");
        var budget=new PlacedFoodDrops.DropIdentity(stamp,Map.of(id(Items.BREAD),2));
        var oversized=new ItemStack(Items.BREAD,3);budget.apply(oversized);
        check(!oversized.hasTag()&&oversized.getCount()==3,"unexpected oversized drops remain native and unstamped");
        var first=new ItemStack(Items.BREAD);budget.apply(first);budget.apply(first);
        var second=new ItemStack(Items.BREAD);budget.apply(second);
        check(stamp.equals(MealStamp.read(second)),"duplicate observer on the same stack does not spend another serving");
        var pocky=new PlacedFoodDrops.DropIdentity(stamp,Map.of(id(Items.BREAD),8,id(Items.CHEST),1));
        var paper=new ItemStack(Items.PAPER);var contents=new ItemStack(Items.BREAD,8);contents.getOrCreateTag().putString("test:native","retained");
        pocky.apply(contents);pocky.apply(paper);
        check(stamp.equals(MealStamp.read(contents))&&contents.getCount()==8&&!paper.hasTag(),"box drops preserve all eight foods without stamping packaging");
        check(contents.getTag().getString("test:native").equals("retained"),"native output NBT remains intact");
        var whole=new ItemStack(Items.CHEST);pocky.apply(whole);
        check(stamp.equals(MealStamp.read(whole))&&whole.getCount()==1,"silk-touch whole retains quality without multiplying count");
        savedStamp(stamp);
        System.out.println("PlacedFoodDropsTest: "+checks+" checks passed");
    }
    private static void savedStamp(MealStamp expected)throws Exception{
        var placed=new ItemStack(Items.BREAD);expected.write(placed);
        var raw=placed.getTag().getCompound(MealStamp.ROOT).copy();
        var row=new CompoundTag();row.putLong("pos",123);row.putString("block","slime_delight:grass_block_cake_4");row.put("stamp",raw);
        row.put("batch",ProductionLedger.writeBatch(new ProductionLedger.Batch("slime_delight:grass_block_cake_1",4,Set.of(expected.cookId()),Set.of(),"cuisinejournal:placed_food")));
        var meals=new ListTag();meals.add(row);var data=new CompoundTag();data.put("meals",meals);
        var load=PlacedMealProduction.class.getDeclaredMethod("load",CompoundTag.class);load.setAccessible(true);
        var saved=(PlacedMealProduction)load.invoke(null,data);var restored=saved.save(new CompoundTag()).getList("meals",10).getCompound(0);
        var stack=new ItemStack(Items.BREAD);stack.getOrCreateTag().put(MealStamp.ROOT,restored.getCompound("stamp"));
        check(expected.equals(MealStamp.read(stack)),"placed quality survives save/reload before later destruction");
        check(restored.getString("block").equals("slime_delight:grass_block_cake_4")&&restored.getCompound("batch").equals(row.getCompound("batch")),"stage and remaining batch survive reload without new production");
    }
    private static ResourceLocation id(Item item){return BuiltInRegistries.ITEM.getKey(item);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
