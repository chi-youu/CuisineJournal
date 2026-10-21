package com.chi__you.cuisinejournal.food;

import java.util.List;

/** Returns the exact slot to replace, so preflight and commit use the same rules. */
public final class CuisineFoodPlacement {
    public static final int REJECT = -2;
    public static final int ADD = -1;
    public static final int TEMPORARY = -3;
    public static final int DIGESTED = -4;

    public static int extraPlacement(boolean temporaryFits, boolean digesting) {
        return temporaryFits ? TEMPORARY : digesting ? DIGESTED : REJECT;
    }

    public static boolean blocksExtra(List<Slot> ordinary) {
        return ordinary.stream().anyMatch(slot -> slot.remainingTicks() > 0 && slot.sameFood());
    }

    private CuisineFoodPlacement() {
    }

    public static boolean acceptsTemporary(List<Slot> slots, int incomingCost, int capacity) {
        return temporaryPlan(slots,incomingCost,capacity).accepted();
    }

    public record TemporaryPlan(boolean accepted,List<Integer> remove) {
        public TemporaryPlan { remove=List.copyOf(remove); }
    }
    public static TemporaryPlan temporaryPlan(List<Slot> slots,int incomingCost,int capacity) {
        if(incomingCost<1||incomingCost>capacity)return new TemporaryPlan(false,List.of());
        List<Integer> remove=new java.util.ArrayList<>();
        int used=slots.stream().mapToInt(Slot::cost).sum();
        for(int i=0;i<slots.size();i++)if(slots.get(i).sameFood()){remove.add(i);used-=slots.get(i).cost();}
        for(int i=0;i<slots.size()&&used+incomingCost>capacity;i++)if(!remove.contains(i)) {
            remove.add(i);used-=slots.get(i).cost();
        }
        return new TemporaryPlan(true,remove);
    }

    public record Slot(int cost, int remainingTicks, int totalTicks, boolean sameFood) {
    }

    public static boolean canRefresh(int remainingTicks, int totalTicks, double fraction) {
        return remainingTicks < 1200 || remainingTicks < totalTicks * fraction;
    }

    public static int find(List<Slot> slots, int incomingCost, int capacity, double fraction) {
        return find(slots, incomingCost, capacity, fraction, false);
    }

    public static int find(List<Slot> slots, int incomingCost, int capacity,
                           double fraction, boolean repeatable) {
        if (incomingCost < 1 || incomingCost > capacity) {
            return REJECT;
        }
        int used = slots.stream().mapToInt(Slot::cost).sum();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.sameFood()) {
                return (repeatable || canRefresh(slot.remainingTicks(), slot.totalTicks(), fraction))
                        && used - slot.cost() + incomingCost <= capacity ? i : REJECT;
            }
        }
        if (used + incomingCost <= capacity) {
            return ADD;
        }
        if (repeatable) {
            return REJECT;
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (canRefresh(slot.remainingTicks(), slot.totalTicks(), fraction)
                    && used - slot.cost() + incomingCost <= capacity) {
                return i;
            }
        }
        return REJECT;
    }
}
