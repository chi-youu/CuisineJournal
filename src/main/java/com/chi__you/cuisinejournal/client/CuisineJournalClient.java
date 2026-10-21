package com.chi__you.cuisinejournal.client;

import com.chi__you.cuisinejournal.client.hud.CookingExperienceHud;
import com.chi__you.cuisinejournal.client.journal.JournalScreen;
import com.chi__you.cuisinejournal.client.journal.JournalText;
import com.chi__you.cuisinejournal.config.ClientConfig;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Client-side bootstrap: the journal keybind, the per-tick pump, and the
 * notification queue.
 *
 * <p>Notifications are aggregated and rate limited before they reach the toast
 * system: a burst of small XP gains becomes one "+12 Cooking XP" rather than
 * six separate popups, and no more toasts are shown per second than the
 * player allows.</p>
 */
public final class CuisineJournalClient {

    private static long pendingXp;
    private static int xpTimer;
    private static long lastToastTick;
    private static long clientTick;

    private CuisineJournalClient() {
    }

    public static void initialize() {
        com.chi__you.cuisinejournal.food.ClientFoodRuleView.install(new JournalFoodRuleView());
        ConfigManager.initialize(Services.PLATFORM.configDirectory());
    }

    /** Called once per client tick from the loader bridge. */
    public static void tick() {
        com.chi__you.cuisinejournal.forge.ForgeNetwork.clientTick();
        ClientJournalState.tickSync();
        com.chi__you.cuisinejournal.client.compat.UnificationDisplayRefresh.tick();
        clientTick++;
        if(!Minecraft.getInstance().isPaused())ClientJournalState.tickChallenges();
        CookingExperienceHud.tick();

        if (ClientPacketHandler.consumeOpenRequest()) {
            openJournal();
        }
        drainNotifications();
        flushXpPopup();
    }

    public static void openJournal() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !(minecraft.screen instanceof JournalScreen)) {
            ClientJournalState.beginJournalSync();
            minecraft.setScreen(new JournalScreen());
        }
    }

    public static void onDisconnect() {
        com.chi__you.cuisinejournal.forge.ForgeNetwork.clearClientTransfers();
        // A journal from one world must never leak into the next.
        ClientJournalState.clear();
        pendingXp = 0L;
        xpTimer = 0;
    }

    private static void drainNotifications() {
        ClientConfig config = ConfigManager.client();
        Packets.Notify notify;
        while ((notify = ClientJournalState.pollNotification()) != null) {
            if (notify.kind() == Packets.Notify.Kind.XP) {
                // Accumulate rather than showing each gain individually.
                pendingXp += notify.amount();
                xpTimer = config.notifications.xpPopupAggregationTicks;
                continue;
            }
            if (!allowed(config, notify.kind())) {
                continue;
            }
            long minimumGap = Math.max(1L, 20L / Math.max(1, config.notifications.maxToastsPerSecond));
            if (clientTick - lastToastTick < minimumGap) {
                // Dropped rather than queued: a backlog of stale toasts is worse
                // than missing one.
                continue;
            }
            lastToastTick = clientTick;
            showToast(notify, config);
        }
    }

    private static void flushXpPopup() {
        if (pendingXp <= 0L) {
            return;
        }
        if (--xpTimer > 0) {
            return;
        }
        ClientConfig config = ConfigManager.client();
        if (config.notifications.xpPopups && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("cuisinejournal.notify.xp", JournalText.number(pendingXp))
                            .withStyle(ChatFormatting.GOLD), true);
        }
        pendingXp = 0L;
    }

    private static boolean allowed(ClientConfig config, Packets.Notify.Kind kind) {
        return switch (kind) {
            case DISCOVERY -> config.notifications.discoveries;
            case MASTERY_RANK, MASTERED -> config.notifications.masteryRanks;
            case LEVEL_UP -> config.notifications.levelUps;
            case CHALLENGE -> config.notifications.challenges;
            case MILESTONE -> config.notifications.milestones;
            case XP -> config.notifications.xpPopups;
        };
    }

    private static void showToast(Packets.Notify notify, ClientConfig config) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getToasts().addToast(new AdvancementToast(JournalNotification.advancement(notify)));

        if (config.notifications.playSounds && minecraft.player != null) {
            // An existing vanilla sound; no custom audio asset.
            minecraft.player.playSound(
                    notify.kind() == Packets.Notify.Kind.MASTERED
                            ? SoundEvents.PLAYER_LEVELUP
                            : SoundEvents.NOTE_BLOCK_BELL.value(),
                    0.5F, 1.2F);
        }
    }

}
