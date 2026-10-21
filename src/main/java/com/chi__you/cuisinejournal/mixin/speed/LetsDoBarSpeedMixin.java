package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.tracking.LetsDoSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="net.satisfy.beachparty.core.block.entity.PalmBarBlockEntity",remap=false)
public abstract class LetsDoBarSpeedMixin {
    @Redirect(method="tick",at=@At(value="FIELD",target="Lnet/satisfy/beachparty/core/block/entity/PalmBarBlockEntity;shakingTime:I",opcode=181),remap=false)
    private static void cuisinejournal$progress(@Coerce Object owner,int value){LetsDoSpeed.write((BlockEntity)owner,"shakingTime",value,LetsDoSpeed.number(owner,"totalShakingTime",50));}
}
