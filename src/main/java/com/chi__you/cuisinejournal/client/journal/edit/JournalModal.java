package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable modal frame for category editing. The owner forwards input while
 * this object is open; the modal consumes every event so the page underneath
 * cannot receive a click intended for the dialog.
 */
public abstract class JournalModal {
    public interface Focusable {
        void setFocused(boolean focused);
        boolean isFocused();
    }

    private static final int HEADER_HEIGHT = 16;
    private static final int FOOTER_HEIGHT = 30;
    private static final int PADDING = 8;

    private Rectangle contentArea;
    private Rectangle bounds;
    private final Component title;
    private final boolean dangerTitle;
    private final JournalButton cancelButton;
    private final JournalButton submitButton;
    private final List<Focusable> focusables = new ArrayList<>();

    private Rectangle bodyBounds;
    private boolean open;
    private boolean submitWaiting;
    private long submitStarted;
    private int contentHeight;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;

    protected JournalModal(int contentX, int contentY, int contentWidth, int contentHeight,
                           Component title) {
        this(contentX, contentY, contentWidth, contentHeight, title, false);
    }

    protected JournalModal(int contentX, int contentY, int contentWidth, int contentHeight,
                           Component title, boolean dangerTitle) {
        this.contentArea = new Rectangle(contentX, contentY, Math.max(0, contentWidth), Math.max(0, contentHeight));
        this.title = title == null ? Component.empty() : title;
        this.dangerTitle = dangerTitle;

        this.bounds = new Rectangle();
        this.bodyBounds = new Rectangle();
        relayout();
        this.contentHeight = bodyBounds.height;

        // Subclass fields are not initialised yet, so labels are resolved lazily in layoutButtons().
        this.cancelButton = new JournalButton(Component.empty(), JournalButton.Variant.NORMAL, this::cancelFromButton);
        this.submitButton = new JournalButton(Component.empty(), JournalButton.Variant.PRIMARY, this::submitFromButton);
    }

    public void open() {
        open = true;
        scrollOffset = 0;
        draggingScrollbar = false;
        submitWaiting = false;
        layoutButtons();
        onOpened();
    }

    public void openCentered(int contentX, int contentY, int contentWidth, int contentHeight) {
        bounds(contentX, contentY, contentWidth, contentHeight);
        open();
    }

    public void close() {
        if (!open) return;
        open = false;
        draggingScrollbar = false;
        submitWaiting = false;
        clearFocusables();
        onClosed();
    }

    public boolean isOpen() { return open; }

    /** True while a text field inside the modal owns the keyboard (letters must type, not trigger hotkeys). */
    public boolean textInputFocused() {
        if (!open) return false;
        for (Focusable focusable : focusables) {
            if (focusable instanceof JournalTextField && focusable.isFocused()) return true;
        }
        return false;
    }
    public Rectangle bounds() { return new Rectangle(bounds); }
    public Rectangle bodyBounds() { return new Rectangle(bodyBounds); }

    /** Updates the content area and re-centres the modal inside it. */
    public void bounds(int contentX, int contentY, int contentWidth, int contentHeight) {
        this.contentArea = new Rectangle(contentX, contentY, Math.max(0, contentWidth), Math.max(0, contentHeight));
        relayout();
        this.contentHeight = Math.max(bodyBounds.height, this.contentHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll());
        layoutButtons();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!open) return;
        renderWithFont(graphics, Minecraft.getInstance().font, mouseX, mouseY, partialTick);
    }

    /** Host-friendly render overload; the supplied font is intentionally authoritative. */
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
        if (!open) return;
        renderWithFont(graphics, font == null ? Minecraft.getInstance().font : font,
                mouseX, mouseY, partialTick);
    }

    public void tick() {
        if (open && submitWaiting && System.nanoTime()-submitStarted>15_000_000_000L) {
            submitWaiting=false;
            var player=Minecraft.getInstance().player;
            if(player!=null)player.displayClientMessage(Component.translatable("cuisinejournal.edit.timeout"),false);
        }
        if (open) onTick();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (!inside(contentArea, mouseX, mouseY)) return true;
        if (!inside(bounds, mouseX, mouseY)) {
            cancelFromButton();
            return true;
        }

        JournalButton footer = footerButton();
        if ((hasCancelButton() && cancelButton.mouseClicked(mouseX, mouseY, button))
                || submitButton.mouseClicked(mouseX, mouseY, button)
                || (footer != null && footer.mouseClicked(mouseX, mouseY, button))) {
            return true;
        }
        ModalScrollbar scrollbar = scrollbar();
        if (button == 0 && scrollbar.contains(mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollbarGrabOffset = scrollbar.grabOffset(mouseY);
            setScrollOffset(scrollbar.offset(mouseY, scrollbarGrabOffset));
            return true;
        }
        if (!inside(bodyBounds, mouseX, mouseY)) return true;
        // Body widgets live in unscrolled body coordinates.
        double bodyMouseY = mouseY + scrollOffset;
        if (bodyClickedFirst(mouseX, bodyMouseY, button)) return true;
        for (Focusable focusable : focusables) {
            if (focusable instanceof JournalTextField field && field.mouseClicked(mouseX, bodyMouseY, button)) {
                focusOnly(field);
                return true;
            }
            if (focusable instanceof JournalButton field && field.mouseClicked(mouseX, bodyMouseY, button)) {
                focusOnly(field);
                return true;
            }
        }
        bodyClicked(mouseX, bodyMouseY, button);
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        boolean consumed = (hasCancelButton() && cancelButton.mouseReleased(mouseX, mouseY, button))
                | submitButton.mouseReleased(mouseX, mouseY, button);
        JournalButton footer = footerButton();
        if (footer != null) consumed |= footer.mouseReleased(mouseX, mouseY, button);
        if (!open) return true;
        for (Focusable focusable : focusables) {
            if (focusable instanceof JournalTextField field) consumed |= field.isFocused();
            if (focusable instanceof JournalButton field) consumed |= field.mouseReleased(mouseX, mouseY + scrollOffset, button);
        }
        return bodyReleased(mouseX, mouseY + scrollOffset, button) || consumed;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (open && button == 0 && draggingScrollbar) {
            setScrollOffset(scrollbar().offset(mouseY, scrollbarGrabOffset));
            return true;
        }
        return open;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!open) return false;
        if (!inside(bounds, mouseX, mouseY)) return true;
        // Body widgets (icon grid, lists) get first go; the padding strips, the scrollbar track
        // and anything they decline scroll the modal itself.
        if (inside(bodyBounds, mouseX, mouseY) && bodyScrolled(mouseX, mouseY + scrollOffset, delta)) return true;
        setScrollOffset(scrollOffset - (int) Math.round(delta * 8.0D));
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelFromButton();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            focusNext((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        for (Focusable focusable : focusables) {
            if (focusable instanceof JournalTextField field && field.isFocused()
                    && field.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (focusable instanceof JournalButton field && field.isFocused()
                    && field.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (submitButton.isFocused() && submitButton.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (hasCancelButton() && cancelButton.isFocused() && cancelButton.keyPressed(keyCode, scanCode, modifiers)) return true;
        return bodyKeyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char character, int modifiers) {
        if (!open) return false;
        for (Focusable focusable : focusables) {
            if (focusable instanceof JournalTextField field && field.isFocused()
                    && field.charTyped(character, modifiers)) return true;
        }
        return bodyCharTyped(character, modifiers);
    }

    protected void renderBody(GuiGraphics graphics, int bodyX, int bodyY, int bodyWidth,
                              int bodyHeight, int mouseX, int mouseY, float partialTick) { }
    protected boolean bodyClicked(double mouseX, double mouseY, int button) { return false; }
    /** Called before the generic focusable loop; used by forms that manage focus themselves. */
    protected boolean bodyClickedFirst(double mouseX, double mouseY, int button) { return false; }
    /** Optional bottom-left footer button (for example the form's delete/block action). */
    protected JournalButton footerButton() { return null; }
    /** Single-button dialogs (for example the hidden-category list) return false. */
    protected boolean hasCancelButton() { return true; }
    protected boolean bodyReleased(double mouseX, double mouseY, int button) { return false; }
    protected boolean bodyScrolled(double mouseX, double mouseY, double delta) { return false; }
    protected boolean bodyKeyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    protected boolean bodyCharTyped(char character, int modifiers) { return false; }
    protected void onOpened() { }
    protected void onTick() { }
    protected void onClosed() { }

    protected void onSubmit() {
        if (!submitEnabled() || submitWaiting) return;
        if (submitAction != null) submitAction.run(); else close();
    }

    protected void onCancel() {
        if (cancelAction != null) cancelAction.run();
    }

    protected boolean submitEnabled() { return true; }
    protected Component submitLabel() { return Component.translatable("cuisinejournal.edit.confirm"); }
    protected Component cancelLabel() { return Component.translatable("cuisinejournal.edit.cancel"); }

    protected void addFocusable(Focusable focusable) {
        if (focusable != null && !focusables.contains(focusable)) focusables.add(focusable);
    }
    protected void clearFocusables() { focusables.clear(); }
    protected void focusNext(boolean backwards) {
        List<Focusable> all = new ArrayList<>(focusables);
        if (hasCancelButton()) all.add(cancelButton);
        all.add(submitButton);
        if (all.isEmpty()) return;
        int current = -1;
        for (int i = 0; i < all.size(); i++) if (all.get(i).isFocused()) { current = i; break; }
        int next = current < 0 ? (backwards ? all.size() - 1 : 0)
                : (current + (backwards ? -1 : 1) + all.size()) % all.size();
        for (Focusable focusable : all) focusable.setFocused(false);
        all.get(next).setFocused(true);
    }
    protected void setScrollContentHeight(int contentHeight) {
        this.contentHeight = Math.max(bodyBounds.height, contentHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll());
    }
    protected int scrollOffset() { return scrollOffset; }
    protected void resetScroll() { scrollOffset = 0; draggingScrollbar = false; }
    private void setScrollOffset(int offset) {
        int previous = scrollOffset;
        scrollOffset = Math.max(0, Math.min(maxScroll(), offset));
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.scroll(previous, scrollOffset);
    }
    private ModalScrollbar scrollbar() {
        return ModalScrollbar.layout(bounds.x + bounds.width - 4, bodyBounds.y,
                bodyBounds.height, contentHeight, scrollOffset);
    }
    protected boolean submitWaiting() { return submitWaiting; }
    protected void setSubmitWaiting(boolean waiting) {
        if(waiting&&!submitWaiting)submitStarted=System.nanoTime();
        submitWaiting = waiting;
    }
    public void waiting(boolean waiting) { setSubmitWaiting(waiting); }
    protected JournalButton submitButton() { return submitButton; }
    protected JournalButton cancelButton() { return cancelButton; }
    protected void setSubmitAction(Runnable action) { submitAction = action; }
    protected void setCancelAction(Runnable action) { cancelAction = action; }
    protected List<Focusable> focusables() { return List.copyOf(focusables); }

    private Runnable submitAction;
    private Runnable cancelAction;

    private void submitFromButton() { onSubmit(); }
    private void cancelFromButton() {
        if (submitWaiting) return;
        if (onBack()) return;
        onCancel();
        close();
    }

    /** Cancel / Esc inside a sub-step: return true to stay open (for example back to a list). */
    protected boolean onBack() { return false; }
    private int maxScroll() { return Math.max(0, contentHeight - bodyBounds.height); }
    private void focusOnly(Focusable target) {
        for (Focusable focusable : focusables) focusable.setFocused(focusable == target);
        cancelButton.setFocused(cancelButton == target);
        submitButton.setFocused(submitButton == target);
    }
    private void layoutButtons() {
        if (submitButton == null || cancelButton == null) return;
        Component submitText = submitLabel();
        Component cancelText = cancelLabel();
        submitButton.setLabel(submitText);
        cancelButton.setLabel(cancelText);
        Font font = Minecraft.getInstance().font;
        int buttonY = bounds.y + bounds.height - 8 - 14;
        int submitWidth = Math.max(52, 12 + font.width(submitButton.label()));
        int cancelWidth = Math.max(52, 12 + font.width(cancelButton.label()));
        submitButton.setBounds(bounds.x + bounds.width - PADDING - submitWidth, buttonY, submitWidth, 14);
        if (hasCancelButton()) cancelButton.setBounds(submitButton.x() - 4 - cancelWidth, buttonY, cancelWidth, 14);
        else cancelButton.setBounds(0, 0, 0, 0);
        JournalButton footer = footerButton();
        if (footer != null) {
            int footerWidth = Math.max(40, 12 + font.width(footer.label()));
            footer.setBounds(bounds.x + PADDING, buttonY, footerWidth, 14);
        }
    }
    /** Re-reads button labels after subclass state (for example a waiting label) changes. */
    protected void refreshButtons() { layoutButtons(); }

    /** Largest width / height of the dialog; big editors override these (called from the constructor). */
    protected int maxModalWidth() { return 220; }
    protected int maxModalHeight() { return 180; }

    private void relayout() {
        int modalWidth = Math.max(0, Math.min(maxModalWidth(), contentArea.width - 16));
        int modalHeight = Math.max(HEADER_HEIGHT + FOOTER_HEIGHT + 1,
                Math.min(Math.max(0, contentArea.height - 16), maxModalHeight()));
        int modalX = contentArea.x + Math.max(0, (contentArea.width - modalWidth) / 2);
        int modalY = contentArea.y + Math.max(0, (contentArea.height - modalHeight) / 2);
        bounds = new Rectangle(modalX, modalY, modalWidth, modalHeight);
        bodyBounds = new Rectangle(modalX + PADDING, modalY + HEADER_HEIGHT,
                Math.max(0, modalWidth - PADDING * 2),
                Math.max(0, modalHeight - HEADER_HEIGHT - FOOTER_HEIGHT));
    }

    private void renderWithFont(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(contentArea.x, contentArea.y,
                contentArea.x + contentArea.width, contentArea.y + contentArea.height, JournalTheme.MASK);
        JournalTheme.panel(graphics, bounds.x, bounds.y, bounds.width, bounds.height);
        graphics.drawString(font, title, bounds.x + 6, bounds.y + 4,
                dangerTitle ? JournalTheme.DANGER : JournalTheme.TEXT, false);
        graphics.fill(bounds.x + 1, bounds.y + HEADER_HEIGHT - 1,
                bounds.x + bounds.width - 1, bounds.y + HEADER_HEIGHT, 0xFF3A2C1F);
        graphics.enableScissor(bodyBounds.x, bodyBounds.y,
                bodyBounds.x + bodyBounds.width, bodyBounds.y + bodyBounds.height);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);
        renderBody(graphics, bodyBounds.x, bodyBounds.y + scrollOffset,
                bodyBounds.width, contentHeight, mouseX, mouseY + scrollOffset, partialTick);
        graphics.pose().popPose();
        graphics.disableScissor();
        if (contentHeight > bodyBounds.height) {
            ModalScrollbar scrollbar = scrollbar();
            Rectangle track = scrollbar.track(), thumb = scrollbar.thumb();
            graphics.fill(track.x, track.y, track.x + track.width, track.y + track.height, 0x662A2018);
            graphics.fill(thumb.x, thumb.y, thumb.x + thumb.width, thumb.y + thumb.height,
                    draggingScrollbar || scrollbar.contains(mouseX, mouseY) ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_FAINT);
        }
        if (hasCancelButton()) cancelButton.render(graphics, font, mouseX, mouseY);
        JournalButton footer = footerButton();
        if (footer != null) footer.render(graphics, font, mouseX, mouseY);
        submitButton.setEnabled(submitEnabled() && !submitWaiting);
        submitButton.render(graphics, font, mouseX, mouseY);
        graphics.pose().popPose();
    }
    private static boolean inside(Rectangle rectangle, double x, double y) {
        return x >= rectangle.x && x < rectangle.x + rectangle.width
                && y >= rectangle.y && y < rectangle.y + rectangle.height;
    }
}
