package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.PlacedFoodDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets={"club.someoneice.jellyfishingdelight.block.KrabbyPatty",
        "net.mcreator.slimedelight.block.GrassBlockCake1Block","net.mcreator.slimedelight.block.GrassBlockCake2Block",
        "net.mcreator.slimedelight.block.GrassBlockCake3Block","net.mcreator.slimedelight.block.GrassBlockCake4Block",
        "net.mcreator.slimedelight.block.GrassBlockCake5Block","net.mcreator.slimedelight.block.GrassBlockCake6Block",
        "net.mcreator.slimedelight.block.GrassBlockCake7Block"},remap=false)
public abstract class PlacedFoodBreakDropsMixin {
    @Inject(method="onDestroyedByPlayer",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(BlockState state,Level level,BlockPos pos,Player player,boolean harvest,FluidState fluid,CallbackInfoReturnable<Boolean> cir){PlacedFoodDrops.begin(level,pos,state);}
    @Inject(method="onDestroyedByPlayer",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(BlockState state,Level level,BlockPos pos,Player player,boolean harvest,FluidState fluid,CallbackInfoReturnable<Boolean> cir){PlacedFoodDrops.end(level,pos,state);}
}
