package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Weight belongs to a group, not to each item within that group. */
public final class TodayDishSelection {
    public record Candidate(ResourceLocation item,ResourceLocation dimension){}
    private record Pool(List<ResourceLocation> items,double weight){}
    private TodayDishSelection() {}
    public static ResourceLocation choose(long day,long seed,ServerConfig.TodayDish config,
                                          List<Candidate> candidates,Set<ResourceLocation> visited){
        if(!config.enabled)return null;
        List<Pool> pools=new ArrayList<>();double total=0;
        for(var group:config.groups){
            if(day+1<group.fromDay||!Double.isFinite(group.weight)||group.weight<=0)continue;
            var members=candidates.stream().filter(c->group.mods.contains(c.item().getNamespace()))
                    .filter(c->!config.requireVisitedDimension||visited.contains(c.dimension()))
                    .map(Candidate::item).distinct().sorted().toList();
            if(members.isEmpty())continue;
            pools.add(new Pool(members,group.weight));total+=group.weight;
        }
        if(pools.isEmpty())return null;
        Random random=new Random(seed^day*0x9E3779B97F4A7C15L);
        double pick=random.nextDouble()*total;
        for(var pool:pools){pick-=pool.weight();if(pick<0)return pool.items().get(random.nextInt(pool.items().size()));}
        var last=pools.get(pools.size()-1);return last.items().get(random.nextInt(last.items().size()));
    }
}
