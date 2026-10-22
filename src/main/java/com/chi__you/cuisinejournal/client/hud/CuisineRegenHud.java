package com.chi__you.cuisinejournal.client.hud;

import com.chi__you.cuisinejournal.food.*;
import com.chi__you.cuisinejournal.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Regeneration readout right of the hotbar (design: 百味纪回血速率HUD设计稿.html).
 *
 * <p>A medical cross, then hearts per second at 0.75 scale in four tiers (&lt;0.1 red, &lt;0.2
 * orange, &lt;0.3 yellow, otherwise green). In combat the ordinary regeneration pauses: the
 * readout turns grey and a 0.5-scale "6s" countdown hangs under the number. With a recovery
 * effect that keeps healing in combat (nourishment) it shows the reduced combat rate instead,
 * slightly darkened, without a countdown. At full health it fades. While the player holds a
 * food that would fit, the change it would make follows in gold (or red when it lowers it).</p>
 *
 * <p>Rates and held-food deltas are estimated from synced food definitions. Combat delay is
 * reported by the server and interpolated locally; attacks and effects never override it.</p>
 */
public final class CuisineRegenHud {
    private static final int RED = 0xF07272, ORANGE = 0xF0A050, YELLOW = 0xEBD866, GREEN = 0x86DB8F;
    private static final int GREY = 0x9A96A4, WAIT = 0x7E7A88, GOLD = 0xF2C14E, LOSS = 0xE05A4A;
    private static final float TEXT_SCALE = 0.75F, WAIT_SCALE = 0.5F;
    private static final int CROSS = 6, TEXT_GAP = 2;

    private static final CombatCountdown COMBAT = new CombatCountdown();
    private static int serverInterval = 5;

    private CuisineRegenHud() {
    }

    // ------------------------------------------------------------------ combat timing

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null || !CuisineFoodClientState.active()) clear();
        else COMBAT.tick(client.isPaused());
    }

    public static void clear() { COMBAT.clear(); serverInterval = 5; }

    /** Authoritative ticks until ordinary regeneration resumes. */
    public static void acceptCombat(int ticksLeft) {
        COMBAT.accept(ticksLeft);
    }

    public static void acceptCombat(int ticksLeft, int intervalTicks) {
        acceptCombat(ticksLeft);
        serverInterval = intervalTicks;
    }

    private static int intervalTicks() {
        return serverInterval;
    }

    // ------------------------------------------------------------------ rates

    public static List<ItemStack> stacks(List<CuisineFoodSnapshot.Slot> slots) {
        List<ItemStack> stacks = new ArrayList<>();
        for (var slot : slots) {
            if (!slot.displayStack().isEmpty() && CuisineFoodClientState.displayedRemaining(slot) > 0)
                stacks.add(slot.displayStack());
        }
        return stacks;
    }

    public static ItemStack drink(CuisineFoodSnapshot snapshot) {
        var drink = snapshot.drink();
        return drink != null && !drink.displayStack().isEmpty() && CuisineFoodClientState.displayedRemaining(drink) > 0
                ? drink.displayStack() : null;
    }

    private static float regen(List<ItemStack> stacks) {
        float total = 0;
        for (ItemStack stack : stacks) {
            var info = CuisineFoodTooltip.info(stack);
            if (info != null && !info.drink()) total += info.regenPerSecond();
        }
        return total;
    }

    private static float drinkMultiplier(ItemStack drink) {
        if (drink == null) return 1;
        var info = CuisineFoodTooltip.info(drink);
        return 1 + (info == null ? 0 : Math.max(0, info.drinkBonus()));
    }

    /** Ordinary regeneration in health points per second (paused in combat). */
    private static float ordinary(List<ItemStack> foods, ItemStack drink) {
        return (0.25F / intervalTicks() + regen(foods)) * drinkMultiplier(drink);
    }

    /** Temporary meals heal in and out of combat, in health points per second. */
    private static float temporary(List<ItemStack> temps, ItemStack drink) {
        return regen(temps) * drinkMultiplier(drink);
    }

    /** Out-of-combat hearts per second for these meals, recovery effects included. */
    public static float hearts(Player player, List<ItemStack> foods, List<ItemStack> temps, ItemStack drink) {
        float ordinary = ordinary(foods, drink);
        var recovery = CuisineEffectAdapters.recovery(player);
        if (recovery != null) ordinary = recovery.amount(ordinary, true);
        return (ordinary + temporary(temps, drink)) / 2F;
    }

    static int tier(float hearts) {
        return hearts < 0.1F ? RED : hearts < 0.2F ? ORANGE : hearts < 0.3F ? YELLOW : GREEN;
    }

    private static int darken(int color) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        r = Math.round(r + (0x1E - r) * 0.25F);
        g = Math.round(g + (0x2A - g) * 0.25F);
        b = Math.round(b + (0x20 - b) * 0.25F);
        return r << 16 | g << 8 | b;
    }

    private static String format(float hearts) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0, hearts));
    }

    // ------------------------------------------------------------------ drawing

    /**
     * @param x       left edge (a few pixels right of the hotbar)
     * @param centerY vertical centre of the ordinary food row
     * @param delta   change in hearts per second if the held food were eaten; NaN for none
     */
    public static void render(GuiGraphics g, Minecraft client, CuisineFoodSnapshot snapshot,
                              int x, int centerY, int screenWidth, float delta) {
        Player player = client.player;
        if (player == null || player.isDeadOrDying() || client.gameMode == null || !client.gameMode.canHurtPlayer()
                || !ConfigManager.client().foodHud.showRegenRate) {
            return;
        }
        List<ItemStack> foods = stacks(snapshot.foods()), temps = stacks(snapshot.temporaryFoods());
        ItemStack drink = drink(snapshot);
        float outOfCombat = hearts(player, foods, temps, drink);
        int wait = COMBAT.remaining();
        var recovery = CuisineEffectAdapters.recovery(player);
        float shown = outOfCombat;
        int color = tier(outOfCombat);
        boolean countdown = false;
        if (wait > 0) {
            if (recovery != null && recovery.combatFraction() > 0) {
                shown = (recovery.amount(ordinary(foods, drink), false) + temporary(temps, drink)) / 2F;
                color = darken(tier(shown));
            } else {
                color = GREY;
                countdown = true;
            }
        }
        int alpha = player.getHealth() >= player.getMaxHealth() ? 0x73 : 0xFF;

        Font font = client.font;
        String number = format(shown);
        String unit = Component.translatable("cuisinejournal.hud.regen_unit").getString();
        String gain = Float.isFinite(delta) && Math.abs(delta) >= 0.005F
                ? (delta > 0 ? " +" : " −") + format(Math.abs(delta)) : "";
        int textX = x + CROSS + TEXT_GAP;
        int room = screenWidth - 2 - textX;
        // Narrow screens drop the change first, then the unit; no room for the number: nothing.
        if (width(font, number + unit + gain) > room) gain = "";
        if (width(font, number + unit) > room) unit = "";
        if (width(font, number) > room) return;

        int top = centerY - CROSS / 2;
        int a = alpha << 24;
        // Pixel by pixel so nothing is painted twice: overlapping translucent fills would make
        // the centre (and the shadow under the cross) brighter or darker when faded.
        int shadow = (alpha == 0xFF ? 0x73 : 0x30) << 24;
        for (int py = 0; py <= CROSS; py++) {
            for (int px = 0; px <= CROSS; px++) {
                if (inCross(px, py)) g.fill(x + px, top + py, x + px + 1, top + py + 1, a | color);
                else if (inCross(px - 1, py - 1)) g.fill(x + px, top + py, x + px + 1, top + py + 1, shadow);
            }
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(textX, top, 0);
        pose.scale(TEXT_SCALE, TEXT_SCALE, 1);
        int cx = g.drawString(font, number, 0, 0, a | color, true);
        if (!unit.isEmpty()) cx = g.drawString(font, unit, cx, 0, a | color, true);
        if (!gain.isEmpty()) g.drawString(font, gain, cx, 0, a | (delta > 0 ? GOLD : LOSS), true);
        pose.popPose();

        if (countdown) {
            String seconds = (wait + 19) / 20 + "s";
            pose.pushPose();
            pose.translate(textX, top + 8, 0);
            pose.scale(WAIT_SCALE, WAIT_SCALE, 1);
            g.drawString(font, seconds, 0, 0, a | WAIT, true);
            pose.popPose();
        }
    }

    /** The 6x6 medical cross: 2-pixel arms through the middle. */
    private static boolean inCross(int px, int py) {
        if (px < 0 || py < 0 || px >= CROSS || py >= CROSS) return false;
        return px == 2 || px == 3 || py == 2 || py == 3;
    }

    private static int width(Font font, String text) {
        return (int) Math.ceil(font.width(text) * TEXT_SCALE);
    }
}
