package com.chi__you.cuisinejournal.client;

import com.chi__you.cuisinejournal.client.journal.JournalText;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.Map;

/** Display-only advancement data, never registered or awarded to a player. */
public final class JournalNotification {
    private JournalNotification() {}

    public static Advancement advancement(Packets.Notify notify) {
        Component event = switch (notify.kind()) {
            case DISCOVERY -> Component.translatable("cuisinejournal.notify.discovery");
            case MASTERY_RANK -> Component.translatable("cuisinejournal.notify.mastery");
            case MASTERED -> Component.translatable("cuisinejournal.notify.mastered");
            case LEVEL_UP -> Component.translatable("cuisinejournal.notify.level_up", notify.amount());
            case CHALLENGE -> Component.translatable("cuisinejournal.notify.challenge");
            case MILESTONE -> Component.translatable("cuisinejournal.notify.milestone");
            case XP -> Component.translatable("cuisinejournal.notify.xp", notify.amount());
        };
        Component title = notify.subject() == null ? event
                : Component.translatable("cuisinejournal.notify.with_subject", event, JournalText.nameOf(notify.subject()));
        ItemStack icon = notify.subject() == null ? ItemStack.EMPTY : JournalText.stackOf(notify.subject());
        if (icon.isEmpty()) icon = new ItemStack(Items.BOOK);
        // TASK avoids the challenge fanfare, so the existing sound switch remains authoritative.
        DisplayInfo display = new DisplayInfo(icon, title, event, null, FrameType.TASK, true, false, true);
        return new Advancement(ResourceLocation.parse("cuisinejournal:notification/"
                + notify.kind().name().toLowerCase(Locale.ROOT)),
                null, display, AdvancementRewards.EMPTY, Map.of(), new String[0][], false);
    }
}
