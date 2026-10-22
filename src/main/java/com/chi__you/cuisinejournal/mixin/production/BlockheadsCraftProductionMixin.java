package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.BlockheadsKitchen;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="net.blay09.mods.cookingforblockheads.menu.inventory.InventoryCraftBook",remap=false)
public abstract class BlockheadsCraftProductionMixin {
    @Inject(method="tryCraft",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfoReturnable<ItemStack> cir){BlockheadsKitchen.beginCraft(this);}
    @Inject(method="fireEventsAndHandleAchievements",at=@At("HEAD"),remap=false)
    private void cuisinejournal$ingredients(CallbackInfo ci){BlockheadsKitchen.craftingInputs(this);}
    @Inject(method="tryCraft",at=@At("RETURN"),remap=false)
    private void cuisinejournal$result(ItemStack output,NonNullList<ItemStack> inputs,Player player,@Coerce Object kitchen,CallbackInfoReturnable<ItemStack> cir){
        BlockheadsKitchen.crafted(this,player,cir.getReturnValue());
    }
}
