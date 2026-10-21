package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.food.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Shaker inventory is portable: contributions travel with its native Storage/Result tags. */
public final class KaleidoscopeCocktails {
    public static final String KEY="cuisinejournal:kaleidoscope_shaker";
    private record Insertion(BlockEntity owner, Set<UUID> contributors) {}
    private static final ProductionFrames<Deque<Insertion>> INSERTIONS=ProductionFrames.stack();
    private KaleidoscopeCocktails(){}
    public static void before(BlockEntity be){
        Set<UUID> owners=new HashSet<>();var data=be.getPersistentData().getCompound(KEY);
        for(var value:data.getList("owners",10))if(((CompoundTag)value).hasUUID("id"))owners.add(((CompoundTag)value).getUUID("id"));
        INSERTIONS.get().push(new Insertion(be,owners));
    }
    public static void after(BlockEntity be,boolean success,net.minecraft.world.entity.LivingEntity user){
        var scopes=INSERTIONS.get();var frame=ProductionFrames.take(scopes,value->value.owner==be);if(scopes.isEmpty())INSERTIONS.remove();if(frame==null)return;var owners=frame.contributors;
        if(!success||be.getLevel()==null||be.getLevel().isClientSide)return;
        if(user instanceof ServerPlayer player)owners.add(player.getUUID());
        CompoundTag data=new CompoundTag();var rows=new net.minecraft.nbt.ListTag();
        owners.forEach(id->{CompoundTag row=new CompoundTag();row.putUUID("id",id);rows.add(row);});
        data.put("owners",rows);be.getPersistentData().put(KEY,data);be.setChanged();
    }
    public static void portable(BlockEntity be,ItemStack shaker){
        var data=be.getPersistentData().getCompound(KEY);if(!data.isEmpty())shaker.getOrCreateTag().put(KEY,data.copy());
    }
    public static void placed(BlockEntity be,ItemStack shaker){
        if(shaker.hasTag()&&shaker.getTag().contains(KEY,10)){be.getPersistentData().put(KEY,shaker.getTag().getCompound(KEY).copy());be.setChanged();}
    }
    public static void pour(Level level,BlockPos pos,ItemStack shaker,ItemStack result){
        if(level.isClientSide||result.isEmpty())return;
        Object raw=HostReflection.invoke(shaker.getItem(),"getStorage",shaker);
        if(!(raw instanceof net.minecraftforge.items.IItemHandler inventory))return;
        List<ItemStack> ingredients=new ArrayList<>();List<Integer> stars=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(int i=0;i<inventory.getSlots();i++){
            ItemStack item=inventory.getStackInSlot(i);if(item.isEmpty())continue;ingredients.add(item);
            ids.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
            if(!item.is(net.minecraft.world.item.Items.POTION))stars.add(CuisineFoodQuality.stars(item));
        }
        String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        boolean mystery=id.equals("kaleidoscope_tavern:mystery_cocktail");
        if(!mystery)IngredientProvenance.sources(result,ingredients);
        else result.getOrCreateTag().put(IngredientProvenance.KEY,new net.minecraft.nbt.ListTag());
        result.getOrCreateTag().putInt(KaleidoscopeFood.INHERITED_STARS,mystery?0:KaleidoscopeFood.minimumStars(stars));
        Set<UUID> owners=new HashSet<>();
        if(shaker.hasTag())for(var value:shaker.getTag().getCompound(KEY).getList("owners",10))if(((CompoundTag)value).hasUUID("id"))owners.add(((CompoundTag)value).getUUID("id"));
        var batch=new ProductionLedger.Batch(id,1,owners,ids,"kaleidoscope_tavern:shaker");ProductionTracker.pending(result,batch);
        PlacedFoodVariants.store(level,pos,result);
    }
}
