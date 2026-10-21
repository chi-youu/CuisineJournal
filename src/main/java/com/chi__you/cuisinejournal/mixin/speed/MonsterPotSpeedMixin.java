package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo
@Mixin(targets="net.yirmiri.dungeonsdelight.common.block.entity.MonsterPotBlockEntity", remap=false)
public abstract class MonsterPotSpeedMixin {
    @Redirect(method="processCooking", at=@At(value="FIELD", target="Lnet/yirmiri/dungeonsdelight/common/block/entity/MonsterPotBlockEntity;cookTime:I", opcode=181), remap=false)
    private void cuisinejournal$progress(@Coerce Object owner, int value) {
        CookingSpeed.write((BlockEntity)owner, "cookTime", value, "cookTimeTotal");
    }
}
