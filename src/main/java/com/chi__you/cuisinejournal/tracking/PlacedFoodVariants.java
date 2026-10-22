package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
/** Full host variants survive blocks that otherwise recreate default item stacks. */
public final class PlacedFoodVariants extends SavedData {
    private record Entry(String block,ItemStack item,List<ItemStack> cups){Entry(String block,ItemStack item){this(block,item,new ArrayList<>());}}
    private final Map<Long,Entry> items=new HashMap<>();
    public static void queueRemoval(ServerLevel level,BlockPos pos){
        var data=level.getDataStorage().get(PlacedFoodVariants::load,"cuisinejournal_placed_food_variants");
        if(data!=null)PlacedDataCleanup.defer(data.items,pos.asLong(),data);
    }
    private static PlacedFoodVariants get(ServerLevel level){return level.getDataStorage().computeIfAbsent(PlacedFoodVariants::load,PlacedFoodVariants::new,"cuisinejournal_placed_food_variants");}
    private static PlacedFoodVariants load(CompoundTag data){var result=new PlacedFoodVariants();
        for(Tag value:data.getList("entries",10)){var row=(CompoundTag)value;var cups=new ArrayList<ItemStack>();
            for(Tag cup:row.getList("cups",10))if(cups.size()<64)cups.add(ItemStack.of((CompoundTag)cup));
            result.items.put(row.getLong("pos"),new Entry(row.getString("block"),ItemStack.of(row.getCompound("item")),cups));}return result;}
    @Override public CompoundTag save(CompoundTag data){var rows=new ListTag();items.forEach((pos,entry)->{var row=new CompoundTag();row.putLong("pos",pos);row.putString("block",entry.block);row.put("item",entry.item.save(new CompoundTag()));
        var cups=new ListTag();entry.cups.forEach(cup->cups.add(cup.save(new CompoundTag())));row.put("cups",cups);rows.add(row);});data.put("entries",rows);return data;}
    public static void store(Level level,BlockPos pos,ItemStack item){if(level instanceof ServerLevel server){var data=get(server);data.items.put(pos.asLong(),new Entry(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString(),item.copyWithCount(1)));data.setDirty();}}
    public static ItemStack read(Level level,BlockPos pos,ItemStack fallback){
        if(!(level instanceof ServerLevel server))return fallback;var data=get(server);var entry=data.items.get(pos.asLong());if(entry==null)return fallback;
        if(!entry.block.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString())){data.items.remove(pos.asLong());data.setDirty();return fallback;}
        ItemStack stored=entry.cups.isEmpty()?entry.item:entry.cups.get(entry.cups.size()-1);
        if(!ItemStack.isSameItem(stored,fallback))return fallback;
        var result=fallback.copy();result.setTag(merge(stored.getTag(),fallback.getTag()));return result;
    }
    public static ItemStack readServing(Level level,BlockPos pos,ItemStack fallback){
        return readServing(level,pos,level.getBlockState(pos),fallback);
    }
    public static ItemStack stored(Level level,BlockPos pos){
        if(!(level instanceof ServerLevel server))return ItemStack.EMPTY;var entry=get(server).items.get(pos.asLong());
        return entry!=null&&entry.block.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString())?entry.item.copy():ItemStack.EMPTY;
    }
    public static ItemStack readServing(Level level,BlockPos pos,net.minecraft.world.level.block.state.BlockState expected,ItemStack fallback){
        if(!(level instanceof ServerLevel server)||fallback.isEmpty())return fallback;
        var entry=get(server).items.get(pos.asLong());
        if(entry==null||!entry.block.equals(BuiltInRegistries.BLOCK.getKey(expected.getBlock()).toString()))return fallback;
        var result=fallback.copy();result.setTag(merge(entry.item.getTag(),fallback.getTag()));return result;
    }
    public static void addCup(Level level,BlockPos pos,ItemStack cup,int previous){
        if(!(level instanceof ServerLevel server)||cup.isEmpty())return;var data=get(server);
        String block=BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        var old=data.items.get(pos.asLong());var cups=new ArrayList<ItemStack>();
        if(old!=null&&old.block.equals(block))cups.addAll(old.cups);
        ItemStack original=old!=null&&old.block.equals(block)?old.item:cup.copyWithCount(1);
        while(cups.size()<Math.min(63,previous))cups.add(original.copyWithCount(1));
        if(cups.size()<64)cups.add(cup.copyWithCount(1));
        data.items.put(pos.asLong(),new Entry(block,original,cups));data.setDirty();
    }
    public static ItemStack takeCup(Level level,BlockPos pos,ItemStack fallback){
        var result=read(level,pos,fallback);if(!(level instanceof ServerLevel server))return result;
        var data=get(server);var entry=data.items.get(pos.asLong());
        if(entry!=null&&ItemStack.isSameItem(entry.item,fallback)){
            if(!entry.cups.isEmpty())entry.cups.remove(entry.cups.size()-1);
            if(entry.cups.isEmpty())data.items.remove(pos.asLong());data.setDirty();
        }return result;
    }
    public static List<ItemStack> drops(Level level,BlockPos pos,List<ItemStack> nativeDrops,boolean plate){
        return drops(level,pos,level.getBlockState(pos),nativeDrops,plate);
    }
    public static List<ItemStack> drops(Level level,BlockPos pos,net.minecraft.world.level.block.state.BlockState expected,List<ItemStack> nativeDrops,boolean plate){
        if(!(level instanceof ServerLevel server))return nativeDrops;
        var entry=get(server).items.get(pos.asLong());
        if(entry==null||!entry.block.equals(BuiltInRegistries.BLOCK.getKey(expected.getBlock()).toString()))return nativeDrops;
        return preview(nativeDrops,entry.cups,entry.item,plate,stack->plateOutput(level,pos,stack));
    }
    static List<ItemStack> preview(List<ItemStack> nativeDrops,List<ItemStack> servings,ItemStack original,boolean plate,java.util.function.Predicate<ItemStack> plateOutput){
        List<ItemStack> result=new ArrayList<>();
        int index=servings.size()-1;
        for(ItemStack stack:nativeDrops){
            if(stack.isEmpty())continue;
            for(int i=0;i<stack.getCount();i++){
                ItemStack source=index>=0?servings.get(index):original;
                ItemStack one=stack.copyWithCount(1);
                boolean matches=plate?plateOutput.test(stack):ItemStack.isSameItem(source,stack);
                if(matches){one=inherit(source,one,plate);if(index>=0)index--;}
                if(!result.isEmpty()&&ItemStack.isSameItemSameTags(result.get(result.size()-1),one)
                        &&result.get(result.size()-1).getCount()<one.getMaxStackSize())result.get(result.size()-1).grow(1);else result.add(one);
            }
        }return result;
    }
    private static boolean plateOutput(Level level,BlockPos pos,ItemStack fallback){
        Object raw=HostReflection.field(level.getBlockState(pos).getBlock(),"items");
        return raw instanceof List<?> outputs&&outputs.stream().anyMatch(value->value instanceof java.util.function.Supplier<?> supplier&&supplier.get()==fallback.getItem());
    }
    private static ItemStack inherit(ItemStack source,ItemStack fallback,boolean plate){
        var result=fallback.copy();if(!source.hasTag())return result;
        if(!plate){result.setTag(merge(source.getTag(),fallback.getTag()));return result;}
        com.chi__you.cuisinejournal.food.FoodVariantIdentity.copyKeys(source,result,
                com.chi__you.cuisinejournal.food.FoodVariantIdentity.PLATE_SERVING);
        return result;
    }
    public static ItemStack plateServing(Level level,BlockPos pos,ItemStack fallback){
        if(!(level instanceof ServerLevel server))return fallback;var data=get(server);var entry=data.items.get(pos.asLong());
        if(entry==null||!entry.block.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString()))return fallback;
        if(!plateOutput(level,pos,fallback))return fallback;
        ItemStack source=entry.cups.isEmpty()?entry.item:entry.cups.get(entry.cups.size()-1);
        return inherit(source,fallback,true);
    }
    public static void finishServing(Level level,BlockPos pos,int remaining){
        if(!(level instanceof ServerLevel server))return;var data=get(server);var entry=data.items.get(pos.asLong());if(entry==null)return;
        while(entry.cups.size()>Math.max(0,remaining))entry.cups.remove(entry.cups.size()-1);
        if(remaining<=0)data.items.remove(pos.asLong());data.setDirty();
    }
    /** Host-generated Effects/Color and per-mouth nutrition take precedence over an earlier template. */
    public static CompoundTag merge(CompoundTag stored,CompoundTag live){
        var result=stored==null?new CompoundTag():stored.copy();
        if(live!=null)result.merge(live);
        return result;
    }
    public static void clear(Level level,BlockPos pos){if(level instanceof ServerLevel server){var data=get(server);if(data.items.remove(pos.asLong())!=null)data.setDirty();}}
}
