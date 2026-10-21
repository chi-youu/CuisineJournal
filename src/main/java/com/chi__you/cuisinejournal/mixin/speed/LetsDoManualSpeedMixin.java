package com.chi__you.cuisinejournal.mixin.speed;
import com.chi__you.cuisinejournal.tracking.LetsDoSpeed;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets={"net.satisfy.farm_and_charm.core.block.entity.CraftingBowlBlockEntity","net.satisfy.farm_and_charm.core.block.entity.MincerBlockEntity"},remap=false)
public abstract class LetsDoManualSpeedMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;m_7731_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),remap=false)
    private boolean cuisinejournal$advance(Level level,BlockPos pos,BlockState state,int flags){return level.setBlock(pos,LetsDoSpeed.manual(level,pos,state),flags);}
}
