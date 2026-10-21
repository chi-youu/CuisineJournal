package com.chi__you.cuisinejournal.tracking;
import com.chi__you.cuisinejournal.food.FoodVariantIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.*;
/** Uses the item's recorded thrower; proximity is never evidence of contribution. */
public final class HotSpringProduction {
    private record Input(ItemEntity entity,int count,UUID owner,String item) {}
    private record Context(Level level,BlockPos pos,List<Input> inputs) {}
    private static final ProductionFrames<Deque<Context>> BATCH=ProductionFrames.stack();
    private HotSpringProduction() {}
    public static void begin(Level level,BlockPos pos) {
        if(level.isClientSide){BATCH.get().push(new Context(level,pos.immutable(),List.of()));return;}List<Input> inputs=new ArrayList<>();
        for(ItemEntity entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(pos,pos).inflate(1,1,1))) {
            ItemStack stack=entity.getItem();if(stack.isEmpty())continue;
            HotSpringSource.mark(entity);stack=entity.getItem();
            inputs.add(new Input(entity,stack.getCount(),HotSpringSource.owner(stack),BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        }
        BATCH.get().push(new Context(level,pos.immutable(),inputs));
    }
    public static ItemStack output(ItemStack item) {
        return output(item,item.getCount());
    }
    public static ItemStack output(ItemStack item,int total) {
        var context=BATCH.get().peek();if(context==null||context.level.isClientSide)return item;List<UUID> sources=new ArrayList<>();Set<String> ingredients=new HashSet<>();
        for(var input:context.inputs) {
            if(!input.entity.isRemoved()&&input.entity.getItem().getCount()>=input.count)continue;
            ingredients.add(input.item);sources.add(input.owner);
        }
        Set<UUID> owners=HotSpringSource.contributors(sources);HotSpringSource.clean(item);
        if(owners.size()==1&&context.level.getServer()!=null){
            com.chi__you.cuisinejournal.progression.CookingRewards.stampProduced(context.level.getServer(),item,owners.iterator().next(),total);
        }
        if(!com.chi__you.cuisinejournal.culinary.MealStamp.isStamped(item))item.getOrCreateTag().putInt(FoodVariantIdentity.BATCH_COUNT,total);
        DirectDropProgress.finish(context.level,item,new ProductionLedger.Batch("",item.getCount(),owners,ingredients,"immortalers_delight:hot_spring"));return item;
    }
    public static void end(Level level,BlockPos pos){
        var frames=BATCH.get();
        FrameStacks.finish(frames,frame->frame.level==level&&frame.pos.equals(pos));
        if(frames.isEmpty())BATCH.remove();
    }
}
