package com.chi__you.cuisinejournal.mixin.food;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Parfaits report isEdible=false; their direct per-bite nutrition must use stack quality once. */
@Pseudo
@Mixin(targets="com.gumillea.exquisito.common.item.ParfaitItem",remap=false)
public abstract class ExquisitoParfaitMixin {
    @Inject(method="m_7203_",at=@At("HEAD"),cancellable=true,remap=false)
    private void cuisinejournal$preflight(Level level,net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<
            net.minecraft.world.InteractionResultHolder<ItemStack>> cir) {
        if(!com.chi__you.cuisinejournal.food.CuisineFoodRuntime.activeForCurrentThread())return;
        ItemStack stack=player.getItemInHand(hand);
        boolean allowed=level.isClientSide?com.chi__you.cuisinejournal.food.CuisineFoodClientState.canConsume(stack)
                :player instanceof net.minecraft.server.level.ServerPlayer server
                &&com.chi__you.cuisinejournal.food.CuisineFoodService.canConsume(server,stack);
        if(!allowed)cir.setReturnValue(net.minecraft.world.InteractionResultHolder.fail(stack));
    }

    @Redirect(method="giveEffect",at=@At(value="INVOKE",target="Lnet/minecraft/world/food/FoodData;m_38707_(IF)V",remap=false),remap=false)
    private void cuisinejournal$singleNutrition(FoodData food,int nutrition,float saturation,ItemStack stack,Level level,LivingEntity entity){
        var properties=com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.itemProperties(stack,entity);
        if(properties!=null)com.chi__you.cuisinejournal.food.CuisineVanillaBonuses.itemApplication(
                ()->food.eat(properties.getNutrition(),properties.getSaturationModifier()));
    }
}
