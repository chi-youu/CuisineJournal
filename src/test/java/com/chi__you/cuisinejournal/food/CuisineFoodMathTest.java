package com.chi__you.cuisinejournal.food;

public final class CuisineFoodMathTest {
    private CuisineFoodMathTest() {
    }

    public static void main(String[] args) {
        verifyHudCapacityLayout();
        verifyBarScale();
        equal(10.0F, CuisineFoodMath.maxHealth(5, 100, 0, 0, 0.1F, false));
        equal(15.0F, CuisineFoodMath.maxHealth(5, 100, 5, 0, 0.1F, false));
        equal(20.0F, CuisineFoodMath.maxHealth(5, 10, 100, 0, 0.1F, false));
        equal(20.0F, CuisineFoodMath.maxHealth(5, 100, 6, 3, 0.1F, true));
        equal(19.0F, CuisineFoodMath.maxHealth(5,100,9,0,0,false));
        equal(22.0F, CuisineFoodMath.maxHealth(5,100,11,0,0.1F,true));
        equal(0.0275F, CuisineFoodMath.healPerPulse(0, 0.25F, 0.1F, true));
        equal(0.0125F, CuisineFoodMath.healPerPulse(0, 0, 0.1F, false));
        equal(0.3F, CuisineFoodMath.effectiveSaturation(0));
        equal(0.35F, CuisineFoodMath.effectiveSaturation(0.1F));
        equal(0.55F, CuisineFoodMath.effectiveSaturation(0.5F));
        equal(0.6F, CuisineFoodMath.effectiveSaturation(0.6F));
        equal(0.8F, CuisineFoodMath.effectiveSaturation(0.8F));
        equal(0.3F, CuisineFoodMath.effectiveSaturation(-1));
        equal(16, CuisineFoodMath.foodHealthPoints(12,0.8F));
        equal(18, CuisineFoodMath.foodHealthPoints(14,0.75F));
        equal(23, CuisineFoodMath.foodHealthPoints(16,1.1F,2));
        equal(22, CuisineFoodMath.foodHealthPoints(18,0.8F,2));
        equal(27, CuisineFoodMath.foodHealthPoints(19,0.8F,3));
        equal(28, CuisineFoodMath.foodHealthPoints(19,0.9F,3));
        equal(21, CuisineFoodMath.foodHealthPoints(20,0.5F,2));
        equal(29, CuisineFoodMath.foodHealthPoints(20,0.85F,3));
        equal(30, CuisineFoodMath.foodHealthPoints(20,0.9F,3));
        equal(31, CuisineFoodMath.foodHealthPoints(20,1,3));
        equal(32, CuisineFoodMath.foodHealthPoints(20,1.2F,3));
        equal(32, CuisineFoodMath.foodHealthPoints(24,1,3));
        equal(33, CuisineFoodMath.foodHealthPoints(24,1.2F,3));
        equal(35, CuisineFoodMath.foodHealthPoints(28,1.3F,3));
        equal(36, CuisineFoodMath.foodHealthPoints(32,1.2F,3));
        equal(36, CuisineFoodMath.foodHealthPoints(36,0.85F,3));
        equal(0, CuisineFoodMath.foodHealthPoints(0,1));
        equal(2, CuisineFoodMath.foodHealthPoints(2,0));
        equal(11, CuisineFoodMath.foodHealthPoints(10,1,1));
        equal(11, CuisineFoodMath.foodHealthPoints(10,0.8F,1));
        equal(9, CuisineFoodMath.foodHealthPoints(8,0.8F,1));
        equal(11, CuisineFoodMath.foodHealthPoints(8,1.2F,1));
        equal(8, CuisineFoodMath.foodHealthPoints(6,1.2F,1));
        equal(11, CuisineFoodMath.foodHealthPoints(32,1.2F,1));
        equal(16, CuisineFoodMath.reduceHealthPoints(16));
        equal(31, CuisineFoodMath.reduceHealthPoints(36));
        equal(35, CuisineFoodMath.reduceHealthPoints(56));
        if(Math.abs(CuisineFoodMath.reduceHealthPoints(16)-CuisineFoodMath.reduceHealthPoints(Math.nextUp(16F)))>0.00001F
                ||Math.abs(CuisineFoodMath.reduceHealthPoints(36)-CuisineFoodMath.reduceHealthPoints(Math.nextUp(36F)))>0.00001F) {
            throw new AssertionError("Health reduction jumps at a segment boundary");
        }
        equal(11, CuisineFoodMath.roundedHealthPoints(10.5F));
        verifyAutomaticSlots();
        verifyStarGrowth();
        equal(0.576F, CuisineFoodMath.foodRegenPerSecond(12,0.8F));
        equal(0.63F, CuisineFoodMath.foodRegenPerSecond(14,0.75F));
        equal(0.96F, CuisineFoodMath.foodRegenPerSecond(16,1));
        equal(1.2F, CuisineFoodMath.foodRegenPerSecond(20,1));
        equal(1.35F, CuisineFoodMath.foodRegenPerSecond(30,1));
        equal(0.036F, CuisineFoodMath.foodRegenPerSecond(2,0));
        equal(0, CuisineFoodMath.foodRegenPerSecond(0,1));
        float combinedRegen = CuisineFoodMath.foodRegenPerSecond(12,0.8F)
                + CuisineFoodMath.foodRegenPerSecond(14,0.75F);
        equal(0.0125F + combinedRegen / 4, CuisineFoodMath.healPerPulse(combinedRegen*5,0,0,false));
        if (!CuisineFoodMath.combatRecoveryReady(0, Long.MIN_VALUE, 120)
                || CuisineFoodMath.combatRecoveryReady(1119, 1000, 120)
                || !CuisineFoodMath.combatRecoveryReady(1120, 1000, 120)
                || CuisineFoodMath.combatRecoveryReady(1120, 1100, 120)
                || CuisineFoodMath.combatRecoveryReady(999, 1000, 120)
                || !CuisineFoodMath.combatRecoveryReady(1000, 1000, 0)) {
            throw new AssertionError("Combat recovery must wait six seconds after the latest attack or hit");
        }
        equal(14400, CuisineFoodMath.durationTicks(60,600,12,false));
        equal(16800, CuisineFoodMath.durationTicks(60,600,14,false));
        equal(1200, CuisineFoodMath.durationTicks(60,600,1,false));
        equal(20, CuisineFoodMath.durationTicks(60,600,0,false));
        equal(12000, CuisineFoodMath.durationTicks(60,600,0,true));
        equal(12000, CuisineFoodMath.durationTicks(60,600,32,true));
        equal(4800, CuisineFoodMath.durationTicks(30,300,8,false));
        equal(6000, CuisineFoodMath.durationTicks(30,300,0,true));
        equal(Integer.MAX_VALUE, CuisineFoodMath.durationTicks(3600,600,Integer.MAX_VALUE,false));
        equal(0.018F, CuisineFoodMath.foodRegenPerSecond(2,0)/2);
        verifyPlacement();
        equal(4800, CuisineFoodMath.remainingAfterAdvance(6000, 1200));
        equal(0, CuisineFoodMath.remainingAfterAdvance(600, 1200));
        equal(600, CuisineFoodMath.remainingAfterAdvance(600, -1));
        equal(0, CuisineFoodMath.remainingAfterAdvance(600, Integer.MAX_VALUE));
        System.out.println("CuisineFoodMathTest: slot anchors, capped standard quality, piecewise health, half-heart rounding and recovery passed");
    }

    private static void verifyHudCapacityLayout() {
        var empty=FoodHudLayout.row(java.util.List.of(),3,14);
        if(empty.size()!=3||empty.stream().anyMatch(span->span.itemIndex()!=-1)||FoodHudLayout.width(empty)!=44)
            throw new AssertionError("Empty HUD shows all three capacity cells");
        var doubleMeal=FoodHudLayout.row(java.util.List.of(2),3,14);
        if(doubleMeal.size()!=2||doubleMeal.get(0).width()!=29||doubleMeal.get(1).offset()!=30)
            throw new AssertionError("Two-slot meal merges cells without an internal gutter and retains one empty cell");
        var tripleMeal=FoodHudLayout.row(java.util.List.of(3),3,14);
        if(tripleMeal.size()!=1||tripleMeal.get(0).width()!=44)
            throw new AssertionError("Three-slot meal uses the full persistent capacity row");
        var mixed=FoodHudLayout.row(java.util.List.of(1,2),5,14);
        if(mixed.size()!=4||mixed.get(1).offset()!=15||FoodHudLayout.width(mixed)!=74)
            throw new AssertionError("Growth capacity leaves stable occupied spans and remaining empty cells");
        var over=FoodHudLayout.row(java.util.List.of(3),1,14);
        if(!over.get(0).overflow()||over.size()!=1)
            throw new AssertionError("Reduced capacity retains active meal with overflow indicator");
        if(!FoodHudLayout.row(java.util.List.of(),0,9).isEmpty())throw new AssertionError("Zero temporary capacity has no phantom cells");
    }

    private static void verifyAutomaticSlots() {
        int[] nutrition={2,6,8,8,10,10,10,11,11,12,12,13,16,18,19,19,20,20,20,20,20,24,24,28,32,36};
        float[] saturation={0.1F,1.2F,0.8F,1.2F,0.8F,1,1.1F,0.6F,0.7F,0.6F,0.8F,0.45F,
                1.1F,0.8F,0.8F,0.9F,0.5F,0.85F,0.9F,1,1.2F,1,1.2F,1.3F,1.2F,0.85F};
        int[] expected={1,1,1,1,1,1,2,1,2,2,2,2,2,2,3,3,2,3,3,3,3,3,3,3,3,3};
        for(int i=0;i<nutrition.length;i++) equal(expected[i],CuisineFoodMath.automaticSlots(
                nutrition[i],saturation[i],10,18,16.2));
        equal(10,CuisineFoodMath.slotScore(10,1));
        equal(2,CuisineFoodMath.automaticSlots(20,0.5F,10,18,16.2));
        equal(2,CuisineFoodMath.automaticSlots(19,0.7F,10,18,16.2));
        equal(3,CuisineFoodMath.automaticSlots(19,0.8F,10,18,16.2));
        equal(2,CuisineFoodMath.automaticSlots(19,0.8F,10,20,16.2));
        equal(2,CuisineFoodMath.automaticSlots(18,1.5F,10,18,16.2));
        for(int n=1;n<=40;n++)for(int step=0;step<=30;step++) {
            float saturationValue=step/20F;
            int slots=CuisineFoodMath.automaticSlots(n,saturationValue,10,18,16.2);
            float health=CuisineFoodMath.foodHealthPoints(n,saturationValue,slots);
            if(slots<1||slots>3||health!=Math.round(health)||slots==1&&health>11) {
                throw new AssertionError("Automatic slot/half-heart invariants failed for "+n+", "+saturationValue);
            }
        }
    }

    private static void verifyBarScale() {
        equal(10,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.HEALTH_HEARTS);
        equal(0.4F,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.REGEN_HEARTS_PER_SECOND);
        equal(0,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.overflow(1));
        equal(0.2F,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.overflow(1.2F));
        equal(0.8F,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.overflow(1.8F));
        equal(1,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.overflow(3));
        equal(0,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.overflow(Float.NaN));
        equal(33,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.overflowWidth(
                com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.overflow(0.73F/0.4F),40));
        equal(40,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.overflowWidth(2,40));
        equal(0,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.overflowWidth(-1,40));
        equal(1,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.overflowWidth(0.001F,40));
        equal(0,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.overflowWidth(1,0));
        int color=0x86DB8F;
        equal(color,com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.valueColor(color,1));
        if(com.chi__you.cuisinejournal.food.tooltip.FoodBarScale.valueColor(color,2)==color)
            throw new AssertionError("Overflow value should brighten its original attribute color");
    }

    private static void verifyStarGrowth() {
        var config=new com.chi__you.cuisinejournal.config.ServerConfig();
        double[] health={-0.2,-0.1,0,0.1,0.2,0.3};
        double[] regen={0.02,0.025,0.03,1.0/30,11.0/300,0.04};
        for(int i=0;i<6;i++) {
            if(Math.abs(config.mastery.bonuses.valheimHealthOffsets.get(i)-health[i])>1e-10
                    ||Math.abs(config.mastery.bonuses.valheimRegenCoefficients.get(i)-regen[i])>1e-10)
                throw new AssertionError("Wrong default star table at "+i);
            equal((float)health[i],(float)CuisineStarBonuses.healthOffset(i));
            equal((float)regen[i],(float)CuisineStarBonuses.regenCoefficient(i));
        }
        equal(9,CuisineFoodMath.foodHealthPoints(10,0.8F,1,-0.2));
        equal(11,CuisineFoodMath.foodHealthPoints(10,0.8F,1,0));
        equal(13,CuisineFoodMath.foodHealthPoints(10,0.8F,1,0.3));
        equal(9,CuisineFoodMath.foodHealthPoints(10,1,1,-0.2));
        equal(11,CuisineFoodMath.foodHealthPoints(10,1,1,0));
        equal(14,CuisineFoodMath.foodHealthPoints(10,1,1,0.3));
        equal(14,CuisineFoodMath.foodHealthPoints(12,0.8F,2,-0.2));
        equal(16,CuisineFoodMath.foodHealthPoints(12,0.8F,2,0));
        equal(19,CuisineFoodMath.foodHealthPoints(12,0.8F,2,0.3));
        equal(35,CuisineFoodMath.foodHealthPoints(32,1.2F,3,-0.2));
        equal(36,CuisineFoodMath.foodHealthPoints(32,1.2F,3,0));
        equal(39,CuisineFoodMath.foodHealthPoints(32,1.2F,3,0.3));
        equal(0.42F,CuisineFoodMath.foodRegenPerSecond(14,0.75F,0.02));
        equal(0.84F,CuisineFoodMath.foodRegenPerSecond(14,0.75F,0.04));
        equal(1.668F,CuisineFoodMath.foodRegenPerSecond(32,1.2F,0.04));
        equal(20,CuisineFoodMath.healthAfterCapacityIncrease(10,20,30));
        equal(30,CuisineFoodMath.healthAfterCapacityIncrease(20,20,30));
        equal(15,CuisineFoodMath.healthAfterCapacityIncrease(5,20,30));
        equal(10,CuisineFoodMath.healthAfterCapacityIncrease(10,20,20));
        equal(10,CuisineFoodMath.healthAfterCapacityIncrease(10,20,15));
        equal(15,CuisineFoodMath.healthAfterCapacityIncrease(19,20,15));
        equal(12,CuisineFoodMath.healthAfterCapacityIncrease(10,20,22));
        equal(10,CuisineFoodMath.healthAfterCapacityIncrease(10,200,200));
        equal(12,CuisineFoodMath.healthAfterCapacityIncrease(12,22,22));
        var perfect=new com.chi__you.cuisinejournal.culinary.MealStamp(4,true,5,null,"");
        equal(5,perfect.effectiveStars());
        for(int n=1;n<=40;n++)for(int step=0;step<=30;step++) {
            float saturation=step/20F;
            int slots=CuisineFoodMath.automaticSlots(n,saturation,10,18,16.2);
            float previousHealth=-1,previousRegen=-1;
            for(int stars=0;stars<=5;stars++) {
                float hp=CuisineFoodMath.foodHealthPoints(n,saturation,slots,health[stars]);
                float recovery=CuisineFoodMath.foodRegenPerSecond(n,saturation,regen[stars]);
                if(hp<previousHealth||recovery<previousRegen||hp!=Math.round(hp))
                    throw new AssertionError("Star growth decreased/violated half-heart precision");
                previousHealth=hp;previousRegen=recovery;
            }
        }
        config.mastery.bonuses.valheimHealthOffsets=java.util.List.of(1.0);
        config.mastery.bonuses.valheimRegenCoefficients=null;
        config.validate();
        equal(6,config.mastery.bonuses.valheimHealthOffsets.size());
        equal(6,config.mastery.bonuses.valheimRegenCoefficients.size());
    }

    private static void verifyPlacement() {
        var fresh = new CuisineFoodPlacement.Slot(1, 5000, 6000, false);
        var expiring = new CuisineFoodPlacement.Slot(1, 1199, 6000, false);
        equal(CuisineFoodPlacement.ADD, CuisineFoodPlacement.find(java.util.List.of(), 3, 3, 0));
        equal(CuisineFoodPlacement.REJECT,
                CuisineFoodPlacement.find(java.util.List.of(fresh), 3, 3, 0));
        equal(CuisineFoodPlacement.REJECT,
                CuisineFoodPlacement.find(java.util.List.of(fresh, fresh, fresh), 1, 3, 0));
        equal(1, CuisineFoodPlacement.find(java.util.List.of(fresh, expiring, fresh), 1, 3, 0));
        equal(CuisineFoodPlacement.REJECT,
                CuisineFoodPlacement.find(java.util.List.of(fresh, expiring, fresh), 2, 3, 0));
        equal(0, CuisineFoodPlacement.find(java.util.List.of(
                new CuisineFoodPlacement.Slot(3, 1199, 6000, false)), 2, 3, 0));
        equal(CuisineFoodPlacement.REJECT, CuisineFoodPlacement.find(java.util.List.of(
                new CuisineFoodPlacement.Slot(1, 5000, 6000, true)), 1, 3, 0));
        equal(0, CuisineFoodPlacement.find(java.util.List.of(
                new CuisineFoodPlacement.Slot(1, 5000, 6000, true)), 1, 3, 0, true));
        equal(CuisineFoodPlacement.REJECT, CuisineFoodPlacement.find(java.util.List.of(
                fresh, fresh, fresh), 1, 3, 0, true));
        equal(CuisineFoodPlacement.REJECT, CuisineFoodPlacement.find(java.util.List.of(
                fresh, expiring, fresh), 1, 3, 0, true));
        equal(0, CuisineFoodPlacement.find(java.util.List.of(
                new CuisineFoodPlacement.Slot(1, 5000, 6000, true), fresh, fresh), 1, 3, 0, true));
        equal(CuisineFoodPlacement.REJECT, CuisineFoodPlacement.find(java.util.List.of(
                new CuisineFoodPlacement.Slot(1, 5000, 6000, true), fresh, fresh), 2, 3, 0, true));
        equal(0, CuisineFoodPlacement.find(java.util.List.of(
                new CuisineFoodPlacement.Slot(1, 1199, 6000, true)), 1, 3, 0));
        equal(CuisineFoodPlacement.REJECT, CuisineFoodPlacement.find(java.util.List.of(
                new CuisineFoodPlacement.Slot(1, 1199, 6000, true), fresh, fresh), 2, 3, 0));
        equal(CuisineFoodPlacement.REJECT, CuisineFoodPlacement.find(java.util.List.of(), 3, 2, 0));
        if (CuisineFoodPlacement.canRefresh(1200, 6000, 0.2)
                || !CuisineFoodPlacement.canRefresh(1199, 6000, 0)) {
            throw new AssertionError("SOL refresh boundary differs");
        }
    }

    private static void equal(float expected, float actual) {
        if (Math.abs(expected - actual) > 0.000001F) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void equal(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
