package com.chi__you.cuisinejournal.challenge;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every challenge definition currently loaded.
 *
 * <p>Replaced wholesale on datapack reload. Definitions that fail validation
 * never make it in, so nothing downstream has to defend against a malformed
 * objective.</p>
 */
public final class ChallengeRegistry {

    public static final ChallengeRegistry EMPTY = new ChallengeRegistry(Map.of());

    private static volatile ChallengeRegistry current = EMPTY;
    private static volatile List<ChallengeDefinition> rotating=List.of();
    public static void setRotating(List<ChallengeDefinition> values){rotating=List.copyOf(values);}

    private final Map<ResourceLocation, ChallengeDefinition> definitions;

    private ChallengeRegistry(Map<ResourceLocation, ChallengeDefinition> definitions) {
        this.definitions = Map.copyOf(definitions);
    }

    public static ChallengeRegistry current() {
        return current;
    }

    public static void replace(Collection<ChallengeDefinition> definitions) {
        Map<ResourceLocation, ChallengeDefinition> byId = new LinkedHashMap<>();
        for (ChallengeDefinition definition : definitions) {
            byId.put(definition.id(), definition);
        }

        // A chain whose prerequisite is missing would be permanently
        // unreachable, so it is dropped with an explanation rather than sitting
        // in the journal forever (never show an impossible objective).
        boolean removed;
        do{removed=byId.values().removeIf(definition->{
            var seen=new java.util.HashSet<ResourceLocation>();var cursor=definition;
            while(cursor!=null){
                if(!seen.add(cursor.id()))return true;
                if(cursor.prerequisite()==null)return false;
                cursor=byId.get(cursor.prerequisite());if(cursor==null)return true;
            }return false;
        });}while(removed);

        current = new ChallengeRegistry(byId);
        CuisineJournalLog.LOGGER.info("Loaded {} challenge definition(s)", byId.size());
    }

    public static void clear() {
        current = EMPTY;
        rotating=List.of();
    }

    public ChallengeDefinition get(ResourceLocation id) {
        var fixed=definitions.get(id);return fixed!=null?fixed:rotating.stream().filter(v->v.id().equals(id)).findFirst().orElse(null);
    }

    public Collection<ChallengeDefinition> all() {
        var all=new ArrayList<>(definitions.values());all.addAll(rotating);return all;
    }

    public List<ChallengeDefinition> byScope(ChallengeDefinition.Scope scope) {
        List<ChallengeDefinition> result = new ArrayList<>();
        for (ChallengeDefinition definition : all()) {
            if (definition.scope() == scope) {
                result.add(definition);
            }
        }
        return result;
    }

    public int size() {
        return definitions.size()+rotating.size();
    }
}
