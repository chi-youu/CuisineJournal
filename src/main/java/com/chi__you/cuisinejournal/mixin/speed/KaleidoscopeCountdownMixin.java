package com.chi__you.cuisinejournal.mixin.speed;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets={"com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity",
        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity",
        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity"},remap=false)
public abstract class KaleidoscopeCountdownMixin {
    @Unique private int cuisinejournal$before;
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        Object raw=HostReflection.field(this,"currentTick");cuisinejournal$before=raw instanceof Number n?n.intValue():0;
    }
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        var be=(BlockEntity)(Object)this;int now=com.chi__you.cuisinejournal.progression.CookingSpeed.number(be,"currentTick");
        int status=com.chi__you.cuisinejournal.progression.CookingSpeed.number(be,"status");
        boolean processing=be.getClass().getName().contains("Stockpot")?status==2:be.getClass().getName().contains("Teapot")?status==1:status==1;
        if(processing&&now>=0&&now<cuisinejournal$before){
            KaleidoscopeSpeed.set(be,"currentTick",KaleidoscopeSpeed.remaining(be,now,cuisinejournal$before-now));
        }
    }
}
