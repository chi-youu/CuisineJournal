package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.FlavorClientState;
import com.chi__you.cuisinejournal.client.journal.FlavorUi;
import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.recipe.FlavorStage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Multi-select list of flavor presets for one ingredient. */
public final class FlavorPickerModal extends JournalModal {
    private static final int ROW = 22;
    private final Font font;
    private final Set<ResourceLocation> chosen;
    private final Consumer<List<ResourceLocation>> submit;
    private final Runnable openLibrary;
    private final JournalTextField search;
    private final JournalButton library;
    private List<FlavorClientState.Entry> rows = List.of();
    private String lastQuery = null;
    private long seenRevision = -1;

    public FlavorPickerModal(Font font, Component ingredient, List<ResourceLocation> current,
                             Consumer<List<ResourceLocation>> submit, Runnable openLibrary) {
        super(0, 0, 400, 300, Component.translatable("cuisinejournal.flavor.picker.title", ingredient));
        this.font = font;
        this.chosen = new LinkedHashSet<>(current);
        this.submit = submit;
        this.openLibrary = openLibrary;
        this.search = new JournalTextField(font, 0, 0, 100, Component.translatable("cuisinejournal.flavor.picker.search"));
        this.search.setMaxLength(64);
        this.search.setResponder(value -> resetScroll());
        this.library = new JournalButton(Component.translatable("cuisinejournal.flavor.library.open"),
                JournalButton.Variant.LINK, () -> { close(); if (openLibrary != null) openLibrary.run(); });
        setSubmitAction(() -> { if (waiting) return; waiting = true; setSubmitWaiting(true); submit.accept(new ArrayList<>(chosen)); });
    }

    private boolean waiting;
    public void accept(boolean success) { waiting = false; setSubmitWaiting(false); if (success) close(); }
    @Override protected boolean submitEnabled() { return !waiting; }

    @Override protected int maxModalWidth() { return 300; }
    @Override protected int maxModalHeight() { return 240; }
    @Override protected JournalButton footerButton() { return library; }
    @Override protected void onOpened() { addFocusable(search); }

    private void refresh() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        if (query.equals(lastQuery) && seenRevision == FlavorClientState.revision()) return;
        lastQuery = query;
        seenRevision = FlavorClientState.revision();
        rows = FlavorClientState.sorted().stream()
                .filter(e -> EffectSearch.matches(e.preset().id().toString(), e.preset().name(), query))
                .toList();
    }

    @Override protected void renderBody(GuiGraphics g, int bx, int by, int bw, int bh, int mx, int my, float tick) {
        // Content coordinates: the host translates by -scroll and passes mouse y in the same space.
        by -= scrollOffset();
        refresh();
        search.setBounds(bx, by + 2, bw);
        search.render(g, mx, my);
        int top = by + 2 + search.totalBounds().height;
        if (rows.isEmpty()) {
            g.drawString(font, Component.translatable("cuisinejournal.flavor.picker.empty"), bx + 4, top + 6, JournalTheme.TEXT_FAINT, false);
        }
        for (int i = 0; i < rows.size(); i++) {
            FlavorClientState.Entry entry = rows.get(i);
            int ry = top + i * ROW;
            boolean enabled = entry.preset().enabled();
            boolean on = chosen.contains(entry.preset().id());
            boolean hover = enabled && mx >= bx && mx < bx + bw && my >= ry && my < ry + ROW;
            if (hover) g.fill(bx, ry, bx + bw, ry + ROW, 0x14FFE9C7);
            // Check box
            int cy = ry + 6;
            g.fill(bx + 3, cy, bx + 12, cy + 9, on ? JournalTheme.TEXT_ACCENT : 0xFF120D09);
            JournalTheme.border(g, bx + 3, cy, 9, 9, on ? JournalTheme.TEXT_ACCENT : enabled ? JournalTheme.TEXT_DIM : 0xFF3A2C1F);
            if (on) { g.fill(bx + 5, cy + 4, bx + 7, cy + 6, 0xFF1B140E); g.fill(bx + 7, cy + 2, bx + 9, cy + 6, 0xFF1B140E); }
            FlavorUi.icon(g, entry.preset(), bx + 16, ry + 4, 12);
            int textColor = enabled ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT;
            String used = Component.translatable("cuisinejournal.flavor.used_by", entry.usedBy()).getString();
            int usedW = font.width(used);
            g.drawString(font, font.plainSubstrByWidth(entry.preset().name(), bw - 36 - usedW - 6), bx + 32, ry + 2, textColor, false);
            g.drawString(font, used, bx + bw - usedW - 2, ry + 2, JournalTheme.TEXT_FAINT, false);
            FlavorStage first = entry.preset().stages().isEmpty() ? null : entry.preset().stages().get(0);
            Component summary = !enabled ? Component.translatable("cuisinejournal.flavor.disabled")
                    : first == null ? Component.translatable("cuisinejournal.flavor.no_effect")
                    : Component.literal("★" + first.minStars() + " ").append(FlavorUi.lines(first).get(0))
                    .append(entry.preset().stages().size() > 1 ? Component.translatable("cuisinejournal.flavor.stage_count", entry.preset().stages().size()) : Component.empty());
            g.pose().pushPose(); g.pose().translate(bx + 32, ry + 12, 0); g.pose().scale(0.75F, 0.75F, 1F);
            g.drawString(font, font.plainSubstrByWidth(summary.getString(), (int) ((bw - 36) / 0.75F)), 0, 0, JournalTheme.TEXT_FAINT, false);
            g.pose().popPose();
        }
        setScrollContentHeight(top - by + rows.size() * ROW + 4);
    }

    @Override protected boolean bodyClicked(double mx, double my, int button) {
        int top = bodyBounds().y + 2 + search.totalBounds().height;
        int index = (int) Math.floor((my - top) / ROW);
        if (index < 0 || index >= rows.size()) return false;
        FlavorClientState.Entry entry = rows.get(index);
        if (!entry.preset().enabled()) return true;
        if (!chosen.remove(entry.preset().id())) chosen.add(entry.preset().id());
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
        refreshButtons();
        return true;
    }

    @Override protected Component submitLabel() { return Component.translatable("cuisinejournal.flavor.picker.apply", chosen.size()); }
}
