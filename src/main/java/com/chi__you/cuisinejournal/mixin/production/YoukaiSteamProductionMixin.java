package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.InPlaceProductionTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.steamer.RackItemData",remap=false)
public abstract class YoukaiSteamProductionMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(@Coerce Object be,Level level,double heat,CallbackInfoReturnable<Boolean> cir){InPlaceProductionTracker.begin(this,(BlockEntity)be);}
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfoReturnable<Boolean> cir){InPlaceProductionTracker.end(this);}
}
