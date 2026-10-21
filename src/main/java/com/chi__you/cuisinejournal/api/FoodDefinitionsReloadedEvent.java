package com.chi__you.cuisinejournal.api;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.Event;

/** Posted after food datapack definitions are ready, including initial startup. */
public final class FoodDefinitionsReloadedEvent extends Event {
    private final MinecraftServer server;

    public FoodDefinitionsReloadedEvent(MinecraftServer server) { this.server = server; }
    public MinecraftServer getServer() { return server; }
}
