package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.DisplayFoodStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Pseudo
@Mixin(targets="com.jkvin114.displaydelight.block.AbstractItemBlock",remap=false)
public abstract class DisplayFoodDropsMixin {
    @Inject(method="m_49635_",at=@At("RETURN"),cancellable=true,remap=false)
    private void cuisinejournal$inherit(BlockState state,LootParams.Builder params,CallbackInfoReturnable<List<ItemStack>> cir){
        var origin=params.getOptionalParameter(LootContextParams.ORIGIN);if(origin==null)return;
        cir.setReturnValue(DisplayFoodStorage.drops(params.getLevel(),BlockPos.containing(origin),state,cir.getReturnValue()));
    }
}
