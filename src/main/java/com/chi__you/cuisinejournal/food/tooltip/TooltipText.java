package com.chi__you.cuisinejournal.food.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class TooltipText {
    public static final int MUTED = 0x8C8C8C;
    public static final int LABEL = 0xCFCFCF;
    public static final int HEALTH = 0xF07272;
    public static final int REGEN = 0x86DB8F;
    public static final int DURATION = 0xE9BC5C;
    public static final int DRINK = 0x6FC8FF;
    public static final int DRINK_GHOST = 0x2E5FA8;
    public static final int GOLD = 0xF2C14E;
    public static final int PERFECT = 0xD08CFF;
    public static final int DIM = 0x7E7A88;
    public static final int BAR_WIDTH = 40;
    public static final int BAR_HEIGHT = 3;
    public static final int ROW_HEIGHT = 10;
    public static final int GAP = 6;
    public static final int GROUP_INDENT = 5;
    public static final ResourceLocation ICON_FONT = ResourceLocation.fromNamespaceAndPath("cuisinejournal", "tooltip");

    private TooltipText() {}

    public static MutableComponent text(String key, Object... args) {
        return Component.translatable("cuisinejournal.tooltip." + key, args);
    }

    public static MutableComponent colored(Component text, int color) {
        return text.copy().withStyle(style -> style.withColor(color));
    }

    public static MutableComponent muted(String key, Object... args) {
        return colored(text(key, args), MUTED);
    }

    public static MutableComponent heart(int color) {
        return Component.literal("\uE000").withStyle(style -> style.withFont(ICON_FONT).withColor(color));
    }

    public static MutableComponent star(int color) {
        return Component.literal("\uE001").withStyle(style -> style.withFont(ICON_FONT).withColor(color));
    }
}
