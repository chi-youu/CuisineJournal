package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
public final class KaleidoscopePlacedFoods {
    private KaleidoscopePlacedFoods(){}
    public static boolean known(Block block){return com.chi__you.cuisinejournal.compat.HostClasses.matches(block,"com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteBlock");}
    public static net.minecraft.core.BlockPos origin(net.minecraft.core.BlockPos pos,BlockState state){
        if(com.chi__you.cuisinejournal.compat.HostClasses.matches(state.getBlock(),"com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteThreeByThreeBlock")){
            for(var property:state.getProperties())if(property.getName().equals("part")){
                Object part=state.getValue(property),x=HostReflection.invoke(part,"getPosX"),z=HostReflection.invoke(part,"getPosY");
                if(x instanceof Number dx&&z instanceof Number dz)return pos.offset(-dx.intValue(),0,-dz.intValue());
            }
        }
        if(com.chi__you.cuisinejournal.compat.HostClasses.matches(state.getBlock(),"com.github.ysbbbbbb.kaleidoscopecookery.block.food.FoodBiteOneByTwoBlock")){
            var position=state.getBlock().getStateDefinition().getProperty("position");
            if(position instanceof net.minecraft.world.level.block.state.properties.IntegerProperty part&&state.getValue(part)==0)
                return pos.relative(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).getCounterClockWise());
        }return pos;
    }
    public static ItemStack serving(Block block,BlockState state){
        Object bites=HostReflection.invoke(block,"getBites"),max=HostReflection.invoke(block,"getMaxBites");
        if(bites instanceof net.minecraft.world.level.block.state.properties.IntegerProperty property
                &&max instanceof Number count&&state.getValue(property)>=count.intValue()){
            String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
            if(!id.equals("kaleidoscope_twilight:glacier_cake")&&!id.equals("kaleidoscope_twilight:witchcraft_cake"))return ItemStack.EMPTY;
        }
        ItemStack stack=new ItemStack(block);
        Object raw=HostReflection.field(block,"foodProperties");
        if(raw instanceof net.minecraft.world.food.FoodProperties food){var data=new net.minecraft.nbt.CompoundTag();
            data.putInt("nutrition",food.getNutrition());data.putFloat("saturation",food.getSaturationModifier());stack.getOrCreateTag().put(KaleidoscopeFood.SERVING,data);
        }
        for(var property:state.getProperties())if(property.getName().equals("quality")&&state.getValue(property) instanceof Integer quality&&quality<4)
            stack.getOrCreateTag().putInt(KaleidoscopeFood.QUALITY,quality);
        return stack;
    }
}
