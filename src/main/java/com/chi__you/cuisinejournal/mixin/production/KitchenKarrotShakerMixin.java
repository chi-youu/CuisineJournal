package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KitchenKarrotShaker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="io.github.tt432.kitchenkarrot.item.ShakerItem",remap=false)
public abstract class KitchenKarrotShakerMixin {
    @Inject(method="m_5922_",at=@At("RETURN"),remap=false)
    private void cuisinejournal$shaken(ItemStack stack,Level level,LivingEntity user,CallbackInfoReturnable<ItemStack> cir){
        KitchenKarrotShaker.shaken(stack,user);
    }
}
