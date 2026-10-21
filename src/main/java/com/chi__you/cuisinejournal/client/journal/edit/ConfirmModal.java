package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Destructive-operation confirmation. Cancel is focused by default. */
public final class ConfirmModal extends JournalModal {
    private final Font font;
    private final List<Component> lines;
    private final Runnable confirm;
    private final Runnable cancel;
    private final Component actionLabel;

    public ConfirmModal(int x, int y, int width, int height, Font font, Component title,
                        List<Component> lines, Runnable confirm, Runnable cancel) {
        this(x, y, width, height, font, title, lines,
                Component.translatable("cuisinejournal.edit.confirm"), confirm, cancel);
    }

    public ConfirmModal(int x, int y, int width, int height, Font font, Component title,
                        List<Component> lines, Component action, Runnable confirm, Runnable cancel) {
        super(x, y, width, height, title, true);
        this.font = font;
        this.lines = List.copyOf(lines == null ? List.of() : lines);
        this.confirm = confirm;
        this.cancel = cancel;
        this.actionLabel = action == null ? Component.translatable("cuisinejournal.edit.confirm") : action;
        setSubmitAction(this::confirmAction);
        setCancelAction(this::cancelAction);
    }

    public ConfirmModal(int x, int y, int width, int height, Font font, Component title,
                        Component line, Runnable confirm, Runnable cancel) {
        this(x, y, width, height, font, title, List.of(line), confirm, cancel);
    }

    public ConfirmModal(Component title, List<Component> body, Component action,
                        Runnable confirm, Runnable cancel) {
        this(0, 0, 320, 200, net.minecraft.client.Minecraft.getInstance().font,
                title, body, action, confirm, cancel);
    }

    @Override public void open() {
        super.open();
        submitButton().setFocused(false);
        cancelButton().setFocused(true);
    }

    @Override protected Component submitLabel() {
        return actionLabel;
    }

    @Override protected Component cancelLabel() {
        return Component.translatable("cuisinejournal.edit.cancel");
    }

    @Override protected void renderBody(GuiGraphics g, int bodyX, int bodyY, int bodyWidth, int bodyHeight,
                                        int mouseX, int mouseY, float partialTick) {
        int y = bodyY + 4 - scrollOffset();
        for (Component line : lines) {
            for (var wrapped : font.split(line, Math.max(40, bodyWidth - 16))) {
                g.drawString(font, wrapped, bodyX + 8, y, JournalTheme.TEXT, false);
                y += 10;
            }
        }
        setScrollContentHeight(Math.max(bodyHeight, y - bodyY + scrollOffset() + 4));
    }

    @Override protected boolean submitEnabled() { return !submitWaiting(); }
    @Override protected void onSubmit() { confirmAction(); }
    @Override protected void onCancel() { cancelAction(); }

    private void confirmAction() {
        if (submitWaiting() || confirm == null) return;
        setSubmitWaiting(true);
        confirm.run();
    }

    private void cancelAction() {
        if (submitWaiting()) return;
        if (cancel != null) cancel.run();
        close();
    }

    public void acceptResult(Packets.CategoryEditResult result) {
        if (result == null) return;
        setSubmitWaiting(false);
        if (result.success()) close();
    }
}
