package com.chi__you.foodinteractions;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pack authors can exclude blocks or supply exact, display-only relations. */
public final class InteractionRules {
    public record File(ResourceLocation id, JsonObject json) {}
    public record Resolved(Map<ResourceLocation,JsonObject> blocks, List<String> problems) {}
    private InteractionRules() {}

    public static Resolved load(ResourceManager resources) {
        List<File> files = new ArrayList<>(); List<String> problems = new ArrayList<>();
        resources.listResources("food_interaction_recipes/compat",id -> id.getPath().endsWith(".json"))
                .forEach((id,resource) -> {
                    try (var reader=resource.openAsReader()) {
                        files.add(new File(id,com.google.gson.JsonParser.parseReader(reader).getAsJsonObject()));
                    } catch(Exception failure) { problems.add(id+": "+failure.getMessage()); }
                });
        Resolved resolved=resolve(files,mod -> net.minecraftforge.fml.ModList.get().isLoaded(mod));
        problems.addAll(resolved.problems()); return new Resolved(resolved.blocks(),List.copyOf(problems));
    }

    public static Resolved resolve(List<File> files, java.util.function.Predicate<String> loaded) {
        Map<ResourceLocation,JsonObject> blocks=new LinkedHashMap<>(); List<String> problems=new ArrayList<>();
        files.stream().sorted(java.util.Comparator.comparing(file -> file.id().toString())).forEach(file -> {
            try {
                JsonObject json=file.json();
                if(json.has("required_mods")) for(var mod:json.getAsJsonArray("required_mods"))
                    if(!loaded.test(mod.getAsString()))return;
                if(!json.has("blocks"))return;
                Map<ResourceLocation,JsonObject> next=new LinkedHashMap<>(blocks);
                for(var raw:json.getAsJsonArray("blocks")) {
                    JsonObject rule=raw.getAsJsonObject();
                    ResourceLocation id=ResourceLocation.parse(rule.get("block").getAsString());
                    next.put(id,rule.deepCopy());
                }
                blocks.clear();blocks.putAll(next);
            } catch(RuntimeException failure) { problems.add(file.id()+": "+failure.getMessage()); }
        });
        return new Resolved(Map.copyOf(blocks),List.copyOf(problems));
    }

    public static boolean shouldGenerate(ResourceLocation output,Set<ResourceLocation> existing,boolean enabled) {
        return enabled && !existing.contains(output);
    }
}
