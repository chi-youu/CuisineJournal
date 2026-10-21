package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.InPlaceProductionTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.steamer.SteamerBlockEntity",remap=false)
public abstract class YoukaiSteamRemovalMixin {
    @Inject(method="removeRack",at=@At("HEAD"),remap=false)
    private void cuisinejournal$remove(CallbackInfo ci){BlockEntity be=(BlockEntity)(Object)this;if(be.getLevel()!=null&&!be.getLevel().isClientSide)InPlaceProductionTracker.removedRack(be);}
}
