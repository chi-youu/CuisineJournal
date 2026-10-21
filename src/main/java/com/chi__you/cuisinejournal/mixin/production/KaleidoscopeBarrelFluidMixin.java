package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarrelBlockEntity",remap=false)
public abstract class KaleidoscopeBarrelFluidMixin {
    @Unique private ItemStack cuisinejournal$fluid=ItemStack.EMPTY;
    @Inject(method="addFluid",at=@At("HEAD"),remap=false)
    private void cuisinejournal$before(LivingEntity user,ItemStack item,CallbackInfoReturnable<Boolean> cir){cuisinejournal$fluid=item.copyWithCount(1);}
    @Inject(method="addFluid",at=@At("RETURN"),remap=false)
    private void cuisinejournal$after(LivingEntity user,ItemStack item,CallbackInfoReturnable<Boolean> cir){if(cir.getReturnValue())KaleidoscopeProduction.fluidSource((BlockEntity)(Object)this,cuisinejournal$fluid);}
    @Inject(method="removeFluid",at=@At("RETURN"),remap=false)
    private void cuisinejournal$removed(LivingEntity user,ItemStack item,CallbackInfoReturnable<Boolean> cir){if(cir.getReturnValue())KaleidoscopeProduction.clearFluid((BlockEntity)(Object)this);}
}
