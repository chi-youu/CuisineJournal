package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.PlacedFoodVariants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopetavern.block.mixology.SignatureCocktailBlock",remap=false)
public abstract class KaleidoscopeGlasswareMixin {
    @Inject(method="m_49635_",at=@At("RETURN"),remap=false)
    private void cuisinejournal$inherit(BlockState state,LootParams.Builder params,CallbackInfoReturnable<List<ItemStack>> cir){
        var be=params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);if(be==null)return;
        for(var stack:cir.getReturnValue())if(!stack.isEmpty()){
            ItemStack marked=PlacedFoodVariants.read(be.getLevel(),be.getBlockPos(),stack);
            if(marked.hasTag())stack.setTag(marked.getTag().copy());
        }
    }
}
