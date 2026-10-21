package com.chi__you.cuisinejournal.mixin.speed;

import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class SmokerSpeedMixin {
    @Shadow int cookingProgress;
    @Shadow int cookingTotalTime;

    @Redirect(method="serverTick", at=@At(value="FIELD",
            target="Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;cookingProgress:I", opcode=181))
    private static void cuisinejournal$progress(AbstractFurnaceBlockEntity be, int value) {
        var access = (SmokerSpeedMixin)(Object)be;
        // The native ticker is shared with furnaces and blast furnaces.
        access.cookingProgress = be instanceof SmokerBlockEntity
                ? CookingSpeed.advance(be, "smoker", access.cookingProgress, value, access.cookingTotalTime)
                : value;
    }
}
