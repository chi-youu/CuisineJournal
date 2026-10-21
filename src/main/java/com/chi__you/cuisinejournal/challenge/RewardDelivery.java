package com.chi__you.cuisinejournal.challenge;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.culinary.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** A persisted outbox, separate from rotating challenge progress. */
public final class RewardDelivery {
    private static final Set<UUID> RUNNING=new HashSet<>(), WARNED=new HashSet<>();
    private RewardDelivery() {}
    public static void clear(){RUNNING.clear();WARNED.clear();}
    public static CompoundTag freeze(ResourceLocation challenge,long day,ChallengeReward reward) {
        var result=new CompoundTag();result.putString("challenge",challenge.toString());result.putLong("day",day);
        var actions=new ListTag();result.put("actions",actions);
        var config=ConfigManager.server();
        add(actions,"cooking",ChallengeRewards.cookingXp(reward,config),"");
        add(actions,"mastery",reward.masteryXp(),reward.masteryDish()==null?"":reward.masteryDish().toString());
        add(actions,"vanilla",reward.vanillaXp(),"");
        if(reward.badge()!=null)add(actions,"badge",1,reward.badge().toString());
        if(reward.advancement()!=null)add(actions,"advancement",1,reward.advancement().toString());
        for(String item:reward.items())add(actions,"item",1,item);
        if(config.challenges.allowCommandRewards)for(String command:reward.commands())add(actions,"command",1,command);
        return result;
    }
    private static void add(ListTag actions,String kind,long amount,String value) {
        if(amount<=0)return;
        var action=new CompoundTag();action.putString("kind",kind);action.putLong("amount",amount);
        action.putString("value",value);actions.add(action);
    }
    public static boolean settled(CompoundTag delivery) {
        for(Tag tag:delivery.getList("actions",Tag.TAG_COMPOUND))
            if(((CompoundTag)tag).getInt("state")!=1)return false;
        return true;
    }
    public static void enqueue(CulinaryProfile profile,ResourceLocation id,long day,ChallengeReward reward) {
        if(reward==null||reward==ChallengeReward.NONE)return;
        var delivery=freeze(id,day,reward);
        if(!settled(delivery)){profile.rewardDeliveries().add(delivery);profile.markDirty();}
    }
    public static void drain(ServerPlayer player,CulinaryProfile profile) {
        if(profile.rewardDeliveries().isEmpty()||!RUNNING.add(player.getUUID()))return;
        try {
            var manager=ProfileManager.get();if(manager==null)return;
            // Never start external side effects unless completion and the outbox are durable.
            if(profile.isDirty()&&!manager.storage().save(profile))return;
            for(Tag tag:List.copyOf(profile.rewardDeliveries())) {
                var delivery=(CompoundTag)tag;
                for(Tag raw:delivery.getList("actions",Tag.TAG_COMPOUND)) {
                    var action=(CompoundTag)raw;int state=action.getInt("state");
                    if(state==1)continue;
                    if(state==2) {warn(player,delivery);continue;}
                    boolean persisted=attempt(action,()->{profile.markDirty();return manager.storage().save(profile);},
                            ()->perform(player,profile,action,delivery.getLong("day")),failure->{
                        CuisineJournalLog.LOGGER.error("Challenge reward delivery needs review: player={}, challenge={}, action={}",
                                player.getUUID(),delivery.getString("challenge"),action,failure);
                        warn(player,delivery);
                    });
                    if(!persisted){profile.markDirty();return;}
                }
                if(settled(delivery)){profile.rewardDeliveries().remove(delivery);profile.markDirty();}
            }
            if(profile.isDirty())manager.storage().save(profile);
        } finally {RUNNING.remove(player.getUUID());}
    }
    private static void warn(ServerPlayer player,CompoundTag delivery) {
        if(WARNED.add(player.getUUID()))player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "cuisinejournal.challenge.reward_review",delivery.getString("challenge")));
    }
    public static boolean attempt(CompoundTag action,java.util.function.BooleanSupplier persist,
                                  Runnable grant,java.util.function.Consumer<RuntimeException> failed) {
        if(action.getInt("state")!=0)return true;
        boolean external=!Set.of("cooking","mastery","badge").contains(action.getString("kind"));
        if(external){
            action.putInt("state",2);
            if(!persist.getAsBoolean()){action.putInt("state",0);return false;}
        }
        try{grant.run();action.putInt("state",1);}
        catch(RuntimeException failure){action.putInt("state",2);failed.accept(failure);}
        return persist.getAsBoolean();
    }
    private static void perform(ServerPlayer player,CulinaryProfile profile,CompoundTag action,long day) {
        long amount=action.getLong("amount");String value=action.getString("value");
        switch(action.getString("kind")) {
            case "cooking" -> {
                var curve=ConfigManager.server().progression.toCurve();int before=profile.cookingLevel(curve);
                profile.addCookingXp(amount,curve);
                com.chi__you.cuisinejournal.event.CulinaryEvents.COOKING_XP_GAINED.fire(
                        new com.chi__you.cuisinejournal.event.CulinaryEvents.CookingXpGained(player,profile,amount,null));
                int after=profile.cookingLevel(curve);
                if(after>before)com.chi__you.cuisinejournal.event.CulinaryEvents.COOKING_LEVEL_CHANGED.fire(
                        new com.chi__you.cuisinejournal.event.CulinaryEvents.CookingLevelChanged(player,profile,before,after));
            }
            case "mastery" -> {if(!value.isEmpty())com.chi__you.cuisinejournal.progression.ProgressionService.grantMasteryBonus(
                    player,profile,ResourceLocation.parse(value),amount,day);}
            case "badge" -> profile.awardBadge(ResourceLocation.parse(value));
            case "vanilla" -> player.giveExperiencePoints((int)amount);
            case "advancement" -> ChallengeRewards.grantAdvancement(player,ResourceLocation.parse(value));
            case "item" -> ChallengeRewards.giveItem(player,value);
            case "command" -> {if(ConfigManager.server().challenges.allowCommandRewards)
                player.server.getCommands().performPrefixedCommand(ChallengeManager.rewardSource(player),value.replace("@s",player.getGameProfile().getName()));}
            default -> throw new IllegalArgumentException("Unknown reward action");
        }
    }
}
