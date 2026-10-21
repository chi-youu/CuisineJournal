package com.chi__you.cuisinejournal.mixin.speed;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.progression.CookingSpeed;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Coerce;
import net.minecraft.world.level.block.entity.BlockEntity;

@Pseudo
@Mixin(targets="com.aetherteam.aether.blockentity.AbstractAetherFurnaceBlockEntity", remap=false)
public abstract class AetherSpeedMixin {
    @Redirect(method="serverTick", at=@At(value="INVOKE", target="Lcom/aetherteam/aether/mixin/mixins/common/accessor/AbstractFurnaceBlockEntityAccessor;aether$setCookingProgress(I)V"), remap=false)
    private static void cuisinejournal$progress(@Coerce Object owner, int value) {
        BlockEntity be = (BlockEntity)owner;
        int result = CookingSpeed.advance(be, "aether", CookingSpeed.aetherNumber(be, "aether$getCookingProgress"), value,
                CookingSpeed.aetherNumber(be, "aether$getCookingTotalTime"));
        HostReflection.invoke(owner, "aether$setCookingProgress", result);
    }
}
