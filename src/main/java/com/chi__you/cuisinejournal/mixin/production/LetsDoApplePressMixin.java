package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo
@Mixin(targets="net.satisfy.vinery.core.block.entity.ApplePressBlockEntity",remap=false)
public abstract class LetsDoApplePressMixin {
    @Inject(method={"processMashing","processFermenting"},at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(@Coerce Object be,@Coerce Object recipe,CallbackInfo ci){ProductionTracker.beginApplePress((BlockEntity)be,(net.minecraft.world.item.crafting.Recipe<?>)recipe);}
    @Inject(method={"processMashing","processFermenting"},at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(@Coerce Object be,@Coerce Object recipe,CallbackInfo ci){ProductionTracker.endCooking((BlockEntity)be);}
}
