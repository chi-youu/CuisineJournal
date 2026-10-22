package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.PlacedFoodDrops;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class PlacedFoodLootDropsMixin {
    @Inject(method="getDrops",at=@At("RETURN"),cancellable=true)
    private void cuisinejournal$inherit(LootParams.Builder params,CallbackInfoReturnable<List<ItemStack>> cir){
        var state=(net.minecraft.world.level.block.state.BlockState)(Object)this;
        if(!PlacedFoodDrops.supports(state))return;
        var origin=params.getOptionalParameter(LootContextParams.ORIGIN);if(origin==null)return;
        cir.setReturnValue(PlacedFoodDrops.loot(params.getLevel(),BlockPos.containing(origin),state,cir.getReturnValue()));
    }
}
