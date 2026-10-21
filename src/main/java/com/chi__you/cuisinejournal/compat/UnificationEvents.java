package com.chi__you.cuisinejournal.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "cuisinejournal")
public final class UnificationEvents {
    private UnificationEvents() {}
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void output(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)
            item.setItem(UnificationRuntime.interactionOutput(item.getItem()));
    }
    @SubscribeEvent
    public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) UnificationRuntime.clearInteractions();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void drops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) return;
        var plan = UnificationRuntime.plan(event.getEntity().getServer()); if (!plan.enabled()) return;
        var entity = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        event.getDrops().removeIf(drop -> UnificationRuntime.suppressedDrop(
                BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()), entity, plan));
        event.getDrops().forEach(drop -> drop.setItem(UnificationRuntime.replace(drop.getItem(), plan)));
    }
    @SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) { UnificationRuntime.clear(); }
}
