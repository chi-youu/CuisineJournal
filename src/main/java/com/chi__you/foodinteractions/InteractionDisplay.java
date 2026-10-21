package com.chi__you.foodinteractions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** One existing interaction, represented as display data only. */
public record InteractionDisplay(ResourceLocation block, ResourceLocation source,
                                 JsonElement input, ResourceLocation output, int count,
                                 Map<String,String> state, String kind, String evidence) {
    public static final String NAMESPACE = "food_interaction_recipes";

    public ResourceLocation recipeId() {
        String suffix = state.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "_" + entry.getValue()).collect(java.util.stream.Collectors.joining("_"));
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE,
                kind + "/" + block.getNamespace() + "/" + block.getPath() + "/"
                        + output.getNamespace() + "/" + output.getPath() + (suffix.isEmpty() ? "" : "/" + suffix));
    }

    public JsonObject json() {
        JsonObject json = new JsonObject();
        json.addProperty("type","lychee:block_interacting");
        json.addProperty("ghost",true);
        // Lychee's outer array denotes hands; a nested array denotes alternatives for one hand.
        if(input.isJsonArray()) {
            JsonArray hands=new JsonArray();hands.add(input.deepCopy());json.add("item_in",hands);
        } else json.add("item_in",input.deepCopy());
        JsonObject predicate = new JsonObject();
        JsonArray blocks = new JsonArray(); blocks.add(block.toString()); predicate.add("blocks",blocks);
        if (!state.isEmpty()) {
            JsonObject properties = new JsonObject(); state.forEach(properties::addProperty); predicate.add("state",properties);
        }
        json.add("block_in",predicate);
        JsonObject result = new JsonObject(); result.addProperty("type","drop_item");
        result.addProperty("item",output.toString()); result.addProperty("count",count);
        JsonArray post = new JsonArray(); post.add(result); json.add("post",post);
        // Metadata is ignored by Lychee, but describes this display without treating a tool as an ingredient.
        JsonObject metadata = new JsonObject(); metadata.addProperty("kind",kind);
        if (source != null) metadata.addProperty("source",source.toString());
        metadata.addProperty("evidence",evidence); json.add("food_interaction_recipes",metadata);
        return json;
    }
}
