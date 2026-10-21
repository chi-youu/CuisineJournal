package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineFoodRuntime;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="cn.breezeth.kaleidoscope_grilling.food.HotFoodHandler",remap=false)
public abstract class KaleidoscopeHotFoodMixin {
    @Inject(method="onFinish",at=@At("HEAD"),cancellable=true,remap=false)
    private static void cuisinejournal$alreadyCaptured(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish event,CallbackInfo ci){
        if(CuisineFoodRuntime.activeForCurrentThread())ci.cancel();
    }
    @Inject(method="doubleNewDurations",at=@At("HEAD"),cancellable=true,remap=false)
    private static void cuisinejournal$finite(LivingEntity entity,java.util.Map<?,?> before,CallbackInfo ci){
        if(CuisineFoodRuntime.activeForCurrentThread())ci.cancel();
    }
}
