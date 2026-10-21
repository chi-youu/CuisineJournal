package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarrelBlockEntity",remap=false)
public abstract class KaleidoscopeBarrelOutputMixin {
    @Unique private ItemStack cuisinejournal$bottle=ItemStack.EMPTY;
    @Inject(method="extractOneOutput",at=@At("RETURN"),remap=false)
    private void cuisinejournal$output(ItemStack template,CallbackInfoReturnable<ItemStack> cir){
        KaleidoscopeProduction.output((BlockEntity)(Object)this,cir.getReturnValue(),true);cuisinejournal$bottle=cir.getReturnValue().copy();
    }
    @ModifyArg(method="placeBottleResult",at=@At(value="INVOKE",target="Lcom/github/ysbbbbbb/kaleidoscopetavern/blockentity/brew/DrinkBlockEntity;addItem(Lnet/minecraft/world/item/ItemStack;)Z"),index=0,remap=false)
    private ItemStack cuisinejournal$placed(ItemStack generated){
        ItemStack result=cuisinejournal$bottle.isEmpty()?generated:cuisinejournal$bottle.copy();cuisinejournal$bottle=ItemStack.EMPTY;return result;
    }
}
