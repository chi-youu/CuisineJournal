package com.chi__you.cuisinejournal.mixin.client;

import com.chi__you.cuisinejournal.client.JournalNotification;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.FrameType;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Keeps vanilla rendering and timing while giving journal events their own heading. */
@Mixin(AdvancementToast.class)
public abstract class JournalAdvancementToastMixin {
    @Shadow @Final private Advancement advancement;

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/advancements/FrameType;getDisplayName()Lnet/minecraft/network/chat/Component;"))
    private Component cuisinejournal$heading(FrameType frame) {
        return advancement.getDisplay() instanceof JournalNotification.Display display
                ? display.heading() : frame.getDisplayName();
    }
}
