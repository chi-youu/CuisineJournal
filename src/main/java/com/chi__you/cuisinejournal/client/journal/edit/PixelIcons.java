package com.chi__you.cuisinejournal.client.journal.edit;

import net.minecraft.client.gui.GuiGraphics;

/** Small code-drawn icons used by the journal editor. */
public final class PixelIcons {
    private PixelIcons() {
    }

    public enum Icon {
        NONE, PENCIL, DOTS, CHECK, PACKET, CREATED, ADJUSTED, PLUS,
        CLOSE, SLASH, ARROW_BACK, ARROW_MOVE, QUESTION, CHEVRON_DOWN, CHEVRON_LEFT, RETURN
    }

    /** U-turn arrow: "send back to where it came from". */
    private static final String[][] RETURN = {{".#.....", "##.....", "#######", "##....#", ".#....#", "......#", "..#####"}};

    private static final String[][] PENCIL = {{".....#.", "....###", "...###.", "..###..", ".###...", ".##....", "#......"}};
    private static final String[][] DOTS = {{"##.##.##", "##.##.##"}};
    private static final String[][] CHECK = {{"......#", ".....##", "#...##.", "##.##..", ".###...", "..#...."}};
    private static final String[][] PACKET = {{"#######", "#.....#", "###.###", "#.....#", "#.....#", "#.....#", "#######"}};
    private static final String[][] CREATED = {{"...#...", "...#...", "...#...", "#######", "...#...", "...#...", "...#..."}};
    private static final String[][] ADJUSTED = {{"....#..", "#######", "....#..", ".......", "....#..", "#######", "..#...."}};
    private static final String[][] PLUS = {{"...#...", "...#...", "...#...", "#######", "...#...", "...#...", "...#..."}};
    private static final String[][] CLOSE = {{"#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"}};
    /** A "no entry" sign: ring with a top-left to bottom-right bar. */
    private static final String[][] SLASH = {{"..###..", ".#...#.", "#.#...#", "#..#..#", "#...#.#", ".#...#.", "..###.."}};
    private static final String[][] CHEVRON_DOWN = {{"##...##", ".##.##.", "..###..", "...#..."}};
    private static final String[][] CHEVRON_LEFT = {{"...#", "..##", ".##.", "##..", ".##.", "..##", "...#"}};
    private static final String[][] ARROW_BACK = {{"..#....", ".##....", "#######", ".##....", "..#...."}};
    private static final String[][] ARROW_MOVE = {{"..#....", "...##..", "########", "...##..", "..#...."}};
    private static final String[][] QUESTION = {{".#####.", "#.....#", "....##.", "...##..", "...#...", ".......", "...#..."}};

    public static void draw(GuiGraphics graphics, Icon icon, int x, int y, int color) {
        String[] pattern = pattern(icon);
        for (int row = 0; row < pattern.length; row++) {
            String line = pattern[row];
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) == '#') {
                    graphics.fill(x + column, y + row, x + column + 1, y + row + 1, color);
                }
            }
        }
    }

    public static void drawSourceBadge(GuiGraphics graphics, Icon icon, int x, int y,
                                       int color, boolean adjusted) {
        graphics.fill(x, y, x + 9, y + 9, 0xFF0E0A07);
        draw(graphics, icon, x + 1, y + 1, color);
        if (adjusted) {
            graphics.fill(x + 7, y, x + 9, y + 2, 0xFF7FA7C9);
        }
    }

    public static int width(Icon icon) {
        String[] pattern = pattern(icon);
        int width = 0;
        for (String line : pattern) width = Math.max(width, line.length());
        return width;
    }

    public static int height(Icon icon) {
        return pattern(icon).length;
    }

    /** Draws the icon centred in the given box. */
    public static void drawCentered(GuiGraphics graphics, Icon icon, int x, int y, int boxWidth, int boxHeight, int color) {
        draw(graphics, icon, x + (boxWidth - width(icon)) / 2, y + (boxHeight - height(icon)) / 2, color);
    }

    private static String[] pattern(Icon icon) {
        return switch (icon) {
            case PENCIL -> PENCIL[0];
            case DOTS -> DOTS[0];
            case CHECK -> CHECK[0];
            case PACKET -> PACKET[0];
            case CREATED -> CREATED[0];
            case ADJUSTED -> ADJUSTED[0];
            case PLUS -> PLUS[0];
            case CLOSE -> CLOSE[0];
            case SLASH -> SLASH[0];
            case ARROW_BACK -> ARROW_BACK[0];
            case ARROW_MOVE -> ARROW_MOVE[0];
            case QUESTION -> QUESTION[0];
            case CHEVRON_DOWN -> CHEVRON_DOWN[0];
            case CHEVRON_LEFT -> CHEVRON_LEFT[0];
            case RETURN -> RETURN[0];
            case NONE -> new String[0];
        };
    }
}
