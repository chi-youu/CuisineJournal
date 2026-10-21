package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.CosmopolitanTubProduction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.gumillea.cosmopolitan.common.blockEntity.FrozenDessertTubBlockEntity",remap=false)
public abstract class CosmopolitanTubProcessingMixin {
    @Inject(method="completeProcessing",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,BlockPos pos,@Coerce BlockEntity be,CallbackInfo ci){CosmopolitanTubProduction.begin(be,net.minecraft.world.item.ItemStack.EMPTY,null);}
    @Inject(method="completeProcessing",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,BlockPos pos,@Coerce BlockEntity be,CallbackInfo ci){CosmopolitanTubProduction.transformed(be);}
}
