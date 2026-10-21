package com.chi__you.cuisinejournal.mixin.client;

import com.chi__you.cuisinejournal.client.journal.JournalSkillsNavigation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Forge's screen-opening event skips null destinations, including Escape to the game. */
@Mixin(Minecraft.class)
public abstract class JournalSkillsReturnMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen cuisinejournal$returnToJournal(Screen next) { return JournalSkillsNavigation.destination(next); }
}
