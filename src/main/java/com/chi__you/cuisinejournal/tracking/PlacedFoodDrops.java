package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.food.DelightPlacedFoods;
import com.chi__you.foodinteractions.SlimeFoodServings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Supplier;

/** Native drops keep their count and conditions; only existing finished quality is inherited. */
public final class PlacedFoodDrops {
    private record Frame(Level level,BlockPos pos,BlockState state,DropIdentity identity) {}
    private static final ProductionFrames<Deque<Frame>> BREAKS=ProductionFrames.stack();
    private PlacedFoodDrops() {}

    public static boolean supports(BlockState state){
        var block=state.getBlock();var id=BuiltInRegistries.BLOCK.getKey(block);
        return DelightPlacedFoods.is(block,DelightPlacedFoods.KRABBY,DelightPlacedFoods.COLORFUL_KRABBY)
                ||id.getNamespace().equals("slime_delight")&&(
                SlimeFoodServings.remaining(id)>0&&block.getClass().getName().startsWith("net.mcreator.slimedelight.block.GrassBlockCake")
                ||id.getPath().equals("slime_pocky_box")&&block.getClass().getName().equals("net.mcreator.slimedelight.block.SlimePockyBoxBlock"));
    }
    private static DropIdentity identity(Level level,BlockPos pos,BlockState state){
        var outputs=new HashMap<ResourceLocation,Integer>();var block=state.getBlock();
        var id=BuiltInRegistries.BLOCK.getKey(block);
        if(DelightPlacedFoods.is(block,DelightPlacedFoods.KRABBY)){
            var serving=DelightPlacedFoods.serving(block,state);
            outputs.put(BuiltInRegistries.ITEM.getKey(serving.getItem()),DelightPlacedFoods.remaining(state));
            if(DelightPlacedFoods.value(state,"bites")==0)outputs.put(BuiltInRegistries.ITEM.getKey(block.asItem()),1);
        }else if(DelightPlacedFoods.is(block,DelightPlacedFoods.COLORFUL_KRABBY)){
            Object raw=HostReflection.field(block,"KRABBY_PATTYS");
            if(raw instanceof List<?> items)for(int i=0;i<Math.min(items.size(),DelightPlacedFoods.value(state,"color"));i++)
                if(items.get(i) instanceof Supplier<?> supplier&&supplier.get() instanceof Item item)
                    outputs.merge(BuiltInRegistries.ITEM.getKey(item),1,Integer::sum);
        }else if(SlimeFoodServings.remaining(id)>0)outputs.put(SlimeFoodServings.slice(),SlimeFoodServings.remaining(id));
        else if(id.toString().equals("slime_delight:slime_pocky_box")){
            outputs.put(ResourceLocation.parse("slime_delight:slime_pocky"),8);outputs.put(id,1);
        }
        return new DropIdentity(PlacedMealProduction.storedStamp(level,pos,state),outputs);
    }
    public static void begin(Level level,BlockPos pos,BlockState state){
        BREAKS.get().push(new Frame(level,pos.immutable(),state,
                !level.isClientSide&&supports(state)?identity(level,pos,state):new DropIdentity(null,Map.of())));
    }
    public static void end(Level level,BlockPos pos,BlockState state){
        var frames=BREAKS.get();ProductionFrames.take(frames,frame->frame.level==level&&frame.pos.equals(pos)&&frame.state.equals(state));
        if(frames.isEmpty())BREAKS.remove();
    }
    public static void spawned(ItemEntity entity){
        var frame=BREAKS.get().peek();
        if(frame!=null&&frame.level==entity.level()&&entity.getX()>=frame.pos.getX()&&entity.getX()<frame.pos.getX()+1
                &&entity.getY()>=frame.pos.getY()&&entity.getY()<frame.pos.getY()+1
                &&entity.getZ()>=frame.pos.getZ()&&entity.getZ()<frame.pos.getZ()+1)frame.identity.apply(entity.getItem());
    }
    public static List<ItemStack> loot(Level level,BlockPos pos,BlockState state,List<ItemStack> drops){
        if(level.isClientSide||!supports(state))return drops;
        var identity=identity(level,pos,state);var result=new ArrayList<ItemStack>(drops.size());
        for(var drop:drops){var copy=drop.copy();identity.apply(copy);result.add(copy);}
        return result;
    }
    static final class DropIdentity {
        private final MealStamp stamp;
        private final Map<ResourceLocation,Integer> remaining;
        private final Set<ItemStack> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        DropIdentity(MealStamp stamp,Map<ResourceLocation,Integer> quantities){this.stamp=stamp;remaining=new HashMap<>(quantities);}
        void apply(ItemStack stack){
            if(stamp==null||stack.isEmpty()||!seen.add(stack))return;
            var id=BuiltInRegistries.ITEM.getKey(stack.getItem());int available=remaining.getOrDefault(id,0);
            // A third-party oversized stack must not duplicate the stored serving identity.
            if(stack.getCount()>available)return;
            remaining.put(id,available-stack.getCount());stamp.write(stack);
        }
    }
}
