package com.chi__you.cuisinejournal.mixin.client;

import com.chi__you.cuisinejournal.client.JournalNotification;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Optional announcement bridge for FancyToasts; ordinary advancements keep their own heading. */
@Pseudo
@Mixin(targets = "net.bivrik.fancytoasts.platform.utility.ToastDisplayInfo", remap = false)
public abstract class FancyJournalToastMixin {
    @Unique private Component cuisinejournal$heading;

    @Inject(method = "<init>(Lnet/minecraft/advancements/DisplayInfo;)V", at = @At("RETURN"), remap = false)
    private void cuisinejournal$capture(DisplayInfo info, CallbackInfo ci) {
        if (info instanceof JournalNotification.Display display) cuisinejournal$heading = display.heading();
    }

    @Inject(method = "getAnnouncement", at = @At("HEAD"), cancellable = true, remap = false)
    private void cuisinejournal$announcement(CallbackInfoReturnable<Component> cir) {
        if (cuisinejournal$heading != null) cir.setReturnValue(cuisinejournal$heading);
    }
}
