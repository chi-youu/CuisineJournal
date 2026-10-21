package com.chi__you.cuisinejournal.client.journal.edit;

import java.awt.Rectangle;

/** Shared geometry for drawing and dragging the modal's scroll thumb. */
public record ModalScrollbar(Rectangle track, Rectangle thumb, int maximum) {
    public static ModalScrollbar layout(int x, int y, int viewportHeight, int contentHeight, int offset) {
        int height = Math.max(0, viewportHeight);
        int maximum = Math.max(0, contentHeight - height);
        int thumbHeight = maximum == 0 ? height : Math.min(height,
                Math.max(8, (int)((long)height * height / Math.max(1, contentHeight))));
        int top = y + (int)((long)(height - thumbHeight) * Math.max(0, Math.min(maximum, offset))
                / Math.max(1, maximum));
        return new ModalScrollbar(new Rectangle(x, y, 2, height),
                new Rectangle(x, top, 2, thumbHeight), maximum);
    }

    public boolean contains(double x, double y) {
        return maximum > 0 && x >= track.x - 2 && x < track.x + track.width + 2
                && y >= track.y && y < track.y + track.height;
    }

    public double grabOffset(double pointerY) {
        return pointerY >= thumb.y && pointerY < thumb.y + thumb.height
                ? pointerY - thumb.y : thumb.height / 2.0;
    }

    public int offset(double pointerY, double grabOffset) {
        int travel = track.height - thumb.height;
        if (maximum <= 0 || travel <= 0) return 0;
        double fraction = (pointerY - track.y - grabOffset) / travel;
        return (int)Math.round(Math.max(0, Math.min(1, fraction)) * maximum);
    }
}
