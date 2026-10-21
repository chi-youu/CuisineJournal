package com.chi__you.cuisinejournal.mixin.production;

import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ItemUtils.class)
public abstract class MilkshakeFilledResultMixin {
    @ModifyVariable(method="createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private static ItemStack cuisinejournal$output(ItemStack result){return com.chi__you.cuisinejournal.tracking.MilkshakeCauldronProduction.output(result);}
}
