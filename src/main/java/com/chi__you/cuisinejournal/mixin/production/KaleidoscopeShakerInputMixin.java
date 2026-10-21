package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeCocktails;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopetavern.blockentity.mixology.ShakerBlockEntity",remap=false)
public abstract class KaleidoscopeShakerInputMixin {
    @Inject(method="addIngredient",at=@At("HEAD"),remap=false)
    private void cuisinejournal$before(ItemStack item,LivingEntity user,CallbackInfoReturnable<Boolean> cir){KaleidoscopeCocktails.before((BlockEntity)(Object)this);}
    @Inject(method="addIngredient",at=@At("RETURN"),remap=false)
    private void cuisinejournal$after(ItemStack item,LivingEntity user,CallbackInfoReturnable<Boolean> cir){KaleidoscopeCocktails.after((BlockEntity)(Object)this,cir.getReturnValue(),user);}
}
