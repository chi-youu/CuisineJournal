package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackBlockEntity", remap=false)
public abstract class DryingRackSpeedMixin {
    @Inject(method="cookTick()V", at=@At("HEAD"), remap=false)
    private void cuisinejournal$progress(CallbackInfo ci) {
        CookingSpeed.array((BlockEntity)(Object)this, "cookingProgress", "cookingTime", 1);
    }
}
