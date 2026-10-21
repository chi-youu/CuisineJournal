package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import com.chi__you.cuisinejournal.mixin.food.FoodEffectDurationAccess;
import java.util.*;

/** Projects finite food reserves onto live effects, accounting for external consumption before restoring them. */
public final class CuisineFoodEffects {
    private static final int EFFECT_INTERVAL_TICKS = 15;
    private static final Map<ServerPlayer, Map<ResourceLocation, Owned>> OWNED = new IdentityHashMap<>();
    private static final Map<LivingEntity, Integer> CURING = new IdentityHashMap<>();
    private static final ThreadLocal<Integer> INTERNAL = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<MobEffectInstance> APPLYING = new ThreadLocal<>();
    private static final class Owned {
        MobEffectInstance instance;
        int duration;
        int amplifier;
        long time;
        boolean removed, cured;
        Owned(MobEffectInstance instance, long time) { reset(instance,time); }
        void reset(MobEffectInstance effect,long now) {
            instance=effect;duration=effect.getDuration();amplifier=effect.getAmplifier();time=now;removed=false;cured=false;
        }
    }
    private record Candidate(MobEffect effect, int amplifier, CuisineFoodEntry source, int remaining) {}
    private CuisineFoodEffects() {}

    private static long now(ServerPlayer player) { return Integer.toUnsignedLong(player.tickCount); }
    public static void beginCure(LivingEntity entity) {
        if(entity instanceof ServerPlayer player){observe(player,CuisineFoodService.state(player));CURING.merge(entity,1,Integer::sum);}
    }
    public static void endCure(LivingEntity entity) {
        int depth=CURING.getOrDefault(entity,0);
        if(depth<=1)CURING.remove(entity);else CURING.put(entity,depth-1);
    }
    public static void removed(LivingEntity entity,MobEffectInstance effect) {
        if(!(entity instanceof ServerPlayer player)||effect==null||INTERNAL.get()>0)return;
        if(CuisineEffectAdapters.nativeFoodUse(entity)) {
            var id=BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
            var owned=OWNED.get(player);
            if(owned!=null)owned.remove(id);
            dropOwner(CuisineFoodService.state(player),id);
            return;
        }
        var record=OWNED.getOrDefault(player,Map.of()).get(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect()));
        if(record!=null&&record.instance==effect&&effect.getDuration()>0) {
            record.removed=true;
            record.cured|=CURING.containsKey(entity);
        }
    }

    /** Called before food timers advance, so live natural countdown and reserve countdown share a baseline. */
    public static void observe(ServerPlayer player,CuisineFoodState state) {
        removeDepletedShield(player,state);
        var owned=OWNED.get(player);
        if(owned==null)return;
        for(var pair:Map.copyOf(owned).entrySet()) {
            var id=pair.getKey();var record=pair.getValue();
            MobEffectInstance live=player.getEffect(record.instance.getEffect());
            var change=FoodEffectReserve.classify(record.duration,now(player)-record.time,record.removed,record.cured,
                    live!=null,live==record.instance,live!=null&&live.isInfiniteDuration(),live==null?0:live.getDuration(),
                    record.amplifier,live==null?0:live.getAmplifier());
            if(change==FoodEffectReserve.Change.CURED) {
                // A standard curative item clears the instance, not the serving's remaining reserve.
                owned.remove(id);dropOwner(state,id);continue;
            }
            if(change==FoodEffectReserve.Change.EXHAUSTED||change==FoodEffectReserve.Change.NATURAL_END) {
                // No replacement by the next observation: ordinary removal consumes all old sources.
                if(change==FoodEffectReserve.Change.EXHAUSTED)exhaust(state,id);
                owned.remove(id);dropOwner(state,id);continue;
            }
            if(change==FoodEffectReserve.Change.EXTERNAL) {
                owned.remove(id);dropOwner(state,id);continue;
            }
            int consumed=FoodEffectReserve.consumption(record.duration,now(player)-record.time,live.getDuration());
            if(consumed>0)consume(state,id,consumed);
            record.reset(live,now(player));
        }
        if(owned.isEmpty())OWNED.remove(player);
    }

    private static void removeDepletedShield(ServerPlayer player,CuisineFoodState state) {
        if(!CuisineFoodRuntime.active()||!player.hasEffect(MobEffects.ABSORPTION)||player.getAbsorptionAmount()>0)return;
        var id=BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.ABSORPTION);
        var owner=OWNED.getOrDefault(player,Map.of()).get(id);
        if(owner==null||player.getEffect(MobEffects.ABSORPTION)!=owner.instance)return;
        exhaust(state,id);
        // Legacy/uninitialized servings must not regenerate a shield after the live shield breaks.
        for(var entry:entries(state))entry.markTriggered(id);
        if(remove(player,MobEffects.ABSORPTION)) {
            var owned=OWNED.get(player);if(owned!=null)owned.remove(id);
            dropOwner(state,id);
        }
    }

    public static void tick(ServerPlayer player,CuisineFoodState state) {
        if(player.tickCount%EFFECT_INTERVAL_TICKS==0)reconcile(player,state);
    }
    public static void onConsumed(ServerPlayer player) {
        var state=CuisineFoodService.state(player);
        observe(player,state);
        reconcile(player,state);
        CuisineFoodService.save(player,state);
    }
    public static void replaceTemporary(ServerPlayer player,CuisineFoodState state,List<CuisineFoodEntry> removed) {
        var owned=OWNED.get(player);
        if(owned==null)return;
        Set<ResourceLocation> removedIds=new HashSet<>();
        removed.forEach(entry->entry.effectReserves().keySet().forEach(id->{if(entry.ownsEffect(id))removedIds.add(id);}));
        for(var id:removedIds) {
            var record=owned.get(id);if(record==null)continue;
            if(player.getEffect(record.instance.getEffect())==record.instance&&!remove(player,record.instance.getEffect()))continue;
            owned.remove(id);dropOwner(state,id);
        }
    }
    public static void clear(ServerPlayer player) {
        var state=CuisineFoodService.state(player);
        observe(player,state);
        release(player,state,Map.of());
        CURING.remove(player);
    }
    public static void clearAll() { OWNED.clear();CURING.clear();INTERNAL.remove();APPLYING.remove(); }

    public static void effectAdded(net.minecraftforge.event.entity.living.MobEffectEvent.Added event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||INTERNAL.get()>0||event.getEffectInstance()==APPLYING.get())return;
        if(CuisineEffectAdapters.nativeFoodUse(player)) {
            var id=BuiltInRegistries.MOB_EFFECT.getKey(event.getEffectInstance().getEffect());
            var owned=OWNED.get(player);
            if(owned!=null)owned.remove(id);
            dropOwner(CuisineFoodService.state(player),id);
            return;
        }
        var record=OWNED.getOrDefault(player,Map.of()).get(BuiltInRegistries.MOB_EFFECT.getKey(event.getEffectInstance().getEffect()));
        if(record==null||record.removed||record.instance!=event.getOldEffectInstance())return;
        int consumed=FoodEffectReserve.consumption(record.duration,now(player)-record.time,record.instance.getDuration());
        if(consumed>0)consume(CuisineFoodService.state(player),BuiltInRegistries.MOB_EFFECT.getKey(record.instance.getEffect()),consumed);
        var preview=new MobEffectInstance(record.instance);
        if(preview.update(event.getEffectInstance())) {
            var id=BuiltInRegistries.MOB_EFFECT.getKey(event.getEffectInstance().getEffect());
            OWNED.get(player).remove(id);dropOwner(CuisineFoodService.state(player),id);
        }
    }
    private static boolean apply(ServerPlayer player,MobEffectInstance effect) {
        var previous=APPLYING.get();APPLYING.set(effect);INTERNAL.set(INTERNAL.get()+1);
        try{return player.addEffect(effect);}
        finally{INTERNAL.set(INTERNAL.get()-1);if(previous==null)APPLYING.remove();else APPLYING.set(previous);}
    }
    private static boolean remove(ServerPlayer player,MobEffect effect) {
        INTERNAL.set(INTERNAL.get()+1);
        try{return player.removeEffect(effect);}finally{INTERNAL.set(INTERNAL.get()-1);}
    }
    private static List<CuisineFoodEntry> entries(CuisineFoodState state) {
        var entries=state.allFoods();if(state.drink()!=null)entries.add(state.drink());return entries;
    }
    private static void consume(CuisineFoodState state,ResourceLocation id,int ticks) {
        for(var entry:entries(state)){var reserve=entry.effectReserves().get(id);if(reserve!=null)reserve.advance(ticks);}
    }
    private static void exhaust(CuisineFoodState state,ResourceLocation id) {
        for(var entry:entries(state)){var reserve=entry.effectReserves().get(id);if(reserve!=null)reserve.exhaust();}
    }
    private static void dropOwner(CuisineFoodState state,ResourceLocation id) {
        for(var entry:entries(state))entry.clearOwned(id);
    }
    private static void assignOwner(CuisineFoodState state,ResourceLocation id,CuisineFoodEntry source) {
        dropOwner(state,id);source.markOwned(id);
    }

    private static void reconcile(ServerPlayer player,CuisineFoodState state) {
        Map<ResourceLocation,Candidate> desired=new HashMap<>();
        for(CuisineFoodEntry entry:state.allFoods())collect(player,entry,desired);
        if(state.drink()!=null)collect(player,state.drink(),desired);
        release(player,state,desired);
        for(var pair:desired.entrySet()) {
            var id=pair.getKey();var candidate=pair.getValue();
            var current=player.getEffect(candidate.effect());
            var record=OWNED.getOrDefault(player,Map.of()).get(id);
            if(current!=null&&record==null&&candidate.source().ownsEffect(id)
                    &&!current.isInfiniteDuration()&&current.getAmplifier()==candidate.amplifier()
                    &&current.getDuration()<=candidate.remaining()) {
                // A persisted marker permits reclaiming its own saved finite instance.
                record=new Owned(current,now(player));
                consume(state,id,candidate.remaining()-current.getDuration());
                OWNED.computeIfAbsent(player,key->new HashMap<>()).put(id,record);
                continue;
            }
            if(current!=null&&(record==null||record.instance!=current))continue;
            if(current!=null&&current.getAmplifier()!=candidate.amplifier()) {
                if(!remove(player,candidate.effect()))continue;
                current=null;
            }
            if(current==null) {
                var next=new MobEffectInstance(candidate.effect(),candidate.remaining(),candidate.amplifier(),false,false);
                if(!apply(player,next))continue;
                current=player.getEffect(candidate.effect());
                if(current==null)continue;
            } else if(current.getDuration()!=candidate.remaining()) {
                // Adjust the owned timer without reapplying absorption/health attributes.
                ((FoodEffectDurationAccess)(Object)current).cuisinejournal$duration(candidate.remaining());
                player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(),current));
                player.sendEffectToPassengers(current);
            }
            OWNED.computeIfAbsent(player,key->new HashMap<>()).put(id,new Owned(current,now(player)));
            assignOwner(state,id,candidate.source());
        }
    }

    private static void collect(ServerPlayer player,CuisineFoodEntry entry,Map<ResourceLocation,Candidate> desired) {
        if(!entry.effectsInitialized()) {
            int elapsed=entry.totalTicks()-entry.remainingTicks();
            for(var setting:CuisineFoodEffectSource.forStack(entry.stack())) {
                MobEffect effect=BuiltInRegistries.MOB_EFFECT.getOptional(setting.id()).orElse(null);
                int remaining=Math.min(entry.remainingTicks(),setting.activeTicks(entry.totalTicks())-elapsed);
                if(effect==null||setting.activeFraction()<=0||remaining<=0)continue;
                if(effect==MobEffects.ABSORPTION&&entry.wasTriggered(setting.id()))continue;
                int amplifier=setting.level()-1;
                if(CuisineEffectAdapters.oneShot(setting.id(),effect.isInstantenous())) {
                    if(entry.markTriggered(setting.id()))CuisineEffectAdapters.applyOneShot(player,effect,amplifier);
                    continue;
                }
                var reserve=new FoodEffectReserve(amplifier,remaining);
                entry.effectReserves().merge(setting.id(),reserve,(a,b)->a.amplifier()>b.amplifier()?a:
                        a.amplifier()==b.amplifier()&&a.remaining()>=b.remaining()?a:b);
            }
            entry.initializeEffects();
        }
        Set<ResourceLocation> allowed = new HashSet<>();
        for(var setting:CuisineFoodEffectSource.forStack(entry.stack()))allowed.add(setting.id());
        for(var pair:entry.effectReserves().entrySet()) {
            if(!allowed.contains(pair.getKey()))continue;
            var reserve=pair.getValue();int remaining=Math.min(entry.remainingTicks(),reserve.remaining());
            MobEffect effect=BuiltInRegistries.MOB_EFFECT.getOptional(pair.getKey()).orElse(null);
            if(effect==null||remaining<=0)continue;
            var previous=desired.get(pair.getKey());
            if(previous==null||previous.amplifier()<reserve.amplifier()
                    ||previous.amplifier()==reserve.amplifier()&&previous.remaining()<remaining)
                desired.put(pair.getKey(),new Candidate(effect,reserve.amplifier(),entry,remaining));
        }
    }
    private static void release(ServerPlayer player,CuisineFoodState state,Map<ResourceLocation,Candidate> desired) {
        var owned=OWNED.get(player);if(owned==null)return;
        for(var pair:Map.copyOf(owned).entrySet())if(!desired.containsKey(pair.getKey())) {
            var current=player.getEffect(pair.getValue().instance.getEffect());
            if(current==pair.getValue().instance&&!remove(player,current.getEffect()))continue;
            owned.remove(pair.getKey());dropOwner(state,pair.getKey());
        }
        if(owned.isEmpty())OWNED.remove(player);
    }
}
