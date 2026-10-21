package com.chi__you.cuisinejournal.mixin.food;
import com.chi__you.cuisinejournal.food.KaleidoscopeLunchBag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopecookery.item.TransmutationLunchBagItem",remap=false)
public abstract class KaleidoscopeLunchBagMixin {
    @Inject(method="m_5922_",at=@At("HEAD"),cancellable=true,remap=false)
    private void cuisinejournal$finish(ItemStack bag,Level level,LivingEntity entity,CallbackInfoReturnable<ItemStack> cir){
        if(KaleidoscopeLunchBag.finish(bag,level,entity))cir.setReturnValue(bag);
    }
    @Inject(method={"canAdd","canConsume"},at=@At("HEAD"),cancellable=true,remap=false)
    private static void cuisinejournal$store(ItemStack stack,CallbackInfoReturnable<Boolean> cir){
        if(KaleidoscopeLunchBag.canStore(stack))cir.setReturnValue(true);
    }
}
