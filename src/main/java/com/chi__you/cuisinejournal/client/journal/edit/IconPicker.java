package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.journal.FlavorUi;
import com.chi__you.cuisinejournal.client.journal.ItemOrder;
import com.chi__you.cuisinejournal.client.journal.JournalSearchField;
import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Icon chooser shared by the category form and the flavor library. Items follow JEI's default
 * order (Minecraft, then mods by name, creative-menu order inside a mod); the flavor library
 * also offers potion-effect icons, listed first. It never creates textures.
 */
public final class IconPicker {
    private static final int CELL = 20;
    private static final int SEARCH_H = 13;
    private static final int MAX_RESULTS = 8000;
    private static final Map<ResourceLocation, String> NAMES = new HashMap<>();

    private record Entry(ResourceLocation id, boolean effect, ItemStack stack) { }

    private final Font font;
    private final EditBox search;
    private final List<ResourceLocation> recommendations = new ArrayList<>();
    private List<Entry> visible = List.of();
    private boolean effects;
    private int x, y, width, gridHeight, scroll;
    private ResourceLocation selected;
    private boolean selectedEffect;
    private boolean emptySelection;

    public IconPicker(Font font, int x, int y, int width) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = Math.max(80, width);
        search = new EditBox(font, x, y, 40, 9, Component.translatable("cuisinejournal.edit.search_all"));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setResponder(value -> refresh());
        refresh();
    }

    /** Also list potion-effect icons (first). */
    public IconPicker withEffects() {
        effects = true;
        refresh();
        return this;
    }

    public void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = Math.max(80, width);
        // Called every frame by the form layout: do not rebuild the list or reset scroll here.
    }

    public void setRecommendations(Iterable<ResourceLocation> ids) {
        recommendations.clear();
        ids.forEach(id -> { if (id != null && !recommendations.contains(id)) recommendations.add(id); });
        refresh();
    }

    public void setSelectedIcon(ResourceLocation icon) {
        selected = icon;
        selectedEffect = false;
        emptySelection = icon == null;
    }

    public void setSelectedEffect(ResourceLocation effect) {
        selected = effect;
        selectedEffect = effect != null;
        emptySelection = effect == null;
    }

    /** The chosen item, or null (nothing chosen, or an effect was chosen). */
    public ResourceLocation selectedIcon() {
        return emptySelection || selectedEffect ? null : selected;
    }

    /** The chosen potion effect, or null. */
    public ResourceLocation selectedEffect() {
        return emptySelection || !selectedEffect ? null : selected;
    }

    public EditBox searchBox() { return search; }

    public void clearSelection() {
        selected = null;
        selectedEffect = false;
        emptySelection = true;
    }

    /** Whole cells only, leaving 3px on the right for the scrollbar so it never covers a cell. */
    private int columns() { return Math.max(1, (width - 3) / CELL); }
    private int gridY() { return y + 22; }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        // Preview of the current choice, then the search box on the same 18px line.
        g.fill(x, y, x + 18, y + 18, 0xFF0A0705);
        JournalTheme.border(g, x, y, 18, 18, JournalTheme.PANEL_BORDER);
        if (selectedEffect() != null) FlavorUi.effectIcon(g, BuiltInRegistries.MOB_EFFECT.get(com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(selectedEffect())), x + 1, y + 1, 16);
        else if (selectedIcon() != null && BuiltInRegistries.ITEM.get(selectedIcon()) != Items.AIR)
            g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(selectedIcon())), x + 1, y + 1);
        else PixelIcons.draw(g, PixelIcons.Icon.QUESTION, x + 5, y + 5, JournalTheme.TEXT_FAINT);
        // The search box ends where the grid's last column ends, so both right edges line up.
        int sx = x + 22, sw = Math.max(30, columns() * CELL - 22), sy = y + (18 - SEARCH_H) / 2;
        JournalSearchField.render(g, font, search, sx, sy, sw, SEARCH_H,
                Component.translatable("cuisinejournal.edit.search_all"), mouseX, mouseY, 0F);

        int columns = columns();
        int rows = (visible.size() + columns - 1) / columns;
        int maxRows = Math.max(2, gridHeight() / CELL);
        int maxScroll = Math.max(0, rows - maxRows);
        scroll = Math.min(scroll, maxScroll);
        int gridY = gridY();
        int first = scroll * columns, last = Math.min(visible.size(), (scroll + maxRows) * columns);
        Entry hovered = null;
        for (int i = first; i < last; i++) {
            int row = i / columns - scroll, col = i % columns;
            int px = x + col * CELL, py = gridY + row * CELL;
            Entry entry = visible.get(i);
            boolean hover = mouseX >= px && mouseX < px + CELL && mouseY >= py && mouseY < py + CELL;
            if (hover) hovered = entry;
            boolean chosen = !emptySelection && entry.id().equals(selected) && entry.effect() == selectedEffect;
            g.fill(px, py, px + CELL, py + CELL, hover ? JournalTheme.CARD_HOVER : JournalTheme.CARD);
            JournalTheme.border(g, px, py, CELL, CELL, chosen ? JournalTheme.TEXT_ACCENT
                    : entry.effect() ? 0xFF4A3A2A : JournalTheme.PANEL_BORDER);
            if (entry.effect()) FlavorUi.effectIcon(g, BuiltInRegistries.MOB_EFFECT.get(entry.id()), px + 2, py + 2, 16);
            else g.renderItem(entry.stack(), px + 2, py + 2);
        }
        if (rows > maxRows) {
            int barX = x + width - 1;
            g.fill(barX, gridY, barX + 1, gridY + gridHeight(), JournalTheme.BAR_TRACK);
            int thumb = Math.max(6, gridHeight() * maxRows / Math.max(1, rows));
            int top = gridY + (gridHeight() - thumb) * scroll / Math.max(1, maxScroll);
            g.fill(barX, top, barX + 1, top + thumb, JournalTheme.TEXT_DIM);
        }
        if (hovered != null) {
            g.pose().pushPose(); g.pose().translate(0, 0, 300);
            g.renderTooltip(font, Component.literal(name(hovered)), mouseX, mouseY);
            g.pose().popPose();
        }
    }

    private int gridHeight() {
        return Math.max(40, gridHeight == 0 ? 60 : gridHeight);
    }

    public void setGridHeight(int height) { gridHeight = Math.max(40, height); }

    public boolean mouseClicked(double mx, double my, int button) {
        int sx = x + 22, sw = Math.max(30, columns() * CELL - 22), sy = y + (18 - SEARCH_H) / 2;
        if (JournalSearchField.click(search, mx, my, button, sx, sy, sw, SEARCH_H)) return true;
        int columns = columns();
        int gridY = gridY();
        if (mx < x || mx >= x + columns * CELL || my < gridY || my >= gridY + gridHeight()) return false;
        int col = (int) ((mx - x) / CELL);
        int row = (int) ((my - gridY) / CELL) + scroll;
        int index = row * columns + col;
        if (index < 0 || index >= visible.size()) return true;
        Entry entry = visible.get(index);
        selected = entry.id();
        selectedEffect = entry.effect();
        emptySelection = false;
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.item(entry.effect() ? ItemStack.EMPTY : entry.stack());
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        int gridY = gridY();
        if (mx < x || mx >= x + width || my < gridY || my >= gridY + gridHeight()) return false;
        int columns = columns();
        int rows = (visible.size() + columns - 1) / columns;
        int maxRows = Math.max(2, gridHeight() / CELL);
        int next = Math.max(0, Math.min(Math.max(0, rows - maxRows), scroll - (int) Math.signum(delta) * 2));
        // At either end the wheel falls through so the surrounding modal can scroll instead.
        if (next == scroll) return false;
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.scroll(scroll, next);
        scroll = next;
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return search.isFocused() && search.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char c, int modifiers) { return search.isFocused() && search.charTyped(c, modifiers); }
    public void tick() { search.tick(); }

    public void setFocused(boolean focused) { search.setFocused(focused); }
    public boolean isFocused() { return search.isFocused(); }

    private void refresh() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Entry> result = new ArrayList<>();
        if (effects) {
            BuiltInRegistries.MOB_EFFECT.keySet().stream()
                    // Vanilla effects first, like the inventory, then mods by id.
                    .sorted(Comparator.<ResourceLocation>comparingInt(id -> id.getNamespace().equals("minecraft") ? 0 : 1)
                            .thenComparing(ResourceLocation::toString))
                    .filter(id -> matches(id, effectName(id), query))
                    .forEach(id -> result.add(new Entry(id, true, ItemStack.EMPTY)));
        }
        Set<ResourceLocation> seen = new HashSet<>();
        if (query.isEmpty()) for (ResourceLocation id : recommendations) addItem(result, seen, id);
        for (ResourceLocation id : ItemOrder.all()) {
            if (result.size() >= MAX_RESULTS) break;
            if (query.isEmpty() || matches(id, itemName(id), query)) addItem(result, seen, id);
        }
        visible = List.copyOf(result);
        scroll = 0;
    }

    private static boolean matches(ResourceLocation id, String name, String query) {
        return query.isEmpty() || id.toString().contains(query) || name.toLowerCase(Locale.ROOT).contains(query)
                || com.chi__you.cuisinejournal.recipe.PinyinMatch.contains(name, query);
    }

    private void addItem(List<Entry> result, Set<ResourceLocation> seen, ResourceLocation id) {
        if (id == null || !seen.add(id)) return;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) return;
        result.add(new Entry(id, false, new ItemStack(item)));
    }

    private String name(Entry entry) {
        return entry.effect() ? effectName(entry.id()) : itemName(entry.id());
    }

    private static String effectName(ResourceLocation id) {
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(id));
        return effect == null ? id.toString() : effect.getDisplayName().getString();
    }

    /** Hover names (blocks included), cached: searching re-reads every item. */
    private static String itemName(ResourceLocation id) {
        return NAMES.computeIfAbsent(id, key -> {
            Item item = BuiltInRegistries.ITEM.get(key);
            return item == null ? key.toString() : new ItemStack(item).getHoverName().getString();
        });
    }
}
