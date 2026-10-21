package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
@Pseudo
@Mixin(targets="net.satisfy.camping.core.world.block.entity.GrillBlockEntity",remap=false)
public abstract class LetsDoGrillMixin {
    @Inject(method="cookTick",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,BlockPos pos,BlockState state,@Coerce Object be,CallbackInfo ci){ProductionTracker.beginCooking((BlockEntity)be);}
    @Inject(method="cookTick",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,BlockPos pos,BlockState state,@Coerce Object be,CallbackInfo ci){ProductionTracker.endCooking((BlockEntity)be);}
    @ModifyArg(method="cookTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/Containers;m_18992_(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),index=4,remap=false)
    private static ItemStack cuisinejournal$output(ItemStack stack){ProductionTracker.emitted(stack);return stack;}
}
