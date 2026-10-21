package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.config.ConfigEnums;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.*;
import com.chi__you.cuisinejournal.event.CulinaryEvents;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Discovery never claims that a player prepared, ate or mastered the shared dish. */
public final class DiscoveryService {
    public enum Trigger { PREPARED, EATEN, OBTAINED }
    private static boolean sharing;
    private DiscoveryService() {}

    public static boolean accepts(ServerConfig.Discovery config, Trigger trigger) {
        return config.mode == ConfigEnums.DiscoveryMode.ALWAYS_VISIBLE
                || trigger == Trigger.OBTAINED && (config.discoverOnObtain || config.mode == ConfigEnums.DiscoveryMode.OBTAIN_OUTPUT)
                || trigger == Trigger.PREPARED && config.mode == ConfigEnums.DiscoveryMode.COOK_RECIPE
                || trigger == Trigger.EATEN && config.mode == ConfigEnums.DiscoveryMode.EAT_OUTPUT;
    }

    public static String group(ConfigEnums.SharedDiscovery mode,String team) {
        return switch(mode) {
            case PERSONAL -> null;
            case SERVER -> "server";
            case TEAM -> team==null?null:"team:"+team;
        };
    }

    private static String group(ServerPlayer player) {
        return group(ConfigManager.server().discovery.shared,player.getTeam()==null?null:player.getTeam().getName());
    }

    public static boolean mark(CulinaryProfile profile,ResourceLocation dish,long day) {
        var registry=CulinaryRegistries.current();
        if(!registry.isTracked(dish))return false;
        boolean fresh=profile.meal(dish).discover(day);
        boolean sourceFresh=profile.meal(registry.preparationTarget(dish)).discover(day);
        if(!fresh&&!sourceFresh)return false;
        if(fresh)profile.pushActivity(new ActivityEntry(ActivityEntry.Type.RECIPE_DISCOVERED,dish,0,day));
        profile.streaks().recordDiscoveryDay(day,Math.max(1,ConfigManager.server().streaks.graceDays));
        profile.records().offerDiscoveryStreak(profile.streaks().discoveryStreak());
        profile.markDirty();
        return true;
    }

    public static boolean discover(ServerPlayer player,ResourceLocation dish,Trigger trigger) {
        var profiles=ProfileManager.get();
        if(profiles==null||!accepts(ConfigManager.server().discovery,trigger))return false;
        var profile=profiles.of(player);long day=CulinaryClock.day(player.server);
        if(!mark(profile,dish,day))return false;
        CulinaryEvents.RECIPE_DISCOVERED.fire(new CulinaryEvents.RecipeDiscovered(player,profile,dish,day));
        return true;
    }

    /** Called by the existing discovery event, so production and consumption use the same sharing rules. */
    public static void onDiscovered(CulinaryEvents.RecipeDiscovered event) {
        if(sharing)return;
        String key=group(event.player());if(key==null)return;
        var data=data(event.player().server);
        data.add(key,event.meal());
        data.add(key,CulinaryRegistries.current().preparationTarget(event.meal()));
        sharing=true;
        try {
            for(var player:event.player().server.getPlayerList().getPlayers()) {
                if(player==event.player()||!key.equals(group(player)))continue;
                var profile=ProfileManager.get().of(player);
                if(mark(profile,event.meal(),event.day()))
                    CulinaryEvents.RECIPE_DISCOVERED.fire(new CulinaryEvents.RecipeDiscovered(player,profile,event.meal(),event.day()));
            }
        } finally {sharing=false;}
    }

    private static SharedDiscoveries data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SharedDiscoveries::load,SharedDiscoveries::new,
                "cuisinejournal_shared_discoveries");
    }

    public static void tick(MinecraftServer server) {
        if(server.getTickCount()%20!=0||ProfileManager.get()==null)return;
        var registry=CulinaryRegistries.current();
        if(registry.size()==0)return;
        var config=ConfigManager.server().discovery;
        var players=server.getPlayerList().getPlayers();
        var ledger=config.shared==ConfigEnums.SharedDiscovery.PERSONAL?null:data(server);
        // Seed existing discoveries when sharing is enabled, including players joining or changing teams.
        if(ledger!=null)for(var player:players) {
            String key=group(player);if(key==null)continue;
            ProfileManager.get().of(player).meals().forEach((dish,record)-> {
                if(record.isDiscovered()&&registry.isTracked(dish))ledger.add(key,dish);
            });
        }
        for(var player:players) {
            var profile=ProfileManager.get().of(player);
            if(config.mode==ConfigEnums.DiscoveryMode.ALWAYS_VISIBLE)
                for(var dish:registry.targets())if(!profile.isDiscovered(dish)&&mark(profile,dish,CulinaryClock.day(server)))
                    CulinaryEvents.RECIPE_DISCOVERED.fire(new CulinaryEvents.RecipeDiscovered(player,profile,dish,CulinaryClock.day(server)));
            if(accepts(config,Trigger.OBTAINED)) {
                var seen=new HashSet<ResourceLocation>();
                for(int slot=0;slot<player.getInventory().getContainerSize();slot++) {
                    var stack=player.getInventory().getItem(slot);if(stack.isEmpty())continue;
                    var source=com.chi__you.cuisinejournal.food.CuisineAlcohol.sourceId(stack);
                    var dish=source==null?net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()):source;
                    if(seen.add(dish)&&!profile.isDiscovered(dish))discover(player,dish,Trigger.OBTAINED);
                }
            }
            if(ledger!=null) {
                long day=CulinaryClock.day(server);
                for(var dish:ledger.entries(group(player)))if(!profile.isDiscovered(dish)&&mark(profile,dish,day))
                    CulinaryEvents.RECIPE_DISCOVERED.fire(new CulinaryEvents.RecipeDiscovered(player,profile,dish,day));
            }
        }
    }
}
