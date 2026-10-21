package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo
@Mixin(targets="io.github.rcneg.bossesdelight.blocks.entity.SoulCookingPotBlockEntity", remap=false)
public abstract class SoulPotSpeedMixin {
    @Redirect(method="processCooking", at=@At(value="FIELD", target="Lio/github/rcneg/bossesdelight/blocks/entity/SoulCookingPotBlockEntity;cookTime:I", opcode=181), remap=false)
    private void cuisinejournal$progress(@Coerce Object owner, int value) {
        CookingSpeed.write((BlockEntity)owner, "cookTime", value, "cookTimeTotal");
    }
}
