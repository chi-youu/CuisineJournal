package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.CosmopolitanTubProduction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.gumillea.cosmopolitan.core.misc.recipes.TubInteractingRecipe",remap=false)
public abstract class CosmopolitanTubInteractMixin {
    @Unique private static final com.chi__you.cuisinejournal.tracking.ProductionFrames<java.util.Deque<java.util.Map<ItemEntity,net.minecraft.world.item.ItemStack>>> cuisinejournal$items=com.chi__you.cuisinejournal.tracking.ProductionFrames.stack();
    @Inject(method="tryApply",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,@Coerce BlockEntity be,BlockPos pos,BlockState state,CallbackInfoReturnable<net.minecraftforge.fluids.FluidStack> cir){
        var inputs=new java.util.IdentityHashMap<ItemEntity,net.minecraft.world.item.ItemStack>();
        if(!level.isClientSide)for(var entity:level.getEntitiesOfClass(ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(.5)))inputs.put(entity,entity.getItem().copy());
        cuisinejournal$items.get().push(inputs);CosmopolitanTubProduction.begin(be,net.minecraft.world.item.ItemStack.EMPTY,null);
    }
    @Inject(method="tryApply",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,@Coerce BlockEntity be,BlockPos pos,BlockState state,CallbackInfoReturnable<net.minecraftforge.fluids.FluidStack> cir){
        var frames=cuisinejournal$items.get();if(frames.isEmpty())return;var inputs=frames.pop();if(frames.isEmpty())cuisinejournal$items.remove();
        var consumed=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        inputs.forEach((entity,before)->{int lost=before.getCount()-(net.minecraft.world.item.ItemStack.isSameItem(before,entity.getItem())?entity.getItem().getCount():0);
            if(lost>0)consumed.add(before.copyWithCount(lost));});CosmopolitanTubProduction.transformed(be,consumed);
    }
}
