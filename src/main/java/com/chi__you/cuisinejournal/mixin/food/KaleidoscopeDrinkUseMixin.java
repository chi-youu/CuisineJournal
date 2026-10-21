package com.chi__you.cuisinejournal.mixin.food;
import com.chi__you.cuisinejournal.food.KaleidoscopeFood;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets={"com.github.ysbbbbbb.kaleidoscopetavern.item.DrinkBlockItem",
        "com.github.ysbbbbbb.kaleidoscopetavern.item.CocktailBlockItem",
        "com.github.ysbbbbbb.kaleidoscopecookery.item.TeacupItem"},remap=false)
public abstract class KaleidoscopeDrinkUseMixin {
    @Inject(method="m_7203_",at=@At("HEAD"),cancellable=true,remap=false)
    private void cuisinejournal$use(Level level,Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir){
        ItemStack stack=player.getItemInHand(hand);if(!KaleidoscopeFood.useAllowed(player,stack))cir.setReturnValue(InteractionResultHolder.fail(stack));
    }
}
