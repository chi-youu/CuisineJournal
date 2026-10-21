package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
public final class KaleidoscopeSpeed {
    private static final Map<BlockEntity,Double> REMAINDERS=new WeakHashMap<>();
    private record Rate(long tick,double extra){}
    private static final Map<BlockEntity,Rate> RATES=new WeakHashMap<>();
    private static final Map<BlockEntity,int[]> ARRAYS=new WeakHashMap<>();
    private static final Map<BlockEntity,Integer> PROGRESS=new WeakHashMap<>();
    private static final Map<BlockEntity,double[]> ARRAY_REMAINDERS=new WeakHashMap<>();
    public static void beforeArray(BlockEntity be,String field){if(be.getLevel()!=null&&!be.getLevel().isClientSide)ARRAYS.put(be,snapshot(be,field));}
    public static void afterArray(BlockEntity be,String field){var before=ARRAYS.remove(be);if(before!=null)array(be,field,before);}
    public static void beforeProgress(BlockEntity be){if(be.getLevel()!=null&&!be.getLevel().isClientSide)PROGRESS.put(be,CookingSpeed.number(be,"progress"));}
    public static void afterProgress(BlockEntity be){var before=PROGRESS.remove(be);if(before!=null)progress(be,before);}
    private KaleidoscopeSpeed(){}
    public static double extra(BlockEntity be){
        String method=KaleidoscopeProduction.method(be);if(method==null||!(be.getLevel() instanceof ServerLevel level))return 0;
        long tick=level.getGameTime();var cached=RATES.get(be);
        if(cached!=null&&tick>=cached.tick&&tick-cached.tick<20)return cached.extra;
        double extra=0;
        for(ServerPlayer player:level.players())if(player.distanceToSqr(be.getBlockPos().getX()+.5,be.getBlockPos().getY()+.5,be.getBlockPos().getZ()+.5)<=64)
            extra=Math.max(extra,CookingSpeed.playerExtra(player,CookingMethod.of(net.minecraft.resources.ResourceLocation.parse(method))));
        RATES.put(be,new Rate(tick,extra));return extra;
    }
    public static int countdown(BlockEntity be,String field,int proposed){
        Object raw=HostReflection.field(be,field);int before=raw instanceof Number number?number.intValue():0;
        int step=before-proposed;
        if(step<=0)return proposed;
        double amount=step*extra(be)+REMAINDERS.getOrDefault(be,0d);int whole=(int)amount;
        REMAINDERS.put(be,amount-whole);return Math.max(0,proposed-whole);
    }
    public static int remaining(BlockEntity be,int proposed,int nativeStep){
        return Math.max(0,proposed-bonus(be,nativeStep));
    }
    private static int bonus(BlockEntity be,int nativeStep){
        double amount=nativeStep*extra(be)+REMAINDERS.getOrDefault(be,0d);int whole=(int)Math.min(Integer.MAX_VALUE,amount);
        REMAINDERS.put(be,amount-whole);return whole;
    }
    public static void set(BlockEntity be,String name,int value){
        for(Class<?> type=be.getClass();type!=null;type=type.getSuperclass())try{
            var field=type.getDeclaredField(name);field.setAccessible(true);field.setInt(be,value);return;
        }catch(NoSuchFieldException ignored){}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    public static void array(BlockEntity be,String field,int[] before){
        Object raw=HostReflection.field(be,field);if(!(raw instanceof int[] current))return;
        double extra=extra(be);if(extra<=0)return;
        double[] fractions=ARRAY_REMAINDERS.computeIfAbsent(be,key->new double[current.length]);
        if(fractions.length!=current.length){fractions=new double[current.length];ARRAY_REMAINDERS.put(be,fractions);}
        for(int i=0;i<Math.min(before.length,current.length);i++)if(current[i]>before[i]){
            int step=current[i]-before[i];double amount=fractions[i]+extra*step;int bonus=(int)amount;fractions[i]=amount-bonus;
            current[i]=(int)Math.min(Integer.MAX_VALUE,(long)current[i]+Math.max(0,bonus));
            if(bonus>0)be.setChanged();
        }
    }
    public static int[] snapshot(BlockEntity be,String field){
        Object raw=HostReflection.field(be,field);return raw instanceof int[] values?values.clone():new int[0];
    }
    public static void progress(BlockEntity be,int before){
        int now=CookingSpeed.number(be,"progress"),total=CookingSpeed.number(be,"maxProgress");
        if(now<=before||total<=0)return;
        int bonus=bonus(be,now-before);
        if(bonus>0){set(be,"progress",(int)Math.min((long)total,(long)now+bonus));be.setChanged();}
    }
}
