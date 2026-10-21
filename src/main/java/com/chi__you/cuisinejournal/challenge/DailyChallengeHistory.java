package com.chi__you.cuisinejournal.challenge;

import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.google.gson.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** World-wide used methods and a frozen daily task set; offline cooks remain eligible. */
public final class DailyChallengeHistory extends SavedData {
    private final Set<ResourceLocation> methods=new TreeSet<>();
    private boolean seeded;
    private long day=Long.MIN_VALUE;
    private List<ChallengeDefinition> tasks=List.of();
    public Set<ResourceLocation> methods(){return Set.copyOf(methods);}
    public long day(){return day;}
    public List<ChallengeDefinition> tasks(){return tasks;}
    public void record(CookingMethod method){if(methods.add(method.id()))setDirty();}
    public void include(CulinaryProfile profile){profile.methods().forEach((method,record)->{if(record.preparations()>0)record(method);});}
    public void select(long day,List<ChallengeDefinition> tasks){this.day=day;this.tasks=List.copyOf(tasks);setDirty();}
    public static DailyChallengeHistory get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(
            DailyChallengeHistory::load,DailyChallengeHistory::new,"cuisinejournal_daily_challenges");}
    public static void recordMethod(MinecraftServer server,CookingMethod method){get(server).record(method);}
    public static void seed(MinecraftServer server){
        var data=get(server);var manager=ProfileManager.get();if(manager==null)return;
        manager.onlineProfiles().values().forEach(data::include);
        if(data.seeded)return;
        var root=manager.storage().root();
        if(java.nio.file.Files.isDirectory(root))try(var files=java.nio.file.Files.list(root)){
            for(var file:files.filter(p->p.getFileName().toString().endsWith(".dat")).toList()){
                try{var name=file.getFileName().toString();data.include(manager.peekOffline(UUID.fromString(name.substring(0,name.length()-4))));}
                catch(IllegalArgumentException invalid){com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Skipping non-profile file {}",file);}
            }
        }catch(java.io.IOException failure){com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Could not seed used cooking methods",failure);return;}
        data.seeded=true;data.setDirty();
    }
    public static DailyChallengeHistory load(CompoundTag tag){
        var data=new DailyChallengeHistory();data.seeded=tag.getBoolean("seeded");
        data.day=tag.contains("day")?tag.getLong("day"):Long.MIN_VALUE;
        com.chi__you.cuisinejournal.data.NbtUtil.forEachId(tag,"methods",data.methods::add);
        var rows=tag.getList("tasks",Tag.TAG_COMPOUND);var tasks=new ArrayList<ChallengeDefinition>();
        for(int i=0;i<rows.size();i++)try{
            var row=rows.getCompound(i);tasks.add(ChallengeDefinition.fromJson(ResourceLocation.parse(row.getString("id")),
                    JsonParser.parseString(row.getString("definition")).getAsJsonObject()));
        }catch(RuntimeException invalid){com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Skipping invalid saved daily task",invalid);}
        data.tasks=List.copyOf(tasks);return data;
    }
    @Override public CompoundTag save(CompoundTag tag){
        tag.putBoolean("seeded",seeded);tag.putLong("day",day);
        tag.put("methods",com.chi__you.cuisinejournal.data.NbtUtil.writeIds(methods));var rows=new ListTag();
        for(var task:tasks){
            var json=new JsonObject();json.addProperty("scope","DAILY");json.addProperty("manual",false);var objectives=new JsonArray();
            for(var objective:task.objectives()){
                var value=new JsonObject();value.addProperty("type",objective.type().name());value.addProperty("value",objective.value());
                value.addProperty("amount",objective.amount());value.addProperty("unique",objective.uniqueOnly());objectives.add(value);
            }
            json.add("objectives",objectives);var reward=new JsonObject();reward.addProperty("cooking_xp",task.rewards().cookingXp());
            if(!task.rewards().items().isEmpty()){
                var items=new JsonArray();task.rewards().items().forEach(items::add);reward.add("items",items);
            }
            json.add("rewards",reward);
            var row=new CompoundTag();row.putString("id",task.id().toString());row.putString("definition",json.toString());rows.add(row);
        }tag.put("tasks",rows);return tag;
    }
}
