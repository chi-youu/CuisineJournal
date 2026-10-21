package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Cooking pots consume inventories and replace themselves; boards retain contributors across transformations. */
public final class YoukaiCookingProduction {
    private static final String KEY="cuisinejournal:youkai_cooking";
    private record Completion(BlockEntity owner, ProductionLedger.Batch batch) {}
    private static final ProductionFrames<Deque<Completion>> COMPLETIONS=ProductionFrames.stack();
    private YoukaiCookingProduction() {}
    private static String identity(ItemStack stack){return stack.isEmpty()?"":BuiltInRegistries.ITEM.getKey(stack.getItem())+"|"+Objects.toString(stack.getTag(),"");}
    private static ProductionLedger ledger(BlockEntity be){return ProductionLedger.load(be.getPersistentData().getCompound(KEY));}
    private static void save(BlockEntity be,ProductionLedger ledger){be.getPersistentData().put(KEY,ledger.save());be.setChanged();}
    public static void inputBefore(BlockEntity be) {
        Object raw=HostReflection.field(be,"items");if(!(raw instanceof Container container))return;
        var ledger=ledger(be);for(int i=0;i<container.getContainerSize();i++){var item=container.getItem(i);ledger.syncInput(i,identity(item),item.getCount(),null);}save(be,ledger);
    }
    public static void inputAfter(BlockEntity be,ItemStack item,boolean success) {
        if(!success||be.getLevel()==null||be.getLevel().isClientSide)return;
        Object raw=HostReflection.field(be,"items");if(!(raw instanceof Container container))return;
        var ledger=ledger(be);ServerPlayer player=ProductionTracker.actor();
        for(int i=0;i<container.getContainerSize();i++){var stack=container.getItem(i);ledger.syncInput(i,identity(stack),stack.getCount(),player==null?null:player.getUUID());}save(be,ledger);
    }
    public static void begin(BlockEntity be,String method) {
        if(be.getLevel()==null||be.getLevel().isClientSide){COMPLETIONS.get().push(new Completion(be,null));return;}
        Object raw=HostReflection.field(be,"items");var ledger=ledger(be);Map<Integer,Integer> consumed=new TreeMap<>();
        if(raw instanceof Container container)for(int i=0;i<container.getContainerSize();i++){var item=container.getItem(i);ledger.syncInput(i,identity(item),item.getCount(),null);if(!item.isEmpty())consumed.put(i,item.getCount());}
        var batch=ledger.produce(consumed,"",1,method);save(be,ledger);COMPLETIONS.get().push(new Completion(be,batch));
    }
    public static void end(BlockEntity be){var scopes=COMPLETIONS.get();ProductionFrames.take(scopes,frame->frame.owner==be);if(scopes.isEmpty())COMPLETIONS.remove();}
    public static ItemStack output(ItemStack item){var frame=COMPLETIONS.get().peek();if(frame!=null&&frame.batch!=null)ProductionTracker.pending(item,frame.batch);return item;}
    public static void placed(BlockEntity be,BlockState state) {
        var frame=COMPLETIONS.get().peek();if(frame==null||frame.owner!=be||frame.batch==null)return;var batch=frame.batch;int count=1;
        for(var property:state.getProperties())if(property.getName().equals("serve")&&state.getValue(property) instanceof Integer number)count=number;
        PlacedMealProduction.store(be.getLevel(),be.getBlockPos(),state,new ProductionLedger.Batch(batch.identity(),count,batch.contributors(),batch.ingredients(),batch.method()));
    }
    public static void boardInput(BlockEntity be,ItemStack input,int used) {
        if(used<=0||be.getLevel()==null||be.getLevel().isClientSide||!boardAllows(be,WorkstationDefinitions.Capability.ITEM_INPUT))return;
        var ledger=ledger(be);ServerPlayer player=ProductionTracker.actor();
        ledger.syncInput(0,identity(input),used,player==null?null:player.getUUID());
        var batch=ledger.produce(Map.of(0,used),"board",1,"youkaishomecoming:cuisine");ledger.append(1,batch);save(be,ledger);
    }
    public static ItemStack boardResult(BlockEntity be,ServerPlayer player,ItemStack item) {
        if(item.isEmpty()||be.getLevel()==null||be.getLevel().isClientSide||!boardAllows(be,WorkstationDefinitions.Capability.ITEM_OUTPUT))return item;
        var ledger=ledger(be);var batch=ledger.peek(1);if(batch==null)return item;
        ProductionTracker.claimBatch(player,item,batch,CulinaryCredit.Source.INTERACTION);ledger.take(1,1);save(be,ledger);return item;
    }
    private static boolean boardAllows(BlockEntity be,WorkstationDefinitions.Capability capability) {
        var definition=WorkstationDefinitions.get(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()));
        return definition==null||definition.enabled()&&definition.capabilities().contains(capability);
    }
    public static ItemStack boardCompleted(BlockEntity be) {
        if(be.getLevel()==null||be.getLevel().isClientSide)return ItemStack.EMPTY;
        Object model=HostReflection.invoke(be,"getModel");
        Object completed=HostReflection.invoke(model,"complete",be.getLevel());
        if(completed instanceof Optional<?> result&&result.orElse(null) instanceof ItemStack stack)return stack.copy();
        return ItemStack.EMPTY;
    }
    public static void clearBoard(BlockEntity be){be.getPersistentData().remove(KEY);be.setChanged();}
}
