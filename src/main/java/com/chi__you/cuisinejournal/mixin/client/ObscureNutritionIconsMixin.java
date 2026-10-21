package com.chi__you.cuisinejournal.mixin.client;

import com.chi__you.cuisinejournal.food.CuisineGameplayRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.obscuria.obscureapi.client.TooltipBuilder$AttributeIcons", remap=false)
public abstract class ObscureNutritionIconsMixin {
    @Inject(method="putFoodIcons", at=@At("HEAD"), cancellable=true, remap=false)
    private static void cuisinejournal$nutrition(List<Component> lines, ItemStack stack, CallbackInfo ci) {
        if(!CuisineGameplayRules.nutritionDisplayEnabled())ci.cancel();
    }
}
