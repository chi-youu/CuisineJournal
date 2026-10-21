package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.tracking.LetsDoSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="net.satisfy.beachparty.core.block.entity.MiniFridgeBlockEntity",remap=false)
public abstract class LetsDoFridgeSpeedMixin {
    @Redirect(method="tick",at=@At(value="FIELD",target="Lnet/satisfy/beachparty/core/block/entity/MiniFridgeBlockEntity;fermentationTime:I",opcode=181),remap=false)
    private void cuisinejournal$progress(@Coerce Object owner,int value){LetsDoSpeed.write((BlockEntity)owner,"fermentationTime",value,LetsDoSpeed.number(owner,"totalFermentationTime",200));}
}
