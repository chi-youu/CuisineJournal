package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tracks both independent production loops owned by Etherium Stove. */
@Pseudo
@Mixin(targets="auviotre.enigmatic.delicacy.contents.entities.EtheriumStoveBlockEntity", remap=false)
public abstract class EtheriumStoveGrillingMixin {
    @Inject(method="cookAndOutputItems", at=@At("HEAD"), remap=false)
    private void cuisinejournal$beginGrilling(CallbackInfo ci) {
        ProductionTracker.beginEtheriumGrilling((BlockEntity)(Object)this);
    }

    @Inject(method="cookAndOutputItems", at=@At("RETURN"), remap=false)
    private void cuisinejournal$endGrilling(CallbackInfo ci) {
        ProductionTracker.endCooking((BlockEntity)(Object)this);
    }

    @Inject(method="burn", at=@At("HEAD"), remap=false)
    private void cuisinejournal$beginFurnace(CallbackInfoReturnable<Boolean> cir) {
        ProductionTracker.beginEtheriumFurnace((BlockEntity)(Object)this);
    }

    @Inject(method="burn", at=@At("RETURN"), remap=false)
    private void cuisinejournal$endFurnace(CallbackInfoReturnable<Boolean> cir) {
        ProductionTracker.endEtheriumFurnace((BlockEntity)(Object)this);
    }
}
