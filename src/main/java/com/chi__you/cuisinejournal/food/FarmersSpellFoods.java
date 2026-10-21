package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Verified portions; scrolls, utensils and native spell actions remain host-owned. */
public final class FarmersSpellFoods {
    public static final String CAKE="com.chenjdy.farmers_spell.block.RedVelvetCakeBlock";
    public static final String BREAD="com.chenjdy.farmers_spell.block.IcebreakerBreadBlock";
    public static final String CHICKEN="com.chenjdy.farmers_spell.block.SaingeziChickenBlock";
    private FarmersSpellFoods(){}
    public static boolean known(Block block){return DelightPlacedFoods.is(block,CAKE,BREAD,CHICKEN);}
    public static boolean multiblock(Block block){return DelightPlacedFoods.is(block,BREAD,CHICKEN);}
    public static String portion(String whole,int stage){
        return switch(whole){
            case "red_velvet_cake" -> "red_velvet_cake_slice";
            case "icebreaker_bread" -> stage>=1&&stage<=2?"icebergcream":stage>=3&&stage<=9?"icebergcream_sandwich":"";
            case "saingezi_chicken" -> stage>=1&&stage<5?"bowl_of_saingezi_chicken":"";
            default -> "";
        };
    }
    public static Map<String,Integer> portions(String whole){
        return switch(whole){
            case "red_velvet_cake" -> Map.of("red_velvet_cake_slice",8);
            case "icebreaker_bread" -> Map.of("icebergcream",2,"icebergcream_sandwich",7);
            case "saingezi_chicken" -> Map.of("bowl_of_saingezi_chicken",4);
            default -> Map.of();
        };
    }
    public static ItemStack serving(Block block,BlockState state){
        String path=portion(BuiltInRegistries.BLOCK.getKey(block).getPath(),DelightPlacedFoods.value(state,"stage"));
        return path.isEmpty()?ItemStack.EMPTY:BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("farmers_spell",path))
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
    public static boolean isPortion(ResourceLocation whole,ResourceLocation portion){
        return whole!=null&&portion!=null&&whole.getNamespace().equals("farmers_spell")
                &&portion.getNamespace().equals("farmers_spell")&&portions(whole.getPath()).containsKey(portion.getPath());
    }
    public static int remaining(BlockState state){
        return DelightPlacedFoods.is(state.getBlock(),CAKE)?Math.max(0,8-DelightPlacedFoods.value(state,"bites"))
                :Math.max(0,(DelightPlacedFoods.is(state.getBlock(),BREAD)?10:5)-DelightPlacedFoods.value(state,"stage"));
    }
    public static List<BlockPos> positions(BlockPos pos,BlockState state){
        if(!multiblock(state.getBlock()))return List.of(pos);
        int part=DelightPlacedFoods.value(state,"position");boolean bread=DelightPlacedFoods.is(state.getBlock(),BREAD);
        var facing=state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Object raw=bread?HostReflection.invoke(state.getBlock(),"getOriginPos",pos,part,facing)
                :HostReflection.invoke(state.getBlock(),"getOriginPos",pos,part);
        if(!(raw instanceof BlockPos origin))throw new IllegalStateException("Farmer's Spell multiblock origin contract changed");
        List<BlockPos> result=new ArrayList<>();
        for(int i=0;i<(bread?2:4);i++){
            Object blockPos=bread?HostReflection.invoke(state.getBlock(),"getBlockPos",origin,i,facing)
                    :HostReflection.invoke(state.getBlock(),"getBlockPos",origin,i);
            if(!(blockPos instanceof BlockPos actual))throw new IllegalStateException("Farmer's Spell multiblock position contract changed");
            result.add(actual);
        }
        return List.copyOf(result);
    }
}
