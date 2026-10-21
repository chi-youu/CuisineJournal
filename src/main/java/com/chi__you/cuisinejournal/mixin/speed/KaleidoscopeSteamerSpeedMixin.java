package com.chi__you.cuisinejournal.mixin.speed;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.tracking.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity",remap=false)
public abstract class KaleidoscopeSteamerSpeedMixin {
    @Inject(method="cookingTick",at=@At("RETURN"),remap=false)
    private void cuisinejournal$progress(CallbackInfo ci){
        var be=(BlockEntity)(Object)this;Object raw=HostReflection.field(be,"cookingProgress"),total=HostReflection.field(be,"cookingTime");
        if(!(raw instanceof int[] progress)||!(total instanceof int[] times))return;
        var above=be.getLevel().getBlockState(be.getBlockPos().above());
        var lid=be.getBlockState().getBlock().getStateDefinition().getProperty("has_lid");
        if(!above.is(be.getBlockState().getBlock())
                &&lid instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag&&!be.getBlockState().getValue(flag))return;
        double extra=KaleidoscopeSpeed.extra(be);long tick=be.getLevel().getGameTime();
        int bonus=(int)(Math.floor((tick+1)*extra)-Math.floor(tick*extra));
        for(int i=0;i<progress.length;i++)if(times[i]>0)progress[i]=Math.min(times[i],progress[i]+Math.max(0,bonus));
    }
}
