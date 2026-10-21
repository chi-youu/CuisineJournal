package com.chi__you.cuisinejournal.client.hud;

/** Sprite selection for the reserve above the vanilla twenty-point bar. */
public final class NutritionOverflow {
    private NutritionOverflow() {}

    public static int rows(float points) {
        return Float.isFinite(points) ? Math.max(0, (int) Math.ceil((Math.min(points, 1024) - 20) / 20F)) : 0;
    }

    public static int hungerSprite(int points, int row, int column, boolean hungry) {
        int remaining = points - 20 - row * 20 - column * 2;
        if (remaining <= 0) return -1;
        return (remaining == 1 ? 1 : 2) + (hungry ? 2 : 0);
    }

    public static int saturationSprite(float points, int row, int column) {
        if (!Float.isFinite(points)) return -1;
        float remaining = points - 20 - row * 20 - column * 2;
        if (remaining <= 0) return -1;
        return remaining >= 2 ? 3 : remaining > 1 ? 2 : remaining > .5F ? 1 : 0;
    }
}
