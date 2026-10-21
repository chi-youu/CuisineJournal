package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Shared Valheim-only capabilities and descriptions for explicitly adapted effects. */
public final class CuisineEffectAdapters {
    public record TemporaryFood(int capacity, int durationTicks) {
        public TemporaryFood {
            if (capacity < 1 || capacity > 64 || durationTicks < 1) throw new IllegalArgumentException("Invalid temporary food policy");
        }
    }
    public record Recovery(double outOfCombatBonus, double combatFraction) {
        public Recovery {
            if (!Double.isFinite(outOfCombatBonus) || outOfCombatBonus < 0
                    || !Double.isFinite(combatFraction) || combatFraction < 0)
                throw new IllegalArgumentException("Invalid recovery policy");
        }
        public float amount(float base, boolean outOfCombat) {
            return (float)Math.min(Float.MAX_VALUE, Math.max(0, base)
                    * (outOfCombat ? 1 + outOfCombatBonus : combatFraction));
        }
    }
    public record Adapter(TemporaryFood temporaryFood, String descriptionKey, boolean digesting, boolean oneShotTick,
                          Recovery recovery) {
        public Adapter(TemporaryFood temporaryFood, String descriptionKey) { this(temporaryFood,descriptionKey,false,false,null); }
        public Adapter(TemporaryFood temporaryFood, String descriptionKey, boolean digesting) { this(temporaryFood,descriptionKey,digesting,false,null); }
        public Adapter(TemporaryFood temporaryFood, String descriptionKey, boolean digesting, boolean oneShotTick) {
            this(temporaryFood,descriptionKey,digesting,oneShotTick,null);
        }
    }
    public static final TemporaryFood APPETIZING = new TemporaryFood(2, 200);
    public static final Recovery NOURISHMENT = new Recovery(0.25, 0.5);
    private static final Map<ResourceLocation, Adapter> ADAPTERS = new ConcurrentHashMap<>();
    private record NativeWindow(LivingEntity consumer,net.minecraft.world.item.ItemStack food,
                                java.util.Set<net.minecraft.world.effect.MobEffectInstance> adjusted) {}
    private static final ThreadLocal<java.util.Deque<NativeWindow>> NATIVE = ThreadLocal.withInitial(java.util.ArrayDeque::new);
    static {
        register(ResourceLocation.parse("fruitsdelight:appetizing"),
                new Adapter(APPETIZING, "cuisinejournal.effect.appetizing.description"));
        register(ResourceLocation.parse("fruitsdelight:digesting"),
                new Adapter(null, "cuisinejournal.effect.digesting.description",true));
        register(ResourceLocation.parse("fruitsdelight:chorus"), new Adapter(null,null,false,true));
        register(ResourceLocation.parse("farmersdelight:nourishment"),
                new Adapter(null,"cuisinejournal.effect.nourishment.description",false,false,NOURISHMENT));
        register(ResourceLocation.parse("minecraft:saturation"),
                new Adapter(null,"cuisinejournal.effect.saturation.description"));
        register(ResourceLocation.parse("brewinandchewin:sweet_heart"),
                new Adapter(null,"cuisinejournal.effect.sweet_heart.description"));
    }
    private CuisineEffectAdapters() {}
    public static void register(ResourceLocation effect, Adapter adapter) { ADAPTERS.put(effect, adapter); }

    public static boolean isNativeFood(net.minecraft.world.item.ItemStack stack, LivingEntity consumer) {
        return !stack.isEmpty() && !(stack.getItem() instanceof net.minecraft.world.item.PotionItem)
                && CuisineEdibility.isFood(stack.getFoodProperties(consumer));
    }

    public static void beginNativeFood(net.minecraft.world.item.ItemStack stack, LivingEntity consumer) {
        boolean food=ExquisitoParfaits.matches(stack)||isNativeFood(stack,consumer);
        NATIVE.get().push(new NativeWindow(food?consumer:null,food?stack.copyWithCount(1):net.minecraft.world.item.ItemStack.EMPTY,
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())));
    }

    public static void endNativeFood() {
        var windows=NATIVE.get();if(!windows.isEmpty())windows.pop();if(windows.isEmpty())NATIVE.remove();
    }

    public static boolean nativeFoodUse(LivingEntity consumer) {
        var windows=NATIVE.get();
        return !windows.isEmpty()&&windows.peek().consumer()==consumer
                ||consumer instanceof net.minecraft.world.entity.player.Player player&&PlacedFoodConsumption.handles(player);
    }

    public static net.minecraft.world.effect.MobEffectInstance limitNativeEffect(LivingEntity consumer,
            net.minecraft.world.effect.MobEffectInstance effect) {
        if(preservesParfaitStage(consumer)||NetherVineryFoodCompat.delayedNative())return effect;
        var windows=NATIVE.get();
        if(!windows.isEmpty()&&windows.peek().consumer()==consumer&&windows.peek().adjusted().add(effect)){
            // Heat is a host-specific bonus; food-slot lifetimes and flavor levels do not alter native effects.
            effect=GrillingFoodCompat.nativeDuration(effect);windows.peek().adjusted().add(effect);
        }
        return effect;
    }

    public static boolean blocksDigestedNative(LivingEntity consumer) {
        if(preservesParfaitStage(consumer))return false;
        var windows=NATIVE.get();
        return !CuisineGameplayRules.nativeEffectsEnabled()&&!windows.isEmpty()&&windows.peek().consumer()==consumer;
    }

    public static boolean blocksFoodEffects(LivingEntity consumer) {
        return !CuisineGameplayRules.nativeEffectsEnabled()
                && (blocksDigestedNative(consumer) || PlacedFoodConsumption.blocksNativeEffect(consumer));
    }

    public static boolean oneShotTick(ResourceLocation id) {
        Adapter adapter=ADAPTERS.get(id);
        return adapter!=null&&adapter.oneShotTick();
    }

    public static boolean preservesParfaitStage(LivingEntity consumer) {
        var windows=NATIVE.get();
        return !windows.isEmpty() && windows.peek().consumer()==consumer && ExquisitoParfaits.matches(windows.peek().food());
    }

    public static boolean oneShot(ResourceLocation id, boolean nativeInstant) {
        return nativeInstant||oneShotTick(id);
    }

    public static void applyOneShot(LivingEntity target, net.minecraft.world.effect.MobEffect effect, int amplifier) {
        if(effect.isInstantenous())effect.applyInstantenousEffect(null,null,target,amplifier,1);
        else if(oneShotTick(BuiltInRegistries.MOB_EFFECT.getKey(effect)))effect.applyEffectTick(target,amplifier);
    }

    public static boolean digesting(LivingEntity player) {
        if(player==null||!CuisineFoodRuntime.activeForCurrentThread())return false;
        for(var entry:ADAPTERS.entrySet())if(entry.getValue().digesting()) {
            var effect=BuiltInRegistries.MOB_EFFECT.getOptional(entry.getKey()).orElse(null);
            if(effect!=null&&player.hasEffect(effect))return true;
        }
        return false;
    }

    public static int digestionDuration(int original,int slots) {
        return (int)Math.min(Integer.MAX_VALUE,Math.max(1L,original)*2L*Math.max(1,slots));
    }

    public static TemporaryFood temporaryFood(LivingEntity player) {
        if (player == null || !CuisineFoodRuntime.activeForCurrentThread()) return null;
        for (var entry : ADAPTERS.entrySet()) {
            var effect = BuiltInRegistries.MOB_EFFECT.getOptional(entry.getKey()).orElse(null);
            if (effect != null && player.hasEffect(effect) && entry.getValue().temporaryFood() != null)
                return entry.getValue().temporaryFood();
        }
        return null;
    }

    public static Recovery recovery(LivingEntity player) {
        if (player == null || !CuisineFoodRuntime.activeForCurrentThread()) return null;
        for (var entry : ADAPTERS.entrySet()) if (entry.getValue().recovery() != null) {
            var effect = BuiltInRegistries.MOB_EFFECT.getOptional(entry.getKey()).orElse(null);
            if (effect != null && player.hasEffect(effect)) return entry.getValue().recovery();
        }
        return null;
    }

    public static boolean warmHeart(LivingEntity player) {
        if (player == null || !CuisineFoodRuntime.activeForCurrentThread()) return false;
        var effect = BuiltInRegistries.MOB_EFFECT.getOptional(ResourceLocation.parse("brewinandchewin:sweet_heart")).orElse(null);
        return effect != null && player.hasEffect(effect);
    }

    public static boolean applySaturation(LivingEntity target) {
        if (!(target instanceof net.minecraft.world.entity.player.Player) || !CuisineFoodRuntime.activeForCurrentThread()) return false;
        if (target instanceof net.minecraft.server.level.ServerPlayer player) CuisineFoodEvents.saturation(player);
        return true;
    }

    public static String descriptionKey(ResourceLocation effect, boolean valheim) {
        Adapter adapter = valheim ? ADAPTERS.get(effect) : null;
        return adapter == null ? null : adapter.descriptionKey();
    }

    public static String translatedDescriptionKey(String original, boolean valheim) {
        if (!valheim) return null;
        for (var entry : ADAPTERS.entrySet()) {
            String key = "effect." + entry.getKey().getNamespace() + "." + entry.getKey().getPath().replace('/', '.') + ".description";
            if (key.equals(original)) return entry.getValue().descriptionKey();
        }
        return null;
    }
}
