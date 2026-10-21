package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.LetsDoJuice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="net.satisfy.vinery.core.block.entity.FermentationBarrelBlockEntity",remap=false)
public abstract class LetsDoJuiceMixin {
    @Inject(method="tick",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$before(Level level,BlockPos pos,@Coerce Object be,CallbackInfo ci){LetsDoJuice.beforeTick((BlockEntity)be);}
    @Inject(method="tick",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$after(Level level,BlockPos pos,@Coerce Object be,CallbackInfo ci){LetsDoJuice.afterTick((BlockEntity)be);}
}
