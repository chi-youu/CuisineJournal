package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.progression.CookingSpeed;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Advance only a host's existing successful progress step, never fuel, heat or whole ticks. */
public final class LetsDoSpeed {
    private record Rate(long tick,double extra){}
    private static final Map<BlockEntity,Rate> RATES=new WeakHashMap<>();
    private static final Map<BlockEntity,Map<String,Double>> FRACTIONS=new WeakHashMap<>();
    private static final Map<BlockEntity,int[]> ARRAYS=new WeakHashMap<>();
    private LetsDoSpeed(){}
    private static double extra(BlockEntity be){
        String method=LetsDoStations.method(be);if(method==null||method.equals("brewery:brewing")||!(be.getLevel() instanceof ServerLevel level))return 0;
        long tick=level.getGameTime();var old=RATES.get(be);if(old!=null&&tick>=old.tick&&tick-old.tick<20)return old.extra;
        double value=0;for(var player:level.players())if(player.distanceToSqr(be.getBlockPos().getCenter())<=64)
            value=Math.max(value,CookingSpeed.playerExtra(player,CookingMethod.of(net.minecraft.resources.ResourceLocation.parse(method))));
        RATES.put(be,new Rate(tick,value));return value;
    }
    private static int bonus(BlockEntity be,String key){
        var values=FRACTIONS.computeIfAbsent(be,ignored->new HashMap<>());double amount=extra(be)+values.getOrDefault(key,0D);
        int whole=(int)Math.min(1000,amount);values.put(key,amount-whole);return whole;
    }
    public static void write(BlockEntity be,String field,int proposed,int maximum){
        Object raw=HostReflection.field(be,field);int before=raw instanceof Number n?n.intValue():0;
        if(proposed==before+1&&maximum>0)proposed=Math.min(maximum,proposed+bonus(be,field));
        KaleidoscopeSpeed.set(be,field,proposed);
    }
    public static int number(Object owner,String field,int fallback){Object raw=HostReflection.field(owner,field);return raw instanceof Number n&&n.intValue()>0?n.intValue():fallback;}
    public static int dryingDuration(){
        try{return ((Number)Class.forName("net.satisfy.herbalbrews.platform.PlatformHelper").getMethod("getDryingDuration").invoke(null)).intValue();}
        catch(ReflectiveOperationException|LinkageError ignored){return 900;}
    }
    public static net.minecraft.world.level.block.state.BlockState manual(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState proposed){
        var be=level.getBlockEntity(pos);if(be==null||!LetsDoStations.host(be)||level.isClientSide)return proposed;
        String key="farm_and_charm:crafting_bowl".equals(LetsDoStations.method(be))?"stirred":"cranked";
        var property=proposed.getBlock().getStateDefinition().getProperty(key);
        if(!(property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty integer))return proposed;
        int value=proposed.getValue(integer),limit=key.equals("stirred")?49:19;
        if(value>0&&value<=limit&&be.getBlockState().getValue(integer)<value)
            return proposed.setValue(integer,Math.min(limit,value+bonus(be,key)));
        return proposed;
    }
    public static void beforeArray(BlockEntity be,String field){Object raw=HostReflection.field(be,field);if(raw instanceof int[] values)ARRAYS.put(be,values.clone());}
    public static void afterArray(BlockEntity be,String field){
        int[] before=ARRAYS.remove(be);Object raw=HostReflection.field(be,field);if(before==null||!(raw instanceof int[] values))return;
        for(int i=0;i<Math.min(before.length,values.length);i++)if(values[i]==before[i]+1)values[i]+=bonus(be,field+i);
    }
    public static void clear(){RATES.clear();FRACTIONS.clear();ARRAYS.clear();}
}
