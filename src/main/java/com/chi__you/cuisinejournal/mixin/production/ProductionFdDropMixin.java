package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="vectorwing.farmersdelight.common.utility.ItemUtils",remap=false)
public abstract class ProductionFdDropMixin {
    @Inject(method="spawnItemEntity",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$drop(Level level,ItemStack stack,double x,double y,double z,double dx,double dy,double dz,CallbackInfo ci){if(!level.isClientSide)ProductionTracker.emitted(stack);}
}
