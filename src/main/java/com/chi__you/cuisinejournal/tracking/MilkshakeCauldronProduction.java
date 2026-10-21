package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.recipe.NeapolitanCauldrons;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
import java.util.function.Supplier;

/** A milkshake mixed in world is production; pouring a finished drink is storage, not another recipe. */
public final class MilkshakeCauldronProduction extends SavedData {
    record Portion(ProductionLedger.Batch batch,ItemStack original) {}
    private record Entry(String block,List<Portion> portions){ Entry { portions=List.copyOf(portions); } }
    private record Frame(ServerPlayer player,BlockPos pos,BlockState state,Entry entry){}
    private final Map<Long,Entry> entries=new HashMap<>();
    public static void queueRemoval(ServerLevel level,BlockPos pos){
        var data=level.getDataStorage().get(MilkshakeCauldronProduction::load,"cuisinejournal_milkshake_cauldrons");
        if(data!=null)PlacedDataCleanup.defer(data.entries,pos.asLong(),data);
    }
    private static final ProductionFrames<Frame> ACTIVE=ProductionFrames.value();
    private static MilkshakeCauldronProduction get(ServerLevel level){return level.getDataStorage().computeIfAbsent(MilkshakeCauldronProduction::load,MilkshakeCauldronProduction::new,"cuisinejournal_milkshake_cauldrons");}
    private static MilkshakeCauldronProduction load(CompoundTag tag){
        var result=new MilkshakeCauldronProduction();
        for(Tag raw:tag.getList("cauldrons",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)raw;var portions=new ArrayList<Portion>();
            if(row.contains("contents",Tag.TAG_LIST)){
                for(Tag saved:row.getList("contents",Tag.TAG_COMPOUND)){
                    if(portions.size()==3)break;var value=(CompoundTag)saved;
                    portions.add(new Portion(value.contains("batch")?ProductionLedger.readBatch(value.getCompound("batch")):null,
                            ItemStack.of(value.getCompound("original"))));
                }
            }else{
                var batch=row.contains("batch")?ProductionLedger.readBatch(row.getCompound("batch")):null;
                var original=ItemStack.of(row.getCompound("original"));
                for(int i=0;i<Math.min(3,row.getInt("portions"));i++)
                    portions.add(new Portion(batch==null?null:batch.count(1),original.copy()));
            }
            if(!portions.isEmpty())result.entries.put(row.getLong("pos"),new Entry(row.getString("block"),portions));
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag){
        ListTag list=new ListTag();entries.forEach((pos,entry)->{
            var row=new CompoundTag();row.putLong("pos",pos);row.putString("block",entry.block);
            var contents=new ListTag();for(var portion:entry.portions){var value=new CompoundTag();
                if(portion.batch!=null)value.put("batch",ProductionLedger.writeBatch(portion.batch));
                value.put("original",portion.original.save(new CompoundTag()));contents.add(value);}
            row.put("contents",contents);list.add(row);
        });tag.put("cauldrons",list);return tag;
    }
    private static String id(BlockState state){return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();}
    private static int portions(BlockState state){return com.chi__you.cuisinejournal.food.DelightPlacedFoods.value(state,"level");}
    public static boolean supports(BlockState state){return id(state).equals("neapolitan:milk_cauldron")||NeapolitanCauldrons.get(state.getBlock())!=null
            ||state.is(net.minecraft.world.level.block.Blocks.CAULDRON)&&BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.ResourceLocation.parse("neapolitan:milk_cauldron"));}
    public static InteractionResult use(Level level,BlockPos pos,BlockState before,Player player,InteractionHand hand,Supplier<InteractionResult> host){
        if(!(level instanceof ServerLevel server)||!(player instanceof ServerPlayer cook))return host.get();
        var data=get(server);var entry=data.entries.get(pos.asLong());
        if(entry!=null&&(!entry.block.equals(id(before))||entry.portions.size()!=portions(before))){data.entries.remove(pos.asLong());data.setDirty();entry=null;}
        ItemStack input=player.getItemInHand(hand).copy();int count=input.getCount();
        Frame previous=ACTIVE.get();ACTIVE.set(new Frame(cook,pos,before,entry));
        InteractionResult result;try{result=host.get();}finally{if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
        BlockState after=level.getBlockState(pos);int n=portions(after),old=portions(before);
        if(before.equals(after))return result;
        var relation=NeapolitanCauldrons.get(after.getBlock());
        boolean used=player.isCreative()||player.getItemInHand(hand).getCount()<count||!ItemStack.isSameItem(input,player.getItemInHand(hand));
        if(relation!=null&&id(before).equals("neapolitan:milk_cauldron")&&input.is(relation.iceCream())&&used){
            Set<UUID> owners=new HashSet<>();Set<String> ingredients=new HashSet<>();
            if(entry!=null)for(var portion:entry.portions)if(portion.batch!=null){owners.addAll(portion.batch.contributors());ingredients.addAll(portion.batch.ingredients());}
            owners.add(cook.getUUID());ingredients.add(BuiltInRegistries.ITEM.getKey(input.getItem()).toString());ingredients.add("neapolitan:milk_bottle");
            var batch=new ProductionLedger.Batch(BuiltInRegistries.ITEM.getKey(relation.bottle()).toString(),1,owners,ingredients,"neapolitan:milkshake_cauldron");
            data.entries.put(pos.asLong(),new Entry(id(after),Collections.nCopies(n,new Portion(batch,ItemStack.EMPTY))));
        }else if(n>old&&used&&(relation!=null&&input.is(relation.bottle())||id(after).equals("neapolitan:milk_cauldron"))){
            if(id(after).equals("neapolitan:milk_cauldron")){
                var added=new Portion(new ProductionLedger.Batch("milk",1,Set.of(cook.getUUID()),Set.of("neapolitan:milk_bottle"),"cuisinejournal:fluid_transfer"),ItemStack.EMPTY);
                data.entries.put(pos.asLong(),new Entry(id(after),append(entry==null?List.of():entry.portions,old,n,added)));
            }else{
                data.entries.put(pos.asLong(),new Entry(id(after),append(entry==null?List.of():entry.portions,old,n,new Portion(null,input.copyWithCount(1)))));
            }
        }else if(n>0&&entry!=null&&id(after).equals(entry.block))
            data.entries.put(pos.asLong(),new Entry(id(after),remaining(entry.portions,n)));
        else data.entries.remove(pos.asLong());
        data.setDirty();return result;
    }
    public static ItemStack output(ItemStack result){
        var frame=ACTIVE.get();if(frame==null||frame.entry==null||result.isEmpty())return result;
        var relation=NeapolitanCauldrons.get(frame.state.getBlock());if(relation==null||!result.is(relation.bottle()))return result;
        var portion=frame.entry.portions.get(0);
        if(portion.batch!=null)ProductionTracker.claimBatch(frame.player,result,portion.batch,CulinaryCredit.Source.INTERACTION);
        else if(!portion.original.isEmpty())result.setTag(portion.original.getTag()==null?null:portion.original.getTag().copy());
        return result;
    }
    // Native liquid is homogeneous; provenance keeps insertion order without merging already-made bottles into new production.
    static List<Portion> append(List<Portion> current,int before,int after,Portion added){
        var next=new ArrayList<>(current);
        while(next.size()<before)next.add(new Portion(null,ItemStack.EMPTY));
        while(next.size()<after)next.add(added);
        return List.copyOf(next);
    }
    static List<Portion> remaining(List<Portion> current,int count){
        return List.copyOf(current.subList(Math.max(0,current.size()-count),current.size()));
    }
    public static void forget(Level level,BlockPos pos){if(level instanceof ServerLevel server){var data=get(server);if(data.entries.remove(pos.asLong())!=null)data.setDirty();}}
}
