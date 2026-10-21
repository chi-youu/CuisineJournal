package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Host-verified B002 bite identities; candle states refer to their original cake. */
public final class AbnormalsPlacedFoods {
    public static final String CAKE="com.teamabnormals.neapolitan.common.block.FlavoredCakeBlock";
    public static final String CANDLE="com.teamabnormals.neapolitan.common.block.FlavoredCandleCakeBlock";
    public static final String PANCAKE="com.teamabnormals.autumnity.common.block.PancakeBlock";
    public static final String EXQUISITO="com.gumillea.exquisito.common.block.ExquisitoCakeBlock";
    public static final String CUPRIC="com.teamabnormals.caverns_and_chasms.common.block.cupric.CupricCandleCakeBlock";
    public static final String YUCCA="com.teamabnormals.atmospheric.common.block.YuccaGateauBlock";
    public static final String TURKEY="com.teamabnormals.autumnity.common.block.TurkeyBlock";
    private AbnormalsPlacedFoods(){}
    public static boolean is(Object value,String... names){return DelightPlacedFoods.is(value,names);}
    public static boolean known(Block block){return is(block,CAKE,CANDLE,PANCAKE,CUPRIC,YUCCA,TURKEY);}
    public static Block base(Block block){
        if(is(block,CANDLE)){Object cake=HostReflection.invoke(block,"getCake");if(cake instanceof Block value)return value;}
        if(is(block,CUPRIC))return net.minecraft.world.level.block.Blocks.CAKE;
        return block;
    }
    public static ItemStack serving(Block block){
        Block base=base(block);
        if(is(base,TURKEY)){
            Object piece=HostReflection.invokeDeclared(base,"getLeg");
            if(piece instanceof Item item)return new ItemStack(item);
            throw new IllegalStateException("Missing turkey serving item");
        }
        if(is(block,CUPRIC))return CuisineBlockFoods.cake();
        if(is(base,EXQUISITO)){
            var slice=com.chi__you.foodinteractions.ExquisitoServingRelations.slice(base);
            if(!slice.isEmpty())return slice;
        }
        return base.asItem()==Items.AIR?ItemStack.EMPTY:new ItemStack(base.asItem());
    }
    public static int remaining(BlockState state){
        if(is(state.getBlock(),TURKEY))return 5-DelightPlacedFoods.value(state,"chunks");
        return is(state.getBlock(),PANCAKE)?DelightPlacedFoods.value(state,"pancakes"):(is(state.getBlock(),YUCCA)?10:7)-DelightPlacedFoods.value(state,"bites");
    }
    public static boolean sameMeal(BlockState before,BlockState after){return base(before.getBlock())==base(after.getBlock());}
    public static boolean turkeyCutting(ItemStack held){
        return net.minecraftforge.fml.ModList.get().isLoaded("farmersdelight")
                ? held.is(net.minecraft.tags.ItemTags.create(ResourceLocation.parse("farmersdelight:tools/knives")))
                : held.getItem() instanceof AxeItem;
    }
    public static FoodProperties properties(ItemStack stack,FoodProperties original){
        if(original!=null||!(stack.getItem() instanceof BlockItem item))return original;
        Block block=item.getBlock();
        if(is(block,TURKEY)){
            var serving=serving(block);return serving.getItem().getFoodProperties(serving,null);
        }
        if(is(block,CAKE)){Object food=HostReflection.field(block,"food");if(food instanceof FoodProperties value)return value;}
        // A placed pancake item contains two bites; these are the host's per-bite values.
        if(is(block,PANCAKE))return new FoodProperties.Builder().nutrition(4).saturationMod(.3F).build();
        if(is(block,YUCCA))return new FoodProperties.Builder().nutrition(1).saturationMod(0).build();
        return original;
    }
}
