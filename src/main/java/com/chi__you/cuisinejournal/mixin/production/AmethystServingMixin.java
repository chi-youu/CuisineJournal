package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
@Pseudo
@Mixin(targets="com.github.L_Ender.cataclysm.blocks.Altar_Of_Amethyst_Block",remap=false)
public abstract class AmethystServingMixin {
    @ModifyArg(method="m_6227_",at=@At(value="INVOKE",target="Lcom/github/L_Ender/cataclysm/blocks/Altar_Of_Amethyst_Block;m_49840_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",remap=false),index=2,remap=false)
    private ItemStack cuisinejournal$claim(ItemStack result){return ProductionTracker.claimInteractionDrop(result);}
}
