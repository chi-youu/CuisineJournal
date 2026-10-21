package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.food.IngredientProvenance;

import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Manual juice retains provenance after Vinery moves it out of its input slot. */
public final class LetsDoJuice {
    private static final String KEY="cuisinejournal:lets_do_juice";
    private record Fill(int level,ItemStack input,ProductionLedger.Batch batch){}
    private static final Map<BlockEntity,Fill> FILLS=new WeakHashMap<>();
    private static final Map<BlockEntity,Integer> CRAFTS=new WeakHashMap<>();
    private LetsDoJuice(){}
    public static void beforeTick(BlockEntity be){
        if(be.getLevel()==null||be.getLevel().isClientSide)return;
        ItemStack input=VerifiedWorkstations.stack(be,0).copy();
        FILLS.put(be,new Fill(level(be),input,ProductionTracker.inputPreview(be,0,input)));
    }
    public static void afterTick(BlockEntity be){
        var fill=FILLS.remove(be);if(fill==null)return;int after=level(be);
        if(after>fill.level&&!fill.input.isEmpty()){
            var data=be.getPersistentData().getCompound(KEY).copy();
            if(fill.level==0)data=new CompoundTag();
            var owners=owners(data);if(fill.batch!=null)owners.addAll(fill.batch.contributors());
            var source=new ItemStack(net.minecraft.world.item.Items.PAPER);
            if(data.contains("sources",9))source.getOrCreateTag().put(IngredientProvenance.KEY,data.get("sources").copy());
            var inherited=source.hasTag()?source.getTag().getList(IngredientProvenance.KEY,8).copy():new ListTag();
            IngredientProvenance.sources(source,List.of(fill.input));
            for(var entry:inherited)if(source.getTag().getList(IngredientProvenance.KEY,8).size()<128&&!source.getTag().getList(IngredientProvenance.KEY,8).contains(entry))source.getTag().getList(IngredientProvenance.KEY,8).add(entry);
            var rows=new ListTag();owners.stream().sorted().forEach(id->{var row=new CompoundTag();row.putUUID("id",id);rows.add(row);});
            data.put("owners",rows);data.put("sources",source.getTag().get(IngredientProvenance.KEY).copy());
            be.getPersistentData().put(KEY,data);be.setChanged();
        }
        if(after<=0&&be.getPersistentData().contains(KEY)){be.getPersistentData().remove(KEY);be.setChanged();}
    }
    public static void beforeCraft(BlockEntity be){if(be.getLevel()!=null&&!be.getLevel().isClientSide)CRAFTS.put(be,level(be));}
    public static ProductionLedger.Batch inherit(BlockEntity be,ItemStack result,ProductionLedger.Batch batch){
        Integer before=CRAFTS.remove(be);if(before==null||level(be)>=before)return batch;
        var data=be.getPersistentData().getCompound(KEY);var owners=new HashSet<>(batch.contributors());owners.addAll(owners(data));
        var ingredients=new HashSet<>(batch.ingredients());for(var value:data.getList("sources",8))ingredients.add(value.getAsString());
        return new ProductionLedger.Batch(batch.identity(),batch.count(),owners,ingredients,batch.method());
    }
    private static int level(BlockEntity be){Object value=HostReflection.field(be,"fluidLevel");return value instanceof Number n?n.intValue():0;}
    private static Set<UUID> owners(CompoundTag data){Set<UUID> result=new HashSet<>();for(var raw:data.getList("owners",10))if(((CompoundTag)raw).hasUUID("id"))result.add(((CompoundTag)raw).getUUID("id"));return result;}
    public static void clear(){FILLS.clear();CRAFTS.clear();}
}
