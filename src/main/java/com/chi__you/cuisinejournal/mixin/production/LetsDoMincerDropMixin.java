package com.chi__you.cuisinejournal.mixin.production;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.chi__you.cuisinejournal.tracking.VerifiedWorkstations;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="net.satisfy.farm_and_charm.core.block.entity.MincerBlockEntity",remap=false)
public abstract class LetsDoMincerDropMixin {
    @Unique private ItemStack cuisinejournal$output=ItemStack.EMPTY;
    @Inject(method="dropItemsInOutputSlot",at=@At("HEAD"),remap=false)
    private void cuisinejournal$before(CallbackInfo ci){cuisinejournal$output=VerifiedWorkstations.stack((BlockEntity)(Object)this,1).copy();}
    @ModifyArg(method="dropItemsInOutputSlot",at=@At(value="INVOKE",target="Lnet/satisfy/farm_and_charm/core/block/entity/MincerBlockEntity;spawnItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;Lnet/minecraft/core/Position;)V"),index=1,remap=false)
    private ItemStack cuisinejournal$inherit(ItemStack generated){
        if(ItemStack.isSameItem(generated,cuisinejournal$output))generated.setTag(cuisinejournal$output.getTag()==null?null:cuisinejournal$output.getTag().copy());
        cuisinejournal$output=ItemStack.EMPTY;return generated;
    }
}
