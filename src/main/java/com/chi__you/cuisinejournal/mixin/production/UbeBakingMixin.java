package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.UbeBakingProduction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.List;
@Pseudo
@Mixin(targets="com.chefmooon.ubesdelight.common.block.entity.forge.BakingMatBlockEntityImpl",remap=false)
public abstract class UbeBakingMixin {
    @Inject(method="processItemUsingTool",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(ItemStack tool,Player player,CallbackInfoReturnable<Boolean> cir){UbeBakingProduction.begin((BlockEntity)(Object)this);}
    @Inject(method="processItemUsingTool",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(ItemStack tool,Player player,CallbackInfoReturnable<Boolean> cir){UbeBakingProduction.finish((BlockEntity)(Object)this,cir.getReturnValue());}
    @Inject(method="spawnRolledResults",at=@At("HEAD"),remap=false)
    private void cuisinejournal$finalStage(CallbackInfo ci){cuisinejournal$final=true;}
    @Inject(method="spawnRolledResults",at=@At("RETURN"),remap=false)
    private void cuisinejournal$finalEnd(CallbackInfo ci){cuisinejournal$final=false;}
    @Unique private boolean cuisinejournal$final;
    @Inject(method="spawnResults",at=@At("HEAD"),remap=false)
    private void cuisinejournal$output(List<ItemStack> outputs,CallbackInfo ci){if(cuisinejournal$final)UbeBakingProduction.results((BlockEntity)(Object)this,outputs);}
    @Inject(method="removeItem",at=@At("RETURN"),remap=false)
    private void cuisinejournal$remove(CallbackInfoReturnable<ItemStack> cir){UbeBakingProduction.clearOnManualRemoval((BlockEntity)(Object)this);}
}
