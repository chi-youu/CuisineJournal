package com.chi__you.cuisinejournal.mixin.speed;

import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CampfireBlockEntity.class)
public abstract class CampfireSpeedMixin {
    @Shadow @Final private int[] cookingProgress;
    @Shadow @Final private int[] cookingTime;
    @Inject(method="cookTick", at=@At("HEAD"))
    private static void cuisinejournal$progress(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, CampfireBlockEntity be, CallbackInfo ci) {
        var access = (CampfireSpeedMixin)(Object)be;
        for (int i=0; i<access.cookingProgress.length; i++) if (!be.getItems().get(i).isEmpty()) {
            int current = access.cookingProgress[i];
            int proposed = current + 1;
            access.cookingProgress[i] += Math.max(0, CookingSpeed.advance(be, "campfire"+i, current, proposed,
                    access.cookingTime[i]) - proposed);
        }
    }
}
