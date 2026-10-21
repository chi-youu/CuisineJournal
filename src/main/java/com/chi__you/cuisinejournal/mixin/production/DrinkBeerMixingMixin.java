package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.DrinkBeerMixing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="lekavar.lma.drinkbeer.blockentities.BartendingTableBlockEntity",remap=false)
public abstract class DrinkBeerMixingMixin {
    @Unique private ItemStack cuisinejournal$base=ItemStack.EMPTY;
    @Inject(method="placeBeer",at=@At("RETURN"),remap=false)
    private void cuisinejournal$placed(ItemStack input,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValue())DrinkBeerMixing.beerPlaced((BlockEntity)(Object)this);
    }
    @Inject(method="putSpice",at=@At("HEAD"),remap=false)
    private void cuisinejournal$before(ItemStack spice,CallbackInfoReturnable<Boolean> cir){
        cuisinejournal$base=DrinkBeerMixing.beer((BlockEntity)(Object)this).copy();
    }
    @Inject(method="putSpice",at=@At("RETURN"),remap=false)
    private void cuisinejournal$mixed(ItemStack spice,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValue())DrinkBeerMixing.beerMixed((BlockEntity)(Object)this,cuisinejournal$base,spice);
        cuisinejournal$base=ItemStack.EMPTY;
    }
    @Inject(method="takeBeer",at=@At("RETURN"),remap=false)
    private void cuisinejournal$taken(boolean simulate,CallbackInfoReturnable<ItemStack> cir){
        DrinkBeerMixing.beerTaken((BlockEntity)(Object)this,cir.getReturnValue(),simulate);
    }
}
