package com.chi__you.cuisinejournal.client;

import com.chi__you.cuisinejournal.network.CuisineJournalPacket;
import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.food.CuisineFoodSnapshot;
import com.chi__you.cuisinejournal.food.CuisineFoodClientState;
import com.chi__you.cuisinejournal.food.CuisineFoodInfoPackets;
import com.chi__you.cuisinejournal.food.CuisineFoodTooltip;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Routes incoming server packets into {@link ClientJournalState}.
 *
 * <p>Client-only dispatch; the Forge transport checks Dist before invoking it.</p>
 */
public final class ClientPacketHandler {

    /** Set when the server asks the journal to open; the screen layer consumes it. */
    private static final AtomicBoolean OPEN_REQUESTED = new AtomicBoolean();

    private ClientPacketHandler() {
    }

    public static void handle(CuisineJournalPacket packet) {
        if(packet instanceof com.chi__you.cuisinejournal.food.CuisineRegenPackets.Combat combat) {
            com.chi__you.cuisinejournal.client.hud.CuisineRegenHud.acceptCombat(combat.ticksLeft(),combat.intervalTicks());
            return;
        }
        if(packet instanceof com.chi__you.cuisinejournal.network.SyncFailure failure) {
            ClientJournalState.syncFailed(failure.failedPacket());
            return;
        }
        if(packet instanceof com.chi__you.cuisinejournal.skills.SkillPackets.Nutrition nutrition) {
            var player=net.minecraft.client.Minecraft.getInstance().player;
            if(player!=null){player.getFoodData().setFoodLevel(nutrition.food());player.getFoodData().setSaturation(nutrition.saturation());}
            return;
        }
        if(packet instanceof com.chi__you.cuisinejournal.network.JournalFeaturePackets.Sync features){
            ClientJournalState.acceptJournalFeatures(features);
        } else if(packet instanceof com.chi__you.cuisinejournal.skills.SkillPackets.Sync skills) {
            ClientJournalState.acceptSkills(skills);
        } else if (packet instanceof CuisineFoodInfoPackets.Batch info) {
            CuisineFoodTooltip.accept(info);
        } else if (packet instanceof CuisineFoodInfoPackets.HostResponse info) {
            CuisineFoodTooltip.accept(info);
        } else if (packet instanceof CuisineFoodInfoPackets.FruitResponse info) {
            CuisineFoodTooltip.accept(info);
        } else if (packet instanceof CuisineFoodSnapshot food) {
            if (!food.active()) {
                com.chi__you.cuisinejournal.client.hud.CuisineRegenHud.clear();
                CuisineFoodTooltip.clear();
            }
            CuisineFoodClientState.accept(food);
        } else if (packet instanceof Packets.SyncCatalogue catalogue) {
            ClientJournalState.acceptCatalogue(catalogue);
        } else if (packet instanceof com.chi__you.cuisinejournal.network.DishSearchPackets.Sync search) {
            DishSearchState.accept(search);
        } else if (packet instanceof Packets.SyncProfile profile) {
            ClientJournalState.acceptProfile(profile);
        } else if (packet instanceof Packets.SyncRules rules) {
            ClientJournalState.acceptRules(rules);
        } else if(packet instanceof Packets.SyncTodayDish today){
            ClientJournalState.acceptTodayDish(today);
        } else if (packet instanceof com.chi__you.cuisinejournal.network.JournalSyncPackets.TagDetails details) {
            ClientJournalState.acceptTagDetails(details);
        } else if (packet instanceof com.chi__you.cuisinejournal.network.JournalSyncPackets.Directory directory) {
            ClientJournalState.acceptDirectory(directory);
        } else if (packet instanceof com.chi__you.cuisinejournal.network.JournalSyncPackets.Progress progress) {
            ClientJournalState.acceptProgress(progress);
        } else if (packet instanceof Packets.SyncJournalExtras extras) {
            ClientJournalState.acceptExtras(extras);
        } else if (packet instanceof Packets.SyncStatistics statistics) {
            ClientJournalState.acceptStatistics(statistics);
        } else if (packet instanceof Packets.SyncDishDetail detail) {
            ClientJournalState.acceptDetail(detail);
        } else if (packet instanceof Packets.MenuContext context) {
            ClientJournalState.acceptMenuContext(context);
        } else if (packet instanceof Packets.Notify notify) {
            ClientJournalState.queueNotification(notify);
        } else if (packet instanceof Packets.OpenJournal) {
            OPEN_REQUESTED.set(true);
        } else if (packet instanceof Packets.SyncCategoryPermissions permissions) {
            ClientJournalState.acceptCategoryPermissions(permissions);
        } else if (packet instanceof Packets.SyncCategoryEditMode mode) {
            ClientJournalState.acceptCategoryEditMode(mode);
        } else if (packet instanceof Packets.SyncServerSettings settings) {
            ClientJournalState.acceptServerSettings(settings);
        } else if (packet instanceof Packets.SyncSchemes schemes) {
            SchemeClientState.accept(schemes);
        } else if (packet instanceof Packets.SchemeResult schemeResult) {
            SchemeClientState.accept(schemeResult);
        } else if (packet instanceof Packets.SyncFlavors flavors) {
            FlavorClientState.accept(flavors);
        } else if (packet instanceof Packets.FlavorEditResult flavorResult) {
            FlavorClientState.accept(flavorResult);
        } else if (packet instanceof Packets.FlavorPreview flavorPreview) {
            FlavorClientState.accept(flavorPreview);
        } else if (packet instanceof Packets.CategoryEditResult result) {
            ClientJournalState.acceptCategoryEditResult(result);
        } else if (packet instanceof Packets.SyncCategoryEditor editor) {
            ClientJournalState.acceptCategoryEditor(editor);
        }
    }

    /** @return {@code true} once per request, so the screen only opens once */
    public static boolean consumeOpenRequest() {
        return OPEN_REQUESTED.compareAndSet(true, false);
    }
}
