package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import static com.chi__you.cuisinejournal.compat.UnificationContent.id;

/** Edits only exact members of configured groups, after the selected packs and recipe repairs. */
public final class UnificationRecipes {
    private static final Set<String> OUTPUTS = Set.of("result", "results", "output", "secondaryOutput", "secondary_output");
    private UnificationRecipes() {}
    public static int apply(Map<ResourceLocation, JsonElement> recipes, UnificationContent.Plan plan) {
        if (!plan.enabled()) return 0;
        Set<ResourceLocation> nativeValues=new HashSet<>();
        recipes.values().forEach(recipe->foodValueItems(recipe,nativeValues));
        int changes = 0;
        for (var key : List.copyOf(recipes.keySet())) {
            JsonElement old = recipes.get(key);
            JsonElement edited = edit(old, key, plan,nativeValues);
            if (edited == null) { recipes.remove(key); changes++; }
            else if (!edited.equals(old)) { recipes.put(key, edited); changes++; }
        }
        return changes;
    }
    private static JsonElement edit(JsonElement original, ResourceLocation key, UnificationContent.Plan plan,Set<ResourceLocation> nativeValues) {
        if (!original.isJsonObject()) return original;
        JsonObject recipe = original.deepCopy().getAsJsonObject();
        if (recipe.has("recipes") && recipe.get("recipes").isJsonArray()) {
            var branches = new JsonArray();
            for (var branch : recipe.getAsJsonArray("recipes")) {
                if (!branch.isJsonObject() || !branch.getAsJsonObject().has("recipe")) { branches.add(branch); continue; }
                var copy = branch.deepCopy().getAsJsonObject();
                var edited = edit(copy.get("recipe"), key, plan,nativeValues);
                if (edited != null) { copy.add("recipe", edited); branches.add(copy); }
            }
            if (branches.isEmpty()) return null;
            recipe.add("recipes", branches); return recipe;
        }
        UnificationIngredientAliases.inputs(recipe,key,plan);
        UnificationTeaBlocks.recipe(key,recipe,plan);
        Set<ResourceLocation> outputs = outputs(recipe);
        Set<ResourceLocation> inputs = new HashSet<>();
        for (var e : recipe.entrySet()) if (!OUTPUTS.contains(e.getKey()) && !Set.of("conditions", "type").contains(e.getKey()))
            itemIds(e.getValue(), inputs);
        // A single source egg must have exactly one recipe per furnace kind.
        String type = recipe.has("type") ? recipe.get("type").getAsString() : "";
        if(type.equals("crockpot:food_values")&&recipe.has("items")){
            var items=new JsonArray();Set<ResourceLocation> seen=new HashSet<>();
            for(var raw:recipe.getAsJsonArray("items")){
                var before=id(raw.getAsString());var after=plan.replace(before);
                if(plan.excluded().contains(before))continue;
                if(before.equals(id("crockpot:cooked_eggplant"))&&plan.winner("cuisinejournal:smoked_eggplant")!=null){
                    for(String path:List.of("smoked_eggplant","smoked_cut_eggplant")){
                        var target=id("culturaldelights:"+path);
                        if(!nativeValues.contains(target)&&seen.add(target))items.add(target.toString());
                    }
                    continue;
                }
                if(!before.equals(after)&&nativeValues.contains(after))continue;
                if(seen.add(after))items.add(after.toString());
            }
            if(items.isEmpty())return null;
            recipe.add("items",items);return recipe;
        }
        if(milkContainerConflict(recipe,plan))return null;
        if(isGarlicReassembly(inputs,outputs,plan))return null;
        if (inputs.contains(id("minecraft:sniffer_egg")) && Set.of("minecraft:smelting", "minecraft:smoking",
                "minecraft:campfire_cooking").contains(type)) {
            var egg = plan.winner("cuisinejournal:sniffer_egg");
            if (egg != null && (egg.getNamespace().equals("trailandtales_delight") || egg.getNamespace().equals("immortalers_delight"))) {
                ResourceLocation wanted = egg.getNamespace().equals("trailandtales_delight")
                        ? id("trailandtales_delight:cooked_sniffer_egg_block") : egg;
                if (!outputs.isEmpty() && outputs.stream().anyMatch(out -> !out.equals(wanted))) return null;
            } else if (type.equals("minecraft:smoking") && plan.winner("cuisinejournal:sniffer_smoking") != null
                    && outputs.stream().anyMatch(out -> !out.equals(plan.winner("cuisinejournal:sniffer_smoking")))) {
                return null;
            }
        }
        // Retain the representative's producers. Storage unpacking must stay available.
        for (var out : outputs) {
            if (plan.excluded().contains(out)) return null;
            if (!plan.replace(out).equals(out)) {
                boolean unpacking = inputs.stream().anyMatch(in -> {
                    var group = groupOf(in, plan);
                    return group != null && group.endsWith("_storage")
                            || in.equals(id("rusticdelight:coffee_beans_bag"));
                });
                boolean coffeeStage = isCoffeeStage(recipe, inputs, out, plan);
                boolean fluidServing=isFluidServing(type);
                if (!unpacking && !coffeeStage && !fluidServing && !plan.keptRecipeOutputs().contains(out)) return null;
            }
        }
        if (inputs.stream().anyMatch(in->plan.excluded().contains(in)&&plan.replace(in).equals(in))) return null;
        // No alternate three-bottle recipe survives when four-bottle milk is selected.
        if (id("farmersdelight:milk_bottle").equals(plan.winner("cuisinejournal:milk_bottle"))
                && key.getNamespace().equals("neapolitan") && key.getPath().startsWith("milk/")) return null;
        replaceItems(recipe, plan);
        // Never leave a hidden storage producer or a self-conversion after normalization.
        Set<ResourceLocation> changedOutputs = outputs(recipe);
        Set<ResourceLocation> changedInputs = new HashSet<>();
        for (var e : recipe.entrySet()) if (!OUTPUTS.contains(e.getKey()) && !e.getKey().equals("conditions")) itemIds(e.getValue(), changedInputs);
        if (!changedOutputs.isEmpty() && changedInputs.containsAll(changedOutputs)
                && (outputs.stream().anyMatch(out -> !plan.replace(out).equals(out))||inputs.stream().anyMatch(in->!plan.replace(in).equals(in)))) return null;
        addWhiteCorn(recipe, plan);
        return recipe;
    }
    private static boolean isFluidServing(String type){
        return Set.of("cosmopolitan:tub_extracting","cosmopolitan:tub_injecting","brewinandchewin:keg_pouring",
                "create:filling","create:emptying").contains(type);
    }
    private static boolean isGarlicReassembly(Set<ResourceLocation> inputs,Set<ResourceLocation> outputs,UnificationContent.Plan plan){
        var whole=plan.winner("cuisinejournal:garlic");var cut=plan.winner("cuisinejournal:garlic_cut");
        if(whole==null||cut==null||outputs.stream().noneMatch(out->plan.replace(out).equals(whole)))return false;
        return inputs.stream().anyMatch(in->plan.replace(in).equals(cut))
                &&(inputs.stream().anyMatch(in->!plan.replace(in).equals(in))||outputs.stream().anyMatch(out->!plan.replace(out).equals(out)));
    }
    private static boolean milkContainerConflict(JsonObject recipe,UnificationContent.Plan plan){
        var winner=plan.winner("cuisinejournal:milk_bottle");
        if(winner==null||!winner.equals(id("farmersdelight:milk_bottle"))||!outputs(recipe).contains(id("minecraft:milk_bucket")))return false;
        if(!recipe.has("ingredients")||!recipe.get("ingredients").isJsonArray())return false;
        int milk=0;
        for(var raw:recipe.getAsJsonArray("ingredients"))if(raw.isJsonObject()){
            var ingredient=raw.getAsJsonObject();
            if(ingredient.has("item")&&plan.replace(id(ingredient.get("item").getAsString())).equals(winner))milk++;
            else if(ingredient.has("tag")&&Set.of("forge:bottles/milk","forge:milk/milk_bottle","forge:milk_bottles").contains(ingredient.get("tag").getAsString()))milk++;
        }
        return milk>0&&milk!=4;
    }
    private static void foodValueItems(JsonElement raw,Set<ResourceLocation> items){
        if(!raw.isJsonObject())return;var value=raw.getAsJsonObject();
        if(value.has("type")&&value.get("type").getAsString().equals("crockpot:food_values")&&value.has("items"))
            value.getAsJsonArray("items").forEach(item->items.add(id(item.getAsString())));
        if(value.has("recipes"))for(var branch:value.getAsJsonArray("recipes"))
            if(branch.isJsonObject()&&branch.getAsJsonObject().has("recipe"))foodValueItems(branch.getAsJsonObject().get("recipe"),items);
    }
    private static boolean isCoffeeStage(JsonObject recipe, Set<ResourceLocation> inputs, ResourceLocation out, UnificationContent.Plan plan) {
        var type = recipe.has("type") ? recipe.get("type").getAsString() : "";
        if (!type.equals("minecraft:smelting") && !type.equals("minecraft:smoking") && !type.equals("minecraft:campfire_cooking")) return false;
        var roasted = plan.winner("cuisinejournal:coffee_roasted");
        var raw = plan.winner("cuisinejournal:coffee_raw");
        return roasted != null && raw != null && !raw.equals(roasted) && plan.replace(out).equals(roasted)
                && inputs.stream().anyMatch(in -> plan.replace(in).equals(raw));
    }
    private static String groupOf(ResourceLocation item, UnificationContent.Plan plan) {
        var winner = plan.replace(item);
        return plan.winners().entrySet().stream().filter(e -> e.getValue().equals(winner)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
    private static void addWhiteCorn(JsonObject recipe, UnificationContent.Plan plan) {
        if (!recipe.has("type") || !recipe.get("type").getAsString().equals("farmersdelight:cutting")) return;
        var kernel = plan.winner("cuisinejournal:corn_kernel");
        if (kernel == null || !outputs(recipe).contains(kernel) || !plan.winners().containsKey("cuisinejournal:corn")) return;
        // Presence is recorded independently of whether Brazilian yellow kernels won their group.
        if (!plan.replacements().containsKey(id("braziliandelight:kernels")) && !kernel.equals(id("braziliandelight:kernels"))) return;
        if (!recipe.has("result") || !recipe.get("result").isJsonArray()) return;
        var results = recipe.getAsJsonArray("result");
        if (outputs(recipe).contains(id("braziliandelight:white_kernels"))) return;
        var white = new JsonObject(); white.addProperty("item", "braziliandelight:white_kernels"); white.addProperty("chance", 0.5); results.add(white);
    }
    public static Set<ResourceLocation> outputs(JsonObject recipe) {
        var result = new HashSet<ResourceLocation>();
        for (String field : OUTPUTS) if (recipe.has(field)) {
            var value = recipe.get(field);
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                var parsed = ResourceLocation.tryParse(value.getAsString()); if (parsed != null) result.add(parsed);
            } else itemIds(value, result);
        }
        return result;
    }
    private static void itemIds(JsonElement value, Set<ResourceLocation> result) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(v -> itemIds(v, result));
        else if (value.isJsonObject()) {
            var obj = value.getAsJsonObject();
            for (var e : obj.entrySet()) {
                if (e.getKey().equals("item") && e.getValue().isJsonPrimitive()) {
                    var parsed = ResourceLocation.tryParse(e.getValue().getAsString()); if (parsed != null) result.add(parsed);
                } else if (!Set.of("nbt", "conditions").contains(e.getKey())) itemIds(e.getValue(), result);
            }
        }
    }
    private static void replaceItems(JsonElement value, UnificationContent.Plan plan) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(v -> replaceItems(v, plan));
        else if (value.isJsonObject()) {
            var obj = value.getAsJsonObject();
            for (var e : List.copyOf(obj.entrySet())) {
                if (Set.of("nbt", "conditions").contains(e.getKey())) continue;
                if ((e.getKey().equals("item") || e.getKey().equals("result")) && e.getValue().isJsonPrimitive()
                        && e.getValue().getAsJsonPrimitive().isString()) {
                    var item = ResourceLocation.tryParse(e.getValue().getAsString());
                    if (item != null) obj.addProperty(e.getKey(), plan.replace(item).toString());
                } else replaceItems(e.getValue(), plan);
            }
        }
    }
}
