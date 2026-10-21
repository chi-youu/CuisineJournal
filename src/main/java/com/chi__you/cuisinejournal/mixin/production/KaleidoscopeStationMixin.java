package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity",
        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity",
        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity",
        "com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity",
        "com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarrelBlockEntity"},remap=false)
public abstract class KaleidoscopeStationMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(Level level,CallbackInfo ci){KaleidoscopeProduction.begin((BlockEntity)(Object)this,null);}
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(Level level,CallbackInfo ci){KaleidoscopeProduction.end((BlockEntity)(Object)this);}
}
