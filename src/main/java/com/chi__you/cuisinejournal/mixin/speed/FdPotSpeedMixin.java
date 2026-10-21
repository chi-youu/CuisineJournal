package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo
@Mixin(targets="vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity", remap=false)
public abstract class FdPotSpeedMixin {
    @Redirect(method="processCooking", at=@At(value="FIELD", target="Lvectorwing/farmersdelight/common/block/entity/CookingPotBlockEntity;cookTime:I", opcode=181), remap=false)
    private void cuisinejournal$progress(@Coerce Object owner, int value) {
        CookingSpeed.write((BlockEntity)owner, "cookTime", value, "cookTimeTotal");
    }
}
