package com.chi__you.cuisinejournal.food.tooltip;

/** Display scales only; neither these ranges nor overflow pixels limit actual food attributes. */
public final class FoodBarScale {
    public static final float HEALTH_HEARTS = 10.0F;
    public static final float REGEN_HEARTS_PER_SECOND = 0.4F;
    private FoodBarScale() {}

    public static float overflow(float fraction) {
        return Float.isFinite(fraction) ? Math.max(0, Math.min(1, fraction - 1)) : 0;
    }

    public static int overflowWidth(float overflow, int width) {
        if (!Float.isFinite(overflow) || overflow <= 0 || width <= 0) return 0;
        return Math.max(1, Math.min(width, Math.round(overflow * width)));
    }

    /** The overflow line: the bar colour mixed 60% toward white (health → pale pink, regen → pale mint). */
    public static int overflowColor(int color) {
        int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
        return (red + (255 - red) * 3 / 5) << 16 | (green + (255 - green) * 3 / 5) << 8 | (blue + (255 - blue) * 3 / 5);
    }

    public static int valueColor(int color, float fraction) {
        if (!Float.isFinite(fraction) || fraction <= 1) return color;
        int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
        return (red + (255 - red) / 5) << 16 | (green + (255 - green) / 5) << 8 | (blue + (255 - blue) / 5);
    }
}
