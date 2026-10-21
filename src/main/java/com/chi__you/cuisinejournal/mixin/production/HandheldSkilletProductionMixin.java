package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Stamp the actual result before insertion; the host drops any remainder with the same stamp. */
@Pseudo
@Mixin(targets="vectorwing.farmersdelight.common.item.SkilletItem",remap=false)
public abstract class HandheldSkilletProductionMixin {
    @Redirect(method="lambda$finishUsingItem$1",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/player/Inventory;m_36054_(Lnet/minecraft/world/item/ItemStack;)Z",
            remap=false),remap=false)
    private static boolean cuisinejournal$deliver(Inventory inventory,ItemStack result,
                                                  Level level,Player player,ItemStack skillet,
                                                  CampfireCookingRecipe recipe) {
        ProductionTracker.handheldSkilletOutput(player,skillet,result);
        return inventory.add(result);
    }
}
