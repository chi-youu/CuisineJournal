package com.chi__you.cuisinejournal.client.journal;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Fixed-size cells, virtualised by row. Each instance keeps its own scroll and selection. */
public final class GridList<T> {
    public interface CellRenderer<T> {
        void render(GuiGraphics graphics, T item, int x, int y, int width, int height,
                    boolean hovered, boolean selected);
    }

    private final int preferredWidth;
    private final int cellHeight;
    private final int gap;
    private final boolean stretch;
    private final CellRenderer<T> renderer;
    private List<T> items = List.of();
    private T selected;
    private int x, y, width, height, columns = 1, cellWidth;
    private int gutter = 5;
    private boolean draggingScrollbar;
    private double scroll;
    private java.util.function.Predicate<T> selectionModel;
    public void selectionModel(java.util.function.Predicate<T> selectionModel) { this.selectionModel = selectionModel; }

    public GridList(int cellWidth, int cellHeight, int gap, boolean stretch,
                    CellRenderer<T> renderer) {
        if (cellWidth <= 0 || cellHeight <= 0 || gap < 0) {
            throw new IllegalArgumentException("Invalid grid cell dimensions");
        }
        this.preferredWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.gap = gap;
        this.stretch = stretch;
        this.renderer = renderer;
    }

    public void bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        int nominalColumns = Math.max(1, (width + gap) / (preferredWidth + gap));
        int spare = width - nominalColumns * preferredWidth - (nominalColumns - 1) * gap;
        gutter = Math.max(1, Math.min(5, spare));
        int usable = Math.max(0, width - gutter);
        columns = Math.max(1, (usable + gap) / (preferredWidth + gap));
        cellWidth = Math.max(1, stretch ? (usable - (columns - 1) * gap) / columns
                : Math.min(preferredWidth, Math.max(1, usable)));
        clampScroll();
    }

    public void items(List<T> values) {
        items = values == null ? List.of() : List.copyOf(values);
        if (selected != null && !items.contains(selected)) selected = null;
        clampScroll();
    }

    public List<T> items() { return items; }
    public int columns() { return columns; }
    public T selected() { return selected; }
    public int[] itemBounds(T value) {
        int index = items.indexOf(value);
        if (index < 0) return null;
        int row = index / columns;
        int col = index % columns;
        return new int[]{x + col * (cellWidth + gap), y + row * (cellHeight + gap) - (int) scroll, cellWidth, cellHeight};
    }
    public void selected(T value) { selected = value != null && items.contains(value) ? value : null; }
    public double scroll() { return scroll; }
    public void scroll(double value) { scroll = value; clampScroll(); }
    public void scrollToTop() { scroll = 0; }
    public int contentHeight() {
        int rows = (items.size() + columns - 1) / columns;
        return rows == 0 ? 0 : rows * (cellHeight + gap) - gap;
    }

    private int maxScroll() { return Math.max(0, contentHeight() - height); }
    private void clampScroll() { scroll = Math.max(0, Math.min(maxScroll(), scroll)); }

    public boolean contains(double mx, double my) {
        return width > 0 && height > 0 && mx >= x && mx < x + width && my >= y && my < y + height;
    }

    public T itemAt(double mx, double my) {
        if (!contains(mx, my) || mx >= x + width - gutter) return null;
        double localX = mx - x;
        double localY = my - y + (int) scroll;
        int col = (int) (localX / (cellWidth + gap));
        int row = (int) (localY / (cellHeight + gap));
        if (col >= columns || localX % (cellWidth + gap) >= cellWidth
                || localY % (cellHeight + gap) >= cellHeight) return null;
        int index = row * columns + col;
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx, my)) return false;
        double previous = scroll;
        scroll -= delta * (cellHeight + gap);
        clampScroll();
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.scroll(previous, scroll);
        return true;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !contains(mx, my) || maxScroll() <= 0) return false;
        if (mx >= x + width - gutter) {
            draggingScrollbar = true;
            dragTo(my);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        if (!draggingScrollbar || button != 0) return false;
        dragTo(my);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (button != 0 || !draggingScrollbar) return false;
        draggingScrollbar = false;
        return true;
    }

    private void dragTo(double mouseY) {
        double previous = scroll;
        int trackHeight = Math.max(1, height);
        int thumb = Math.min(height, Math.max(8, height * height / Math.max(1, contentHeight())));
        int travel = Math.max(1, trackHeight - thumb);
        double fraction = Math.max(0.0, Math.min(1.0, (mouseY - y - thumb / 2.0) / travel));
        scroll = fraction * maxScroll();
        clampScroll();
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.scroll(
                (int) previous / (cellHeight + gap), (int) scroll / (cellHeight + gap));
    }

    public void moveSelection(int offset) {
        if (items.isEmpty()) return;
        int index = selected == null ? 0 : Math.max(0, Math.min(items.size() - 1, items.indexOf(selected) + offset));
        selected = items.get(index);
        int rowY = index / columns * (cellHeight + gap);
        if (rowY < scroll) scroll = rowY;
        if (rowY + cellHeight > scroll + height) scroll = rowY + cellHeight - height;
        clampScroll();
    }

    public void render(GuiGraphics g, int mx, int my) {
        if (width <= 0 || height <= 0 || items.isEmpty()) return;
        int stride = cellHeight + gap;
        int first = (int) scroll / stride;
        int last = Math.min((items.size() + columns - 1) / columns, first + height / stride + 2);
        T hovered = itemAt(mx, my);
        g.enableScissor(Math.max(0, x - 1), Math.max(0, y - 1), x + width + 1, y + height + 1);
        for (int row = first; row < last; row++) {
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= items.size()) break;
                T item = items.get(index);
                renderer.render(g, item, x + col * (cellWidth + gap), y + row * stride - (int) scroll,
                        cellWidth, cellHeight, item.equals(hovered), selectionModel == null ? item.equals(selected) : selectionModel.test(item));
            }
        }
        g.disableScissor();
        if (maxScroll() > 0) {
            int thumb = Math.min(height, Math.max(8, height * height / Math.max(1, contentHeight())));
            int thumbY = y + (int) ((height - thumb) * scroll / maxScroll());
            int barWidth = Math.max(1, gutter - 1);
            g.fill(x + width - barWidth, y, x + width, y + height, JournalTheme.BAR_TRACK);
            g.fill(x + width - barWidth, thumbY, x + width, thumbY + thumb, JournalTheme.TEXT_FAINT);
        }
    }
}
