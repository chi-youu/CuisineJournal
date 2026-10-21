package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.tracking.LetsDoSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="net.satisfy.vinery.core.block.entity.FermentationBarrelBlockEntity",remap=false)
public abstract class LetsDoWineSpeedMixin {
    @Redirect(method="tick",at=@At(value="FIELD",target="Lnet/satisfy/vinery/core/block/entity/FermentationBarrelBlockEntity;fermentationTime:I",opcode=181),remap=false)
    private static void cuisinejournal$progress(@Coerce Object owner,int value){LetsDoSpeed.write((BlockEntity)owner,"fermentationTime",value,2147483647);}
}
