package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.PlacedMealProduction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.InteractionResult;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.block.food.PotFoodBlock",remap=false)
public abstract class YoukaiPotServingProductionMixin {
    @Unique private Level cuisinejournal$level;
    @Unique private BlockPos cuisinejournal$pos;
    @Unique private ServerPlayer cuisinejournal$player;
    @Inject(method="m_6227_",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit,CallbackInfoReturnable<InteractionResult> cir){cuisinejournal$level=level;cuisinejournal$pos=pos;cuisinejournal$player=player instanceof ServerPlayer server?server:null;}
    @ModifyArg(method="m_6227_",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Inventory;m_150079_(Lnet/minecraft/world/item/ItemStack;)V",remap=false),index=0,remap=false)
    private ItemStack cuisinejournal$claim(ItemStack item){if(cuisinejournal$player!=null)PlacedMealProduction.claim(cuisinejournal$level,cuisinejournal$pos,cuisinejournal$player,item,true);return item;}
}
