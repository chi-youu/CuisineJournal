package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.YoukaiCookingProduction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.cooking.core.CookingBlockEntity",remap=false)
public abstract class YoukaiCookingProductionMixin {
    @Inject(method="tryAddItem",at=@At("HEAD"),remap=false)
    private void cuisinejournal$inputBegin(ItemStack stack,boolean simulate,CallbackInfoReturnable<Boolean> cir){if(!simulate)YoukaiCookingProduction.inputBefore((BlockEntity)(Object)this);}
    @Inject(method="tryAddItem",at=@At("RETURN"),remap=false)
    private void cuisinejournal$inputEnd(ItemStack stack,boolean simulate,CallbackInfoReturnable<Boolean> cir){if(!simulate)YoukaiCookingProduction.inputAfter((BlockEntity)(Object)this,stack,cir.getReturnValue());}
    @Inject(method="finishRecipe(Lnet/minecraft/world/level/Level;Ldev/xkmc/youkaishomecoming/content/pot/cooking/core/PotCookingRecipe;)V",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){YoukaiCookingProduction.begin((BlockEntity)(Object)this,"youkaishomecoming:pot_cooking");}
    @Inject(method="finishRecipe(Lnet/minecraft/world/level/Level;Ldev/xkmc/youkaishomecoming/content/pot/cooking/core/PotCookingRecipe;)V",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){YoukaiCookingProduction.end((BlockEntity)(Object)this);}
    @ModifyArg(method="finishRecipe(Lnet/minecraft/world/level/Level;Ldev/xkmc/youkaishomecoming/content/pot/cooking/core/PotCookingRecipe;)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;m_46597_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",remap=false),index=1,remap=false)
    private BlockState cuisinejournal$placed(BlockState state){YoukaiCookingProduction.placed((BlockEntity)(Object)this,state);return state;}
    @ModifyArg(method="finishRecipe(Lnet/minecraft/world/level/Level;Ldev/xkmc/youkaishomecoming/content/pot/cooking/core/PotCookingRecipe;)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;m_49840_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",remap=false),index=2,remap=false)
    private ItemStack cuisinejournal$drop(ItemStack stack){return YoukaiCookingProduction.output(stack);}
}
