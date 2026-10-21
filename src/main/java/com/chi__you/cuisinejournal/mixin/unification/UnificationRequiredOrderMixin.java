package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.UnificationPacks;
import net.minecraft.server.packs.repository.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

@Mixin(PackRepository.class)
public abstract class UnificationRequiredOrderMixin {
    @Inject(method="rebuildSelected",at=@At("RETURN"),cancellable=true)
    private void cuisinejournal$priority(Collection<String> requested,CallbackInfoReturnable<List<Pack>> cir) {
        cir.setReturnValue(UnificationPacks.requiredOrder(requested,cir.getReturnValue()));
    }
}
