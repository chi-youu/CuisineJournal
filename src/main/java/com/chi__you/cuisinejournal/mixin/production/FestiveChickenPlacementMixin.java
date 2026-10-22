package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.tracking.PlacedMealProduction;
import com.chi__you.foodinteractions.FestiveServings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="net.toopa.festivedelight.procedures.FestiveChickenPlaceProcedure",remap=false)
public abstract class FestiveChickenPlacementMixin {
    @Shadow private static void execute(Event event,LevelAccessor world,double x,double y,double z,Entity entity){throw new AssertionError();}
    @Redirect(method="onRightClickBlock",at=@At(value="INVOKE",target="Lnet/toopa/festivedelight/procedures/FestiveChickenPlaceProcedure;execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V"),remap=false)
    private static void cuisinejournal$place(Event event,LevelAccessor world,double x,double y,double z,Entity entity){
        ItemStack source=entity instanceof LivingEntity living?living.getMainHandItem().copy():ItemStack.EMPTY;
        var pos=BlockPos.containing(x,y+1,z);var before=world.getBlockState(pos);
        execute(event,world,x,y,z,entity);
        if(world instanceof Level level&&!level.isClientSide&&FestiveServings.whole().equals(BuiltInRegistries.ITEM.getKey(source.getItem()))){
            var after=world.getBlockState(pos);
            if(!after.equals(before)&&FestiveServings.remaining(BuiltInRegistries.BLOCK.getKey(after.getBlock()))==4)
                PlacedMealProduction.storeStamped(level,pos,after,source,4);
        }
    }
}
