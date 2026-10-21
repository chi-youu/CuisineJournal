package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.food.IngredientProvenance;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** DrinkBeer spice additions remain one final production batch. */
public final class DrinkBeerMixing {
    private static final String OWNER="cuisinejournal:drink_beer_mix_owner";
    private DrinkBeerMixing() {}

    public static ItemStack beer(BlockEntity be){
        Object raw=HostReflection.field(be,"inv");
        if(!(raw instanceof net.minecraft.world.Container inventory))return ItemStack.EMPTY;
        return inventory.getItem(0).isEmpty()?inventory.getItem(1):inventory.getItem(0);
    }

    public static void beerPlaced(BlockEntity be){
        var actor=ProductionTracker.actor();var item=beer(be);
        if(actor!=null&&!item.isEmpty())item.getOrCreateTag().putUUID(OWNER,actor.getUUID());
    }

    public static void beerMixed(BlockEntity be,ItemStack input,ItemStack spice){
        if(be.getLevel()==null||be.getLevel().isClientSide)return;var result=beer(be);if(result.isEmpty())return;
        Set<UUID> owners=new HashSet<>();Set<String> ingredients=new HashSet<>();
        if(input.hasTag()){
            if(input.getTag().hasUUID(OWNER))owners.add(input.getTag().getUUID(OWNER));
            var old=ProductionLedger.readBatch(input.getTag().getCompound(ProductionTracker.PENDING));
            if(old.method().equals("drinkbeer:mixology")){owners.addAll(old.contributors());ingredients.addAll(old.ingredients());}
        }
        var actor=ProductionTracker.actor();if(actor!=null)owners.add(actor.getUUID());
        var inputs=List.of(input,spice);IngredientProvenance.sources(result,inputs);
        for(var item:inputs)if(!item.isEmpty())ingredients.add(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        result.removeTagKey(com.chi__you.cuisinejournal.culinary.MealStamp.ROOT);
        ProductionTracker.pending(result,new ProductionLedger.Batch(BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),
                result.getCount(),owners,ingredients,"drinkbeer:mixology"));
        be.setChanged();
    }

    public static void beerTaken(BlockEntity be,ItemStack result,boolean simulate){
        if(simulate||result.isEmpty()||be.getLevel()==null||be.getLevel().isClientSide)return;
        result.removeTagKey(OWNER);
        if(result.hasTag()&&result.getTag().contains(ProductionTracker.PENDING,10)){
            var batch=ProductionLedger.readBatch(result.getTag().getCompound(ProductionTracker.PENDING));
            if(batch.method().equals("drinkbeer:mixology"))DirectDropProgress.finish(be.getLevel(),result,batch);
        }
    }
}
