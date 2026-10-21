package com.chi__you.cuisinejournal.food;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.IdentityHashMap;
import java.util.Map;

/** Extra-food healing requires a completed, server-timed eating action. */
public final class CuisineDigestionUse {
    private record Use(ItemStack food,long start,int ticks) {}
    private static final Map<ServerPlayer,Use> USES=new IdentityHashMap<>();
    private CuisineDigestionUse() {}

    public static int start(LivingEntity consumer,ItemStack stack,int original) {
        if(!CuisineEffectAdapters.digesting(consumer))return original;
        if(consumer instanceof ServerPlayer player) {
            var definition=CuisineFoodService.definition(stack);
            if(definition==null||definition.drink()
                    ||!CuisineFoodService.isTemporaryUse(player,stack)&&!CuisineFoodService.isDigestedUse(player,stack))return original;
            int ticks=CuisineEffectAdapters.digestionDuration(original,definition.slotCost());
            USES.put(player,new Use(stack.copyWithCount(1),player.level().getGameTime(),ticks));
            return ticks;
        }
        var info=CuisineFoodTooltip.info(stack);
        if(info==null||info.drink()||CuisineFoodClientState.ordinaryAccepts(stack))return original;
        return CuisineEffectAdapters.digestionDuration(original,info.slotCost());
    }

    public static boolean completed(ServerPlayer player,ItemStack stack) {
        Use use=USES.remove(player);
        return use!=null&&ItemStack.isSameItemSameTags(use.food(),stack)
                &&elapsedEnough(player.level().getGameTime()-use.start(),use.ticks());
    }
    public static boolean elapsedEnough(long elapsed,int duration) {return elapsed>=Math.max(1,duration)-1L;}
    public static void cancel(ServerPlayer player){USES.remove(player);}
    public static void clear(){USES.clear();}
}
