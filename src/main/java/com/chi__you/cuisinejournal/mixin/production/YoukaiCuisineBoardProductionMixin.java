package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.YoukaiCookingProduction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.table.board.CuisineBoardBlockEntity",remap=false)
public abstract class YoukaiCuisineBoardProductionMixin {
    @Unique private ServerPlayer cuisinejournal$player;
    @Unique private ItemStack cuisinejournal$cutSource=ItemStack.EMPTY;
    @Unique private boolean cuisinejournal$cutClaimed;
    @Inject(method="performToolAction",at=@At("HEAD"),remap=false)
    private void cuisinejournal$cutStart(ItemStack tool,CallbackInfoReturnable<Boolean> cir){
        cuisinejournal$cutSource=YoukaiCookingProduction.boardCompleted((BlockEntity)(Object)this);
        cuisinejournal$cutClaimed=false;
    }
    @ModifyArg(method="performToolAction",at=@At(value="INVOKE",target="Lvectorwing/farmersdelight/common/utility/ItemUtils;spawnItemEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;DDDDDD)V",remap=false),index=1,remap=false)
    private ItemStack cuisinejournal$cutOutput(ItemStack result){
        if(!cuisinejournal$cutClaimed){
            YoukaiCookingProduction.boardResult((BlockEntity)(Object)this,com.chi__you.cuisinejournal.tracking.ProductionTracker.actor(),cuisinejournal$cutSource);
            cuisinejournal$cutClaimed=true;
        }
        var stamp=com.chi__you.cuisinejournal.culinary.MealStamp.read(cuisinejournal$cutSource);
        if(stamp!=null)stamp.write(result);
        return result;
    }
    @Inject(method="addItem",at=@At("RETURN"),remap=false)
    private void cuisinejournal$input(ItemStack item,CallbackInfoReturnable<Integer> cir){YoukaiCookingProduction.boardInput((BlockEntity)(Object)this,item,cir.getReturnValue());}
    @Inject(method="addToPlayer",at=@At("HEAD"),remap=false)
    private void cuisinejournal$take(Player player,CallbackInfoReturnable<Boolean> cir){cuisinejournal$player=player instanceof ServerPlayer server?server:null;}
    @ModifyArg(method="addToPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Inventory;m_150079_(Lnet/minecraft/world/item/ItemStack;)V",remap=false),index=0,remap=false)
    private ItemStack cuisinejournal$claim(ItemStack result){return YoukaiCookingProduction.boardResult((BlockEntity)(Object)this,cuisinejournal$player,result);}
    @Inject(method="clear",at=@At("RETURN"),remap=false)
    private void cuisinejournal$clear(CallbackInfo ci){YoukaiCookingProduction.clearBoard((BlockEntity)(Object)this);}
    @Inject(method="addToPlayer",at=@At("RETURN"),remap=false)
    private void cuisinejournal$clearTaken(CallbackInfoReturnable<Boolean> cir){if(cir.getReturnValue())YoukaiCookingProduction.clearBoard((BlockEntity)(Object)this);}
    @Inject(method="performToolAction",at=@At("RETURN"),remap=false)
    private void cuisinejournal$clearCut(CallbackInfoReturnable<Boolean> cir){if(cir.getReturnValue())YoukaiCookingProduction.clearBoard((BlockEntity)(Object)this);cuisinejournal$cutSource=ItemStack.EMPTY;cuisinejournal$cutClaimed=false;}
}
