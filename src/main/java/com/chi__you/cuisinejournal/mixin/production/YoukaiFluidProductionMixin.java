package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.FluidProductionTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"dev.xkmc.youkaishomecoming.content.pot.kettle.KettleBlockEntity",
        "dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationTankBlockEntity"},remap=false)
public abstract class YoukaiFluidProductionMixin {
    @Inject(method={"finishRecipe(Lnet/minecraft/world/level/Level;Ldev/xkmc/youkaishomecoming/content/pot/kettle/KettleRecipe;)V","finishRecipe(Lnet/minecraft/world/level/Level;Ldev/xkmc/youkaishomecoming/content/pot/ferment/FermentationRecipe;)V"},at=@At("HEAD"),remap=false,require=1)
    private void cuisinejournal$begin(CallbackInfo ci){FluidProductionTracker.begin((BlockEntity)(Object)this,true);}
    @Inject(method={"finishRecipe(Lnet/minecraft/world/level/Level;Ldev/xkmc/youkaishomecoming/content/pot/kettle/KettleRecipe;)V","finishRecipe(Lnet/minecraft/world/level/Level;Ldev/xkmc/youkaishomecoming/content/pot/ferment/FermentationRecipe;)V"},at=@At("RETURN"),remap=false,require=1)
    private void cuisinejournal$end(CallbackInfo ci){FluidProductionTracker.end((BlockEntity)(Object)this);}
}
