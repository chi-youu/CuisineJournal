package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="club.someoneice.jellyfishingdelight.block.GrillTile",remap=false)
public abstract class JellyfishGrillProductionMixin {
    @Inject(method="cookingTick",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$begin(Level level,BlockPos pos,BlockState state,@Coerce Object tile,CallbackInfo ci){ProductionTracker.beginCooking((BlockEntity)tile);}
    @Inject(method="cookingTick",at=@At("RETURN"),remap=false)
    private static void cuisinejournal$end(Level level,BlockPos pos,BlockState state,@Coerce Object tile,CallbackInfo ci){ProductionTracker.endCooking((BlockEntity)tile);}
    @ModifyArg(method="lambda$cookingTick$1",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/item/ItemEntity;<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),index=4,remap=false)
    private static ItemStack cuisinejournal$output(ItemStack stack){ProductionTracker.emitted(stack);return stack;}
    @Inject(method="lambda$cookingTick$1",at=@At(value="FIELD",target="Lclub/someoneice/jellyfishingdelight/block/GrillTile;cookingTimes:[I",ordinal=0),remap=false)
    private static void cuisinejournal$progress(Level level,@Coerce Object tile,boolean submerged,int slot,CallbackInfo ci){
        int[] progress=(int[])HostReflection.field(tile,"cookingTimes"),total=(int[])HostReflection.field(tile,"cookingTimesTotal");
        progress[slot]=CookingSpeed.advance((BlockEntity)tile,"jellyfish:"+slot,progress[slot],progress[slot]+1,total[slot])-1;
    }
}
