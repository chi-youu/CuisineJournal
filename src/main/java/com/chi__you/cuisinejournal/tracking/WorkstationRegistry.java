package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.Constants;
import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.chi__you.cuisinejournal.recipe.CulinaryTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which blocks count as cooking workstations.
 *
 * <p>Combines known host defaults, explicit registrations, configuration and
 * workstation definitions. All host identifiers are optional. Classification
 * alone does not install production hooks for an unknown machine.</p>
 */
public final class WorkstationRegistry {

    private static final Map<ResourceLocation, CookingMethod> DEFAULTS = defaults();

    private static final Map<ResourceLocation, CookingMethod> registered =
            new ConcurrentHashMap<>(DEFAULTS);
    private static final Map<ResourceLocation, CookingMethod> configured = new ConcurrentHashMap<>();

    private WorkstationRegistry() {
    }

    private static Map<ResourceLocation, CookingMethod> defaults() {
        Map<ResourceLocation, CookingMethod> map = new LinkedHashMap<>();
        String fd = Constants.FARMERS_DELIGHT_ID;
        map.put(new ResourceLocation(fd, "cooking_pot"), CookingMethod.COOKING_POT);
        map.put(new ResourceLocation(fd, "cutting_board"), CookingMethod.CUTTING_BOARD);
        map.put(new ResourceLocation(fd, "skillet"), CookingMethod.of(fd, "skillet"));
        map.put(new ResourceLocation(fd, "stove"), CookingMethod.of(fd, "stove"));
        // Vanilla's only cooking block without a screen. Everything else -
        // furnace, smoker, crafting table - is recognised by its menu instead,
        // which is why a campfire counted for nothing until it was listed here.
        map.put(new ResourceLocation("campfire"), CookingMethod.CAMPFIRE);
        map.put(new ResourceLocation("soul_campfire"), CookingMethod.CAMPFIRE);
        return map;
    }

    /**
     * @return the method to credit, or {@code null} when the block is not a
     *         workstation. Storage blocks never match, which is what stops
     *         shuffling cooked food around a chest from counting as cooking.
     */
    public static CookingMethod methodFor(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) {
            return null;
        }
        WorkstationDefinitions.Definition definition = WorkstationDefinitions.get(blockId);
        if (definition != null && !definition.enabled()) return null;
        CookingMethod local = configured.get(blockId);
        if (local != null) return local;
        if (definition != null && definition.method() != null) return CookingMethod.of(definition.method());
        {
            var block=state.getBlock();
            if(block instanceof net.minecraft.world.level.block.EntityBlock){
                // Registry lookup must not construct block entities: method names are exact host contracts.
                String name=block.getClass().getName();String method=null;
                if(name.startsWith("com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.")){
                    method=switch(block.getClass().getSimpleName()){
                        case "PotBlock"->"pot";case "StockpotBlock"->"stockpot";case "SteamerBlock"->"steamer";
                        case "TeapotBlock"->"teapot";case "MillstoneBlock"->"millstone";case "BambooTrayBlock"->"bamboo_tray";
                        case "ChoppingBoardBlock"->"chopping_board";default->null;};
                    if(method!=null)return CookingMethod.of("kaleidoscope_cookery",method);
                }
                if(name.equals("com.github.ysbbbbbb.kaleidoscopetavern.block.brew.BarrelBlock"))return CookingMethod.of("kaleidoscope_tavern","barrel");
            }
        }
        if(com.chi__you.cuisinejournal.recipe.NeapolitanCauldrons.get(state.getBlock())!=null
                ||blockId.toString().equals("neapolitan:milk_cauldron"))return CookingMethod.of("neapolitan","milkshake_cauldron");
        CookingMethod method = registered.get(blockId);
        if (method != null) {
            return method;
        }
        // Placed feasts are workstations for serving purposes: right-clicking
        // one with a bowl hands the player a portion.
        if (state.is(CulinaryTags.FEAST_BLOCKS)) {
            return CookingMethod.FEAST_SERVING;
        }
        // Anything a datapack has named. UNKNOWN because the tag carries no
        // method; that credits mastery for the dish without claiming to know
        // how the block cooked it.
        if (state.is(CulinaryTags.WORKSTATION_BLOCKS)) {
            return CookingMethod.UNKNOWN;
        }
        if (definition != null) return CookingMethod.UNKNOWN;
        return null;
    }

    public static boolean isWorkstation(BlockState state) {
        return methodFor(state) != null;
    }

    public static void register(ResourceLocation blockId, CookingMethod method) {
        registered.put(blockId, method);
    }

    /** Rebuilt from configuration on every reload so removing an entry takes effect. */
    public static void applyConfiguration(Iterable<String> extraBlocks) {
        registered.clear();
        configured.clear();
        registered.putAll(DEFAULTS);
        for (String raw : extraBlocks) {
            // "modid:block" or "modid:block=modid:method"
            String[] halves = raw.split("=", 2);
            ResourceLocation blockId = ResourceLocation.tryParse(halves[0].trim());
            if (blockId == null) {
                CuisineJournalLog.LOGGER.warn(
                        "compatibility.extraWorkstationBlocks contains an unparseable block id: '{}'",
                        raw);
                continue;
            }
            CookingMethod method = CookingMethod.UNKNOWN;
            if (halves.length == 2) {
                ResourceLocation methodId = ResourceLocation.tryParse(halves[1].trim());
                if (methodId == null) {
                    CuisineJournalLog.LOGGER.warn(
                            "compatibility.extraWorkstationBlocks contains an unparseable method id: "
                                    + "'{}'", raw);
                    continue;
                }
                method = CookingMethod.of(methodId);
            }
            registered.put(blockId, method);
            configured.put(blockId, method);
        }
    }

    public static Map<ResourceLocation, CookingMethod> all() {
        Map<ResourceLocation, CookingMethod> result = new LinkedHashMap<>(registered);
        WorkstationDefinitions.all().forEach((block, definition) -> {
            if (!definition.enabled()) result.remove(block);
            else if (definition.method() != null) result.put(block, CookingMethod.of(definition.method()));
            else result.putIfAbsent(block, CookingMethod.UNKNOWN);
        });
        result.putAll(configured);
        WorkstationDefinitions.all().forEach((block, d) -> {if (!d.enabled()) result.remove(block);});
        return Map.copyOf(result);
    }
}
