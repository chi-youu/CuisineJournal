package com.chi__you.cuisinejournal.mixin.food;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo
@Mixin(targets="net.satisfy.brewery.core.effect.alcohol.AlcoholLevel",remap=false)
public abstract class LetsDoAlcoholStateMixin {
    @Inject(method={"isDrunk","isBlackout"},at=@At("HEAD"),cancellable=true,remap=false)
    private void cuisinejournal$gate(CallbackInfoReturnable<Boolean> cir){if(!com.chi__you.cuisinejournal.food.CuisineGameplayRules.nativeEffectsEnabled())cir.setReturnValue(false);}
}
