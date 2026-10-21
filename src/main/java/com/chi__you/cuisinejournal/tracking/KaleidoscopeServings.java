package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Deque;
import java.util.function.Supplier;

/** Cups without block entities retain each serving separately, including mixed batches. */
public final class KaleidoscopeServings {
    private record Frame(Level level,BlockPos pos,boolean cups,boolean plate,java.util.Set<ItemStack> delivered){}
    private static final ProductionFrames<Deque<Frame>> FRAMES=ProductionFrames.stack();
    private KaleidoscopeServings(){}
    public static boolean supports(BlockState state){
        return com.chi__you.cuisinejournal.compat.HostClasses.matches(state.getBlock(),"com.github.ysbbbbbb.kaleidoscopecookery.block.drink.TeacupBlock")
                ||com.chi__you.cuisinejournal.compat.HostClasses.matches(state.getBlock(),"com.github.ysbbbbbb.kaleidoscopecookery.block.drink.EmptyCupBlock")
                ||plate(state)
                ||com.chi__you.cuisinejournal.compat.HostClasses.matches(state.getBlock(),"com.github.ysbbbbbb.kaleidoscopetavern.block.mixology.GlasswareBlock");
    }
    public static boolean plate(BlockState state){return com.chi__you.cuisinejournal.compat.HostClasses.matches(state.getBlock(),"com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.PlateBlock");}
    private static int tea(BlockState state){
        Object property=HostReflection.invoke(state.getBlock(),plate(state)?"getServingsProperty":"getTeaCountProperty");
        return property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty count?state.getValue(count):0;
    }
    public static InteractionResult use(Level level,BlockPos pos,BlockState before,ItemStack held,Supplier<InteractionResult> action){
        if(level.isClientSide)return action.get();
        ItemStack input=held.copy();boolean cups=before.getBlock().getClass().getName().contains("cookery");
        var frames=FRAMES.get();frames.push(new Frame(level,pos,cups,plate(before),java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())));
        try{
            var result=action.get();var after=level.getBlockState(pos);
            if(cups&&result.consumesAction()&&tea(after)>tea(before)){
                Object poured=HostReflection.invoke(input.getItem(),"getPourOut",input);
                ItemStack serving=poured instanceof ItemStack stack?stack.copyWithCount(1):input.copyWithCount(1);
                PlacedFoodVariants.addCup(level,pos,serving,tea(before));
            }
            if(cups&&tea(after)<tea(before))PlacedFoodVariants.finishServing(level,pos,tea(after));
            if(!cups&&after.getBlock()!=before.getBlock())PlacedFoodVariants.clear(level,pos);
            return result;
        }finally{frames.pop();if(frames.isEmpty())FRAMES.remove();}
    }
    public static void output(ItemStack stack){
        var frames=FRAMES.get();if(frames.isEmpty()||stack.isEmpty())return;var frame=frames.peek();
        if(!frame.delivered.add(stack))return;
        ItemStack marked=frame.plate?PlacedFoodVariants.plateServing(frame.level,frame.pos,stack)
                :PlacedFoodVariants.read(frame.level,frame.pos,stack);
        if(marked.hasTag())stack.setTag(marked.getTag().copy());
    }
}
