package com.chi__you.cuisinejournal.client.compat;

import com.chi__you.cuisinejournal.mixin.client.UnificationCreativeTabsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "cuisinejournal", value = Dist.CLIENT)
public final class UnificationDisplayRefresh {
    private static int pending;
    private UnificationDisplayRefresh() {}

    @SubscribeEvent
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) pending = 2;
    }

    @Mod.EventBusSubscriber(modid = "cuisinejournal", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Creative {
        @SubscribeEvent
        public static void build(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
            var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    com.chi__you.cuisinejournal.compat.UnificationRules.HIDDEN);
            var removed = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
            for (var entry : event.getEntries()) if (entry.getKey().is(tag)) removed.add(entry.getKey());
            removed.forEach(stack -> event.getEntries().remove(stack));
        }
    }

    public static void tick() {
        if (pending == 0 || --pending != 0) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        UnificationCreativeTabsAccessor.cuisinejournal$invalidate(null);
        if (net.minecraftforge.fml.ModList.get().isLoaded("jei"))
            CuisineJournalJeiPlugin.refreshUnifiedItems();
    }

    public static void clear() { pending = 0; }
}
