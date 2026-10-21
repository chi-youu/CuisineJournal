package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import java.util.*;
import java.util.function.ToLongFunction;

/** Only real host upgrade edges share mastery; item records and categories stay independent. */
public final class LatiaoMastery {
    private static volatile Map<ResourceLocation,Set<ResourceLocation>> families;
    private LatiaoMastery() {}
    public static Set<ResourceLocation> family(ResourceLocation target) {
        if (!target.getNamespace().equals("ltc2")) return Set.of(target);
        var current=families;
        if(current==null) synchronized(LatiaoMastery.class) {
            current=families;
            if(current==null) {
                Map<ResourceLocation,ResourceLocation> edges=new LinkedHashMap<>();
                Map<String,Map<Integer,ResourceLocation>> declaredGrades=new HashMap<>();
                for(var id:BuiltInRegistries.ITEM.keySet()) {
                    if(!id.getNamespace().equals("ltc2"))continue;
                    Item item=BuiltInRegistries.ITEM.get(id);
                    Object next=HostReflection.invoke(item,"findNextGrade");
                    Object grade=HostReflection.invoke(HostReflection.invoke(item,"getGrade"),"getLatiaoGradeValue");
                    if(grade instanceof Number n&&n.intValue()>=1&&n.intValue()<=5
                            &&com.chi__you.cuisinejournal.compat.HostClasses.matches(item,"com.doggystudio.chirencqr.ltc.server.item.UpgradableLatiaoItem")){
                        String path=id.getPath(),prefix=switch(n.intValue()){case 2->"rare";case 3->"superior";case 4->"delicacy";case 5->"treasure";default->"";};
                        if(prefix.isEmpty()||path.startsWith(prefix+"_")){
                            String base=prefix.isEmpty()?path:path.substring(prefix.length()+1);
                            declaredGrades.computeIfAbsent(base,key->new HashMap<>()).put(n.intValue(),id);
                        }
                    }
                    if(next instanceof Item upgraded&&upgraded!=item) {
                        var destination=BuiltInRegistries.ITEM.getKey(upgraded);
                        if(destination!=null&&destination.getNamespace().equals("ltc2"))edges.put(id,destination);
                    }
                }
                // The host's normal grade identifiers contain underscores inconsistently in findNextGrade.
                // Pair only registered Upgradable items with the matching declared adjacent grade.
                for(var variants:declaredGrades.values())for(int grade=1;grade<5;grade++)
                    if(variants.containsKey(grade)&&variants.containsKey(grade+1))edges.putIfAbsent(variants.get(grade),variants.get(grade+1));
                families=current=compile(edges);
            }
        }
        return current.getOrDefault(target,Set.of(target));
    }
    public static Map<ResourceLocation,Set<ResourceLocation>> compile(Map<ResourceLocation,ResourceLocation> edges) {
        Map<ResourceLocation,Set<ResourceLocation>> graph=new HashMap<>(),result=new HashMap<>();
        edges.forEach((a,b)->{graph.computeIfAbsent(a,k->new HashSet<>()).add(b);graph.computeIfAbsent(b,k->new HashSet<>()).add(a);});
        for(var node:graph.keySet()) {
            if(result.containsKey(node))continue;
            Set<ResourceLocation> component=new HashSet<>();Deque<ResourceLocation> pending=new ArrayDeque<>();pending.add(node);
            while(!pending.isEmpty()){var next=pending.remove();if(component.add(next))pending.addAll(graph.getOrDefault(next,Set.of()));}
            var members=Set.copyOf(component);for(var member:members)result.put(member,members);
        }
        return Map.copyOf(result);
    }
    public static long points(ResourceLocation target,ToLongFunction<ResourceLocation> points) {
        return points(family(target),points);
    }
    public static long points(Set<ResourceLocation> family,ToLongFunction<ResourceLocation> points) {
        long total=0;
        for(var member:family) {long value=Math.max(0,points.applyAsLong(member));total=total>Long.MAX_VALUE-value?Long.MAX_VALUE:total+value;}
        return total;
    }
}
