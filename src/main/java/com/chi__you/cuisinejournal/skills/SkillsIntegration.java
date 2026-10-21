package com.chi__you.cuisinejournal.skills;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.food.*;
import com.chi__you.cuisinejournal.network.Network;
import com.chi__you.cuisinejournal.network.ServerPacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.api.SkillsAPI;
import net.puffish.skillsmod.api.reward.*;
import java.util.*;

public final class SkillsIntegration {
    public static final ResourceLocation TREE = ResourceLocation.parse("cuisinejournal:valheim");
    public static final ResourceLocation VANILLA_TREE = ResourceLocation.parse("cuisinejournal:vanilla");
    public static ResourceLocation currentTree() { return CuisineFoodRuntime.active() ? TREE : VANILLA_TREE; }
    private static final ResourceLocation SOURCE = ResourceLocation.parse("cuisinejournal:cooking_level");
    private static final Map<UUID,Map<String,CompoundTag>> CONTRIBUTIONS = new HashMap<>();
    private static final Set<UUID> PENDING = new HashSet<>();
    private static final Map<UUID,Boolean> MODES = new HashMap<>();
    private static final Map<UUID,Float> SHIELDS = new HashMap<>();
    private static final Map<UUID,Long> REFILLS = new HashMap<>();
    private static final Map<UUID,Set<String>> EXPECTED = new HashMap<>();
    private static final Set<UUID> REFRESH = new HashSet<>();
    private SkillsIntegration() {}

    public static void install() {
        installRewards();
    }
    public static void installRewards() {
        SkillsAPI.registerReward(ResourceLocation.parse("cuisinejournal:skill"), context -> context.getData()
                .andThen(element -> element.getAsObject()).andThen(object -> object.getString("id").andThen(id -> object.getObject("effects").mapSuccess(effectObject -> {
                    CompoundTag effects = new CompoundTag();
                    var json = effectObject.getJson();
                    json.entrySet().forEach(e -> effects.putDouble(e.getKey(),e.getValue().getAsDouble()));
                    String tree = object.getJson().has("tree")
                            ? object.getString("tree").getSuccess().orElseThrow() : "valheim";
                    if(!tree.equals("valheim")&&!tree.equals("vanilla"))throw new IllegalArgumentException("Unknown skill tree " + tree);
                    return new SkillReward(tree,id,effects);
                }))));
    }
    private record SkillReward(String tree, String id, CompoundTag effects) implements Reward {
        private String key() { return tree + "/" + id; }
        public void update(RewardUpdateContext context) {
            UUID uuid = context.getPlayer().getUUID();
            var values = CONTRIBUTIONS.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>());
            if (context.getCount() <= 0) values.remove(key());
            else {
                var scaled = new CompoundTag();
                effects.getAllKeys().forEach(k -> scaled.putDouble(k,effects.getDouble(k)*context.getCount()));
                if (tree.equals(CuisineFoodRuntime.active() ? "valheim" : "vanilla")) values.put(key(),scaled);
                else values.remove(key());
            }
            PENDING.add(uuid);
        }
        public void dispose(RewardDisposeContext context) {
            CONTRIBUTIONS.forEach((uuid,map) -> { map.remove(key()); REFRESH.add(uuid); });
        }
    }

    public static void tick(ServerPlayer player) {
        var manager = ProfileManager.get();
        if (manager == null) return;
        var profile = manager.of(player);
        ((VanillaSkillNutrition.NutritionOwner)player.getFoodData()).cuisinejournal$owner(player);
        if (REFRESH.remove(player.getUUID())) {
            SkillsAPI.updateRewards(player,SkillReward.class);
            PENDING.add(player.getUUID());
        }
        boolean active = CuisineFoodRuntime.active();
        ResourceLocation activeTree = currentTree();
        boolean available = SkillsAPI.getCategory(activeTree).isPresent();
        if (profile.skillsActive()!=available) PENDING.add(player.getUUID());
        SkillsAPI.getCategory(active ? VANILLA_TREE : TREE).ifPresent(tree -> {
            if(tree.isUnlocked(player))tree.lock(player);
        });
        Boolean previous = MODES.put(player.getUUID(),active);
        if (previous == null || previous != active) {
            CONTRIBUTIONS.remove(player.getUUID());
            EXPECTED.remove(player.getUUID());
            SHIELDS.remove(player.getUUID());
            for (var id : List.of(TREE,VANILLA_TREE)) SkillsAPI.getCategory(id).ifPresent(tree -> {
                if (id.equals(activeTree)) tree.unlock(player); else tree.lock(player);
            });
            SkillsAPI.updateRewards(player,SkillReward.class);
            profile.setSkillsActive(available);
            PENDING.add(player.getUUID());
            if(previous!=null&&previous!=active)CuisineFoodService.clear(player);
        }
        SkillsAPI.getCategory(activeTree).ifPresent(tree -> {
            if(!tree.isUnlocked(player)) {
                tree.unlock(player);SkillsAPI.updateRewards(player,SkillReward.class);PENDING.add(player.getUUID());
            }
            int points = profile.cookingLevel(ConfigManager.server().progression.toCurve());
            if (tree.getPoints(player,SOURCE) != points) {
                tree.setPointsSilently(player,SOURCE,points);
                PENDING.add(player.getUUID());
            }
            if(player.tickCount%20==0 || previous==null) {
                Set<String> current=tree.streamUnlockedSkills(player).map(net.puffish.skillsmod.api.Skill::getId)
                        .collect(java.util.stream.Collectors.toSet());
                Set<String> expected=EXPECTED.put(player.getUUID(),current);
                if(!current.equals(expected))SkillsAPI.updateRewards(player,SkillReward.class);
            }
        });
        if (!active) VanillaSkillNutrition.sendIfChanged(player);
        if (PENDING.remove(player.getUUID())) {
            if(!player.isDeadOrDying())SkillsAPI.getCategory(activeTree).ifPresent(tree -> {
                if(tree.getSpentPoints(player)>tree.getPointsTotal(player))tree.resetSkills(player);
            });
            profile.setSkillBonuses(CookingSkills.sum(CONTRIBUTIONS.getOrDefault(player.getUUID(),Map.of()).values()));
            profile.setSkillMode(active?"valheim":"vanilla");
            profile.setSkillsActive(available);
            reconcileCapacity(player);
            VanillaSkillNutrition.reconcile(player);
            if (active) CuisineFoodEvents.synchronizeHealth(player,CuisineFoodService.state(player));
            player.setHealth(Math.min(player.getHealth(),player.getMaxHealth()));
            sync(player);
            ServerPacketHandler.sendProfile(player);
            ServerPacketHandler.sendExtras(player);
            ServerPacketHandler.sendStatistics(player);
            CuisineFoodSync.send(player);
        }
        float before = SHIELDS.getOrDefault(player.getUUID(),0F);
        if (!available || player.isDeadOrDying() || CookingSkills.value(profile,"shield") <= 0) {
            SHIELDS.remove(player.getUUID());
            if(player.isDeadOrDying())REFILLS.remove(player.getUUID());
            if (before > 0) sync(player);
            return;
        }
        long now = player.server.overworld().getGameTime();
        long next = REFILLS.computeIfAbsent(player.getUUID(),id -> Math.max(now+200,player.getPersistentData().getLong("cuisinejournal:shield_next_refill")));
        if (now >= next) {
            SHIELDS.put(player.getUUID(),(float)CookingSkills.value(profile,"shield"));
            REFILLS.put(player.getUUID(),now + 200);
            player.getPersistentData().putLong("cuisinejournal:shield_next_refill",now+200);
            sync(player);
        }
    }
    public static void sync(ServerPlayer player) {
        var p = ProfileManager.get().of(player);
        Network.toPlayer(player,new SkillPackets.Sync(p.skillsActive(),CookingSkills.starCap(p),CookingSkills.eatMultiplier(p),
                CookingSkills.value(p,"perfect"),CookingSkills.value(p,"floor"),CookingSkills.value(p,"batch"),
                SHIELDS.getOrDefault(player.getUUID(),0F),Math.max(.05,1+CookingSkills.value(p,"duration")),
                VanillaSkillNutrition.hungerCap(player),VanillaSkillNutrition.saturationCap(player)));
    }
    public static float absorb(ServerPlayer player,float damage) {
        if (damage <= 0 || CookingSkills.value(player,"shield")<=0) return damage;
        float shield = SHIELDS.getOrDefault(player.getUUID(),0F);
        if(shield>0) { SHIELDS.put(player.getUUID(),CookingSkills.shieldRemaining(shield,damage)); sync(player); }
        return CookingSkills.shieldDamage(shield,damage);
    }
    private static void reconcileCapacity(ServerPlayer player) {
        if (!CuisineFoodRuntime.active()) return;
        var s = CuisineFoodService.state(player);
        // Drop newest records only when a reload/admin change removes capacity; never re-grant food.
        var removed=new ArrayList<CuisineFoodEntry>();
        while (s.usedFoodSlots() > CuisineFoodCapacity.ordinary(player) && !s.foods().isEmpty())
            removed.add(s.foods().remove(s.foods().size()-1));
        while (s.temporaryFoods().stream().mapToInt(CuisineFoodEntry::slotCost).sum() > CuisineFoodCapacity.temporary(player)
                && !s.temporaryFoods().isEmpty()) removed.add(s.temporaryFoods().remove(s.temporaryFoods().size()-1));
        CuisineFoodEffects.replaceTemporary(player,s,removed);
        CuisineFoodService.save(player,s);
    }
    public static int open(ServerPlayer player) {
        tick(player);
        var tree=SkillsAPI.getCategory(currentTree());
        if(tree.isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("百味纪技能树未能加载，请检查日志中的 puffish_skills 数据包错误。"),false);
            return 0;
        }
        tree.get().openScreen(player);
        return 1;
    }
    public static int reset(ServerPlayer player) {
        tick(player);
        if (player.isDeadOrDying() || CuisineFoodRuntime.active() && CuisineFoodService.state(player).isReadOnly() || player.experienceLevel < 5) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("洗点需要5级原版经验。"),false); return 0;
        }
        var tree = SkillsAPI.getCategory(currentTree());
        if (tree.isEmpty() || tree.get().getSpentPoints(player) == 0) return 0;
        player.giveExperienceLevels(-5);
        if (CuisineFoodRuntime.active()) CuisineFoodService.clear(player);
        tree.get().resetSkills(player);
        REFILLS.put(player.getUUID(),player.server.overworld().getGameTime()+200);
        SHIELDS.remove(player.getUUID());
        PENDING.add(player.getUUID()); tick(player);
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("技能已重置，技能点已退还，已消耗5级原版经验。"),false);
        return 1;
    }
    public static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        CONTRIBUTIONS.remove(id);PENDING.remove(id);MODES.remove(id);SHIELDS.remove(id);EXPECTED.remove(id);
        REFRESH.remove(id);
        VanillaSkillNutrition.forget(player);
        // Persist the next recharge boundary; reconnecting never grants an immediate shield.
        if (REFILLS.containsKey(id)) player.getPersistentData().putLong("cuisinejournal:shield_next_refill",REFILLS.remove(id));
    }
    public static void clear() { CONTRIBUTIONS.clear();PENDING.clear();MODES.clear();SHIELDS.clear();REFILLS.clear();EXPECTED.clear();REFRESH.clear();VanillaSkillNutrition.clear(); }
}
