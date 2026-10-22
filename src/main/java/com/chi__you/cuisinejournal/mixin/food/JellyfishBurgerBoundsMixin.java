package com.chi__you.cuisinejournal.mixin.food;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="club.someoneice.jellyfishingdelight.block.KrabbyPatty",remap=false)
public abstract class JellyfishBurgerBoundsMixin {
    @Inject(method="onBiteOrCut",at=@At("HEAD"),cancellable=true,remap=false)
    private static void cuisinejournal$lastBite(Level level,BlockState state,BlockPos pos,CallbackInfo ci){
        var property=state.getBlock().getStateDefinition().getProperty("bites");
        if(property instanceof IntegerProperty bites&&state.getValue(bites)>=3){level.removeBlock(pos,false);ci.cancel();}
    }
}
