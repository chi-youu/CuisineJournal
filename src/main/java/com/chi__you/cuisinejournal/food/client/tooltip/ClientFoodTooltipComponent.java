package com.chi__you.cuisinejournal.food.client.tooltip;

import com.chi__you.cuisinejournal.food.tooltip.FoodTooltipComponent;
import com.chi__you.cuisinejournal.food.tooltip.FoodBarScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import static com.chi__you.cuisinejournal.food.tooltip.TooltipText.*;

/** Pixel-aligned rows measured with the same font used by Minecraft's tooltip. */
public final class ClientFoodTooltipComponent implements ClientTooltipComponent {
    private static final int EFFECT_TEXT_OFFSET = 12;
    private static final int COLUMN_GAP = 12;
    private final FoodTooltipComponent component;
    private final java.util.List<net.minecraft.util.FormattedCharSequence> textLines;

    public ClientFoodTooltipComponent(FoodTooltipComponent component) {
        this.component = component;
        var minecraft = Minecraft.getInstance();
        int available = Math.max(120, Math.min(360, minecraft.getWindow().getGuiScaledWidth() - 24));
        textLines = component instanceof FoodTooltipComponent.TextLine line
                ? minecraft.font.split(line.text(), available) : java.util.List.of();
    }

    @Override
    public int getHeight() {
        if (component instanceof FoodTooltipComponent.Spacer spacer) return spacer.height();
        if (component instanceof FoodTooltipComponent.Divider) return 5;
        if (component instanceof FoodTooltipComponent.HintLine) return 14;
        if (component instanceof FoodTooltipComponent.TextLine) return Math.max(1, textLines.size()) * ROW_HEIGHT;
        if (component instanceof FoodTooltipComponent.NoStackGroup group) return (group.rows().size() + 1) * ROW_HEIGHT;
        if (component instanceof FoodTooltipComponent.EffectGrid grid) {
            return (grid.entries().size() + grid.columns() - 1) / grid.columns() * ROW_HEIGHT;
        }
        return ROW_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        if (component instanceof FoodTooltipComponent.NutritionRow row)
            return font.width(nutritionText(row));
        if (component instanceof FoodTooltipComponent.TextLine) return textLines.stream().mapToInt(font::width).max().orElse(0);
        if (component instanceof FoodTooltipComponent.StatBar bar) {
            return bar.valueX() + font.width(bar.value());
        }
        if (component instanceof FoodTooltipComponent.ValueRow row) return row.valueX() + font.width(row.value());
        if (component instanceof FoodTooltipComponent.HintLine hint) {
            return font.width(hint.before()) + KEY_GAP + keyWidth(font, hint) + KEY_GAP + font.width(hint.after());
        }
        if (component instanceof FoodTooltipComponent.NoStackGroup group) {
            int valueWidth = group.rows().stream().mapToInt(row -> font.width(row.value())).max().orElse(0);
            return Math.max(GROUP_INDENT + font.width(group.heading()), group.valueX() + valueWidth);
        }
        if (component instanceof FoodTooltipComponent.EffectGrid grid) {
            int[] widths = columnWidths(font, grid);
            return widths[0] + (grid.columns() == 2 ? COLUMN_GAP + widths[1] : 0);
        }
        return 0;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f pose, MultiBufferSource.BufferSource buffer) {
        if (component instanceof FoodTooltipComponent.NutritionRow row) {
            draw(font, nutritionText(row), x, y, pose, buffer);
        } else if (component instanceof FoodTooltipComponent.TextLine) {
            for (int i = 0; i < textLines.size(); i++) {
                font.drawInBatch(textLines.get(i), x, y + i * ROW_HEIGHT, -1, true, pose, buffer,
                        Font.DisplayMode.NORMAL, 0, 15728880);
            }
        } else if (component instanceof FoodTooltipComponent.StatBar bar) {
            draw(font, bar.label(), x, y, pose, buffer);
            draw(font, bar.value(), x + bar.valueX(), y, pose, buffer);
        } else if (component instanceof FoodTooltipComponent.ValueRow row) {
            draw(font, row.label(), x, y, pose, buffer);
            draw(font, row.value(), x + row.valueX(), y, pose, buffer);
        } else if (component instanceof FoodTooltipComponent.HintLine hint) {
            // The key label itself is drawn in renderImage, on top of its keycap fill.
            int afterX = x + font.width(hint.before()) + KEY_GAP + keyWidth(font, hint) + KEY_GAP;
            draw(font, hint.before(), x, y + KEY_TEXT_Y, pose, buffer);
            draw(font, hint.after(), afterX, y + KEY_TEXT_Y, pose, buffer);
        } else if (component instanceof FoodTooltipComponent.NoStackGroup group) {
            draw(font, group.heading(), x + GROUP_INDENT, y, pose, buffer);
            for (int i = 0; i < group.rows().size(); i++) {
                var row = group.rows().get(i);
                int rowY = y + (i + 1) * ROW_HEIGHT;
                draw(font, row.label(), x + GROUP_INDENT, rowY, pose, buffer);
                draw(font, row.value(), x + group.valueX(), rowY, pose, buffer);
            }
        } else if (component instanceof FoodTooltipComponent.EffectGrid grid) {
            int[] widths = columnWidths(font, grid);
            for (int i = 0; i < grid.entries().size(); i++) {
                int entryX = x + (i % grid.columns() == 0 ? 0 : widths[0] + COLUMN_GAP);
                draw(font, grid.entries().get(i).text(), entryX + EFFECT_TEXT_OFFSET,
                        y + i / grid.columns() * ROW_HEIGHT, pose, buffer);
            }
        }
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        if (component instanceof FoodTooltipComponent.StatBar bar) {
            drawBar(graphics, x + bar.labelWidth() + GAP, y + (ROW_HEIGHT - BAR_HEIGHT) / 2, bar);
        } else if (component instanceof FoodTooltipComponent.NoStackGroup) {
            graphics.fill(x, y + 1, x + 1, y + getHeight() - 2, 0x73F2C14E);
        } else if (component instanceof FoodTooltipComponent.Divider divider) {
            graphics.fill(x, y + 2, x + divider.width(), y + 3, 0x3D6E46FF);
        } else if (component instanceof FoodTooltipComponent.HintLine hint) {
            keycap(graphics, font, hint, x + font.width(hint.before()) + KEY_GAP, y + 1);
        } else if (component instanceof FoodTooltipComponent.EffectGrid grid) {
            int[] widths = columnWidths(font, grid);
            for (int i = 0; i < grid.entries().size(); i++) {
                int entryX = x + (i % grid.columns() == 0 ? 0 : widths[0] + COLUMN_GAP);
                int entryY = y + i / grid.columns() * ROW_HEIGHT;
                var sprite = Minecraft.getInstance().getMobEffectTextures().get(grid.entries().get(i).effect());
                graphics.blit(entryX, entryY, 0, 9, 9, sprite);
            }
        }
    }

    /** One stat bar at {@code barX, barY}: shadow, track, ghost, fill and the overflow line. Shared with the journal. */
    public static void drawBar(GuiGraphics graphics, int barX, int barY, FoodTooltipComponent.StatBar bar) {
        graphics.fill(barX + 1, barY + 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xFF000000);
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF2A2433);
        fill(graphics, barX, barY, bar.ghostFill(), bar.ghostColor());
        fill(graphics, barX, barY, bar.fill(), bar.fillColor());
        // Overflow: its own 1px line one row below the bar (the bar's drop shadow sits between
        // them), left-aligned with the bar and given the same 1px right/down shadow, so the
        // pair reads as bar + sub-bar instead of a step in the shadow. Pale tint of the bar
        // colour so it never matches the duration bar's gold.
        int overflowWidth = FoodBarScale.overflowWidth(bar.overflowFill(), BAR_WIDTH);
        if (overflowWidth > 0) {
            int lineY = barY + BAR_HEIGHT + 1;
            graphics.fill(barX + 1, lineY + 1, barX + overflowWidth + 1, lineY + 2, 0xFF000000);
            graphics.fill(barX, lineY, barX + overflowWidth, lineY + 1,
                    0xFF000000 | FoodBarScale.overflowColor(bar.fillColor()));
        }
    }

    // Keycap: 11px tall face plus a 1px "key depth" edge; label sits on the face without a drop shadow.
    private static final int KEY_GAP = 4, KEY_PAD = 4, KEY_HEIGHT = 11, KEY_TEXT_Y = 3;

    private static int keyWidth(Font font, FoodTooltipComponent.HintLine hint) {
        return font.width(hint.key()) + KEY_PAD * 2;
    }

    /** A small physical-looking key: soft face, lighter top edge, darker bottom edge, clipped corners. */
    private static void keycap(GuiGraphics g, Font font, FoodTooltipComponent.HintLine hint, int kx, int ky) {
        int w = keyWidth(font, hint), right = kx + w, bottom = ky + KEY_HEIGHT;
        g.fill(kx + 1, ky, right - 1, bottom, 0xFF3A3445);            // face
        g.fill(kx, ky + 1, kx + 1, bottom - 1, 0xFF3A3445);           // left/right (corners stay open)
        g.fill(right - 1, ky + 1, right, bottom - 1, 0xFF3A3445);
        g.fill(kx + 1, ky, right - 1, ky + 1, 0xFF5C5569);            // top highlight
        g.fill(kx + 1, bottom, right - 1, bottom + 1, 0xFF17131D);    // key depth
        g.drawString(font, hint.key(), kx + KEY_PAD, ky + 2, 0xFFE2DEEA, false);
    }

    private static void fill(GuiGraphics graphics, int x, int y, float fraction, int color) {
        if (!Float.isFinite(fraction) || fraction <= 0.0F) return;
        int width = Math.max(2, Math.min(BAR_WIDTH, Math.round(fraction * BAR_WIDTH)));
        graphics.fill(x, y, x + width, y + BAR_HEIGHT, 0xFF000000 | color);
    }

    private static Component nutritionText(FoodTooltipComponent.NutritionRow row) {
        return com.chi__you.cuisinejournal.client.tooltip.NutritionTooltip.format(row.nutrition(), row.saturationModifier());
    }

    private static int[] columnWidths(Font font, FoodTooltipComponent.EffectGrid grid) {
        int[] widths = new int[2];
        for (int i = 0; i < grid.entries().size(); i++) {
            int column = i % grid.columns();
            widths[column] = Math.max(widths[column], EFFECT_TEXT_OFFSET + font.width(grid.entries().get(i).text()));
        }
        return widths;
    }

    private static void draw(Font font, Component text, int x, int y, Matrix4f pose,
                             MultiBufferSource.BufferSource buffer) {
        font.drawInBatch(text.getVisualOrderText(), x, y, -1, true, pose, buffer,
                Font.DisplayMode.NORMAL, 0, 15728880);
    }
}
