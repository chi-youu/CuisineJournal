package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets="com.soytutta.mynethersdelight.common.block.entity.BlazierBlockEntity",remap=false)
public abstract class BlazierProductionMixin {
    @Inject(method="cookTick",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,BlockPos pos,BlockState state,@Coerce Object owner,CallbackInfo ci){
        var be=(BlockEntity)owner;ProductionTracker.beginBlazier(be);CookingSpeed.array(be,"cookingProgress","cookingTime",1);
    }
    @ModifyArg(method="cookTick",at=@At(value="INVOKE",target="Lnet/minecraft/world/Containers;m_18992_(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V",remap=false),index=4,remap=false)
    private static ItemStack cuisinejournal$output(ItemStack stack){ProductionTracker.emitted(stack);return stack;}
    @Inject(method="cookTick",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,BlockPos pos,BlockState state,@Coerce Object owner,CallbackInfo ci){ProductionTracker.endCooking((BlockEntity)owner);}
}
