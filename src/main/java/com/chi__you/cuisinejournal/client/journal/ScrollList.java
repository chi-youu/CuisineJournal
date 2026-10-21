package com.chi__you.cuisinejournal.client.journal;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * A virtualised vertical list.
 *
 * <p>Only the rows inside the viewport are drawn, which is what keeps a
 * thousand-dish modpack scrolling smoothly instead of building a thousand
 * widgets.</p>
 */
public final class ScrollList<T> {

    /** Draws one row; the list handles scissoring, hover and positioning. */
    public interface RowRenderer<T> {
        void render(GuiGraphics graphics, T item, int x, int y, int width, int height,
                    boolean hovered);
    }

    private final int rowHeight;
    private final int rowSpacing;
    private final RowRenderer<T> renderer;

    private List<T> items = List.of();
    private int x;
    private int y;
    private int width;
    private int height;
    private double scroll;
    private boolean dragging;
    private double dragOffset;

    public ScrollList(int rowHeight, int rowSpacing, RowRenderer<T> renderer) {
        this.rowHeight = rowHeight;
        this.rowSpacing = rowSpacing;
        this.renderer = renderer;
    }

    public void bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    public void items(List<T> items) {
        this.items = items == null ? List.of() : items;
        // A filter change can shorten the list past the current offset.
        clampScroll();
    }

    public List<T> items() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    private int stride() {
        return rowHeight + rowSpacing;
    }

    private int contentHeight() {
        return items.isEmpty() ? 0 : items.size() * stride() - rowSpacing;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - height);
    }

    private void clampScroll() {
        scroll = Math.max(0.0, Math.min(maxScroll(), scroll));
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (items.isEmpty()) {
            return;
        }
        graphics.enableScissor(x, y, x + width, y + height);

        int first = Math.max(0, (int) (scroll / stride()));
        int last = Math.min(items.size(), first + height / stride() + 2);
        int barWidth = maxScroll() > 0 ? 4 : 0;
        int rightMargin = barWidth > 0 ? barWidth + 2 : 0; // 滚动条宽度 + 2px间距

        for (int index = first; index < last; index++) {
            int rowY = y + index * stride() - (int) scroll;
            boolean hovered = mouseX >= x && mouseX < x + width - rightMargin
                    && mouseY >= rowY && mouseY < rowY + rowHeight
                    && mouseY >= y && mouseY < y + height;
            renderer.render(graphics, items.get(index), x, rowY, width - rightMargin, rowHeight,
                    hovered);
        }
        graphics.disableScissor();

        if (barWidth > 0) {
            renderScrollbar(graphics, barWidth);
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int barWidth) {
        int trackX = x + width - barWidth;
        graphics.fill(trackX, y, trackX + barWidth, y + height, 0x33000000);

        int thumbHeight = Math.min(height, Math.max(16, height * height / Math.max(1, contentHeight())));
        int travel = height - thumbHeight;
        int thumbY = y + (int) (travel * (scroll / Math.max(1, maxScroll())));
        graphics.fill(trackX, thumbY, trackX + barWidth, thumbY + thumbHeight,
                JournalTheme.TEXT_FAINT);
    }

    /** @return the item under the cursor, or {@code null} */
    public T itemAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return null;
        }
        if (maxScroll() > 0 && mouseX >= x + width - 4) return null;
        int index = (int) ((mouseY - y + scroll) / stride());
        if (index < 0 || index >= items.size()) {
            return null;
        }
        // Reject the gap between rows so a click never lands on the wrong item.
        double offsetInRow = (mouseY - y + scroll) % stride();
        return offsetInRow < rowHeight ? items.get(index) : null;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }
        double previous = scroll;
        scroll -= delta * stride();
        clampScroll();
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.scroll(previous, scroll);
        return true;
    }

    public void scrollToTop() {
        scroll = 0.0;
    }

    /** Reveal the matching row with minimal movement, preserving an already visible row. */
    public void ensureVisible(java.util.function.Predicate<T> matches) {
        if (height <= 0) return;
        for (int index = 0; index < items.size(); index++) {
            if (!matches.test(items.get(index))) continue;
            int top = index * stride();
            int bottom = top + rowHeight;
            if (top < scroll) scroll = top;
            else if (bottom > scroll + height) scroll = height < rowHeight ? top : bottom - height;
            clampScroll();
            return;
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || maxScroll() <= 0 || mx < x + width - 4 || mx >= x + width
                || my < y || my >= y + height) return false;
        int thumb = Math.min(height, Math.max(16, height * height / Math.max(1, contentHeight())));
        double top = y + (height - thumb) * scroll / maxScroll();
        dragOffset = my >= top && my < top + thumb ? my - top : thumb / 2.0;
        dragging = true;
        dragTo(my);
        return true;
    }

    private void dragTo(double my) {
        int thumb = Math.min(height, Math.max(16, height * height / Math.max(1, contentHeight())));
        double previous = scroll;
        scroll = (my - y - dragOffset) / Math.max(1, height - thumb) * maxScroll();
        clampScroll();
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.scroll(
                (int) previous / stride(), (int) scroll / stride());
    }

    public boolean mouseDragged(double mx, double my, int button) {
        if (!dragging || button != 0) return false;
        dragTo(my);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (!dragging || button != 0) return false;
        dragging = false;
        return true;
    }
}
