package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity",
        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity"},remap=false)
public abstract class KaleidoscopeReadyMixin {
    @Inject(method={"applyRecipe","applyFlexRecipe"},at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){KaleidoscopeProduction.begin((BlockEntity)(Object)this,null);}
    @Inject(method={"applyRecipe","applyFlexRecipe"},at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){KaleidoscopeProduction.end((BlockEntity)(Object)this);}
}
