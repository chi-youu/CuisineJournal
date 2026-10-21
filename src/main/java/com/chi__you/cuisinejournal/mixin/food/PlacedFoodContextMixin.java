package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.food.PlacedFoodConsumption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class PlacedFoodContextMixin {
    @Redirect(method="use",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult cuisinejournal$placedFood(Block block,BlockState state,Level level,BlockPos pos,
                                                        Player player,InteractionHand hand,BlockHitResult hit) {
        return PlacedFoodConsumption.use(block,state,level,pos,player,hand,hit);
    }
}
