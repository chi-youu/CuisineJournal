package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets={"com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity",
        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity"},remap=false)
public abstract class KaleidoscopeServingQualityMixin {
    @Inject(method="takeOutProduct",at=@At("HEAD"),remap=false)
    private void cuisinejournal$freeze(CallbackInfoReturnable<Boolean> cir){
        KaleidoscopeProduction.prepareResult((BlockEntity)(Object)this);
    }
}
