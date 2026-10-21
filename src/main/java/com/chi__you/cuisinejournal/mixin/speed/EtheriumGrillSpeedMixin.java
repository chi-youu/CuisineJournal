package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="auviotre.enigmatic.delicacy.contents.entities.EtheriumStoveBlockEntity", remap=false)
public abstract class EtheriumGrillSpeedMixin {
    @Inject(method="cookAndOutputItems", at=@At("HEAD"), remap=false)
    private void cuisinejournal$progress(CallbackInfo ci) {
        CookingSpeed.array((BlockEntity)(Object)this, "cookingTimes", "cookingTimesTotal", 2);
    }
}
