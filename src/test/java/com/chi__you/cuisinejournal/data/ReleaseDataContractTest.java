package com.chi__you.cuisinejournal.data;

import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.food.CuisineFoodState;
import com.chi__you.cuisinejournal.food.IngredientProvenance;
import com.chi__you.cuisinejournal.tracking.ProductionLedger;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** First-release data identities and round trips; pre-release save compatibility is intentionally absent. */
public final class ReleaseDataContractTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Map<ResourceLocation, Resource> files = new LinkedHashMap<>();
        String definition = "{\"scope\":\"CHAIN\",\"objectives\":[{\"type\":\"COOK_RECIPE\",\"amount\":%d}]}";
        files.put(id("cuisinejournal/challenges/first_steps.json"), resource(definition.formatted(9)));
        var result = DatapackLoader.load(manager(files));
        check(result.problems().isEmpty() && result.challenges().size() == 1, "one canonical resource directory");
        check(result.challenges().get(0).objectives().get(0).amount() == 9, "resource manager's selected definition is used");
        check(result.challenges().get(0).id().equals(id("first_steps")), "built-in identity uses the journal namespace");
        files.put(id("cuisinejournal/challenges/invalid.json"), resource("{}"));
        result = DatapackLoader.load(manager(files));
        check(result.challenges().size() == 1 && result.problems().size() == 1, "bad resource cannot hide valid definitions");

        UUID player = new UUID(1, 2);
        CulinaryProfile profile = new CulinaryProfile(player);
        profile.awardBadge(id("home_cook"));
        check(profile.selectBadge(id("home_cook")), "earned badge selected");
        var restored = CulinaryProfile.load(player, profile.save());
        check(restored.selectedBadge().equals(id("home_cook")), "single badge identity persists");
        check(!profile.save().contains("title") && !profile.save().contains("honorsMigrated"), "no retired title storage");
        check(Files.readString(Path.of("src/main/resources/META-INF/services/com.chi__you.cuisinejournal.platform.IPlatformHelper"))
                .trim().equals("com.chi__you.cuisinejournal.forge.ForgePlatformHelper"), "service registration matches implementation");
        for (String language : List.of("en_us", "zh_cn")) {
            var json = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/cuisinejournal/lang/" + language + ".json"))).getAsJsonObject();
            check(json.has("cuisinejournal.journal.title"), "journal title: " + language);
            check(json.has("cuisinejournal.method.minecraft.crafting"), "dynamic method name: " + language);
            check(json.has("cuisinejournal.category.cuisinejournal.other_food"), "fallback category name: " + language);
            check(json.keySet().stream().noneMatch(key -> key.contains("mealmastery")), "no duplicate pre-release language identities");
        }
        itemDataRoundTrips(player);
        var foodApi = com.chi__you.cuisinejournal.api.CuisineFoodAPI.class;
        for (String method : List.of("isActive", "isFull", "remainingTimelineTicks")) {
            check(foodApi.getMethod(method, net.minecraft.world.entity.player.Player.class) != null,
                    "existing CYS integration entry point: " + method);
        }
        check(foodApi.getMethod("consumeTimelineTicks", net.minecraft.world.entity.player.Player.class, int.class)
                .getReturnType() == int.class, "existing CYS food-time consumption contract");
        System.out.println("ReleaseDataContractTest: " + checks + " checks passed");
    }

    private static void itemDataRoundTrips(UUID player) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrap = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true);
        bootstrap.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
        var stack = new ItemStack(Items.APPLE);
        stack.getOrCreateTag().putString("host:test", "preserved");
        var stamp = new MealStamp(3, true, 5, player, "Chef");
        stamp.write(stack);
        check(MealStamp.ROOT.equals("cuisinejournal:quality") && stack.getTag().contains(MealStamp.ROOT, 10), "namespaced quality compound");
        check(stamp.equals(MealStamp.read(ItemStack.of(stack.save(new CompoundTag())))), "quality/cook survive stack NBT round trip");
        check(stack.getTag().getString("host:test").equals("preserved"), "quality leaves host data untouched");
        var sources = new net.minecraft.nbt.ListTag();
        sources.add(net.minecraft.nbt.StringTag.valueOf("minecraft:apple"));
        stack.getOrCreateTag().put(IngredientProvenance.KEY, sources);
        var batch = new ProductionLedger.Batch("minecraft:apple", 1, Set.of(player), Set.of("minecraft:apple"), "minecraft:crafting");
        ProductionTracker.pending(stack, batch);
        var restored = ItemStack.of(stack.save(new CompoundTag()));
        check(IngredientProvenance.sources(restored).equals(List.of("minecraft:apple")), "actual sources have an independent shared key");
        var pending = ProductionLedger.readBatch(restored.getTag().getCompound(ProductionTracker.PENDING));
        check(pending.count() == 1 && pending.contributors().equals(batch.contributors())
                && pending.ingredients().equals(batch.ingredients()) && pending.method().equals(batch.method()),
                "pending production persists independently");
        check(stamp.equals(MealStamp.read(restored)), "pending production does not erase quality");
        check(CuisineFoodState.ROOT.equals("cuisinejournal:food_state"), "player food state uses journal namespace");
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("cuisinejournal:" + path); }
    private static Resource resource(String json) {
        var pack = (PackResources) Proxy.newProxyInstance(ReleaseDataContractTest.class.getClassLoader(), new Class<?>[]{PackResources.class},
                (proxy, method, args) -> method.getName().equals("packId") ? "test" : null);
        return new Resource(pack, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
    @SuppressWarnings("unchecked")
    private static ResourceManager manager(Map<ResourceLocation, Resource> files) {
        return (ResourceManager) Proxy.newProxyInstance(ReleaseDataContractTest.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("listResources")) return null;
                    Map<ResourceLocation, Resource> selected = new LinkedHashMap<>();
                    files.forEach((id, resource) -> {
                        if (id.getPath().startsWith(args[0] + "/") && ((Predicate<ResourceLocation>) args[1]).test(id)) selected.put(id, resource);
                    });
                    return selected;
                });
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
