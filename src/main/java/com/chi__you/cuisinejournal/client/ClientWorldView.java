package com.chi__you.cuisinejournal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

/** Keeps ClientLevel and LocalPlayer verification inside a client-only class. */
public final class ClientWorldView {
    private ClientWorldView() {}

    public static Level level() {
        return Minecraft.getInstance().level;
    }

    public static Player player() {
        return Minecraft.getInstance().player;
    }
}
