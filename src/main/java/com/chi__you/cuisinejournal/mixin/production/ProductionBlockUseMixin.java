package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class ProductionBlockUseMixin {
    @Inject(method="use",at=@At("HEAD"))
    private void cuisinejournal$begin(Level l,Player p,InteractionHand h,BlockHitResult hit,CallbackInfoReturnable<InteractionResult> cir){ProductionTracker.beginBlock(p,l,hit.getBlockPos());}
    @Inject(method="use",at=@At("RETURN"))
    private void cuisinejournal$end(Level l,Player p,InteractionHand h,BlockHitResult hit,CallbackInfoReturnable<InteractionResult> cir){if(!l.isClientSide)ProductionTracker.endOperation(p);}
}
