package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity",remap=false)
public abstract class KaleidoscopeFluidInputMixin {
    @Unique private ItemStack cuisinejournal$fluid;
    @Inject(method="addSoupBase",at=@At("HEAD"),remap=false)
    private void cuisinejournal$before(Level level,LivingEntity user,ItemStack input,CallbackInfoReturnable<Boolean> cir){cuisinejournal$fluid=input.copyWithCount(1);}
    @Inject(method="addSoupBase",at=@At("RETURN"),remap=false)
    private void cuisinejournal$after(Level level,LivingEntity user,ItemStack input,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValue())KaleidoscopeProduction.fluidSource((BlockEntity)(Object)this,cuisinejournal$fluid);
    }
    @Inject(method="removeSoupBase",at=@At("RETURN"),remap=false)
    private void cuisinejournal$removed(Level level,LivingEntity user,ItemStack input,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValue())KaleidoscopeProduction.clearFluid((BlockEntity)(Object)this);
    }
}
