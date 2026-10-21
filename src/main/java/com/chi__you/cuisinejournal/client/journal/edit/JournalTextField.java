package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.awt.Rectangle;
import java.util.function.Consumer;

/** EditBox wrapper with a stable label and error row. */
public final class JournalTextField implements JournalModal.Focusable {
    private static final int INPUT_HEIGHT = 14;
    private static final int LABEL_HEIGHT = 9;
    private static final int LABEL_GAP = 3;
    private static final int ERROR_HEIGHT = 9;
    /** Unbordered EditBoxes draw text flush at their origin, so the box sits inset inside the frame. */
    private static final int INSET_X = 4;
    private static final int INSET_Y = 3;
    private static final int TEXT_HEIGHT = 8;

    private final Font font;
    private final EditBox editBox;
    private final Component label;
    private Rectangle totalBounds;
    private Component error;
    private boolean enabled = true;

    public JournalTextField(Font font, int x, int y, int width, Component label) {
        this.font = font;
        this.label = label == null ? Component.empty() : label;
        this.editBox = new EditBox(font, x + INSET_X, y + LABEL_HEIGHT + LABEL_GAP + INSET_Y,
                Math.max(0, width - INSET_X * 2), TEXT_HEIGHT, this.label);
        this.editBox.setBordered(false);
        this.editBox.setTextColor(JournalTheme.TEXT);
        this.editBox.setTextColorUneditable(0xFF4E443A);
        this.totalBounds = new Rectangle(x, y, Math.max(0, width), LABEL_HEIGHT + LABEL_GAP + INPUT_HEIGHT + ERROR_HEIGHT);
    }

    public void setBounds(int x, int y, int width) {
        int safeWidth = Math.max(0, width);
        editBox.setX(x + INSET_X);
        editBox.setY(y + LABEL_HEIGHT + LABEL_GAP + INSET_Y);
        editBox.setWidth(Math.max(0, safeWidth - INSET_X * 2));
        totalBounds = new Rectangle(x, y, safeWidth, LABEL_HEIGHT + LABEL_GAP + INPUT_HEIGHT + ERROR_HEIGHT);
    }

    /** The input rectangle, excluding label and reserved error row. */
    public Rectangle bounds() {
        return new Rectangle(totalBounds.x, totalBounds.y + LABEL_HEIGHT + LABEL_GAP, totalBounds.width, INPUT_HEIGHT);
    }

    public Rectangle totalBounds() { return new Rectangle(totalBounds); }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, label, totalBounds.x, totalBounds.y, JournalTheme.TEXT_DIM, false);
        Rectangle input = bounds();
        int border = error != null ? JournalTheme.DANGER
                : isFocused() ? JournalTheme.TEXT_DIM : 0xFF3A2C1F;
        graphics.fill(input.x, input.y, input.x + input.width, input.y + input.height, 0xFF140F0A);
        JournalTheme.border(graphics, input.x, input.y, input.width, input.height, border);
        editBox.render(graphics, mouseX, mouseY, 0.0F);
        if (error != null) {
            graphics.drawString(font, error, input.x, input.y + INPUT_HEIGHT + 1,
                    JournalTheme.DANGER, false);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean hit = contains(mouseX, mouseY);
        editBox.setFocused(hit && enabled);
        if (hit && enabled) {
            // Clicks anywhere on the frame land inside the inset box so the caret still follows the mouse.
            double clampedX = Math.max(editBox.getX(), Math.min(editBox.getX() + editBox.getWidth() - 1, mouseX));
            double clampedY = editBox.getY() + 1;
            editBox.mouseClicked(clampedX, clampedY, button);
        }
        return hit;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return isFocused() && enabled && editBox.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char character, int modifiers) {
        return isFocused() && enabled && editBox.charTyped(character, modifiers);
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= totalBounds.x && mouseX < totalBounds.x + totalBounds.width
                && mouseY >= totalBounds.y && mouseY < totalBounds.y + totalBounds.height;
    }

    public String getValue() { return editBox.getValue(); }
    public void setValue(String value) { editBox.setValue(value == null ? "" : value); }
    public void setHint(String hint) { editBox.setHint(Component.literal(hint == null ? "" : hint)); }
    public void setError(Component error) { this.error = error; }
    public void clearError() { this.error = null; }
    public boolean isValid() { return error == null; }
    public void setMaxLength(int maxLength) { editBox.setMaxLength(Math.max(0, maxLength)); }
    public void setResponder(Consumer<String> responder) { editBox.setResponder(responder == null ? value -> { } : responder); }
    @Override public void setFocused(boolean focused) { editBox.setFocused(focused && enabled); }
    @Override public boolean isFocused() { return editBox.isFocused(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; editBox.setEditable(enabled); if (!enabled) setFocused(false); }
    public boolean isEnabled() { return enabled; }
    public EditBox editBox() { return editBox; }
}
