package com.chi__you.cuisinejournal.mixin.food;
import com.chi__you.cuisinejournal.food.CuisineGameplayRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopecookery.item.TeacupItem",remap=false)
public abstract class KaleidoscopeTeaMixin {
    @Inject(method="addTeaEffect",at=@At("HEAD"),cancellable=true,remap=false)
    private void cuisinejournal$effect(Level level,LivingEntity entity,CallbackInfo ci){
        if(!CuisineGameplayRules.nativeEffectsEnabled())ci.cancel();
    }
}
