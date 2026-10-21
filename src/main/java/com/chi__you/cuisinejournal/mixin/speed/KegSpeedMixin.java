package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
@Pseudo
@Mixin(targets="umpaz.brewinandchewin.common.block.entity.KegBlockEntity", remap=false)
public abstract class KegSpeedMixin {
    @Redirect(method="processFermenting", at=@At(value="FIELD", target="Lumpaz/brewinandchewin/common/block/entity/KegBlockEntity;fermentTime:I", opcode=181), remap=false)
    private void cuisinejournal$progress(@Coerce Object owner, int value) {
        CookingSpeed.write((BlockEntity)owner, "fermentTime", value, "fermentTimeTotal");
    }
}
