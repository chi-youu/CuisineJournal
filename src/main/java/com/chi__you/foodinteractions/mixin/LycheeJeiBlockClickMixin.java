package com.chi__you.foodinteractions.mixin;

import com.chi__you.foodinteractions.InteractionBlockDisplay;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Lychee's preview click bypasses its ingredient index and calls Block.asItem directly. */
@Pseudo
@Mixin(targets="snownee.lychee.compat.jei.category.BaseJEICategory",remap=false)
public abstract class LycheeJeiBlockClickMixin {
    @Redirect(method="clickBlock",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/level/block/Block;m_5456_()Lnet/minecraft/world/item/Item;",
            remap=false),remap=false)
    private Item foodinteractions$source(Block block) {
        return InteractionBlockDisplay.sourceItem(block);
    }
}
