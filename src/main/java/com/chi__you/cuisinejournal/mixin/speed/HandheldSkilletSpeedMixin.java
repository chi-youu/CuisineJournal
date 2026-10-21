package com.chi__you.cuisinejournal.mixin.speed;

import com.chi__you.cuisinejournal.mixin.food.CookingUseTimer;

import com.chi__you.cuisinejournal.progression.CookingSpeed;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets="vectorwing.farmersdelight.common.item.SkilletItem", remap=false)
public abstract class HandheldSkilletSpeedMixin {
    @Unique private static final java.util.Map<ServerPlayer, Double> cuisinejournal$fractions = new java.util.WeakHashMap<>();
    @Inject(method="m_5929_", at=@At("RETURN"), remap=false)
    private void cuisinejournal$speed(Level level, LivingEntity entity, ItemStack skillet, int remaining, CallbackInfo ci) {
        if (!(entity instanceof ServerPlayer player)) return;
        if(remaining<=1) {cuisinejournal$fractions.remove(player);return;}
        double extra = cuisinejournal$fractions.getOrDefault(player, 0.0) + CookingSpeed.playerExtra(player, CookingMethod.CAMPFIRE);
        int whole = (int)Math.floor(extra + 1e-9);
        cuisinejournal$fractions.put(player, Math.max(0, extra - whole));
        // Keep the final native use tick so its normal completion/production transaction still runs.
        ((CookingUseTimer)player).cuisinejournal$remaining(Math.max(1, remaining-whole));
    }
}
