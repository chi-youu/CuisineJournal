package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CampfireBlockEntity.class)
public abstract class ProductionCampfireMixin {
    @Inject(method="cookTick",at=@At("HEAD"))
    private static void cuisinejournal$begin(Level l,BlockPos p,BlockState s,CampfireBlockEntity be,CallbackInfo ci){ProductionTracker.beginCooking(be);}
    @ModifyArg(method="cookTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),index=4)
    private static ItemStack cuisinejournal$drop(ItemStack stack){ProductionTracker.emitted(stack);return stack;}
    @Inject(method="cookTick",at=@At("RETURN"))
    private static void cuisinejournal$end(Level l,BlockPos p,BlockState s,CampfireBlockEntity be,CallbackInfo ci){ProductionTracker.endCooking(be);}
}
