package com.chi__you.cuisinejournal.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** One daily choice and dimension visit history per world. */
public final class TodayDishState extends SavedData {
    private long day=Long.MIN_VALUE;
    private ResourceLocation dish;
    private final Set<ResourceLocation> visited=new HashSet<>();
    public long day(){return day;}
    public ResourceLocation dish(){return dish;}
    public Set<ResourceLocation> visited(){return Set.copyOf(visited);}
    public void visit(ResourceLocation dimension){if(visited.add(dimension))setDirty();}
    public void select(long day,ResourceLocation dish){this.day=day;this.dish=dish;setDirty();}
    public static TodayDishState load(CompoundTag tag){
        var state=new TodayDishState();state.day=tag.contains("day")?tag.getLong("day"):Long.MIN_VALUE;
        state.dish=ResourceLocation.tryParse(tag.getString("dish"));
        var dimensions=tag.getList("visited",8);for(int i=0;i<dimensions.size();i++){
            var id=ResourceLocation.tryParse(dimensions.getString(i));if(id!=null)state.visited.add(id);
        }return state;
    }
    @Override public CompoundTag save(CompoundTag tag){
        tag.putLong("day",day);if(dish!=null)tag.putString("dish",dish.toString());
        tag.put("visited",com.chi__you.cuisinejournal.data.NbtUtil.writeIds(visited));return tag;
    }
}
