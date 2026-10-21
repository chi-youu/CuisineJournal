package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopecookery.item.TeapotItem",remap=false)
public abstract class KaleidoscopeTeapotServingMixin {
    @Inject(method="getPourOut",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$batch(ItemStack pot,CallbackInfoReturnable<ItemStack> cir){
        KaleidoscopeProduction.prepareTeapotItem(pot,cir.getReturnValue());
    }
}
