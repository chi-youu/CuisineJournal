package com.chi__you.cuisinejournal.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import java.util.HashMap;
import java.util.Map;

/** Placed servings outlive their original cooking block entity. */
public final class PlacedMealProduction extends SavedData {
    private record Entry(String block,ProductionLedger.Batch batch,MealStamp stamp) {}
    private final Map<Long,Entry> entries=new HashMap<>();
    public static void queueRemoval(ServerLevel level,BlockPos pos){
        var data=level.getDataStorage().get(PlacedMealProduction::load,"cuisinejournal_placed_meals");
        if(data!=null)PlacedDataCleanup.defer(data.entries,pos.asLong(),data);
    }
    private static PlacedMealProduction get(ServerLevel level){return level.getDataStorage().computeIfAbsent(PlacedMealProduction::load,PlacedMealProduction::new,"cuisinejournal_placed_meals");}
    private static PlacedMealProduction load(CompoundTag tag) {
        var data=new PlacedMealProduction();
        for(Tag value:tag.getList("meals",Tag.TAG_COMPOUND)) {
            CompoundTag row=(CompoundTag)value;
            MealStamp stamp=null;
            if(row.contains("stamp",Tag.TAG_COMPOUND)) {
                CompoundTag raw=row.getCompound("stamp");
                stamp=new MealStamp(raw.getInt("stars"),raw.getBoolean("perfect"),raw.getInt("starCap"),
                        raw.hasUUID("cook")?raw.getUUID("cook"):null,raw.getString("cookName"));
            }
            data.entries.put(row.getLong("pos"),new Entry(row.getString("block"),ProductionLedger.readBatch(row.getCompound("batch")),stamp));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list=new ListTag();entries.forEach((pos,entry)->{CompoundTag row=new CompoundTag();row.putLong("pos",pos);row.putString("block",entry.block);row.put("batch",ProductionLedger.writeBatch(entry.batch));if(entry.stamp!=null){CompoundTag raw=new CompoundTag();raw.putInt("stars",entry.stamp.stars());raw.putInt("starCap",entry.stamp.starCap());if(entry.stamp.perfect())raw.putBoolean("perfect",true);if(entry.stamp.cookId()!=null)raw.putUUID("cook",entry.stamp.cookId());if(!entry.stamp.cookName().isEmpty())raw.putString("cookName",entry.stamp.cookName());row.put("stamp",raw);}list.add(row);});tag.put("meals",list);return tag;
    }
    public static void store(Level level,BlockPos pos,BlockState state,ProductionLedger.Batch batch) {
        if(!(level instanceof ServerLevel server))return;
        var data=get(server);data.entries.put(pos.asLong(),new Entry(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),batch,null));data.setDirty();
    }

    public static void storeStamped(Level level,BlockPos pos,BlockState state,ItemStack placed,int servings) {
        if(!(level instanceof ServerLevel server)||servings<=0)return;
        MealStamp stamp=MealStamp.read(placed);if(stamp==null)return;
        String item=BuiltInRegistries.ITEM.getKey(placed.getItem()).toString();
        ProductionLedger.Batch batch=new ProductionLedger.Batch(item,servings,
                stamp.cookId()==null?java.util.Set.of():java.util.Set.of(stamp.cookId()),java.util.Set.of(),"cuisinejournal:placed_food");
        var data=get(server);data.entries.put(pos.asLong(),new Entry(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),batch,stamp));data.setDirty();
    }
    public static void claim(Level level,BlockPos pos,ServerPlayer player,ItemStack serving,boolean container) {
        claim(level,pos,level.getBlockState(pos),player,serving,container);
    }

    public static void claim(Level level,BlockPos pos,BlockState expectedState,ServerPlayer player,ItemStack serving,boolean container) {
        if(!(level instanceof ServerLevel server))return;
        var data=get(server);Entry entry=data.entries.get(pos.asLong());if(entry==null)return;
        if(expectedState==null||!entry.block.equals(BuiltInRegistries.BLOCK.getKey(expectedState.getBlock()).toString())) {data.entries.remove(pos.asLong());data.setDirty();return;}
        ProductionLedger.Batch batch=entry.batch;
        if(entry.stamp!=null)entry.stamp.write(serving);
        if(entry.stamp==null||!batch.method().equals("cuisinejournal:placed_food"))
            ProductionTracker.claimBatch(player,serving,batch,CulinaryCredit.Source.INTERACTION);
        int rest=entry.batch.count()-serving.getCount();
        if(rest<=0)data.entries.remove(pos.asLong());
        else data.entries.put(pos.asLong(),new Entry(entry.block,new ProductionLedger.Batch(entry.batch.identity(),rest,entry.batch.contributors(),entry.batch.ingredients(),entry.batch.method()),entry.stamp));
        data.setDirty();
    }

    public static void transferOutput(Level level,BlockPos pos,BlockState expected,ItemStack output) {
        transferOutput(level,pos,expected,output,output.getCount());
    }

    public static void stampOutput(Level level,BlockPos pos,BlockState expected,ItemStack output) {
        if(!(level instanceof ServerLevel server)||output.isEmpty())return;
        var data=get(server);Entry entry=data.entries.get(pos.asLong());if(entry==null)return;
        if(expected==null||!entry.block.equals(BuiltInRegistries.BLOCK.getKey(expected.getBlock()).toString()))return;
        String source=entry.batch.identity();int split=source.indexOf('|');if(split>=0)source=source.substring(0,split);
        if(CulinaryRegistries.current().isPortionOf(net.minecraft.resources.ResourceLocation.tryParse(source),
                BuiltInRegistries.ITEM.getKey(output.getItem()))&&entry.stamp!=null)entry.stamp.write(output);
    }

    public static void transferOutput(Level level,BlockPos pos,BlockState expected,ItemStack output,int amount) {
        if(!(level instanceof ServerLevel server)||output.isEmpty())return;
        var data=get(server);Entry entry=data.entries.get(pos.asLong());if(entry==null)return;
        if(expected==null||!entry.block.equals(BuiltInRegistries.BLOCK.getKey(expected.getBlock()).toString()))return;
        String source=entry.batch.identity();int split=source.indexOf('|');if(split>=0)source=source.substring(0,split);
        if(!CulinaryRegistries.current().isPortionOf(net.minecraft.resources.ResourceLocation.tryParse(source),
                BuiltInRegistries.ITEM.getKey(output.getItem())))return;
        if(entry.stamp!=null)entry.stamp.write(output);
        if(amount<=0)return;
        int rest=entry.batch.count()-amount;
        if(rest<=0)data.entries.remove(pos.asLong());
        else data.entries.put(pos.asLong(),new Entry(entry.block,new ProductionLedger.Batch(entry.batch.identity(),rest,entry.batch.contributors(),entry.batch.ingredients(),entry.batch.method()),entry.stamp));
        data.setDirty();
    }
    public static void remove(Level level,BlockPos pos){if(level instanceof ServerLevel server){var data=get(server);if(data.entries.remove(pos.asLong())!=null)data.setDirty();}}
    public static void transition(Level level,BlockPos pos,BlockState before){
        if(!(level instanceof ServerLevel server))return;
        BlockState after=level.getBlockState(pos);
        if(before.getBlock()==after.getBlock()){
            if(com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.known(before.getBlock())
                    &&com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.remaining(after)>com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.remaining(before))remove(level,pos);
            if(com.chi__you.cuisinejournal.food.DelightPlacedFoods.known(before.getBlock())
                    &&com.chi__you.cuisinejournal.food.DelightPlacedFoods.remaining(after)>com.chi__you.cuisinejournal.food.DelightPlacedFoods.remaining(before))remove(level,pos);
            return;
        }
        var data=get(server);var entry=data.entries.get(pos.asLong());
        if(entry==null||!entry.block.equals(BuiltInRegistries.BLOCK.getKey(before.getBlock()).toString()))return;
        if(com.chi__you.cuisinejournal.food.DelightPlacedFoods.sameMeal(before,after)
                ||com.chi__you.cuisinejournal.food.CakeFoodCompat.sameCake(before,after)
                ||com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.known(before.getBlock())
                    &&com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.sameMeal(before,after))
            data.entries.put(pos.asLong(),new Entry(BuiltInRegistries.BLOCK.getKey(after.getBlock()).toString(),entry.batch,entry.stamp));
        else data.entries.remove(pos.asLong());
        data.setDirty();
    }
}
