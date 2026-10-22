package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostClasses;
import com.chi__you.cuisinejournal.compat.HostReflection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Decorative storage preserves each physical serving, without invoking cooking rewards. */
public final class DisplayFoodStorage {
    public static final String HOST="com.jkvin114.displaydelight.block.AbstractItemBlock";
    private static final String CONTENTS="cuisinejournal:display_contents";
    private record Insertion(ServerLevel level,BlockPos pos,ItemStack item,int previous) {}
    private record Retrieval(ServerLevel level,BlockPos pos,BlockState state) {}
    private static final ProductionFrames<Deque<Insertion>> INSERTIONS=ProductionFrames.stack();
    private static final ProductionFrames<Deque<Retrieval>> RETRIEVALS=ProductionFrames.stack();
    private DisplayFoodStorage() {}

    public static boolean supports(BlockState state){return HostClasses.matches(state.getBlock(),HOST);}
    public static int count(BlockState state){
        if(!supports(state))return 0;
        Object stacks=HostReflection.invoke(state.getBlock(),"getStacks",state);
        return stacks instanceof Number value?value.intValue():1;
    }
    private static ItemStack food(BlockState state){
        if(!supports(state))return ItemStack.EMPTY;
        Object item=HostReflection.invoke(state.getBlock(),"getStackFor");
        return item instanceof ItemStack stack?stack:ItemStack.EMPTY;
    }
    public static void beginInsert(Player player,ServerLevel level,BlockPos pos,boolean mainHand){
        var held=player.getItemInHand(mainHand?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND);
        INSERTIONS.get().push(new Insertion(level,pos.immutable(),held.copy(),count(level.getBlockState(pos))));
    }
    public static void beginWorldPlace(Player player,ServerLevel level,net.minecraft.world.phys.BlockHitResult hit,boolean mainHand){
        var hand=mainHand?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
        var context=new net.minecraft.world.item.context.BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(player,hand,hit));
        beginInsert(player,level,context.getClickedPos(),mainHand);
    }
    public static void endInsert(boolean success){
        var frames=INSERTIONS.get();var frame=frames.poll();if(frames.isEmpty())INSERTIONS.remove();
        if(frame==null||!success)return;
        var state=frame.level.getBlockState(frame.pos);
        if(!ItemStack.isSameItem(frame.item,food(state)))return;
        int added=count(state)-frame.previous;
        if(frame.previous>0&&PlacedFoodVariants.stored(frame.level,frame.pos).isEmpty())
            for(int i=0;i<frame.previous;i++)PlacedFoodVariants.addCup(frame.level,frame.pos,food(state),i);
        for(int i=0;i<added;i++)PlacedFoodVariants.addCup(frame.level,frame.pos,frame.item,frame.previous+i);
    }
    public static void placed(Level level,BlockPos pos,BlockState state,ItemStack source){
        if(level.isClientSide||!supports(state))return;
        int count=count(state);
        var rows=source.hasTag()?source.getTag().getList(CONTENTS,10):new ListTag();
        PlacedFoodVariants.clear(level,pos);
        ItemStack expected=food(state);
        for(int i=0;i<count;i++){
            ItemStack item=i<rows.size()?ItemStack.of(rows.getCompound(i)):source;
            if(!ItemStack.isSameItem(item,expected))item=expected;
            PlacedFoodVariants.addCup(level,pos,item,i);
        }
    }
    public static void beginTake(ServerLevel level,BlockPos pos){
        RETRIEVALS.get().push(new Retrieval(level,pos.immutable(),level.getBlockState(pos)));
    }
    public static void endTake(boolean success){
        var frames=RETRIEVALS.get();var frame=frames.poll();if(frames.isEmpty())RETRIEVALS.remove();
        if(frame!=null&&success)PlacedFoodVariants.finishServing(frame.level,frame.pos,count(frame.level.getBlockState(frame.pos)));
    }
    private static List<ItemStack> returned(ItemStack nativeStack){
        var frame=RETRIEVALS.get().peek();
        return frame==null?List.of(nativeStack):PlacedFoodVariants.drops(frame.level,frame.pos,frame.state,List.of(nativeStack),false);
    }
    public static boolean intoInventory(Player player,ItemStack nativeStack){
        boolean accepted=true;
        for(var stack:returned(nativeStack)){
            boolean inserted=player.getInventory().add(stack);accepted&=inserted;
            if(!stack.isEmpty())player.drop(stack,false);
        }
        return accepted;
    }
    public static void intoHand(Player player,InteractionHand hand,ItemStack nativeStack){
        var stacks=returned(nativeStack);
        if(stacks.isEmpty())return;
        player.setItemInHand(hand,stacks.get(0));
        for(int i=1;i<stacks.size();i++){var stack=stacks.get(i);player.getInventory().add(stack);if(!stack.isEmpty())player.drop(stack,false);}
    }
    public static List<ItemStack> drops(Level level,BlockPos pos,BlockState state,List<ItemStack> nativeDrops){
        var contents=PlacedFoodVariants.drops(level,pos,state,List.of(food(state).copyWithCount(count(state))),false);
        var result=PlacedFoodVariants.drops(level,pos,state,nativeDrops,false);
        for(var output:result)if(output.is(state.getBlock().asItem())){
            var rows=new ListTag();
            // Restore insertion order; normal retrieval takes the last stored serving first.
            for(int i=contents.size()-1;i>=0;i--){var part=contents.get(i);for(int n=0;n<part.getCount();n++)rows.add(part.copyWithCount(1).save(new CompoundTag()));}
            output.getOrCreateTag().put(CONTENTS,rows);
        }
        return result;
    }
}
