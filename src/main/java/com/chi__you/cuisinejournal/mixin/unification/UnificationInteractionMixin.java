package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.UnificationRuntime;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class UnificationInteractionMixin {
    @Inject(method = "use", at = @At("HEAD"))
    private void cuisinejournal$begin(Level level, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        UnificationRuntime.beginInteraction(level);
    }
    @Inject(method = "use", at = @At("RETURN"))
    private void cuisinejournal$end(Level level, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        UnificationRuntime.endInteraction();
    }
}
