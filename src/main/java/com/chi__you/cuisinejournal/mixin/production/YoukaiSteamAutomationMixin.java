package com.chi__you.cuisinejournal.mixin.production;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.tracking.InPlaceProductionTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="dev.xkmc.youkaishomecoming.content.pot.steamer.SteamerItemHandler",remap=false)
public abstract class YoukaiSteamAutomationMixin {
    @Unique private ItemStack cuisinejournal$before;
    @Inject(method="insertItem",at=@At("HEAD"),remap=false)
    private void cuisinejournal$before(int slot,ItemStack item,boolean simulate,CallbackInfoReturnable<ItemStack> cir) {
        Object rack=HostReflection.invoke(this,"rack");Object[] entries=(Object[])HostReflection.field(rack,"list");
        Object old=slot>=0&&slot<entries.length&&entries[slot]!=null?HostReflection.field(entries[slot],"stack"):null;
        cuisinejournal$before=old instanceof ItemStack stack?stack.copy():ItemStack.EMPTY;
    }
    @Inject(method="insertItem",at=@At("RETURN"),remap=false)
    private void cuisinejournal$insert(int slot,ItemStack item,boolean simulate,CallbackInfoReturnable<ItemStack> cir) {
        if(simulate||slot<0||slot>=4||cir.getReturnValue().getCount()>=item.getCount())return;
        Object rack=HostReflection.invoke(this,"rack");Object[] entries=(Object[])HostReflection.field(rack,"list");
        InPlaceProductionTracker.inserted(entries[slot],(BlockEntity)HostReflection.invoke(this,"be"),cuisinejournal$before,null);
    }
    @Inject(method="extractItem",at=@At("RETURN"),remap=false)
    private void cuisinejournal$take(int slot,int amount,boolean simulate,CallbackInfoReturnable<ItemStack> cir) {
        if(simulate||cir.getReturnValue().isEmpty())return;
        Object rack=HostReflection.invoke(this,"rack");BlockEntity be=(BlockEntity)HostReflection.invoke(this,"be");
        InPlaceProductionTracker.claim(be,rack,slot,null,cir.getReturnValue());InPlaceProductionTracker.withdrawn(be,rack,slot);
    }
}
