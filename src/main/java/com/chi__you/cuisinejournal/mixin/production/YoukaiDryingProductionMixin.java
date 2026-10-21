package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackBlockEntity",remap=false)
public abstract class YoukaiDryingProductionMixin {
    @Inject(method="cookTick()V",at=@At("HEAD"),remap=false)
    private void cuisinejournal$begin(CallbackInfo ci){ProductionTracker.beginCooking((BlockEntity)(Object)this);}
    @Inject(method="cookTick()V",at=@At("RETURN"),remap=false)
    private void cuisinejournal$end(CallbackInfo ci){ProductionTracker.endCooking((BlockEntity)(Object)this);}
    @ModifyArg(method="cookTick()V",at=@At(value="INVOKE",target="Lnet/minecraft/world/Containers;m_18992_(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V",remap=false),index=4,remap=false)
    private ItemStack cuisinejournal$drop(ItemStack stack){ProductionTracker.emitted(stack);return stack;}
}
