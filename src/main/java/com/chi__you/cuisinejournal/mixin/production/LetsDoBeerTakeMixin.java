package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.world.item.ItemStack;
@Pseudo
@Mixin(targets="net.satisfy.brewery.core.block.entity.BrewstationBlockEntity",remap=false)
public abstract class LetsDoBeerTakeMixin {
    @Inject(method="getBeer",at=@At("RETURN"),remap=false)
    private void cuisinejournal$take(CallbackInfoReturnable<ItemStack> cir){ProductionTracker.takeDirect((BlockEntity)(Object)this,cir.getReturnValue(),100);}
}
