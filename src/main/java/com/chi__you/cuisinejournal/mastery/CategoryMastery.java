package com.chi__you.cuisinejournal.mastery;

/** Shared category rank calculation for server rewards and client display. */
public final class CategoryMastery {
    private CategoryMastery() {
    }

    /** Temporary five-step cap; the thresholds live here until balance is decided. */
    public static int rankCap(int discovered, int total) {
        if (total <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(MasteryRank.highestIndex(),
                (int) Math.ceil((double) Math.max(0,discovered) * MasteryRank.highestIndex() / total)));
    }

    public static MasteryRank rank(long points, int discovered, int total, MasteryCurve curve) {
        int index = Math.min(curve.rankFor(points).ordinal(), rankCap(discovered, total));
        return MasteryRank.byIndex(index);
    }
}
