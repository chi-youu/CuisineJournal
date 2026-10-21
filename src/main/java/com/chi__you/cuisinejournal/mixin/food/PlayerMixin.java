package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineFoodRuntime;
import com.chi__you.cuisinejournal.food.CuisineFoodClientState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import com.chi__you.cuisinejournal.food.CuisineSolMigration;
import com.chi__you.cuisinejournal.food.CuisineFoodService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void cuisinejournal$captureLegacyFood(CompoundTag tag, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            CuisineSolMigration.capture(tag, player.getPersistentData());
            CuisineFoodService.forget(player);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void cuisinejournal$preserveLegacyFood(CompoundTag tag, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player
                && !tag.contains("sol_food_data")
                && player.getPersistentData().contains(CuisineSolMigration.BACKUP, 10)) {
            tag.put("sol_food_data", player.getPersistentData()
                    .getCompound(CuisineSolMigration.BACKUP).copy());
        }
    }

    @Inject(method = "canEat(Z)Z", at = @At("HEAD"), cancellable = true)
    private void cuisinejournal$allowFoodUse(boolean ignoreHunger, CallbackInfoReturnable<Boolean> cir) {
        ((com.chi__you.cuisinejournal.skills.VanillaSkillNutrition.NutritionOwner)((Player)(Object)this).getFoodData())
                .cuisinejournal$owner((Player)(Object)this);
        if (((Player) (Object) this).level().isClientSide
                ? CuisineFoodClientState.active() : CuisineFoodRuntime.active()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "causeFoodExhaustion(F)V", at = @At("HEAD"), cancellable = true)
    private void cuisinejournal$ignoreExhaustion(float amount, CallbackInfo ci) {
        if (CuisineFoodRuntime.activeForCurrentThread()) {
            ci.cancel();
        }
    }
}
