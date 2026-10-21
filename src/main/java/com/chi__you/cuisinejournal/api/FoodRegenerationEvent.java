package com.chi__you.cuisinejournal.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Server-side food regeneration pulse, independent of other healing sources. */
@Cancelable
public final class FoodRegenerationEvent extends Event {
    private final ServerPlayer player;
    private float amount;
    private boolean allowed;
    private final boolean temporaryFood;

    public FoodRegenerationEvent(ServerPlayer player, float amount, boolean allowed) {
        this(player, amount, allowed, false);
    }

    public FoodRegenerationEvent(ServerPlayer player, float amount, boolean allowed, boolean temporaryFood) {
        this.player = player;
        this.amount = amount;
        this.allowed = allowed;
        this.temporaryFood = temporaryFood;
    }

    public ServerPlayer getPlayer() { return player; }
    public float getAmount() { return amount; }
    public boolean isAllowed() { return allowed; }
    public boolean isTemporaryFood() { return temporaryFood; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    public void setAmount(float amount) {
        this.amount = Float.isFinite(amount) ? Math.max(0.0F, amount) : 0.0F;
    }
}
