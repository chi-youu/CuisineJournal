package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.food.CuisineFoodDefinitions;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

public final class TodayDishService {
    private TodayDishService(){}
    public static TodayDishState state(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(
            TodayDishState::load,TodayDishState::new,"cuisinejournal_today_dish");}
    public static void visit(ServerPlayer player){state(player.server).visit(player.level().dimension().location());}
    public static void sync(ServerPlayer player){
        com.chi__you.cuisinejournal.network.Network.toPlayer(player,new com.chi__you.cuisinejournal.network.Packets.SyncTodayDish(
                com.chi__you.cuisinejournal.culinary.DailyCalendar.current(player.server).day()+1,current(player.server)));
    }
    public static ResourceLocation current(MinecraftServer server){
        if(server==null||!ConfigManager.server().todayDish.enabled)return null;
        refresh(server,false);return state(server).dish();
    }
    public static boolean isToday(ServerPlayer player,ResourceLocation meal){
        var selected=current(player.server);var registry=CulinaryRegistries.current();
        return selected!=null&&registry.preparationTarget(selected).equals(registry.preparationTarget(meal));
    }
    public static void tick(MinecraftServer server){if(server.getTickCount()%20==0)refresh(server,false);}
    public static void refresh(MinecraftServer server,boolean validate){
        var state=state(server);long day=com.chi__you.cuisinejournal.culinary.DailyCalendar.current(server).day();
        state.visit(CuisineFoodDefinitions.DEFAULT_DIMENSION);
        if(state.day()==day&&!validate)return;
        var registry=CulinaryRegistries.current();
        var candidates=registry.entries().stream().filter(e->!registry.preparationRecipes(e.target()).isEmpty())
                .map(e->{var values=CuisineFoodDefinitions.get(e.target());return new TodayDishSelection.Candidate(e.target(),
                        values==null?CuisineFoodDefinitions.DEFAULT_DIMENSION:values.dimension());}).toList();
        if(validate&&state.day()==day&&state.dish()!=null){
            var selected=state.dish();var config=ConfigManager.server().todayDish;
            if(config.enabled&&candidates.stream().anyMatch(c->c.item().equals(selected)
                    &&(!config.requireVisitedDimension||state.visited().contains(c.dimension())))
                    &&config.groups.stream().anyMatch(g->g.weight>0&&day+1>=g.fromDay&&g.mods.contains(selected.getNamespace())))return;
        }
        if(candidates.isEmpty()){
            state.select(day,null);
            return;
        }
        state.select(day,TodayDishSelection.choose(day,server.overworld().getSeed(),ConfigManager.server().todayDish,candidates,state.visited()));
        for(var player:server.getPlayerList().getPlayers())sync(player);
    }
}
