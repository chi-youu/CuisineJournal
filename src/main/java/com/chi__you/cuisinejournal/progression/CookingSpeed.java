package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.chi__you.cuisinejournal.tracking.WorkstationRegistry;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import com.chi__you.cuisinejournal.tracking.VerifiedWorkstations;
import com.chi__you.cuisinejournal.tracking.WorkstationDefinitions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;

/** Requests are collected once per server tick; native progress hooks consume the strongest request. */
public final class CookingSpeed {
    private record Request(long tick, double extra) {}
    private static final Map<BlockEntity, Request> REQUESTS = new WeakHashMap<>();
    private static final Map<BlockEntity, Map<Object, ProgressBudget>> BUDGETS = new WeakHashMap<>();
    private static final Map<String, BiFunction<ServerPlayer, CookingMethod, Double>> PROVIDERS = new LinkedHashMap<>();
    private CookingSpeed() {}

    /** Extra work per native unit, e.g. 0.5 means 50% faster. Providers can award future level bonuses. */
    public static void register(String id, BiFunction<ServerPlayer, CookingMethod, Double> provider) {
        PROVIDERS.put(id, provider);
    }
    public static void clear() { REQUESTS.clear(); BUDGETS.clear(); }
    public static int aetherNumber(BlockEntity be, String method) {
        Object value = HostReflection.invoke(be, method);
        return value instanceof Number number ? number.intValue() : 0;
    }
    public static CookingMethod timedMethod(BlockEntity be) {
        for(Class<?> type=be.getClass();type!=null;type=type.getSuperclass()) {
            if(type.getName().equals("dev.xkmc.youkaishomecoming.content.pot.steamer.SteamerBlockEntity"))
                return CookingMethod.of("youkaishomecoming","steaming");
            if(type.getName().equals("dev.xkmc.youkaishomecoming.content.pot.base.TimedRecipeBlockEntity")) {
                Object recipeType=privateRecipe(be,"getRecipeType");
                if(recipeType instanceof net.minecraft.world.item.crafting.RecipeType<?> actual) {
                    var id=BuiltInRegistries.RECIPE_TYPE.getKey(actual);
                    return id==null?null:CookingMethod.fromRecipeType(id);
                }
            }
        }
        return null;
    }

    public static void tick(ServerPlayer player, BlockPos pos, CookingMethod method, AbstractContainerMenu menu) {
        if (!(player.level() instanceof ServerLevel level) || method == null) return;
        if (menu != null) {
            var station = ProductionTracker.menu(menu);
            if (station != null && station.owner() instanceof BlockEntity exact && exact.getLevel() == level) {
                pos = exact.getBlockPos(); method = station.method();
            } else if (pos == null || menu == player.inventoryMenu || !level.isLoaded(pos)
                    || !method.equals(WorkstationRegistry.methodFor(level.getBlockState(pos)))) return;
        }
        if (pos == null || !level.isLoaded(pos)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || excludedFurnace(be)) return;
        var definition = WorkstationDefinitions.get(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()));
        if (definition != null && (!definition.enabled()
                || !definition.capabilities().contains(WorkstationDefinitions.Capability.TIMED_PROCESSING))) return;
        request(be, playerExtra(player, method));
    }

    public static double playerExtra(ServerPlayer player, CookingMethod method) {
        var config = ConfigManager.server();
        double extra = 0;
        var profiles = ProfileManager.get();
        if (profiles != null && profiles.of(player).skillsActive()) {
            extra = com.chi__you.cuisinejournal.skills.CookingSkills.value(profiles.of(player),"speed");
        } else if (profiles != null && config.mastery.enabled && config.mastery.bonuses.enabled) {
            var record = profiles.of(player).peekMethod(method);
            int familiarity = record == null ? 0 : (int) Math.min(5, record.preparations() / 25);
            extra = config.mastery.bonuses.cookingSpeedAtMaxRank * familiarity / 5.0;
        }
        for (var provider : PROVIDERS.values()) {
            Double supplied = provider.apply(player, method);
            if (supplied != null && Double.isFinite(supplied)) extra += Math.max(0, supplied);
        }
        return Double.isFinite(extra) ? Math.max(0, Math.min(20, extra)) : 0;
    }
    private static void request(BlockEntity be, double extra) {
        long tick = be.getLevel().getGameTime();
        Request old = REQUESTS.get(be);
        REQUESTS.put(be, new Request(tick, old != null && old.tick == tick ? Math.max(old.extra, extra) : extra));
    }
    private static double rate(BlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide || be.isRemoved() || excludedFurnace(be)) return 0;
        Request request = REQUESTS.get(be);
        long tick = be.getLevel().getGameTime();
        return request != null && request.tick <= tick && tick - request.tick <= 1 ? request.extra : 0;
    }
    private static ProgressBudget budget(BlockEntity be, Object key) {
        return BUDGETS.computeIfAbsent(be, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(key, ignored -> new ProgressBudget());
    }
    public static int advance(BlockEntity be, Object key, int before, int proposed, int total) {
        double extra=rate(be);
        if(proposed<=before||extra<=0) {
            var existing=BUDGETS.get(be);
            if(proposed<=before&&existing!=null)existing.remove(key);
            return proposed;
        }
        return budget(be, key).advance(before, proposed, total, extra);
    }
    public static double scale(BlockEntity be, double increment) {
        return increment > 0 ? increment * (1 + rate(be)) : increment;
    }
    private static boolean excludedFurnace(BlockEntity be) {
        return be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
                && !(be instanceof net.minecraft.world.level.block.entity.SmokerBlockEntity)
                && !be.getClass().getName().startsWith("com.aetherteam.aether.");
    }
    private static Object privateRecipe(BlockEntity be,String name,Object... args) {
        for(Class<?> type=be.getClass();type!=null;type=type.getSuperclass())for(var method:type.getDeclaredMethods()) {
            if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;
            try {method.setAccessible(true);return method.invoke(be,args);}
            catch(ReflectiveOperationException failure) {return null;}
        }
        return null;
    }
    public static int number(Object owner, String name) {
        Object value = HostReflection.field(owner, name);
        return value instanceof Number number ? number.intValue() : 0;
    }
    public static void write(BlockEntity be, String field, int proposed, String totalField) {
        write(be, field, proposed, number(be, totalField));
    }
    public static void write(BlockEntity be, String name, int proposed, int total) {
        int value = advance(be, name, number(be, name), proposed, total);
        for (Class<?> type = be.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); field.setInt(be, value); return; }
            catch (NoSuchFieldException ignored) {}
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Verified cooking timer changed: " + name, failure); }
        }
        throw new IllegalStateException("Missing verified cooking timer: " + name);
    }
    /** Called inside the native heated cooking loop, before its per-slot increments. */
    public static int[] array(BlockEntity be, String progressName, String totalName, int nativeStep) {
        int[] progress = (int[]) HostReflection.field(be, progressName);
        if(rate(be)<=0)return progress;
        int[] total = (int[]) HostReflection.field(be, totalName);
        Object inventory = HostReflection.invoke(be, "getItems");
        if (inventory == null) inventory = be;
        for (int slot = 0; slot < progress.length; slot++) {
            var item=inventory instanceof java.util.List<?> list && list.get(slot) instanceof net.minecraft.world.item.ItemStack stack
                    ? stack : VerifiedWorkstations.stack(inventory, slot);
            if (item.isEmpty() || total[slot] <= 0) continue;
            int proposed = progress[slot] + nativeStep;
            progress[slot] += Math.max(0, budget(be,progressName+slot).advance(progress[slot], proposed, total[slot],rate(be)) - proposed);
        }
        return progress;
    }
}
