package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo
@Mixin(targets={"net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity","net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity","net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity","net.satisfy.bakery.core.block.entity.SmallCookingPotBlockEntity","net.satisfy.candlelight.core.block.entity.LargeCookingPotBlockEntity","net.satisfy.candlelight.core.block.entity.CookingPanBlockEntity","net.satisfy.meadow.core.block.entity.CheeseFormBlockEntity","net.satisfy.meadow.core.block.entity.CookingCauldronBlockEntity","net.satisfy.herbalbrews.core.blocks.entity.TeaKettleBlockEntity","net.satisfy.beachparty.core.block.entity.PalmBarBlockEntity","net.satisfy.beachparty.core.block.entity.MiniFridgeBlockEntity","net.satisfy.vinery.core.block.entity.FermentationBarrelBlockEntity"},remap=false)
public abstract class LetsDoCraftMixin {
    @Inject(method="craft",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){var be=(BlockEntity)(Object)this;ProductionTracker.beginCooking(be);
        if("vinery:wine_fermentation".equals(com.chi__you.cuisinejournal.tracking.LetsDoStations.method(be)))com.chi__you.cuisinejournal.tracking.LetsDoJuice.beforeCraft(be);}
    @Inject(method="craft",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){ProductionTracker.endCooking((BlockEntity)(Object)this);}
}
