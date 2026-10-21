package com.chi__you.foodinteractions.mixin;

import com.chi__you.foodinteractions.InteractionBlockDisplay;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep translated block names; custom placeables fall back to their translated whole item. */
@Pseudo
@Mixin(targets="snownee.lychee.core.def.BlockPredicateHelper",remap=false)
public abstract class LycheeBlockNameMixin {
    @Redirect(method="getTooltips",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/level/block/Block;m_49954_()Lnet/minecraft/network/chat/MutableComponent;",
            remap=false),remap=false)
    private static MutableComponent foodinteractions$name(Block block) {
        return InteractionBlockDisplay.name(block);
    }
}
