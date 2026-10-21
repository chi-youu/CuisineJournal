package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Host release-to-finish bypasses ItemStack.finishUsingItem, so it needs the same admission window. */
@Pseudo
@Mixin(targets={"cn.breezeth.kaleidoscope_grilling.skewer.SkewerItem",
        "cn.breezeth.kaleidoscope_grilling.skewer.SecretSkewerItem"},remap=false)
public abstract class KaleidoscopeSkewerUseMixin {
    @Unique private static final ThreadLocal<java.util.Deque<Boolean>> cuisinejournal$windows=ThreadLocal.withInitial(java.util.ArrayDeque::new);
    @Inject(method="finishFoodAndEffect",at=@At("HEAD"),cancellable=true,remap=false)
    private void cuisinejournal$begin(ItemStack stack,Level level,LivingEntity consumer,CallbackInfoReturnable<ItemStack> cir){
        boolean own=!FoodUseContext.active();
        if(own&&CuisineFoodRuntime.activeForCurrentThread()&&consumer instanceof ServerPlayer player
                &&CuisineFoodService.definition(stack)!=null&&!CuisineFoodService.canConsume(player,stack)){
            KaleidoscopeFood.reject(player,stack);cir.setReturnValue(stack);return;
        }
        cuisinejournal$windows.get().push(own);
        if(own){FoodUseContext.begin(stack);CuisineEffectAdapters.beginNativeFood(stack,consumer);}
    }
    @Inject(method="finishFoodAndEffect",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(ItemStack stack,Level level,LivingEntity consumer,CallbackInfoReturnable<ItemStack> cir){
        var frames=cuisinejournal$windows.get();if(frames.isEmpty())return;boolean own=frames.pop();
        if(frames.isEmpty())cuisinejournal$windows.remove();
        if(own){try{GrillingFoodCompat.finishHot(stack,consumer);CuisineEffectAdapters.endNativeFood();}finally{FoodUseContext.end();}}
    }
}
