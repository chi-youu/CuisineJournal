package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.CosmopolitanTubProduction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.gumillea.cosmopolitan.core.misc.recipes.TubInjectRecipe",remap=false)
public abstract class CosmopolitanTubInjectMixin {
    @Inject(method="tryApply",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,@Coerce BlockEntity be,ItemStack held,Player player,InteractionHand hand,CallbackInfoReturnable<ItemStack> cir){CosmopolitanTubProduction.begin(be,held,player);}
    @Inject(method="tryApply",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,@Coerce BlockEntity be,ItemStack held,Player player,InteractionHand hand,CallbackInfoReturnable<ItemStack> cir){CosmopolitanTubProduction.added(be);}
}
