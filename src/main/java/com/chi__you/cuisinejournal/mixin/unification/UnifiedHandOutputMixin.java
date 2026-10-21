package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.UnificationDairy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Several verified harvest/unwrapping hosts put fresh output directly into the hand. */
@Mixin(LivingEntity.class)
public abstract class UnifiedHandOutputMixin {
    @ModifyVariable(method="setItemInHand",at=@At("HEAD"),argsOnly=true)
    private ItemStack cuisinejournal$output(ItemStack stack){
        return (Object)this instanceof Player player?UnificationDairy.handOutput(player,stack):stack;
    }
}
