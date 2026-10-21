package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.PapercraftProduction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.kazi_cat.papercraft_magic_decoration.blockentity.CopperBartenderBlockEntity",remap=false)
public abstract class PapercraftBartenderProductionMixin {
    @Inject(method="tryPlaceResult",at=@At("HEAD"),remap=false)
    private void cuisinejournal$produced(net.minecraft.world.level.Level level,ItemStack result,CallbackInfo ci){
        var be=(BlockEntity)(Object)this;PapercraftProduction.produce(be,result);PapercraftProduction.settled(be,result);
    }
}
