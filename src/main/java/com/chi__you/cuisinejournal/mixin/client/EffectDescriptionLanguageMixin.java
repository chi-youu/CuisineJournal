package com.chi__you.cuisinejournal.mixin.client;

import com.chi__you.cuisinejournal.food.CuisineEffectAdapters;
import com.chi__you.cuisinejournal.food.CuisineFoodClientState;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLanguage.class)
public abstract class EffectDescriptionLanguageMixin {
    private static final ThreadLocal<Boolean> CUISINEJOURNAL_TRANSLATING = ThreadLocal.withInitial(() -> false);
    @Inject(method="getOrDefault",at=@At("HEAD"),cancellable=true)
    private void cuisinejournal$adaptDescription(String key,String fallback,CallbackInfoReturnable<String> cir) {
        if (CUISINEJOURNAL_TRANSLATING.get()) return;
        String adapted=CuisineEffectAdapters.translatedDescriptionKey(key,CuisineFoodClientState.active());
        if(adapted!=null && !adapted.equals(key)) {
            CUISINEJOURNAL_TRANSLATING.set(true);
            try { cir.setReturnValue(((ClientLanguage)(Object)this).getOrDefault(adapted,fallback)); }
            finally { CUISINEJOURNAL_TRANSLATING.remove(); }
        }
    }
}
