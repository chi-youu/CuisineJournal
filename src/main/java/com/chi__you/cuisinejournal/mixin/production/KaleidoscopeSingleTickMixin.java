package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.MillstoneBlockEntity",
        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ShawarmaSpitBlockEntity"},remap=false)
public abstract class KaleidoscopeSingleTickMixin {
    @Unique private int cuisinejournal$cookTime;
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){cuisinejournal$cookTime=com.chi__you.cuisinejournal.progression.CookingSpeed.number(this,"cookTime");KaleidoscopeProduction.begin((BlockEntity)(Object)this,null);}
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){
        var be=(BlockEntity)(Object)this;int now=com.chi__you.cuisinejournal.progression.CookingSpeed.number(be,"cookTime");
        if(now>0&&now<cuisinejournal$cookTime)KaleidoscopeSpeed.set(be,"cookTime",KaleidoscopeSpeed.remaining(be,now,cuisinejournal$cookTime-now));
        KaleidoscopeProduction.end(be);
    }
}
