package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.tracking.LetsDoSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="net.satisfy.vinery.core.block.entity.ApplePressBlockEntity",remap=false)
public abstract class LetsDoAppleSpeedMixin {
    @Redirect(method="tick",at=@At(value="FIELD",target="Lnet/satisfy/vinery/core/block/entity/ApplePressBlockEntity;progress1:I",opcode=181),remap=false)
    private void cuisinejournal$mash(@Coerce Object owner,int value){LetsDoSpeed.write((BlockEntity)owner,"progress1",value,LetsDoSpeed.number(owner,"maxProgress1",600));}
    @Redirect(method="tick",at=@At(value="FIELD",target="Lnet/satisfy/vinery/core/block/entity/ApplePressBlockEntity;progress2:I",opcode=181),remap=false)
    private void cuisinejournal$ferment(@Coerce Object owner,int value){LetsDoSpeed.write((BlockEntity)owner,"progress2",value,LetsDoSpeed.number(owner,"maxProgress2",800));}
}
