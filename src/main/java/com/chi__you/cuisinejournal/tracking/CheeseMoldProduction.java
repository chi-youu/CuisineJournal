package com.chi__you.cuisinejournal.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import com.chi__you.cuisinejournal.food.DelightPlacedFoods;
import java.util.*;
import java.util.function.Supplier;

/** Scheduled mold aging has no block entity; persist real manual contributions per position. */
public final class CheeseMoldProduction extends SavedData {
    public static final String HOST="net.ribs.vintagedelight.block.custom.CheeseMoldBlock";
    private record Entry(int count,ProductionLedger ledger,ItemStack stored){}
    private record Frame(ServerLevel level,BlockPos pos,Entry entry){}
    private final Map<Long,Entry> entries=new HashMap<>();
    public static void queueRemoval(ServerLevel level,BlockPos pos){
        var data=level.getDataStorage().get(CheeseMoldProduction::load,"cuisinejournal_cheese_molds");
        if(data!=null)PlacedDataCleanup.defer(data.entries,pos.asLong(),data);
    }
    private static final ProductionFrames<Frame> OUTPUTS=ProductionFrames.value();
    private static CheeseMoldProduction get(ServerLevel level){return level.getDataStorage().computeIfAbsent(CheeseMoldProduction::load,CheeseMoldProduction::new,"cuisinejournal_cheese_molds");}
    private static CheeseMoldProduction load(CompoundTag tag){
        var data=new CheeseMoldProduction();
        for(Tag raw:tag.getList("molds",Tag.TAG_COMPOUND)){var row=(CompoundTag)raw;data.entries.put(row.getLong("pos"),new Entry(row.getInt("count"),ProductionLedger.load(row.getCompound("ledger")),ItemStack.of(row.getCompound("stored"))));}
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        ListTag list=new ListTag();entries.forEach((pos,entry)->{var row=new CompoundTag();row.putLong("pos",pos);row.putInt("count",entry.count);row.put("ledger",entry.ledger.save());row.put("stored",entry.stored.save(new CompoundTag()));list.add(row);});tag.put("molds",list);return tag;
    }
    public static InteractionResult use(Level level,BlockPos pos,BlockState state,Player player,ItemStack held,Supplier<InteractionResult> host){
        if(!(level instanceof ServerLevel server))return host.get();
        var data=get(server);int before=DelightPlacedFoods.value(state,"level");var entry=data.entries.get(pos.asLong());
        if(before==0||entry!=null&&before<4&&entry.count!=before){data.entries.remove(pos.asLong());entry=null;}
        ItemStack input=held.copy();int count=held.getCount();
        Frame previous=OUTPUTS.get();OUTPUTS.set(new Frame(server,pos,entry));
        InteractionResult result;
        try{result=host.get();}finally{if(previous==null)OUTPUTS.remove();else OUTPUTS.set(previous);}
        int after=DelightPlacedFoods.value(level.getBlockState(pos),"level");
        String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(input.getItem()).toString();
        if(after==before+1&&before<4&&id.equals("vintagedelight:cheese_curds")&&(held.getCount()<count||player.isCreative())){
            var ledger=entry==null?new ProductionLedger():entry.ledger;
            if(entry==null&&before>0)ledger.syncInput(0,id,before,null);
            ledger.syncInput(0,id,after,player.getUUID());data.entries.put(pos.asLong(),new Entry(after,ledger,ItemStack.EMPTY));data.setDirty();
        }else if(before==0&&after==5&&id.equals("vintagedelight:cheese_wheel")){
            data.entries.put(pos.asLong(),new Entry(0,new ProductionLedger(),input.copyWithCount(1)));data.setDirty();
        }else if(after==0){data.entries.remove(pos.asLong());data.setDirty();}
        return result;
    }
    public static void output(ItemEntity entity){
        var frame=OUTPUTS.get();if(frame==null||frame.entry==null||entity.level()!=frame.level)return;
        var stack=entity.getItem();if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("vintagedelight:cheese_wheel"))return;
        if(!frame.entry.stored.isEmpty()){stack.setTag(frame.entry.stored.getTag()==null?null:frame.entry.stored.getTag().copy());return;}
        if(frame.entry.count==4)ProductionTracker.pending(stack,frame.entry.ledger.produce(Map.of(0,4),"vintagedelight:cheese_wheel",1,"vintagedelight:cheese_mold"));
    }
    public static void forget(Level level,BlockPos pos){if(level instanceof ServerLevel server){var data=get(server);if(data.entries.remove(pos.asLong())!=null)data.setDirty();}}
}
