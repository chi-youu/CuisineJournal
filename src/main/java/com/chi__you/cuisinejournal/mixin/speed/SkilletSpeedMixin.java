package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo
@Mixin(targets="vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity", remap=false)
public abstract class SkilletSpeedMixin {
    @Redirect(method="cookAndOutputItems", at=@At(value="FIELD", target="Lvectorwing/farmersdelight/common/block/entity/SkilletBlockEntity;cookingTime:I", opcode=181), remap=false)
    private void cuisinejournal$progress(@Coerce Object owner, int value) {
        CookingSpeed.write((BlockEntity)owner, "cookingTime", value, "cookingTimeTotal");
    }
}
