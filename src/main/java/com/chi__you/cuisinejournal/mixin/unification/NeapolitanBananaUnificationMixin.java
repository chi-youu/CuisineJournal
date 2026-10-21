package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.UnificationRuntime;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.teamabnormals.neapolitan.common.item.BananaBunchItem",remap=false)
public abstract class NeapolitanBananaUnificationMixin {
    @Inject(method="handleOpening",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(Level level,Player player,InteractionHand hand,ItemStack stack,CallbackInfo ci){
        UnificationRuntime.beginInteraction(level);
    }
    @Inject(method="handleOpening",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(Level level,Player player,InteractionHand hand,ItemStack stack,CallbackInfo ci){
        UnificationRuntime.endInteraction();
    }
}
