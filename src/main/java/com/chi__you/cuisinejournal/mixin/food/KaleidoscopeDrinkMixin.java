package com.chi__you.cuisinejournal.mixin.food;
import com.chi__you.cuisinejournal.food.CuisineGameplayRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"com.github.ysbbbbbb.kaleidoscopetavern.item.DrinkBlockItem",
        "com.github.ysbbbbbb.kaleidoscopetavern.item.CocktailBlockItem",
        "com.github.ysbbbbbb.kaleidoscopetavern.item.SignatureCocktailBlockItem"},remap=false)
public abstract class KaleidoscopeDrinkMixin {
    @Inject(method="addDrinkEffect",at=@At("HEAD"),cancellable=true,remap=false)
    private void cuisinejournal$effect(ItemStack stack,Level level,LivingEntity entity,CallbackInfo ci){
        if(!CuisineGameplayRules.nativeEffectsEnabled())ci.cancel();
    }
}
