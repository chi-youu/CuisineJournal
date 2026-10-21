package com.chi__you.cuisinejournal.mixin.speed;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.tracking.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.MillstoneBlockEntity",remap=false)
public abstract class KaleidoscopeProgressSpeedMixin {
    @Unique private int cuisinejournal$before;
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void cuisinejournal$before(CallbackInfo ci){Object value=HostReflection.field(this,"progress");cuisinejournal$before=value instanceof Number n?n.intValue():0;}
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private void cuisinejournal$after(CallbackInfo ci){
        var be=(BlockEntity)(Object)this;Object value=HostReflection.field(this,"progress");
        if(value instanceof Number n&&n.intValue()>0&&n.intValue()<cuisinejournal$before)
            KaleidoscopeSpeed.set(be,"progress",KaleidoscopeSpeed.remaining(be,n.intValue(),cuisinejournal$before-n.intValue()));
    }
}
