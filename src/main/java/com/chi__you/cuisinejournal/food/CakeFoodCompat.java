package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.compat.HostClasses;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** A CakeBlock subclass is not a vanilla cake. Native slices win; unsliced cakes keep their item. */
public final class CakeFoodCompat {
    public static final String SERVING="cuisinejournal:cake_serving";
    private static final Map<String,String> SLICES=Map.of(
            "net.mehdinoui.veggiesdelight.common.block.CarrotCakeBlock","veggiesdelight:carrot_cake_slice",
            "ru.nub5s.galosphericdelight.block.GDCakeBlock","galospheric_delight:salted_caramel_cake_slice",
            "umpaz.farmersrespite.common.block.CoffeeCakeBlock","farmersrespite:coffee_cake_slice",
            "club.someoneice.pineapple.common.BlockPineappleCake","pineapple_delight:pineapple_cake_slice",
            "club.someoneice.pineapple.common.BlockPineappleCandleCake","pineapple_delight:pineapple_cake_slice");
    private CakeFoodCompat() {}
    public static boolean cake(Block block){return block instanceof CakeBlock||HostClasses.matches(block,"club.someoneice.pineapple.common.BlockPineappleCandleCake");}
    public static boolean knifeInteraction(Block block){
        return SLICES.keySet().stream().anyMatch(name->HostClasses.matches(block,name))
                ||HostClasses.matches(block,"dev.xkmc.twilightdelight.content.block.TwilightCakeBlock",
                "dev.xkmc.youkaishomecoming.content.block.food.YHCakeBlock");
    }
    public static ItemStack serving(Block block){
        if(block==Blocks.CAKE)return CuisineBlockFoods.cake();
        for(var row:SLICES.entrySet())if(HostClasses.matches(block,row.getKey())){
            var slice=item(row.getValue());if(!slice.isEmpty())return nativeValues(block,slice);
        }
        for(String field:List.of("slice","food")){
            Object raw=HostReflection.field(block,field);
            if(raw instanceof java.util.function.Supplier<?> supplier&&supplier.get() instanceof Item actual)
                return nativeValues(block,new ItemStack(actual));
        }
        var own=new ItemStack(block.asItem());
        if(own.isEmpty())for(var candidate:BuiltInRegistries.ITEM)if(candidate instanceof BlockItem placed&&placed.getBlock()==block){own=new ItemStack(candidate);break;}
        return nativeValues(block,own);
    }
    private static ItemStack nativeValues(Block block,ItemStack serving){
        if(serving.isEmpty())return serving;
        FoodProperties food=null;
        if(HostClasses.matches(block,"ru.nub5s.galosphericdelight.block.GDCakeBlock",
                "umpaz.farmersrespite.common.block.CoffeeCakeBlock"))food=copy(null,2,.1F);
        if(HostClasses.matches(block,"dev.xkmc.twilightdelight.content.block.TwilightCakeBlock")){
            Object raw=HostReflection.field(block,"food");if(raw instanceof FoodProperties value)food=value;
        }
        if(food==null)food=serving.getItem().getFoodProperties(serving,null);
        if(!CuisineEdibility.isFood(food))food=perBite(block);
        if(food!=null)mark(serving,food);
        return serving;
    }
    private static void mark(ItemStack stack,FoodProperties food){
        var tag=new net.minecraft.nbt.CompoundTag();tag.putInt("nutrition",food.getNutrition());tag.putFloat("saturation",food.getSaturationModifier());
        stack.getOrCreateTag().put(SERVING,tag);
    }
    public static FoodProperties properties(ItemStack stack,FoodProperties original){
        if(stack.hasTag()&&stack.getTag().contains(SERVING,10)){
            var tag=stack.getTag().getCompound(SERVING);return copy(original,tag.getInt("nutrition"),tag.getFloat("saturation"));
        }
        if(CuisineEdibility.isFood(original)||!(stack.getItem() instanceof BlockItem placed)
                ||placed.getBlock()==Blocks.CAKE||!cake(placed.getBlock()))return original;
        return perBite(placed.getBlock());
    }
    private static FoodProperties perBite(Block block){
        Object food=HostReflection.field(block,"food");if(food instanceof FoodProperties value)return value;
        if(HostClasses.matches(block,"ru.nub5s.galosphericdelight.block.GDCakeBlock",
                "umpaz.farmersrespite.common.block.CoffeeCakeBlock"))return copy(null,2,.1F);
        for(var entry:SLICES.entrySet())if(HostClasses.matches(block,entry.getKey())){
            var slice=item(entry.getValue());
            if(!slice.isEmpty()){
                var nativeFood=slice.getItem().getFoodProperties(slice,null);
                if(CuisineEdibility.isFood(nativeFood))return nativeFood;
            }
        }
        // These installed families call vanilla eating (2/.1), except the explicit chorus-cake override (4/.1).
        if(HostClasses.matches(block,"com.morallenplay.vanillacookbook.block.ChorusCakeBlock"))return copy(null,4,.1F);
        if(HostClasses.matches(block,"club.someoneice.pineapple.common.BlockPineappleCake"))return copy(null,3,.4F);
        if(block instanceof CakeBlock)return copy(null,2,.1F);
        return null;
    }
    private static FoodProperties copy(FoodProperties source,int nutrition,float saturation){
        var builder=new FoodProperties.Builder().nutrition(nutrition).saturationMod(saturation);
        if(source!=null){if(source.canAlwaysEat())builder.alwaysEat();
            for(var effect:source.getEffects())builder.effect(()->new net.minecraft.world.effect.MobEffectInstance(effect.getFirst()),effect.getSecond());}
        return builder.build();
    }
    public static int remaining(BlockState state){return 7-DelightPlacedFoods.value(state,"bites");}
    public static boolean sameCake(BlockState before,BlockState after){
        if(before.getBlock()==after.getBlock())return true;
        return HostClasses.matches(before.getBlock(),"club.someoneice.pineapple.common.BlockPineappleCake",
                "club.someoneice.pineapple.common.BlockPineappleCandleCake")
                &&HostClasses.matches(after.getBlock(),"club.someoneice.pineapple.common.BlockPineappleCake",
                "club.someoneice.pineapple.common.BlockPineappleCandleCake");
    }
    public static ItemStack eating(Block block,BlockState state,ItemStack held){
        if(remaining(state)<=0)return ItemStack.EMPTY;
        if(knifeInteraction(block)&&held.is(net.minecraft.tags.ItemTags.create(ResourceLocation.parse("forge:tools/knives"))))return ItemStack.EMPTY;
        boolean candleCake=HostClasses.matches(block,"club.someoneice.pineapple.common.BlockPineappleCandleCake");
        if(candleCake&&(held.is(Items.FLINT_AND_STEEL)||held.is(Items.FIRE_CHARGE)))return ItemStack.EMPTY;
        boolean decorates=block==Blocks.CAKE||HostClasses.matches(block,
                "club.someoneice.pineapple.common.BlockPineappleCake","umpaz.farmersrespite.common.block.CoffeeCakeBlock",
                "dev.xkmc.youkaishomecoming.content.block.food.YHCakeBlock","ru.nub5s.galosphericdelight.block.GDCakeBlock");
        if(decorates&&DelightPlacedFoods.value(state,"bites")==0&&held.is(net.minecraft.tags.ItemTags.CANDLES))return ItemStack.EMPTY;
        return serving(block);
    }
    private static ItemStack item(String value){return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(value)).map(ItemStack::new).orElse(ItemStack.EMPTY);}
}
