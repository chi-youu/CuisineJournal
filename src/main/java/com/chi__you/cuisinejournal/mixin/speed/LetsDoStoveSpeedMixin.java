package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.tracking.LetsDoSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity",remap=false)
public abstract class LetsDoStoveSpeedMixin {
    @Redirect(method="tick",at=@At(value="FIELD",target="Lnet/satisfy/farm_and_charm/core/block/entity/StoveBlockEntity;cookTime:I",opcode=181),remap=false)
    private void cuisinejournal$progress(@Coerce Object owner,int value){LetsDoSpeed.write((BlockEntity)owner,"cookTime",value,LetsDoSpeed.number(owner,"cookTimeTotal",240));}
}
