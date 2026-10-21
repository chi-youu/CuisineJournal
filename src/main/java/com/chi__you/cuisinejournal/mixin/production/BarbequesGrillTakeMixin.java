package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.ManualGrillProduction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Empty-hand collection bypasses the inventory delivery observer. */
@Pseudo
@Mixin(targets="com.mao.barbequesdelight.content.block.GrillBlock",remap=false)
public abstract class BarbequesGrillTakeMixin {
    @Redirect(method="onClick",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/player/Player;m_21008_(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"),remap=false)
    private void cuisinejournal$take(Player player,InteractionHand hand,ItemStack result){
        ManualGrillProduction.handOutput(player,result);
        player.setItemInHand(hand,result);
    }
}
