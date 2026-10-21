package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.tracking.InPlaceProductionTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.steamer.RackData",remap=false)
public abstract class YoukaiSteamRackMixin {
    @Unique private ItemStack cuisinejournal$before=ItemStack.EMPTY;
    @Unique private BlockEntity cuisinejournal$be;
    @Unique private int cuisinejournal$slot;
    @Unique private ServerPlayer cuisinejournal$player;
    @Inject(method="tryAddItemAt(Ldev/xkmc/youkaishomecoming/content/pot/steamer/SteamerBlockEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/player/Player;)Z",at=@At("HEAD"),remap=false)
    private void cuisinejournal$insertBefore(@Coerce Object be,Level level,ItemStack stack,int slot,Player player,CallbackInfoReturnable<Boolean> cir) {
        Object[] items=(Object[])HostReflection.field(this,"list");
        Object data=slot>=0&&slot<items.length?items[slot]:null;
        Object old=data==null?null:HostReflection.field(data,"stack");cuisinejournal$before=old instanceof ItemStack item?item.copy():ItemStack.EMPTY;
    }
    @Inject(method="tryAddItemAt(Ldev/xkmc/youkaishomecoming/content/pot/steamer/SteamerBlockEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/player/Player;)Z",at=@At("RETURN"),remap=false)
    private void cuisinejournal$insertAfter(@Coerce Object be,Level level,ItemStack stack,int slot,Player player,CallbackInfoReturnable<Boolean> cir) {
        if(!cir.getReturnValue()||level.isClientSide)return;
        Object[] items=(Object[])HostReflection.field(this,"list");
        InPlaceProductionTracker.inserted(items[slot],(BlockEntity)be,cuisinejournal$before,player instanceof ServerPlayer server?server:null);
    }
    @Inject(method="tryTakeItemAt(Ldev/xkmc/youkaishomecoming/content/pot/steamer/SteamerBlockEntity;Lnet/minecraft/world/level/Level;ILnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Z",at=@At("HEAD"),remap=false)
    private void cuisinejournal$takeBegin(@Coerce Object be,Level level,int slot,Player player,InteractionHand hand,CallbackInfoReturnable<Boolean> cir){cuisinejournal$be=(BlockEntity)be;cuisinejournal$slot=slot;cuisinejournal$player=player instanceof ServerPlayer server?server:null;}
    @ModifyArg(method="tryTakeItemAt(Ldev/xkmc/youkaishomecoming/content/pot/steamer/SteamerBlockEntity;Lnet/minecraft/world/level/Level;ILnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Z",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Inventory;m_150079_(Lnet/minecraft/world/item/ItemStack;)V",remap=false),index=0,remap=false)
    private ItemStack cuisinejournal$claim(ItemStack item){return InPlaceProductionTracker.claim(cuisinejournal$be,this,cuisinejournal$slot,cuisinejournal$player,item);}
}
