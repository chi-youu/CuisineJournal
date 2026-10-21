package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.PlacedFoodVariants;
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
@Mixin(targets={"com.github.ysbbbbbb.kaleidoscopecookery.block.drink.TeacupBlock",
        "com.github.ysbbbbbb.kaleidoscopecookery.block.decoration.PlateBlock"},remap=false)
public abstract class KaleidoscopeCupDropsMixin {
    @Inject(method="m_49635_",at=@At("RETURN"),cancellable=true,remap=false)
    private void cuisinejournal$inherit(BlockState state,LootParams.Builder params,CallbackInfoReturnable<List<ItemStack>> cir){
        var origin=params.getOptionalParameter(LootContextParams.ORIGIN);if(origin==null)return;
        cir.setReturnValue(PlacedFoodVariants.drops(params.getLevel(),BlockPos.containing(origin),cir.getReturnValue(),
                com.chi__you.cuisinejournal.tracking.KaleidoscopeServings.plate(state)));
    }
}
