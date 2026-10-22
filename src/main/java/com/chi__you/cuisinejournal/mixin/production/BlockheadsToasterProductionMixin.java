package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.BlockheadsKitchen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.blay09.mods.cookingforblockheads.tile.ToasterBlockEntity",remap=false)
public abstract class BlockheadsToasterProductionMixin {
    @Shadow private int toastTicks;
    @Inject(method="serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){BlockheadsKitchen.beginTick((BlockEntity)(Object)this);}
    @Inject(method="serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){BlockheadsKitchen.endTick((BlockEntity)(Object)this);}
    @ModifyArg(method="serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/item/ItemEntity;<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V",remap=false),index=4,remap=false)
    private ItemStack cuisinejournal$toast(ItemStack value){return BlockheadsKitchen.toast((BlockEntity)(Object)this,value);}
    @Redirect(method="serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",at=@At(value="FIELD",
            target="Lnet/blay09/mods/cookingforblockheads/tile/ToasterBlockEntity;toastTicks:I",opcode=181),remap=false)
    private void cuisinejournal$progress(@Coerce Object owner,int proposed){
        int value=BlockheadsKitchen.countdown((BlockEntity)owner,proposed);
        toastTicks=value;
    }
}
