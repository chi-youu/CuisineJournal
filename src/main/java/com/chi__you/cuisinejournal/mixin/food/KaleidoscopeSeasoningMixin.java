package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.FoodUseContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="cn.breezeth.kaleidoscope_grilling.food.FoodState",remap=false)
public abstract class KaleidoscopeSeasoningMixin {
    @Inject(method="applySeasoning",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(CallbackInfo ci){FoodUseContext.seasoning(true);}
    @Inject(method="applySeasoning",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(CallbackInfo ci){FoodUseContext.seasoning(false);}
}
