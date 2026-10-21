package com.chi__you.cuisinejournal.progression;

/** Fractional work never changes idle/cooling/reset writes or overshoots an equality-based finish. */
public final class ProgressBudget {
    private double fraction;
    public int advance(int before, int proposed, int total, double extra) {
        if (proposed <= before || total <= 0 || before >= total) { fraction = 0; return proposed; }
        if (!Double.isFinite(extra) || extra <= 0) return proposed;
        double work = fraction + (proposed - (double) before) * Math.min(20, extra);
        long whole = (long) Math.floor(work + 1e-9);
        fraction = Math.max(0, work - whole);
        int result = (int) Math.min(total, proposed + whole);
        if (result >= total) fraction = 0;
        return Math.max(proposed, result);
    }
}
