package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.client.EditClientState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Lists hidden categories and restores them immediately, without confirmation. */
public final class HiddenCategoriesModal extends JournalModal {
    private final Font font;
    private final Consumer<Packets.CategoryEditRequest> restore;
    private final Runnable cancel;
    private final List<Packets.CategoryMetadata> entries = new ArrayList<>();
    private Map<String, Long> expectedVersions = Map.copyOf(EditClientState.snapshot());
    private int pendingIndex = -1;
    private long pendingId;

    public HiddenCategoriesModal(int x, int y, int width, int height, Font font,
                                 List<Packets.CategoryMetadata> categories,
                                 Consumer<Packets.CategoryEditRequest> restore, Runnable cancel) {
        super(x, y, width, height, Component.translatable("cuisinejournal.edit.blocked.title"));
        this.font = font;
        this.restore = restore;
        this.cancel = cancel;
        if (categories != null) categories.stream().filter(Packets.CategoryMetadata::hidden).forEach(entries::add);
        setSubmitAction(this::closeFromButton);
        setCancelAction(this::cancelFromButton);
    }

    public HiddenCategoriesModal(Consumer<Packets.CategoryEditRequest> submit, Runnable cancel) {
        this(0, 0, 320, 200, net.minecraft.client.Minecraft.getInstance().font,
                com.chi__you.cuisinejournal.client.ClientJournalState.categoryEditor().categories(), submit, cancel);
    }

    @Override protected Component submitLabel() { return Component.translatable("cuisinejournal.edit.close"); }
    @Override protected Component cancelLabel() { return Component.translatable("cuisinejournal.edit.close"); }
    @Override protected boolean hasCancelButton() { return false; }
    @Override protected void onSubmit() { closeFromButton(); }
    @Override protected void onCancel() { cancelFromButton(); }

    @Override protected void renderBody(GuiGraphics g, int bodyX, int bodyY, int bodyWidth, int bodyHeight,
                                        int mouseX, int mouseY, float partialTick) {
        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("cuisinejournal.edit.blocked.empty"),
                    bodyX + 8, bodyY + 18, JournalTheme.TEXT_FAINT, false);
            setScrollContentHeight(bodyHeight);
            return;
        }
        int rowY = bodyY + 4 - scrollOffset();
        for (int i = 0; i < entries.size(); i++) {
            Packets.CategoryMetadata entry = entries.get(i);
            boolean parentHidden = entry.parent() != null && entries.stream()
                    .anyMatch(parent -> parent.id().equals(entry.parent()));
            boolean hovered = mouseX >= bodyX + 2 && mouseX < bodyX + bodyWidth - 2
                    && mouseY >= rowY && mouseY < rowY + 24;
            JournalTheme.card(g, bodyX + 2, rowY, bodyWidth - 4, 24, hovered, false);
            int colour = parentHidden ? JournalTheme.TEXT_FAINT : JournalTheme.TEXT;
            int textWidth = Math.max(10, bodyWidth - 28 - 10 - font.width(Component.translatable(
                    parentHidden ? "cuisinejournal.edit.blocked.need_parent" : "cuisinejournal.edit.restore")));
            g.drawString(font, font.plainSubstrByWidth(displayName(entry), textWidth), bodyX + 20, rowY + 3, colour, false);
            g.drawString(font, font.plainSubstrByWidth(entry.id().toString(), textWidth + 12), bodyX + 8, rowY + 13,
                    JournalTheme.TEXT_FAINT, false);
            PixelIcons.drawSourceBadge(g, entry.source() == 1 ? PixelIcons.Icon.CREATED : PixelIcons.Icon.PACKET,
                    bodyX + 6, rowY + 3, entry.source() == 1 ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_FAINT,
                    entry.source() == 2);
            Component action = Component.translatable(parentHidden ? "cuisinejournal.edit.blocked.need_parent" : "cuisinejournal.edit.restore");
            int actionX = bodyX + bodyWidth - 6 - font.width(action);
            g.drawString(font, action, actionX, rowY + 8,
                    parentHidden ? JournalTheme.TEXT_FAINT : JournalTheme.TEXT_ACCENT, false);
            rowY += 24;
        }
        setScrollContentHeight(8 + entries.size() * 24);
    }

    @Override protected boolean bodyClicked(double mx, double my, int button) {
        if (submitWaiting()) return true;
        // my is already in unscrolled body coordinates.
        int index = (int) Math.floor((my - bodyBounds().y - 4) / 24.0D);
        if (mx < bodyBounds().x + 2 || mx >= bodyBounds().x + bodyBounds().width - 2
                || index < 0 || index >= entries.size()) return false;
        restoreEntry(index);
        return true;
    }

    private void restoreEntry(int index) {
        if (restore == null || submitWaiting()) return;
        Packets.CategoryMetadata entry = entries.get(index);
        if (entry.parent() != null && entries.stream().anyMatch(parent -> parent.id().equals(entry.parent()))) return;
        pendingIndex = index;
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
        pendingId = entry.id().hashCode();
        setSubmitWaiting(true);
        restore.accept(new Packets.CategoryEditRequest(0,
                entry.parent() == null ? Packets.CategoryEditRequest.Action.RESTORE_ROOT
                        : Packets.CategoryEditRequest.Action.RESTORE_SUB,
                entry.id(), entry.parent(), entry.literalTitle() ? entry.titleKey()
                        : Component.translatable(entry.titleKey()).getString(), null, List.of(), expectedVersions));
    }

    public void acceptResult(Packets.CategoryEditResult result) {
        if (result == null) return;
        setSubmitWaiting(false);
        // Keep the row until the authoritative SyncCategoryEditor arrives.
        // The host can call refreshEntries() after accepting that sync.
        pendingIndex = -1;
        pendingId = 0;
    }

    public void refreshEntries(List<Packets.CategoryMetadata> categories) {
        expectedVersions = Map.copyOf(EditClientState.snapshot());
        entries.clear();
        if (categories != null) categories.stream().filter(Packets.CategoryMetadata::hidden).forEach(entries::add);
    }

    public List<Packets.CategoryMetadata> entries() { return List.copyOf(entries); }

    private void closeFromButton() { if (!submitWaiting()) close(); }
    private void cancelFromButton() { if (!submitWaiting()) { if (cancel != null) cancel.run(); close(); } }

    private static String displayName(Packets.CategoryMetadata category) {
        return category.literalTitle() ? category.titleKey() : Component.translatable(category.titleKey()).getString();
    }
}
