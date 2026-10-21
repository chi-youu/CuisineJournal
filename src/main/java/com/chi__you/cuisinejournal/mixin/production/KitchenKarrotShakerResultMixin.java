package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KitchenKarrotShaker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="io.github.tt432.kitchenkarrot.menu.ShakerMenu",remap=false)
public abstract class KitchenKarrotShakerResultMixin {
    @Redirect(method="lambda$finishRecipe$1",at=@At(value="INVOKE",
            target="Lnet/minecraftforge/items/IItemHandler;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"),remap=false)
    private ItemStack cuisinejournal$result(IItemHandler handler,int slot,ItemStack raw,boolean simulate,Player user,IItemHandler inputs){
        var result=simulate?raw:KitchenKarrotShaker.shakerResult(this,user,inputs,raw);
        return handler.insertItem(slot,result,simulate);
    }
}
