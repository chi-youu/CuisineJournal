package com.chi__you.cuisinejournal.mixin.speed;

import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets="com.github.L_Ender.cataclysm.blockentities.AltarOfAmethyst_Block_Entity", remap=false)
public abstract class AmethystSpeedMixin {
    @Redirect(method="cookTick", at=@At(value="FIELD", target="Lcom/github/L_Ender/cataclysm/blockentities/AltarOfAmethyst_Block_Entity;blessingProgress:I", opcode=181), remap=false)
    private static void cuisinejournal$progress(@Coerce Object owner, int value) {
        // This station compares >= before incrementing and has no cached total field.
        CookingSpeed.write((BlockEntity)owner, "blessingProgress", value, Integer.MAX_VALUE);
    }
}
