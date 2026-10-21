package com.chi__you.cuisinejournal.food.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;

/** Immutable display data, with no dependency on client rendering classes. */
public interface FoodTooltipComponent extends TooltipComponent {
    record TextLine(Component text) implements FoodTooltipComponent {}
    record NutritionRow(int nutrition, float saturationModifier) implements FoodTooltipComponent {}
    record Spacer(int height) implements FoodTooltipComponent {}
    record StatBar(Component label, int labelWidth, int valueX, float fill, int fillColor,
                   float ghostFill, int ghostColor, Component value, float overflowFill) implements FoodTooltipComponent {
        public StatBar(Component label, int labelWidth, int valueX, float fill, int fillColor,
                       float ghostFill, int ghostColor, Component value) {
            this(label,labelWidth,valueX,fill,fillColor,ghostFill,ghostColor,value,0);
        }
    }
    record ValueRow(Component label, int valueX, Component value) implements FoodTooltipComponent {}
    record GroupRow(Component label, Component value) {}
    record NoStackGroup(Component heading, List<GroupRow> rows, int valueX) implements FoodTooltipComponent {
        public NoStackGroup { rows = List.copyOf(rows); }
    }
    record EffectEntry(MobEffect effect, Component text) {}
    record EffectGrid(List<EffectEntry> entries, int columns) implements FoodTooltipComponent {
        public EffectGrid {
            entries = List.copyOf(entries);
            columns = Math.max(1, Math.min(2, columns));
        }
    }
    record Divider(int width) implements FoodTooltipComponent {}
    record HintLine(Component before, Component key, Component after) implements FoodTooltipComponent {}
}
