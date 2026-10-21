package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="net.ribs.vintagedelight.block.entity.FermentingJarBlockEntity",remap=false)
public abstract class VintageFermentingMixin {
    @Inject(method="craftItem",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){ProductionTracker.beginCooking((BlockEntity)(Object)this);}
    @Inject(method="craftItem",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){ProductionTracker.endCooking((BlockEntity)(Object)this);}
    @Redirect(method="increaseCraftingProgress",at=@At(value="FIELD",target="Lnet/ribs/vintagedelight/block/entity/FermentingJarBlockEntity;progress:I",opcode=181),remap=false)
    private void cuisinejournal$speed(@Coerce Object owner,int value){CookingSpeed.write((BlockEntity)owner,"progress",value,"maxProgress");}
}
