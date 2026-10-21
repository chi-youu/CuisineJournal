package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.PlacedFoodVariants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
@Mixin(BlockBehaviour.class)
public abstract class KaleidoscopeBlockDropsMixin {
    @Inject(method="getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;",at=@At("RETURN"))
    private void cuisinejournal$inherit(BlockState state,LootParams.Builder params,CallbackInfoReturnable<List<ItemStack>> cir){
        if(!getClass().getName().startsWith("com.github.ysbbbbbb.kaleidoscopetavern.block.mixology."))return;
        var origin=params.getOptionalParameter(LootContextParams.ORIGIN);if(origin==null)return;
        var pos=net.minecraft.core.BlockPos.containing(origin);
        for(var stack:cir.getReturnValue())if(!stack.isEmpty()){
            ItemStack marked=PlacedFoodVariants.read(params.getLevel(),pos,stack);if(marked.hasTag())stack.setTag(marked.getTag().copy());
        }
    }
}
