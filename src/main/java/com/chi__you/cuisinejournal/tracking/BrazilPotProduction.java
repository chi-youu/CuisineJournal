package com.chi__you.cuisinejournal.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
import java.util.function.Supplier;

/** Milk mixing and cheese coagulation replace the pot block instead of running a recipe. */
public final class BrazilPotProduction extends SavedData {
    private record Entry(ProductionLedger ledger,boolean mixed,ItemStack original){}
    private record Frame(ServerPlayer player,Entry entry){}
    private final Map<Long,Entry> entries=new HashMap<>();
    public static void queueRemoval(ServerLevel level,BlockPos pos){
        var data=level.getDataStorage().get(BrazilPotProduction::load,"cuisinejournal_brazil_pots");
        if(data!=null)PlacedDataCleanup.defer(data.entries,pos.asLong(),data);
    }
    private static final ProductionFrames<Frame> OUTPUT=ProductionFrames.value();
    private static BrazilPotProduction get(ServerLevel level){return level.getDataStorage().computeIfAbsent(BrazilPotProduction::load,BrazilPotProduction::new,"cuisinejournal_brazil_pots");}
    private static BrazilPotProduction load(CompoundTag tag){
        var result=new BrazilPotProduction();for(Tag raw:tag.getList("pots",Tag.TAG_COMPOUND)){var row=(CompoundTag)raw;result.entries.put(row.getLong("pos"),new Entry(ProductionLedger.load(row.getCompound("ledger")),row.getBoolean("mixed"),ItemStack.of(row.getCompound("original"))));}return result;
    }
    @Override public CompoundTag save(CompoundTag tag){
        ListTag list=new ListTag();entries.forEach((pos,entry)->{var row=new CompoundTag();row.putLong("pos",pos);row.put("ledger",entry.ledger.save());row.putBoolean("mixed",entry.mixed);row.put("original",entry.original.save(new CompoundTag()));list.add(row);});tag.put("pots",list);return tag;
    }
    public static boolean supports(BlockState state){
        if(!BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.parse("braziliandelight:milk_pot")))return false;
        String name=state.getBlock().getClass().getName();
        return name.equals("vectorwing.farmersdelight.common.block.CookingPotBlock")
                ||name.equals("com.dannbrown.braziliandelight.common.content.blocks.MinasCheesePot")
                ||name.equals("com.dannbrown.braziliandelight.common.content.blocks.MilkPotBlock")
                ||name.equals("com.dannbrown.braziliandelight.common.content.blocks.HeavyCreamPotBlock");
    }
    private static String stage(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();}
    public static InteractionResult use(Level level,BlockPos pos,BlockState state,Player player,net.minecraft.world.InteractionHand hand,Supplier<InteractionResult> host){
        if(!(level instanceof ServerLevel server)||!(player instanceof ServerPlayer cook))return host.get();
        String before=stage(state);var data=get(server);var entry=data.entries.get(pos.asLong());
        ItemStack input=player.getItemInHand(hand).copy();
        Frame previous=OUTPUT.get();OUTPUT.set(new Frame(cook,entry));
        InteractionResult result;try{result=host.get();}finally{if(previous==null)OUTPUT.remove();else OUTPUT.set(previous);}
        var next=level.getBlockState(pos);String after=stage(next);ItemStack held=player.getItemInHand(hand);
        if(before.equals("farmersdelight:cooking_pot")&&(after.equals("braziliandelight:milk_pot")||after.equals("braziliandelight:heavy_cream_pot"))){
            var ledger=new ProductionLedger();ledger.syncInput(0,BuiltInRegistries.ITEM.getKey(input.getItem()).toString(),1,cook.getUUID());
            data.entries.put(pos.asLong(),new Entry(ledger,false,input.copyWithCount(1)));data.setDirty();
        }else if(entry!=null&&before.startsWith("braziliandelight:")){
            boolean changed=!state.equals(next);
            if(changed&&after.equals("braziliandelight:heavy_cream_pot")&&before.equals("braziliandelight:milk_pot")){
                data.entries.put(pos.asLong(),new Entry(entry.ledger,true,ItemStack.EMPTY));data.setDirty();
            }else if(changed&&after.startsWith("braziliandelight:")&&input.getCount()>0
                    &&(held.getCount()<input.getCount()||player.isCreative())){
                int slot=2;
                for(String name:List.of("has_salt","has_coagulant")){
                    var property=state.getBlock().getStateDefinition().getProperty(name);
                    if(property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag&&state.getValue(flag))slot=3;
                }
                entry.ledger.syncInput(slot,BuiltInRegistries.ITEM.getKey(input.getItem()).toString(),1,cook.getUUID());
                data.entries.put(pos.asLong(),new Entry(entry.ledger,entry.mixed,ItemStack.EMPTY));data.setDirty();
            }else if(changed&&after.equals("farmersdelight:cooking_pot")){
                if(com.chi__you.cuisinejournal.compat.UnificationDairy.creamBucket(held,
                        com.chi__you.cuisinejournal.compat.UnificationRuntime.plan(server.getServer()))){
                    if(entry.mixed)ProductionTracker.claimBatch(cook,held,entry.ledger.produce(Map.of(0,1),"braziliandelight:heavy_cream_bucket",1,"braziliandelight:milk_mixing"),CulinaryCredit.Source.INTERACTION);
                    else if(!entry.original.isEmpty())held.setTag(entry.original.getTag()==null?null:entry.original.getTag().copy());
                }
                data.entries.remove(pos.asLong());data.setDirty();
            }
        }
        return result;
    }
    public static void output(ItemEntity entity){
        var frame=OUTPUT.get();if(frame==null||frame.entry==null)return;var stack=entity.getItem();
        if(!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("braziliandelight:minas_cheese"))return;
        ProductionTracker.pending(stack,frame.entry.ledger.produce(Map.of(0,1,2,1,3,1),"braziliandelight:minas_cheese",1,"braziliandelight:cheese_making"));
    }
    public static void forget(Level level,BlockPos pos){if(level instanceof ServerLevel server){var data=get(server);if(data.entries.remove(pos.asLong())!=null)data.setDirty();}}
}
