package com.chi__you.cuisinejournal.food;

import net.minecraft.nbt.CompoundTag;

/** A serving's finite effect resource, independent of the live potion instance. */
public final class FoodEffectReserve {
    private final int amplifier;
    private int remaining;
    public FoodEffectReserve(int amplifier, int remaining) {
        this.amplifier = Math.max(0, Math.min(255, amplifier));
        this.remaining = Math.max(0, remaining);
    }
    public int amplifier() { return amplifier; }
    public int remaining() { return remaining; }
    public void advance(int ticks) { remaining = CuisineFoodMath.remainingAfterAdvance(remaining, ticks); }
    public void exhaust() { remaining = 0; }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("amplifier", amplifier); tag.putInt("remaining", remaining); return tag;
    }
    public static FoodEffectReserve read(CompoundTag tag) { return new FoodEffectReserve(tag.getInt("amplifier"), tag.getInt("remaining")); }

    public static int consumption(int previous, long elapsed, int actual) {
        int expected = (int) Math.max(0L, (long) previous - Math.max(0L, elapsed));
        return Math.max(0, expected - Math.max(0, actual));
    }
    public enum Change { CURED, EXHAUSTED, NATURAL_END, CONSUMED, EXTERNAL }
    public static Change classify(int previous,long elapsed,boolean removed,boolean cured,boolean present,
                                  boolean sameInstance,boolean infinite,int actual,int oldAmplifier,int amplifier) {
        int expected=(int)Math.max(0L,(long)previous-Math.max(0L,elapsed));
        if(cured)return Change.CURED;
        if(!present)return removed||expected>0?Change.EXHAUSTED:Change.NATURAL_END;
        if(!sameInstance&&!(removed&&!infinite&&amplifier==oldAmplifier&&actual<=expected))return Change.EXTERNAL;
        if(infinite||actual>expected||amplifier>oldAmplifier)return Change.EXTERNAL;
        return Change.CONSUMED;
    }
}
