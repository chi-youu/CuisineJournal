package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.PressingTubBlockEntity",remap=false)
public abstract class KaleidoscopePressMixin {
    @Unique private ItemStack cuisinejournal$fruit=ItemStack.EMPTY;
    @Inject(method="press",at=@At("HEAD"),remap=false)
    private void cuisinejournal$before(LivingEntity user,float distance,CallbackInfoReturnable<Boolean> cir){
        Object raw=HostReflection.field(this,"items");
        if(raw instanceof net.minecraftforge.items.IItemHandler inventory)cuisinejournal$fruit=inventory.getStackInSlot(0).copyWithCount(1);
    }
    @Inject(method="press",at=@At("RETURN"),remap=false)
    private void cuisinejournal$after(LivingEntity user,float distance,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValue())KaleidoscopeProduction.fluidSource((BlockEntity)(Object)this,cuisinejournal$fruit);
    }
}
