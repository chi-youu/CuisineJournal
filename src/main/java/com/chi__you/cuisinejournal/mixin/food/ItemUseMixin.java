package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.CuisineFoodClientState;
import com.chi__you.cuisinejournal.food.CuisineFoodRuntime;
import com.chi__you.cuisinejournal.food.CuisineFoodService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rejects a full SOL slot before vanilla starts the eating animation. */
@Mixin(Item.class)
public abstract class ItemUseMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void cuisinejournal$preflightFood(Level level, Player player, InteractionHand hand,
                                               CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (level.isClientSide ? !CuisineFoodClientState.active() : !CuisineFoodRuntime.active()) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (CuisineFoodService.definition(stack) == null) {
            return;
        }
        boolean allowed = level.isClientSide
                ? CuisineFoodClientState.canConsume(stack)
                : player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && CuisineFoodService.canConsume(serverPlayer, stack);
        if (!allowed) {
            cir.setReturnValue(InteractionResultHolder.fail(stack));
        }
    }
}
