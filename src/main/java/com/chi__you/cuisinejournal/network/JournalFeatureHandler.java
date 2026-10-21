package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.culinary.*;
import com.chi__you.cuisinejournal.recipe.*;
import com.chi__you.cuisinejournal.progression.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class JournalFeatureHandler {
    private JournalFeatureHandler(){}
    public static List<RecipeEntry> recipes(ResourceLocation dish){
        var registry=CulinaryRegistries.current();
        var result=new LinkedHashMap<ResourceLocation,RecipeEntry>();
        java.util.stream.Stream.concat(registry.preparationRecipes(dish).stream(),registry.interactionRecipes(dish).stream())
                .forEach(recipe->result.putIfAbsent(recipe.recipeId(),recipe));return List.copyOf(result.values());
    }
    public static void track(ServerPlayer player,JournalFeaturePackets.Track request){
        var manager=ProfileManager.get();if(manager==null)return;var profile=manager.of(player);
        var result=updateTracking(profile,request);
        if(result==TrackResult.FULL)player.displayClientMessage(Component.translatable("cuisinejournal.tracking.full"),true);
        if(result==TrackResult.APPLIED)manager.flush(player.getUUID());
        ServerPacketHandler.sendProfile(player);
    }

    public enum TrackResult { APPLIED, FULL, REJECTED }

    /** Tracking follows recipe visibility; it never discovers or credits the requested dish. */
    public static TrackResult updateTracking(CulinaryProfile profile,JournalFeaturePackets.Track request){
        if(request.recipe()==null){
            if(!profile.recipeTracks().containsKey(request.dish()))return TrackResult.REJECTED;
            profile.trackRecipe(request.dish(),null,0,1);return TrackResult.APPLIED;
        }
        if(request.target()<1||request.target()>4096||!trackingVisible(profile,request.dish())
                ||!CulinaryRegistries.current().isTracked(request.dish()))return TrackResult.REJECTED;
        var recipe=recipes(request.dish()).stream().filter(r->r.recipeId().equals(request.recipe())).findFirst().orElse(null);
        if(recipe==null||recipe.ingredients().isEmpty()||recipe.ingredients().size()>64||recipe.ingredients().stream().anyMatch(s->s.items().isEmpty()||s.items().size()>8192))return TrackResult.REJECTED;
        if(!profile.trackRecipe(request.dish(),request.recipe(),request.target(),ConfigManager.server().advanced.maxPinnedRecipes)){
            return TrackResult.FULL;}
        return TrackResult.APPLIED;
    }

    public static boolean trackingVisible(CulinaryProfile profile,ResourceLocation dish){
        return !ConfigManager.server().discovery.hideUndiscoveredDetails||profile.isDiscovered(dish);
    }
    public static void signature(ServerPlayer player,ResourceLocation dish){
        var manager=ProfileManager.get();if(manager==null)return;var profile=manager.of(player);
        if(Objects.equals(dish,profile.signatureDish())){send(player);return;}
        if(dish!=null&&!SignatureDishService.eligible(profile,dish)){message(player,"ineligible");send(player);return;}
        long day=CulinaryClock.day(player.server);
        if(!SignatureDishService.mayChange(profile.signatureChosen(),profile.signatureChangedDay(),day)){message(player,"daily_limit");send(player);return;}
        int cost=profile.signatureChosen()?ConfigManager.server().signatureDish.changeExperienceLevels:0;
        if(player.experienceLevel<cost){message(player,"experience",cost);send(player);return;}
        if(player.isDeadOrDying())return;
        if(cost>0)player.giveExperienceLevels(-cost);
        profile.selectSignature(dish,day);manager.flush(player.getUUID());send(player);message(player,dish==null?"cleared":"selected");
    }
    private static void message(ServerPlayer player,String key,Object...args){player.displayClientMessage(Component.translatable("cuisinejournal.signature."+key,args),false);}
    public static void send(ServerPlayer player){
        var manager=ProfileManager.get();if(manager==null)return;var profile=manager.of(player);var config=ConfigManager.server();
        for(var dish:List.copyOf(profile.pinned()))if(!profile.recipeTracks().containsKey(dish)){
            // Legacy pins become tracks while there is room; a pin that no longer fits is cleared, not forced in.
            var options=recipes(dish);
            if(options.isEmpty()||!profile.trackRecipe(dish,options.get(0).recipeId(),1,config.advanced.maxPinnedRecipes))profile.trackRecipe(dish,null,0,1);
        }
        var tracked=new ArrayList<JournalFeaturePackets.Tracked>();
        for(var pin:profile.recipeTracks().entrySet()){
            if(tracked.size()>=16)break;
            if(!trackingVisible(profile,pin.getKey()))continue;
            var recipe=recipes(pin.getKey()).stream().filter(r->r.recipeId().equals(pin.getValue().recipe())).findFirst().orElse(null);
            if(recipe==null||recipe.ingredients().isEmpty()||recipe.ingredients().size()>64
                    ||recipe.ingredients().stream().anyMatch(s->s.items().isEmpty()||s.items().size()>8192))continue;
            int batches=RecipeTracking.batches(pin.getValue().target(),recipe.outputCount());
            var slots=new ArrayList<JournalFeaturePackets.Slot>();
            for(var slot:recipe.ingredients()){
                var tag=slot.explicitItems().isEmpty()&&slot.tags().size()==1?slot.tags().get(0):null;
                int same=-1;for(int i=0;i<slots.size();i++)if(slots.get(i).items().equals(slot.items())&&Objects.equals(slots.get(i).tag(),tag)){same=i;break;}
                if(same<0)slots.add(new JournalFeaturePackets.Slot(slot.items(),tag,batches));
                else{var old=slots.get(same);slots.set(same,new JournalFeaturePackets.Slot(old.items(),tag,old.needed()+batches));}
            }
            tracked.add(new JournalFeaturePackets.Tracked(pin.getKey(),recipe.recipeId(),recipe.method()==null?null:recipe.method().id(),pin.getValue().target(),Math.max(1,recipe.outputCount()),slots));
        }
        Network.toPlayer(player,new JournalFeaturePackets.Sync(profile.signatureDish(),profile.signatureChosen(),profile.signatureChangedDay(),
                CulinaryClock.day(player.server),config.signatureDish.perfectChanceBonus,config.signatureDish.changeExperienceLevels,
                SignatureDishService.eligible(profile,profile.signatureDish()),tracked,Math.max(1,Math.min(16,config.advanced.maxPinnedRecipes))));
    }
}
