package com.chi__you.cuisinejournal.challenge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A challenge, as written by a datapack.
 *
 * @param id           registry-style identifier, derived from the file path
 * @param scope        how long it lives
 * @param objectives   every objective must be satisfied
 * @param rewards      what completing it grants
 * @param prerequisite another challenge that must be complete first, or null
 * @param hidden       kept out of the challenge list until it is available
 */
public record ChallengeDefinition(ResourceLocation id,
                                  Scope scope,
                                  List<ChallengeObjective> objectives,
                                  ChallengeReward rewards,
                                  ResourceLocation prerequisite,
                                  boolean hidden, boolean manual, int durationTicks) {
    public ChallengeDefinition(ResourceLocation id,Scope scope,List<ChallengeObjective> objectives,ChallengeReward rewards,
                               ResourceLocation prerequisite,boolean hidden){this(id,scope,objectives,rewards,prerequisite,hidden,false,0);}

    public enum Scope {
        /** Available forever, completed once. */
        PERMANENT,
        /** Regenerated each Minecraft day. */
        DAILY,
        /** Part of a discovery chain; unlocked by its prerequisite. */
        CHAIN;

        public static Scope byName(String raw) {
            if("WEEKLY".equalsIgnoreCase(raw))throw new ChallengeParseException("Weekly challenges have been removed");
            if (raw == null) {
                return PERMANENT;
            }
            for (Scope scope : values()) {
                if (scope.name().equalsIgnoreCase(raw)) {
                    return scope;
                }
            }
            return PERMANENT;
        }
    }

    public ChallengeDefinition {
        objectives = List.copyOf(objectives);
        if(objectives.isEmpty()||objectives.size()>16)throw new ChallengeParseException("Invalid challenge size");
        manual=false;
        durationTicks=0;
    }

    public String titleKey() {
        if(id.getPath().startsWith("daily/"))return "cuisinejournal.challenge.generated.daily.title";
        return "cuisinejournal.challenge." + id.getNamespace() + "." + id.getPath() + ".title";
    }

    public String descriptionKey() {
        if(id.getPath().startsWith("daily/"))return "cuisinejournal.challenge.generated.description";
        return "cuisinejournal.challenge." + id.getNamespace() + "." + id.getPath() + ".description";
    }

    public static ChallengeDefinition fromJson(ResourceLocation id, JsonObject json) {
        if (!json.has("objectives") || !json.get("objectives").isJsonArray()) {
            throw new ChallengeParseException("challenge has no \"objectives\" array");
        }
        JsonArray array = json.getAsJsonArray("objectives");
        if (array.isEmpty()) {
            throw new ChallengeParseException("challenge has an empty \"objectives\" array");
        }
        List<ChallengeObjective> objectives = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) {
                throw new ChallengeParseException("objective " + i + " is not an object");
            }
            objectives.add(ChallengeObjective.fromJson(array.get(i).getAsJsonObject()));
        }

        Scope scope = Scope.byName(json.has("scope") ? json.get("scope").getAsString() : null);
        ChallengeReward rewards = json.has("rewards")
                ? ChallengeReward.fromJson(json.getAsJsonObject("rewards"))
                : ChallengeReward.NONE;

        ResourceLocation prerequisite = null;
        if (json.has("prerequisite")) {
            prerequisite = ResourceLocation.tryParse(json.get("prerequisite").getAsString());
            if (prerequisite == null) {
                throw new ChallengeParseException("\"prerequisite\" is not a valid id");
            }
        }
        boolean hidden = json.has("hidden") && json.get("hidden").getAsBoolean();

        return new ChallengeDefinition(id, scope, objectives, rewards, prerequisite, hidden,false,0);
    }
}
