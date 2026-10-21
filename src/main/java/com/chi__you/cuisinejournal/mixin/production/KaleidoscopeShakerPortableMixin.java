package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeCocktails;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.List;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopetavern.block.mixology.ShakerBlock",remap=false)
public abstract class KaleidoscopeShakerPortableMixin {
    @Inject(method="m_6402_",at=@At("RETURN"),remap=false)
    private void cuisinejournal$placed(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity user,ItemStack stack,CallbackInfo ci){
        var be=level.getBlockEntity(pos);if(be!=null)KaleidoscopeCocktails.placed(be,stack);
    }
    @Inject(method="m_49635_",at=@At("RETURN"),remap=false)
    private void cuisinejournal$drops(BlockState state,LootParams.Builder params,CallbackInfoReturnable<List<ItemStack>> cir){
        var be=params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);if(be!=null)for(var stack:cir.getReturnValue())
            if(stack.getItem().getClass().getName().endsWith(".ShakerItem"))KaleidoscopeCocktails.portable(be,stack);
    }
}
