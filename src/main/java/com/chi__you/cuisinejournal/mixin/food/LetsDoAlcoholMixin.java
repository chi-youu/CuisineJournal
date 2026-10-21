package com.chi__you.cuisinejournal.mixin.food;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.server.level.ServerPlayer;
@Pseudo
@Mixin(targets="net.satisfy.brewery.core.effect.alcohol.AlcoholManager",remap=false)
public abstract class LetsDoAlcoholMixin {
    @Inject(method="drinkAlcohol",at=@At("HEAD"),cancellable=true,remap=false)
    private static void cuisinejournal$gate(ServerPlayer player,CallbackInfo ci){if(!com.chi__you.cuisinejournal.food.CuisineGameplayRules.nativeEffectsEnabled())ci.cancel();}
}
