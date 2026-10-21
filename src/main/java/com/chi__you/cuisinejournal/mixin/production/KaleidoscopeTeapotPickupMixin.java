package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import net.minecraft.world.item.ItemStack;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.TeapotBlockEntity",remap=false)
public abstract class KaleidoscopeTeapotPickupMixin {
    @Inject(method="getDrops",at=@At("HEAD"),remap=false)
    private void cuisinejournal$batch(CallbackInfoReturnable<List<ItemStack>> cir){KaleidoscopeProduction.prepareResult((BlockEntity)(Object)this);}
}
