package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.BlockheadsKitchen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.blay09.mods.cookingforblockheads.tile.OvenBlockEntity",remap=false)
public abstract class BlockheadsOvenProductionMixin {
    @Inject(method="serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){BlockheadsKitchen.beginTick((BlockEntity)(Object)this);}
    @Inject(method="serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){BlockheadsKitchen.endTick((BlockEntity)(Object)this);}
    @Redirect(method="serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",at=@At(value="INVOKE",
            target="Lnet/blay09/mods/balm/api/container/SubContainer;m_6836_(ILnet/minecraft/world/item/ItemStack;)V",remap=false),remap=false)
    private void cuisinejournal$processing(@Coerce Object inventory,int slot,ItemStack stack){BlockheadsKitchen.processingWrite((BlockEntity)(Object)this,inventory,slot,stack);}
}
