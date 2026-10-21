package com.chi__you.cuisinejournal.food;

import java.util.ArrayList;
import java.util.List;

/** Each span reserves real capacity units; multi-slot meals have no internal gutters. */
public final class FoodHudLayout {
    public record Span(int itemIndex, int units, int offset, int width, boolean overflow) {}
    private FoodHudLayout() {}

    public static List<Span> row(List<Integer> costs, int capacity, int cellSize) {
        List<Span> spans = new ArrayList<>();
        int used = 0, step = cellSize + 1;
        for (int i = 0; i < costs.size(); i++) {
            int units = Math.max(1, Math.min(3, costs.get(i)));
            spans.add(new Span(i, units, used * step, units * step - 1, used + units > capacity));
            used += units;
        }
        for (int i = used; i < capacity; i++) spans.add(new Span(-1, 1, i * step, cellSize, false));
        return List.copyOf(spans);
    }

    public static int width(List<Span> spans) {
        return spans.stream().mapToInt(span -> span.offset() + span.width()).max().orElse(0);
    }
}
