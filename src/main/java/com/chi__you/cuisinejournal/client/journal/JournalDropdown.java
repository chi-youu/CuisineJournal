package com.chi__you.cuisinejournal.client.journal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** A single popup menu owned by a journal page; no Screen widgets or server writes. */
public final class JournalDropdown<T> {
    public record Option<T>(T value, Component label) { }
    private final Component title;
    private final Consumer<T> responder;
    private List<Option<T>> options = List.of();
    private T value;
    private int x, y, width, pageRight, pageBottom, firstOption;
    private boolean open;

    public JournalDropdown(Component title, T initial, Consumer<T> responder) {
        this.title = title;
        this.value = initial;
        this.responder = responder;
    }

    public void options(List<Option<T>> values) {
        options = List.copyOf(values);
        if (options.stream().noneMatch(o -> Objects.equals(o.value(), value)) && !options.isEmpty()) {
            value = options.get(0).value();
        }
        firstOption = Math.min(firstOption, Math.max(0, options.size() - visibleOptions()));
    }
    public T value() { return value; }
    public void value(T selected) {
        if (options.stream().noneMatch(option -> Objects.equals(option.value(), selected))) return;
        value = selected; open = false; firstOption = 0;
    }
    public int preferredWidth(Font font) {
        return font.width(title.copy().append(": ").append(label())) + 20;
    }

    /** Width that fits the longest option, so the button does not resize when the choice changes. */
    public int stableWidth(Font font) {
        int widest = 0;
        for (Option<T> option : options) widest = Math.max(widest, font.width(option.label()));
        return font.width(title.copy().append(": ")) + widest + 20;
    }
    public void close() { open = false; }
    public boolean isOpen() { return open; }

    public void bounds(int x, int y, int width, int pageRight, int pageBottom) {
        this.x = x; this.y = y; this.width = width;
        this.pageRight = pageRight; this.pageBottom = pageBottom;
    }

    private Component label() {
        return options.stream().filter(o -> Objects.equals(o.value(), value)).findFirst()
                .map(Option::label).orElse(Component.empty());
    }
    private int visibleOptions() { return Math.max(1, Math.min(options.size(), (pageBottom - y - 14) / 13)); }
    private int popupWidth(Font font) {
        int wanted = width;
        for (Option<T> option : options) wanted = Math.max(wanted, font.width(option.label()) + 10);
        return Math.max(width, Math.min(wanted, pageRight - x));
    }

    public void renderButton(GuiGraphics g, Font font, int mx, int my) {
        boolean hovered = inside(mx, my, x, y, width, 12);
        JournalTheme.card(g, x, y, width, 12, hovered, open);
        Component value = label();
        int arrowWidth = font.width("\u25be");
        int valueWidth = Math.max(0, width - 9 - arrowWidth);
        String titleText = font.plainSubstrByWidth(title.getString() + ": ", valueWidth);
        int valueX = x + 3 + font.width(titleText);
        g.drawString(font, titleText, x + 3, y + 2, JournalTheme.TEXT_DIM, false);
        g.drawString(font, font.plainSubstrByWidth(value.getString(),
                        Math.max(0, x + width - 5 - arrowWidth - valueX)),
                valueX, y + 2, JournalTheme.TEXT, false);
        g.drawString(font, "\u25be", x + width - 3 - arrowWidth, y + 2, JournalTheme.TEXT_DIM, false);
    }

    public void renderPopup(GuiGraphics g, Font font, int mx, int my) {
        if (!open) return;
        int popupWidth = popupWidth(font), count = visibleOptions();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x, y + 13, x + popupWidth, y + 13 + count * 13, JournalTheme.PANEL_BOTTOM);
        JournalTheme.border(g, x, y + 13, popupWidth, count * 13, JournalTheme.PANEL_BORDER);
        for (int row = 0; row < count && row + firstOption < options.size(); row++) {
            Option<T> option = options.get(row + firstOption);
            int rowY = y + 13 + row * 13;
            JournalTheme.card(g, x + 1, rowY, popupWidth - 2, 13,
                    inside(mx, my, x, rowY, popupWidth, 13), Objects.equals(option.value(), value));
            g.drawString(font, font.plainSubstrByWidth(option.label().getString(), popupWidth - 8),
                    x + 4, rowY + 2, JournalTheme.TEXT, false);
        }
        g.pose().popPose();
    }

    public boolean mouseClicked(Font font, double mx, double my) {
        if (inside(mx, my, x, y, width, 12)) {
            open = !open;
            com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
            return true;
        }
        if (!open) return false;
        if (inside(mx, my, x, y + 13, popupWidth(font), visibleOptions() * 13)) {
            int index = firstOption + (int) ((my - y - 13) / 13);
            if (index < options.size()) {
                com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
                value = options.get(index).value();
                responder.accept(value);
            }
            open = false;
            return true;
        }
        open = false;
        return true;
    }

    public boolean mouseScrolled(Font font, double mx, double my, double delta) {
        if (!open || !inside(mx, my, x, y + 13, popupWidth(font), visibleOptions() * 13)) return false;
        int previous = firstOption;
        firstOption = Math.max(0, Math.min(Math.max(0, options.size() - visibleOptions()),
                firstOption - (int) Math.signum(delta)));
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.scroll(previous, firstOption);
        return true;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
