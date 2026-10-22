package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.food.PlacedFoodConsumption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="net.toopa.festivedelight.procedures.ChickenEatProcedure",remap=false)
public abstract class FestiveChickenServingMixin {
    @Shadow private static void execute(Event event,LevelAccessor world,double x,double y,double z,Entity entity){throw new AssertionError();}
    @Redirect(method="onRightClickBlock",at=@At(value="INVOKE",target="Lnet/toopa/festivedelight/procedures/ChickenEatProcedure;execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V"),remap=false)
    private static void cuisinejournal$serve(Event event,LevelAccessor world,double x,double y,double z,Entity entity){
        if(world instanceof Level level)PlacedFoodConsumption.interaction(level,BlockPos.containing(x,y,z),()->execute(event,world,x,y,z,entity));
        else execute(event,world,x,y,z,entity);
    }
}
