package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.event.CulinaryHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;

/** One native use fills vacant ordinary/drink capacity; rejected entries remain in their original slots. */
public final class KaleidoscopeLunchBag {
    private KaleidoscopeLunchBag(){}
    public static boolean foodFits(java.util.List<CuisineFoodPlacement.Slot> slots,int cost,int capacity){
        return cost>0&&slots.stream().noneMatch(CuisineFoodPlacement.Slot::sameFood)
                &&slots.stream().mapToInt(CuisineFoodPlacement.Slot::cost).sum()+cost<=capacity;
    }
    public static boolean canStore(ItemStack stack){return CuisineFoodRuntime.activeForCurrentThread()&&KaleidoscopeFood.drink(stack);}
    public static boolean finish(ItemStack bag,Level level,LivingEntity entity) {
        if(!CuisineFoodRuntime.activeForCurrentThread())return false;
        if(!(entity instanceof ServerPlayer player))return true;
        Object raw=HostReflection.invoke(bag.getItem(),"getItems",bag);
        if(!(raw instanceof ItemStackHandler inventory))return true;
        for(int slot=0;slot<inventory.getSlots();slot++){
            for(int count=0;count<64;count++){
                ItemStack stored=inventory.getStackInSlot(slot);if(stored.isEmpty())break;
                boolean potion=stored.is(Items.POTION)&&!KaleidoscopeFood.water(stored);
                var definition=CuisineFoodService.definition(stored);
                if(!potion){
                    if(definition==null)break;
                    var state=CuisineFoodService.state(player);if(state.isReadOnly())break;
                    if(definition.drink()){if(state.drink()!=null)break;}
                    else {
                        var slots=state.foods().stream().filter(entry->entry.remainingTicks()>0)
                                .map(entry->new CuisineFoodPlacement.Slot(entry.slotCost(),entry.remainingTicks(),entry.totalTicks(),CuisineFoodService.sameFood(entry.stack(),stored))).toList();
                        if(!foodFits(slots,definition.slotCost(),CuisineFoodCapacity.ordinary(player)))break;
                    }
                }
                ItemStack consumed=inventory.extractItem(slot,1,false),original=consumed.copy();
                // Calling the Item retains native actions; this wrapper owns the single journal consumption.
                CuisineEffectAdapters.beginNativeFood(original,player);
                ItemStack remainder;
                try {remainder=consumed.getItem().finishUsingItem(consumed,level,player);}
                finally {CuisineEffectAdapters.endNativeFood();}
                if(!remainder.isEmpty()){
                    // A remaining bite stays in the bag; only a finished item's container leaves it.
                    if(ItemStack.isSameItem(original,remainder))remainder=inventory.insertItem(slot,remainder,false);
                    if(!remainder.isEmpty())net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player,remainder);
                }
                if(!potion&&CuisineFoodService.consume(player,original)){
                    CulinaryHooks.onItemEaten(player,original);CuisineFoodEffects.onConsumed(player);
                }
            }
        }
        HostReflection.invoke(bag.getItem(),"setItems",bag,inventory);
        CuisineFoodSync.send(player);return true;
    }
}
