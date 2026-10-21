package com.chi__you.cuisinejournal.mixin.speed;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo
@Mixin(targets="umpaz.brewinandchewin.common.block.UnripeCheeseWheelBlock",remap=false)
public abstract class KaleidoscopeBrewinCheeseMixin {
    @Redirect(method="m_213898_",at=@At(value="INVOKE",target="Lnet/minecraft/util/RandomSource;m_188501_()F"),remap=false)
    private float cuisinejournal$chance(RandomSource random,BlockState state,ServerLevel level,BlockPos pos,RandomSource supplied){
        double extra=0;for(var player:level.players())if(player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64)
            extra=Math.max(extra,com.chi__you.cuisinejournal.progression.CookingSpeed.playerExtra(player,
                    com.chi__you.cuisinejournal.recipe.CookingMethod.of("brewinandchewin","fermenting")));
        return (float)(random.nextFloat()/(1+extra));
    }
}
