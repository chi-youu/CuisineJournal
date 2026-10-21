package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Reads Fruits Delight's JEI interaction table, including intermediate cauldron states. */
public final class FruitCauldronBridge {
    public static final ResourceLocation TYPE = ResourceLocation.parse("fruitsdelight:cauldron");
    public record Step(ResourceLocation state, ResourceLocation input, int count,
                       ResourceLocation next, ResourceLocation output, int outputCount) {}
    private FruitCauldronBridge() {}

    public static List<RecipeEntry> read() {
        try {
            Class<?> type = Class.forName("dev.xkmc.fruitsdelight.content.cauldrons.CauldronRecipe");
            List<Step> steps = new ArrayList<>();
            for (Object raw : (List<?>) type.getField("LIST").get(null)) {
                ItemStack state = (ItemStack) type.getMethod("getBlockInput").invoke(raw);
                ItemStack input = (ItemStack) type.getMethod("getItemInput").invoke(raw);
                ItemStack next = (ItemStack) type.getMethod("getBlockOutput").invoke(raw);
                ItemStack output = (ItemStack) type.getMethod("getItemOutput").invoke(raw);
                if (!state.isEmpty() && !input.isEmpty()) steps.add(new Step(id(state), id(input), input.getCount(),
                        next.isEmpty() ? null : id(next), output.isEmpty() ? null : id(output), output.getCount()));
            }
            return recipes(steps);
        } catch (ClassNotFoundException absent) {
            return List.of();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            CuisineJournalLog.LOGGER.warn("Cannot read Fruits Delight cauldron recipes", failure);
            return List.of();
        }
    }

    private static ResourceLocation id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()); }

    public static ResourceLocation storedFood(net.minecraft.world.item.Item item) {
        if (!(item instanceof net.minecraft.world.item.BlockItem blockItem)) return null;
        Object block = blockItem.getBlock();
        String name = block.getClass().getName();
        String method = name.equals("dev.xkmc.fruitsdelight.content.block.JelloBlock") ? "getJello"
                : name.equals("dev.xkmc.fruitsdelight.content.block.JellyBlock") ? "getJelly" : null;
        if (method == null) return null;
        try {
            Object fruit = block.getClass().getField("fruit").get(block);
            return BuiltInRegistries.ITEM.getKey((net.minecraft.world.item.Item) fruit.getClass().getMethod(method).invoke(fruit));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { return null; }
    }

    public static List<RecipeEntry> recipes(List<Step> steps) {
        steps=steps.stream().filter(step->{
            boolean valid=step.count()>=1&&step.count()<=64&&step.outputCount()>=0&&step.outputCount()<=64;
            if(!valid)CuisineJournalLog.LOGGER.warn("Skipping invalid Fruits Delight cauldron step: {}",step);
            return valid;
        }).toList();
        Set<ResourceLocation> products = new HashSet<>();
        steps.forEach(step -> { if (step.output() != null) products.add(step.output()); });
        Map<ResourceLocation,List<Step>> parents = new LinkedHashMap<>();
        for (Step step : steps) if (step.next() != null && !products.contains(step.input()))
            parents.computeIfAbsent(step.next(), unused -> new ArrayList<>()).add(step);
        List<RecipeEntry> result = new ArrayList<>();
        for (Step step : steps) {
            if (step.output() == null || step.outputCount() < 1) continue;
            List<RecipeEntry.IngredientSlot> inputs = new ArrayList<>();
            if (!collect(step.state(), parents, new HashSet<>(), inputs)) continue;
            if (!container(step.input())) add(inputs, step.input(), step.count());
            result.add(new RecipeEntry(ResourceLocation.fromNamespaceAndPath("fruitsdelight",
                    "cauldron/" + step.output().getPath()), TYPE, CookingMethod.of(TYPE), step.output(),
                    step.outputCount(), inputs, "fruitsdelight"));
        }
        return List.copyOf(result);
    }

    private static boolean collect(ResourceLocation state, Map<ResourceLocation,List<Step>> parents,
                                   Set<ResourceLocation> path, List<RecipeEntry.IngredientSlot> inputs) {
        List<Step> candidates = parents.getOrDefault(state, List.of());
        if (candidates.isEmpty()) return true;
        if (candidates.size() != 1 || !path.add(state)) return false;
        Step step = candidates.get(0);
        if (!collect(step.state(), parents, path, inputs)) return false;
        add(inputs, step.input(), step.count());
        return true;
    }

    private static void add(List<RecipeEntry.IngredientSlot> inputs, ResourceLocation item, int count) {
        if (count < 1 || count > 64) throw new IllegalArgumentException("Invalid cauldron input count");
        for (int i = 0; i < count; i++) inputs.add(new RecipeEntry.IngredientSlot(List.of(item)));
    }

    private static boolean container(ResourceLocation item) {
        return item.toString().equals("minecraft:glass_bottle") || item.toString().equals("minecraft:bowl");
    }
}
