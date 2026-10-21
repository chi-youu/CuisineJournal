package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.github.L_Ender.cataclysm.blockentities.AltarOfAmethyst_Block_Entity",remap=false)
public abstract class AmethystProductionMixin {
    @Inject(method="cookTick",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,BlockPos pos,BlockState state,@Coerce Object be,CallbackInfo ci){ProductionTracker.beginCooking((net.minecraft.world.level.block.entity.BlockEntity)be);}
    @Inject(method="cookTick",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,BlockPos pos,BlockState state,@Coerce Object be,CallbackInfo ci){ProductionTracker.endCooking((net.minecraft.world.level.block.entity.BlockEntity)be);}
}
