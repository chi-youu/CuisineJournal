package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.client.hud.CuisineRegenHud;

import com.chi__you.cuisinejournal.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Food slot HUD (design: 百味纪食物槽HUD设计稿.html).
 *
 * <p>Bottom row(s): ordinary meals (merged multi-slot spans) then the remaining capacity, then
 * the drink. The temporary row sits one row above, purple, right-aligned the same way. Free
 * capacity is a row of small pips; while the player holds something edible it expands into
 * empty cells with a dashed outline where the held food would go (gold: ordinary, purple:
 * temporary, red: nowhere — the meals it must wait for get a red edge). Temporary meals that
 * the new one would replace get a purple edge and a ✕. Everything is static: no slides, no
 * blinking.</p>
 */
public final class CuisineFoodHud {
    private static final float HUD_SCALE = 0.85F;
    /** Widest a row may grow leftwards from the hotbar's right edge, in unscaled HUD pixels. */
    private static final int ROW_LIMIT = 117;
    private static final int GROUP_GAP = 3;

    private static final int GOLD = 0xE6E8AE3A, PURPLE = 0xF2C48CF0, RED = 0xF2E05A4A;
    private static final int TEMP_EDGE = 0x8CB282DC;

    private CuisineFoodHud() {
    }

    /** What the held item would do; null fields mean "no preview there". */
    private record Preview(ItemStack stack, int cost, boolean drink, boolean drinkOk,
                           int ordinary, boolean ordinaryOk, Set<Integer> waitFor,
                           boolean temporary, int sameTemporary, Set<Integer> replaced) {}

    public static void render(GuiGraphics graphics, int width, int height) {
        render(null, graphics, width, height);
    }

    public static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics, int width, int height) {
        CuisineFoodSnapshot snapshot = CuisineFoodClientState.snapshot();
        Minecraft client = Minecraft.getInstance();
        if (snapshot == null || !snapshot.active() || client.player == null || client.options.hideGui) {
            return;
        }

        boolean large = ConfigManager.client().foodHud.useLargeFoodIcons;
        int size = large ? 14 : 9;
        int rowStep = size + 2;
        int right = width / 2 + 91;
        int y = height - 39 - (large ? 6 : 0);
        var pose = graphics.pose();
        pose.pushPose();
        // Scale the full HUD about its lower-right edge, retaining the hotbar alignment.
        pose.translate(right, y + size, 0);
        pose.scale(HUD_SCALE, HUD_SCALE, 1);
        pose.translate(-right, -y - size, 0);
        int rows, topLocal;
        Preview preview;
        try {
            preview = preview(snapshot);
            rows = renderOrdinary(graphics, snapshot, preview, right, y, size, rowStep, large);
            topLocal = y - (rows - 1) * rowStep;
            List<CuisineFoodSnapshot.Slot> temps = active(snapshot.temporaryFoods());
            boolean tempExpanded = preview != null && preview.temporary();
            if (!temps.isEmpty() || tempExpanded) {
                renderTemporary(graphics, snapshot, temps, preview, right, y - rows * rowStep, size, large);
                topLocal = y - rows * rowStep;
            } else if (snapshot.temporaryCapacity() > 0) {
                // Only free temporary capacity: its purple pips sit just above whatever the
                // ordinary row shows (its pips, or its cells), and move up once a meal lands.
                int pip = size >= 12 ? 3 : 2;
                var drink = snapshot.drink();
                boolean onlyPips = active(snapshot.foods()).isEmpty() && rows == 1 && preview == null
                        && (drink == null || CuisineFoodClientState.displayedRemaining(drink) <= 0);
                int below = onlyPips ? y + (size - pip) / 2 : topLocal;
                int pipTop = below - 2 - pip;
                pips(graphics, right, pipTop - (size - pip) / 2, size, snapshot.temporaryCapacity(), 0xD9C896F0);
                topLocal = Math.min(topLocal, pipTop);
            }
        } finally {
            pose.popPose();
        }
        // Regeneration readout: right of the hotbar, centred on the (scaled) ordinary row.
        int rowCentre = Math.round((y + size) - size / 2F * HUD_SCALE);
        CuisineRegenHud.render(graphics, client, snapshot, right + 4, rowCentre, width, regenDelta(snapshot, preview));
        // Tell Forge how tall the right-hand HUD stack is, so air bubbles etc. sit above it.
        if (gui != null) {
            int topScreen = Math.round((y + size) - (y + size - topLocal) * HUD_SCALE);
            gui.rightHeight = Math.max(gui.rightHeight, height - topScreen + 1);
        }
        var skills=com.chi__you.cuisinejournal.client.ClientJournalState.skills();
        if(skills!=null&&skills.active()&&skills.shield()>0) {
            graphics.renderItem(new ItemStack(net.minecraft.world.item.Items.SHIELD),width/2-109,height-51);
            graphics.drawString(client.font,String.format(java.util.Locale.ROOT,"%.1f",skills.shield()/2),width/2-93,height-45,0x99E7FF,true);
        }
    }

    private static List<CuisineFoodSnapshot.Slot> active(List<CuisineFoodSnapshot.Slot> slots) {
        return slots.stream().filter(slot -> !slot.displayStack().isEmpty()
                && CuisineFoodClientState.displayedRemaining(slot) > 0).toList();
    }

    // ------------------------------------------------------------------ held-item preview

    private static Preview preview(CuisineFoodSnapshot snapshot) {
        var player = Minecraft.getInstance().player;
        ItemStack stack = ItemStack.EMPTY;
        CuisineFoodInfoPackets.Response info = null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty()) continue;
            var candidate = CuisineFoodTooltip.info(held);
            if (candidate != null) { stack = held; info = candidate; break; }
        }
        if (info == null) return null;
        int cost = Math.max(1, info.slotCost());
        if (info.drink()) {
            var drink = snapshot.drink();
            boolean ok = drink == null || CuisineFoodClientState.displayedRemaining(drink) <= 0 || CuisineAlcohol.isAlcohol(stack)
                    || CuisineFoodPlacement.canRefresh(CuisineFoodClientState.displayedRemaining(drink), drink.totalTicks(), snapshot.eatAgainFraction());
            return new Preview(stack, 1, true, ok, CuisineFoodPlacement.REJECT, false, Set.of(), false, -1, Set.of());
        }
        List<CuisineFoodSnapshot.Slot> foods = active(snapshot.foods());
        ItemStack held = stack;
        var slots = foods.stream().map(slot -> new CuisineFoodPlacement.Slot(slot.slotCost(),
                CuisineFoodClientState.displayedRemaining(slot), slot.totalTicks(),
                CuisineFoodService.sameFood(slot.displayStack(), held))).toList();
        List<CuisineFoodSnapshot.Slot> temps = active(snapshot.temporaryFoods());
        var tempSlots = temps.stream().map(slot -> new CuisineFoodPlacement.Slot(slot.slotCost(),
                CuisineFoodClientState.displayedRemaining(slot), slot.totalTicks(),
                CuisineFoodService.sameFood(slot.displayStack(), held))).toList();
        boolean parfait=ExquisitoParfaits.matches(stack);
        int placement = parfait ? ExquisitoParfaits.placement(stack.getDamageValue(),slots,tempSlots,cost,
                snapshot.capacity(),snapshot.temporaryCapacity(),snapshot.eatAgainFraction(),info.repeatable())
                : CuisineFoodPlacement.find(slots, cost, snapshot.capacity(), snapshot.eatAgainFraction(), info.repeatable());
        if (placement >= CuisineFoodPlacement.ADD)
            return new Preview(stack, cost, false, false, placement, true, Set.of(), false, -1, Set.of());
        // Ordinary slots refuse it: the temporary row may take it (replacing the oldest meals).
        var plan = CuisineFoodPlacement.temporaryPlan(tempSlots, cost, snapshot.temporaryCapacity());
        if (plan.accepted() && (!parfait || placement == CuisineFoodPlacement.TEMPORARY)) {
            int same = -1;
            for (int i = 0; i < tempSlots.size(); i++) if (tempSlots.get(i).sameFood()) same = i;
            Set<Integer> replaced = new HashSet<>(plan.remove());
            if (same >= 0) replaced.remove(same);
            return new Preview(stack, cost, false, false, CuisineFoodPlacement.REJECT, false, Set.of(), true, same, replaced);
        }
        // Eaten for instant healing while digesting: nothing is placed, so nothing to preview.
        if (!parfait && CuisineEffectAdapters.digesting(player)) return null;
        // Nowhere: mark the ordinary meals that must run out first (soonest first).
        int free = snapshot.capacity() - foods.stream().mapToInt(CuisineFoodSnapshot.Slot::slotCost).sum();
        int need = cost - Math.max(0, free);
        Set<Integer> wait = new HashSet<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < foods.size(); i++) order.add(i);
        order.sort((a, b) -> Integer.compare(CuisineFoodClientState.displayedRemaining(foods.get(a)),
                CuisineFoodClientState.displayedRemaining(foods.get(b))));
        for (int index : order) {
            if (need <= 0 || cost > snapshot.capacity()) break;
            wait.add(index);
            need -= foods.get(index).slotCost();
        }
        return new Preview(stack, cost, false, false, CuisineFoodPlacement.REJECT, false, wait, false, -1, Set.of());
    }

    /** Hearts per second gained (or lost) by eating the previewed food; NaN when it would not be eaten. */
    private static float regenDelta(CuisineFoodSnapshot snapshot, Preview preview) {
        if (preview == null) return Float.NaN;
        var player = Minecraft.getInstance().player;
        List<CuisineFoodSnapshot.Slot> foodSlots = active(snapshot.foods()), tempSlots = active(snapshot.temporaryFoods());
        List<ItemStack> foods = new ArrayList<>(foodSlots.stream().map(CuisineFoodSnapshot.Slot::displayStack).toList());
        List<ItemStack> temps = new ArrayList<>(tempSlots.stream().map(CuisineFoodSnapshot.Slot::displayStack).toList());
        ItemStack drink = CuisineRegenHud.drink(snapshot);
        float before = CuisineRegenHud.hearts(player, foods, temps, drink);
        ItemStack held = preview.stack().copyWithCount(1);
        if (preview.drink()) {
            if (!preview.drinkOk()) return Float.NaN;
            drink = held;
        } else if (preview.ordinaryOk()) {
            int at = preview.ordinary();
            if (at >= 0 && at < foods.size()) foods.set(at, held);
            else foods.add(held);
        } else if (preview.temporary()) {
            if (preview.sameTemporary() >= 0) temps.set(preview.sameTemporary(), held);
            else {
                List<ItemStack> kept = new ArrayList<>();
                for (int i = 0; i < temps.size(); i++) if (!preview.replaced().contains(i)) kept.add(temps.get(i));
                kept.add(held);
                temps = kept;
            }
        } else {
            return Float.NaN;
        }
        return CuisineRegenHud.hearts(player, foods, temps, drink) - before;
    }

    // ------------------------------------------------------------------ rows

    /** Packs widths right to left, wrapping upwards; returns {row, offset} per piece. */
    private static List<int[]> pack(List<Integer> widths, int firstRowLimit) {
        List<int[]> result = new ArrayList<>();
        int row = 0, offset = 0;
        for (int w : widths) {
            int limit = row == 0 ? firstRowLimit : ROW_LIMIT;
            if (offset > 0 && offset + w > limit) { row++; offset = 0; }
            result.add(new int[]{row, offset});
            offset += w + 1;
        }
        return result;
    }

    private static int span(int units, int size) { return units * (size + 1) - 1; }

    /** Ordinary meals, free capacity and the drink; returns how many rows they used. */
    private static int renderOrdinary(GuiGraphics g, CuisineFoodSnapshot snapshot, Preview preview,
                                      int right, int y, int size, int rowStep, boolean large) {
        List<CuisineFoodSnapshot.Slot> foods = active(snapshot.foods());
        var spans = FoodHudLayout.row(foods.stream().map(CuisineFoodSnapshot.Slot::slotCost).toList(), snapshot.capacity(), size);
        int used = foods.stream().mapToInt(slot -> Math.max(1, Math.min(3, slot.slotCost()))).sum();
        int free = Math.max(0, snapshot.capacity() - used);
        boolean expand = preview != null && !preview.drink() && !preview.temporary();
        boolean adding = expand && (preview.ordinary() == CuisineFoodPlacement.ADD || !preview.ordinaryOk());

        // Pieces in order: meals, [ghost covering the first free cells], remaining free cells.
        List<Integer> widths = new ArrayList<>();
        for (var s : spans) if (s.itemIndex() >= 0) widths.add(s.width());
        int ghostPiece = -1, covered = 0;
        if (adding) {
            ghostPiece = widths.size();
            widths.add(span(preview.cost(), size));
            covered = Math.min(preview.cost(), free);
        }
        int emptiesFrom = widths.size();
        if (expand) for (int i = covered; i < free; i++) widths.add(size);
        int drinkReserve = size + GROUP_GAP;
        List<int[]> places = pack(widths, ROW_LIMIT - drinkReserve);
        int rows = places.isEmpty() ? 1 : places.get(places.size() - 1)[0] + 1;

        int piece = 0, bottomEnd = 0;
        int[] mealX = new int[foods.size()], mealY = new int[foods.size()], mealW = new int[foods.size()];
        for (var s : spans) {
            if (s.itemIndex() < 0) continue;
            int[] p = places.get(piece++);
            int x = right - p[1] - s.width(), cy = y - p[0] * rowStep;
            mealX[s.itemIndex()] = x; mealY[s.itemIndex()] = cy; mealW[s.itemIndex()] = s.width();
            int mark = preview != null && preview.waitFor().contains(s.itemIndex()) ? 1 : 0;
            renderSlot(g, foods.get(s.itemIndex()), x, cy, s.width(), size, large, false, false, s.overflow(), mark);
            if (p[0] == 0) bottomEnd = Math.max(bottomEnd, p[1] + s.width());
        }
        if (expand) {
            if (ghostPiece >= 0) {
                int[] p = places.get(ghostPiece);
                int w = span(preview.cost(), size), gx = right - p[1] - w, gy = y - p[0] * rowStep;
                // The free cells the outline covers, then the outline itself.
                for (int i = 0; i < covered; i++) renderEmpty(g, gx + w - (i + 1) * (size + 1) + 1, gy, size, false, false);
                ghost(g, preview.stack(), gx, gy, w, size, large, preview.ordinaryOk() ? GOLD : RED, null);
                if (p[0] == 0) bottomEnd = Math.max(bottomEnd, p[1] + w);
            }
            for (int i = emptiesFrom; i < widths.size(); i++) {
                int[] p = places.get(i);
                renderEmpty(g, right - p[1] - size, y - p[0] * rowStep, size, false, false);
                if (p[0] == 0) bottomEnd = Math.max(bottomEnd, p[1] + size);
            }
            // Eating again / replacing a nearly finished meal: outline over that meal.
            if (preview.ordinary() >= 0 && preview.ordinary() < foods.size()) {
                int i = preview.ordinary(), w = span(preview.cost(), size);
                ghost(g, preview.stack(), mealX[i] + mealW[i] - w, mealY[i], w, size, large, GOLD, null);
            }
        } else if (free > 0) {
            // Free capacity as pips after the last meal, on its row.
            int lastRow = places.isEmpty() ? 0 : places.get(places.size() - 1)[0];
            int offset = places.isEmpty() ? 0 : places.get(places.size() - 1)[1] + widths.get(widths.size() - 1) + 2;
            int end = pips(g, right - offset, y - lastRow * rowStep, size, free, 0xA6F0E4CD);
            if (lastRow == 0) bottomEnd = right - end;
        }

        // Drink: bottom row, left of everything else there.
        int drinkRight = right - bottomEnd - (bottomEnd > 0 ? GROUP_GAP : 0);
        var drink = snapshot.drink();
        boolean drinking = drink != null && CuisineFoodClientState.displayedRemaining(drink) > 0;
        boolean holdingDrink = preview != null && preview.drink();
        if (drinking) renderSlot(g, drink, drinkRight - size, y, size, size, large, true, false, false, 0);
        else if (expand || holdingDrink) renderEmpty(g, drinkRight - size, y, size, true, false);
        else pips(g, drinkRight, y, size, 1, 0xC08CBEFF);
        if (holdingDrink) ghost(g, preview.stack(), drinkRight - size, y, size, size, large, preview.drinkOk() ? GOLD : RED, null);
        return rows;
    }

    private static void renderTemporary(GuiGraphics g, CuisineFoodSnapshot snapshot, List<CuisineFoodSnapshot.Slot> temps,
                                        Preview preview, int right, int y, int size, boolean large) {
        int used = temps.stream().mapToInt(slot -> Math.max(1, Math.min(3, slot.slotCost()))).sum();
        int free = Math.max(0, snapshot.temporaryCapacity() - used);
        boolean expand = preview != null && preview.temporary();
        int offset = 0;
        for (int i = 0; i < temps.size(); i++) {
            var slot = temps.get(i);
            int w = span(Math.max(1, Math.min(3, slot.slotCost())), size);
            int x = right - offset - w;
            int mark = expand && preview.replaced().contains(i) ? 2 : 0;
            renderSlot(g, slot, x, y, w, size, large, false, true, false, mark);
            if (expand && preview.sameTemporary() == i) {
                int gw = span(preview.cost(), size);
                ghost(g, preview.stack(), x + w - gw, y, gw, size, large, PURPLE, "10");
            }
            offset += w + 1;
        }
        if (expand && preview.sameTemporary() < 0) {
            int gw = span(preview.cost(), size), covered = Math.min(preview.cost(), free);
            int gx = right - offset - gw;
            for (int i = 0; i < covered; i++) renderEmpty(g, gx + gw - (i + 1) * (size + 1) + 1, y, size, false, true);
            ghost(g, preview.stack(), gx, y, gw, size, large, PURPLE, "10");
            offset += gw + 1;
            for (int i = covered; i < free; i++) { renderEmpty(g, right - offset - size, y, size, false, true); offset += size + 1; }
        } else if (expand) {
            for (int i = 0; i < free; i++) { renderEmpty(g, right - offset - size, y, size, false, true); offset += size + 1; }
        } else if (free > 0) {
            pips(g, right - offset - (offset > 0 ? 1 : 0), y, size, free, 0xD9C896F0);
        }
    }

    /** Small capacity pips drawn leftwards from {@code rightX}; returns the leftmost x used. */
    private static int pips(GuiGraphics g, int rightX, int y, int size, int count, int color) {
        int pip = size >= 12 ? 3 : 2;
        int py = y + (size - pip) / 2, x = rightX;
        for (int i = 0; i < count; i++) {
            x -= pip;
            g.fill(x + 1, py + 1, x + pip + 1, py + pip + 1, 0x40000000);
            g.fill(x, py, x + pip, py + pip, color);
            x -= 1;
        }
        return x + 1;
    }

    // ------------------------------------------------------------------ cells

    private static int fullColor(boolean drink, boolean temporary) {
        return temporary ? 0x48644480 : drink ? 0x50203A5C : 0x58000000;
    }

    /** A free cell while the held food is previewed: faint fill and a 1px inner edge. */
    private static void renderEmpty(GuiGraphics g, int x, int y, int size, boolean drink, boolean temporary) {
        g.fill(x, y, x + size, y + size, temporary ? 0x385A3C78 : drink ? 0x382A4E78 : 0x24000000);
        border(g, x, y, size, size, temporary ? 0x59B282DC : drink ? 0x2E8CBEFF : 0x1AFFFFFF);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Dashed outline (2 on, 1 off) with a pale copy of the held item; {@code time} for temporary. */
    private static void ghost(GuiGraphics g, ItemStack stack, int x, int y, int w, int size, boolean large, int color, String time) {
        var pose = g.pose();
        g.fill(x, y, x + w, y + size, (color & 0x00FFFFFF) | 0x1F000000);
        float itemScale = large ? 0.75F : 0.5F;
        pose.pushPose();
        pose.translate(x + (w - 16 * itemScale) / 2F, y + (size - 16 * itemScale) / 2F, 0);
        pose.scale(itemScale, itemScale, 1.0F);
        g.renderItem(displayStack(stack), 0, 0);
        pose.popPose();
        pose.pushPose();
        pose.translate(0, 0, 300);
        // Wash the icon out so it reads as "would go here", not as a real meal.
        g.fill(x + 1, y + 1, x + w - 1, y + size - 1, 0x66201810);
        for (int dx = 0; dx < w; dx += 3) {
            g.fill(x + dx, y, x + Math.min(w, dx + 2), y + 1, color);
            g.fill(x + dx, y + size - 1, x + Math.min(w, dx + 2), y + size, color);
        }
        for (int dy = 0; dy < size; dy += 3) {
            g.fill(x, y + dy, x + 1, y + Math.min(size, dy + 2), color);
            g.fill(x + w - 1, y + dy, x + w, y + Math.min(size, dy + 2), color);
        }
        if (time != null) {
            float scale = large ? 0.75F : 0.5F;
            pose.translate(x + w - Math.round(clientFont().width(time) * scale), y + size - Math.round(8 * scale) + 1, 10);
            pose.scale(scale, scale, 1);
            g.drawString(clientFont(), time, 0, 0, 0xFFE6C8FF, true);
        }
        pose.popPose();
    }

    /**
     * One meal cell. {@code mark}: 1 = must run out before the held food fits (red edge, dim),
     * 2 = would be replaced by the held temporary food (purple edge, dim, ✕).
     */
    private static void renderSlot(GuiGraphics graphics, CuisineFoodSnapshot.Slot slot,
                                   int x, int y, int spanWidth, int size, boolean large, boolean drink,
                                   boolean temporary, boolean overflow, int mark) {
        ItemStack stack = displayStack(slot.displayStack());
        if (stack.isEmpty()) {
            return;
        }
        int remainingTicks = CuisineFoodClientState.displayedRemaining(slot);
        float fraction = Math.min(1.0F, Math.max(0.0F,
                (float) remainingTicks / Math.max(1, slot.totalTicks())));
        boolean low = fraction < 0.2F;
        // Original look: a translucent dark slot, darker where time remains; the original bright
        // red once less than a fifth is left. No level line.
        graphics.fill(x, y, x + spanWidth, y + size, temporary ? 0x30452B56 : drink ? 0x452A4E78 : 0x38000000);
        // Round up: a freshly eaten slot fills completely instead of leaving a 1px empty row on top.
        int level = (int) Math.ceil(size * fraction - 1e-4);
        if (remainingTicks > 0 && level < 1) level = 1;
        if (level > 0) {
            int top = y + size - level;
            graphics.fill(x, top, x + spanWidth, y + size,
                    low ? temporary ? 0x70FF0A0A : 0xB4FF0A0A : fullColor(drink, temporary));
        }
        if (overflow) graphics.fill(x, y, x + spanWidth, y + 1, 0x809F899F);

        var pose = graphics.pose();
        pose.pushPose();
        float itemScale = large ? 0.75F : 0.5F;
        pose.translate(x + (spanWidth - 16 * itemScale) / 2F, y + (size - 16 * itemScale) / 2F, 0.0F);
        pose.scale(itemScale, itemScale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();

        // Temporary meals carry a thin purple edge so they never read as ordinary slots.
        pose.pushPose();
        pose.translate(0, 0, 190);
        if (mark != 0) graphics.fill(x, y, x + spanWidth, y + size, 0x66000000);
        int edge = mark == 1 ? RED : mark == 2 ? PURPLE : temporary ? TEMP_EDGE : 0;
        if (edge != 0) border(graphics, x, y, spanWidth, size, edge);
        pose.popPose();

        float minutes = remainingTicks / 1200.0F;
        String time = minutes < 1.0F
                ? Integer.toString(Math.round(remainingTicks / 20.0F))
                : String.format(Locale.ROOT, "%.0f", minutes);
        float scale = large ? 0.75F : 0.5F;
        // Snap the scaled text to whole pixels so it stays crisp over the icon.
        int timeX = x + spanWidth - Math.round(clientFont().width(time) * scale);
        int timeY = y + size - Math.round(8 * scale) + 1;
        pose.pushPose();
        pose.translate(timeX, timeY, 200.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(clientFont(), time, 0, 0,
                minutes < 1.0F ? 0xFFED3939 : 0xFFFFFFFF, true);
        pose.popPose();

        if (mark == 2) {
            String cross = "✕";
            pose.pushPose();
            float s = large ? 1F : 0.75F;
            pose.translate(x + (spanWidth - clientFont().width(cross) * s) / 2F, y + (size - 8 * s) / 2F, 250);
            pose.scale(s, s, 1);
            graphics.drawString(clientFont(), cross, 0, 0, 0xFFF2D7FF, true);
            pose.popPose();
        }
    }

    private static net.minecraft.client.gui.Font clientFont() {
        return Minecraft.getInstance().font;
    }

    private static ItemStack displayStack(ItemStack original) {
        if (original.is(Items.CAKE) && ModList.get().isLoaded("farmersdelight")) {
            var slice = BuiltInRegistries.ITEM.getOptional(
                    new net.minecraft.resources.ResourceLocation("farmersdelight", "cake_slice")).orElse(null);
            if (slice != null) {
                return new ItemStack(slice);
            }
        }
        return original;
    }
}
