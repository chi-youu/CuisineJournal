package com.chi__you.cuisinejournal.mixin.food;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
@Pseudo
@Mixin(targets="net.satisfy.vinery.core.item.DrinkBlockItem",remap=false)
public abstract class LetsDoWineShareMixin {
    @Inject(method="getShareTag",at=@At("RETURN"),cancellable=true,remap=false)
    private void cuisinejournal$share(ItemStack stack,CallbackInfoReturnable<CompoundTag> cir){var tag=stack.hasTag()?stack.getTag().copy():new CompoundTag();if(cir.getReturnValue()!=null)tag.merge(cir.getReturnValue());cir.setReturnValue(tag);}
    @Inject(method="readShareTag",at=@At("HEAD"),remap=false)
    private void cuisinejournal$read(ItemStack stack,CompoundTag tag,CallbackInfo ci){stack.setTag(tag==null?null:tag.copy());}
}
