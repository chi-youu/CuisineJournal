package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Steamer rack data holds a single input/result item; provenance is persisted with the rack. */
public final class InPlaceProductionTracker {
    private static final String KEY="cuisinejournal:rack_production";
    private static final Map<Object,Snapshot> COOKING=Collections.synchronizedMap(new IdentityHashMap<>());
    private record Snapshot(BlockEntity be,ItemStack before,int index) {}
    private InPlaceProductionTracker() {}
    private static String identity(ItemStack stack){return stack.isEmpty()?"":BuiltInRegistries.ITEM.getKey(stack.getItem())+"|"+Objects.toString(stack.getTag(),"");}
    private static ItemStack stack(Object data){Object stack=HostReflection.field(data,"stack");return stack instanceof ItemStack item?item:ItemStack.EMPTY;}
    private static int index(BlockEntity be,Object data) {
        Object racks=HostReflection.field(be,"racks");if(!(racks instanceof List<?> list))return -1;
        for(int r=0;r<list.size();r++) {
            Object raw=HostReflection.field(list.get(r),"list");if(!(raw instanceof Object[] items))continue;
            for(int i=0;i<items.length;i++)if(items[i]==data)return r*4+i;
        }
        return -1;
    }
    private static ProductionLedger ledger(BlockEntity be){return ProductionLedger.load(be.getPersistentData().getCompound(KEY));}
    private static void save(BlockEntity be,ProductionLedger ledger){be.getPersistentData().put(KEY,ledger.save());be.setChanged();}
    public static void begin(Object data,BlockEntity be) {
        if(be.getLevel()==null||be.getLevel().isClientSide)return;int index=index(be,data);if(index<0)return;
        ItemStack item=stack(data);var ledger=ledger(be);ledger.syncInput(index,identity(item),item.getCount(),null);save(be,ledger);
        COOKING.put(data,new Snapshot(be,item.copy(),index));
    }
    public static void end(Object data) {
        Snapshot snapshot=COOKING.remove(data);if(snapshot==null)return;
        ItemStack after=stack(data);if(ItemStack.isSameItemSameTags(snapshot.before,after)||after.isEmpty())return;
        var ledger=ledger(snapshot.be);var batch=ledger.produce(Map.of(snapshot.index,snapshot.before.getCount()),identity(after),after.getCount(),YoukaiWorkstations.method(snapshot.be,"steaming"));
        ledger.append(snapshot.index,batch);ledger.adoptInput(snapshot.index,identity(after),after.getCount(),batch);save(snapshot.be,ledger);
    }
    public static void inserted(Object data,BlockEntity be,ItemStack before,ServerPlayer player) {
        if(be.getLevel()==null||be.getLevel().isClientSide||COOKING.containsKey(data))return;
        int index=index(be,data);if(index<0)return;ItemStack after=stack(data);var ledger=ledger(be);
        int held=ItemStack.isSameItemSameTags(before,after)?before.getCount():0;
        ledger.syncInput(index,identity(after),held,null);ledger.syncInput(index,identity(after),after.getCount(),player==null?null:player.getUUID());
        if(held==0)ledger.syncOutput(index,identity(after),0,YoukaiWorkstations.method(be,"steaming"));save(be,ledger);
    }
    public static ItemStack claim(BlockEntity be,Object rack,int slot,ServerPlayer player,ItemStack item) {
        Object racks=HostReflection.field(be,"racks");if(!(racks instanceof List<?> list))return item;
        int r=list.indexOf(rack);if(r<0)return item;int index=r*4+slot;
        var ledger=ledger(be);var batch=ledger.peek(index);
        if(batch!=null&&batch.identity().equals(identity(item))) {
            ProductionTracker.claimBatch(player,item,batch,CulinaryCredit.Source.INTERACTION);
            ledger.take(index,item.getCount());ledger.syncInput(index,"",0,null);save(be,ledger);
        }
        return item;
    }
    public static void withdrawn(BlockEntity be,Object rack,int slot) {
        Object raw=HostReflection.field(be,"racks");if(!(raw instanceof List<?> racks))return;
        int r=racks.indexOf(rack);if(r<0)return;var ledger=ledger(be);int index=r*4+slot;
        ledger.syncInput(index,"",0,null);ledger.syncOutput(index,"",0,YoukaiWorkstations.method(be,"steaming"));save(be,ledger);
    }
    public static void removedRack(BlockEntity be) {
        Object raw=HostReflection.field(be,"racks");if(!(raw instanceof List<?> racks)||racks.isEmpty())return;
        Object rack=racks.get(racks.size()-1);Object[] items=(Object[])HostReflection.field(rack,"list");
        for(int i=0;i<items.length;i++){Object value=items[i]==null?null:HostReflection.field(items[i],"stack");
            if(value instanceof ItemStack item&&!item.isEmpty())claim(be,rack,i,ProductionTracker.actor(),item);withdrawn(be,rack,i);}
    }
    public static void clear(){COOKING.clear();}
}
