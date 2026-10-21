package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.tracking.LetsDoSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="net.satisfy.farm_and_charm.core.block.entity.SiloBlockEntity",remap=false)
public abstract class LetsDoSiloSpeedMixin {
    @Inject(method="dry",at=@At("HEAD"),remap=false)
    private void cuisinejournal$before(CallbackInfo ci){LetsDoSpeed.beforeArray((BlockEntity)(Object)this,"times");}
    @Inject(method="dry",at=@At("RETURN"),remap=false)
    private void cuisinejournal$after(CallbackInfo ci){LetsDoSpeed.afterArray((BlockEntity)(Object)this,"times");}
}
