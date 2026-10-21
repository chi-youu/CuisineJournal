package com.chi__you.cuisinejournal.progression;

/** One quality roll for the entire extracted/produced batch. Probabilities use fractions. */
public final class ProductionQuality {
    private ProductionQuality(){}
    public static int stars(int earned,boolean today){return Math.max(0,Math.min(5,earned+(today?1:0)));}
    public static double chance(double base,boolean today,int count){
        return chance(base,today,count,.01);
    }
    /** Apply the floor last; callers can supply a player's future skill-adjusted minimum. */
    public static double chance(double base,boolean today,int count,double minimum){
        return chance(base,today,count,minimum,.01);
    }
    public static double chance(double base,boolean today,int count,double minimum,double batchPenalty){
        if(count<=0)return 0;
        double floor=Double.isFinite(minimum)?Math.max(0,Math.min(1,minimum)):0;
        return Math.max(floor,Math.min(1,base+(today?.20:0)-Math.max(0,count-1)*Math.max(0,batchPenalty)));
    }
}
