package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.PlacedFoodConsumption;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the host's spoon fill without replacing its serving redirect. */
@Pseudo
@Mixin(targets="show.tatd.mod.item.BigWoodenSpoonItem",remap=false)
public abstract class TrailIceCreamSpoonMixin {
    @Inject(method="setFood",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$serve(ItemStack spoon, ItemStack food, CallbackInfo ci) {
        PlacedFoodConsumption.iceCreamSpoonOutput(food);
    }
}
