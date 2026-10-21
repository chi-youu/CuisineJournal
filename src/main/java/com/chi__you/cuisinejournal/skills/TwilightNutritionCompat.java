package com.chi__you.cuisinejournal.skills;

import net.minecraft.world.entity.player.Player;
import java.lang.reflect.Method;

/** Optional host API: resolving it lazily keeps host classes off the no-mod path. */
public final class TwilightNutritionCompat {
    private static final ThreadLocal<Boolean> QUERYING=ThreadLocal.withInitial(()->false);
    public static boolean querying(){return QUERYING.get();}
    private static boolean resolved;
    private static Method food,saturation;
    private static boolean failed;
    private TwilightNutritionCompat() {}

    private static void resolve() {
        if(resolved)return;
        resolved=true;
        if(!net.minecraftforge.fml.ModList.get().isLoaded("twilight_spark_delight"))return;
        try {
            Class<?> type=Class.forName("xy177.twilightsparksdelight.common.food.ExtendedFoodProgression");
            food=type.getMethod("getMaxFood",Player.class);
            saturation=type.getMethod("getMaxSaturation",Player.class);
        } catch(ReflectiveOperationException | LinkageError failure) {
            failed=true;
            com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn(
                    "Unsupported Twilight nutrition API; preserving current nutrition instead of trimming it",failure);
        }
    }

    public static int cap(Player player,boolean saturating) {
        if(player==null)return 20;
        resolve();
        Method method=saturating?saturation:food;
        if(method==null)return failed?retained(player,saturating):20;
        boolean before=QUERYING.get();QUERYING.set(true);
        try { return ((Number)method.invoke(null,player)).intValue(); }
        catch(ReflectiveOperationException | RuntimeException failure){
            if(!failed)com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Twilight nutrition lookup failed; preserving current nutrition",failure);
            failed=true;return retained(player,saturating);
        }
        finally {QUERYING.set(before);}
    }
    private static int retained(Player player,boolean saturating) {
        return Math.max(20,(int)Math.ceil(saturating?player.getFoodData().getSaturationLevel():player.getFoodData().getFoodLevel()));
    }
}
