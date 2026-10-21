package com.chi__you.cuisinejournal.recipe;

import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Pure reverse-index builder, independently testable with explicit tag membership. */
public final class IngredientJournalIndex {
    public record Input(ResourceLocation dish, ResourceLocation recipe, int slotIndex,
                        RecipeEntry.IngredientSlot slot) {
        public Input(ResourceLocation dish, RecipeEntry.IngredientSlot slot) {
            this(dish, null, -1, slot);
        }
    }
    /** Real tag members stay separate from the display group's union. */
    public record TagSource(ResourceLocation tag, List<ResourceLocation> members) {
        public TagSource { members = List.copyOf(members); }
    }
    /** One original tag branch in one recipe slot, not the whole OR slot. */
    public record TagUse(ResourceLocation dish, ResourceLocation recipe, int slotIndex,
                         ResourceLocation tag, List<ResourceLocation> acceptedItems) {
        public TagUse { acceptedItems = List.copyOf(acceptedItems); }
    }
    public record Audit(String original, String result, String target, List<String> rules) {
        public Audit { rules = List.copyOf(rules); }
    }
    private final Map<ResourceLocation, Set<ResourceLocation>> itemDishes = new TreeMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> allDishes = new TreeMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> tagDishes = new TreeMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> sources = new TreeMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> itemTags = new TreeMap<>();
    private final Map<ResourceLocation, CulinaryRegistry.TagEntry> tags = new TreeMap<>();
    private final List<Audit> audit = new ArrayList<>();
    private final Map<ResourceLocation, List<TagSource>> tagSources = new TreeMap<>();
    private final Map<ResourceLocation, List<TagUse>> tagUses = new TreeMap<>();
    private IngredientJournalIndex() {}

    public static IngredientJournalIndex build(List<Input> inputs, IngredientCompatRules rules,
                                               Function<ResourceLocation, List<ResourceLocation>> members,
                                               Predicate<ResourceLocation> usable,
                                               Predicate<ResourceLocation> hidden) {
        return build(inputs, rules, members, usable, hidden, item -> true);
    }

    public static IngredientJournalIndex build(List<Input> inputs, IngredientCompatRules rules,
                                               Function<ResourceLocation, List<ResourceLocation>> members,
                                               Predicate<ResourceLocation> usable,
                                               Predicate<ResourceLocation> hidden,
                                               Predicate<ResourceLocation> hostVisible) {
        IngredientJournalIndex out = new IngredientJournalIndex();
        Map<ResourceLocation, Set<ResourceLocation>> groupIds = new TreeMap<>(), groupMembers = new TreeMap<>();
        Set<ResourceLocation> rawItems = new TreeSet<>(), rawTags = new TreeSet<>();
        Map<ResourceLocation, String> tagResults = new TreeMap<>(), tagTargets = new TreeMap<>();
        Map<ResourceLocation, ResourceLocation> displayKeys = new TreeMap<>();
        Map<ResourceLocation, ResourceLocation> logicalKeys = new TreeMap<>();
        Map<ResourceLocation, Set<TagUse>> originalUses = new TreeMap<>();
        for (Input input : inputs) {
            rawItems.addAll(input.slot().items());
            for (ResourceLocation tag : input.slot().tags()) rawItems.addAll(members.apply(tag));
        }
        Set<ResourceLocation> reserved = new TreeSet<>(rawItems);
        for (ResourceLocation item : rawItems) reserved.add(rules.alias(item));
        Set<ResourceLocation> logicalTags = new TreeSet<>();
        for (Input input : inputs) for (ResourceLocation tag : input.slot().tags()) {
            IngredientCompatRules.Merge merge = rules.mergeFor(tag);
            logicalTags.add(merge == null ? IngredientCompatRules.canonical(tag) : merge.id());
            if (rules.tagItem(tag) != null) reserved.add(rules.alias(rules.tagItem(tag)));
        }
        Set<ResourceLocation> allocated = new TreeSet<>(reserved);
        allocated.addAll(logicalTags);
        for (ResourceLocation logical : logicalTags) {
            ResourceLocation display = logical;
            if (reserved.contains(display)) {
                String path = "tag/" + logical.getNamespace() + "/" + logical.getPath();
                display = ResourceLocation.fromNamespaceAndPath("cuisinejournal", path);
                int suffix = 1;
                while (allocated.contains(display))
                    display = ResourceLocation.fromNamespaceAndPath("cuisinejournal", path + "/" + suffix++);
            }
            allocated.add(display);displayKeys.put(logical,display);logicalKeys.put(display,logical);
        }
        for (Input input : inputs) {
            RecipeEntry.IngredientSlot slot = input.slot();
            rawItems.addAll(slot.items());
            for (ResourceLocation item : slot.items()) {
                ResourceLocation target = rules.alias(item);
                if (usable.test(item) && usable.test(target))
                    out.allDishes.computeIfAbsent(target, k -> new TreeSet<>()).add(input.dish());
            }
            for (ResourceLocation item : slot.explicitItems()) out.addItem(item, input.dish(), rules, usable);
            for (ResourceLocation tag : slot.tags()) {
                rawTags.add(tag);
                if (hidden.test(tag)) { tagResults.put(tag, "hidden"); tagTargets.put(tag, ""); continue; }
                // Resolve each real tag separately: a mixed OR slot also contains other tags/items.
                List<ResourceLocation> resolved = members.apply(tag);
                List<ResourceLocation> actual = resolved.stream().filter(slot.items()::contains).toList();
                if (resolved.isEmpty() && slot.explicitItems().isEmpty() && slot.tags().size() == 1) actual = slot.items();
                ResourceLocation itemTarget = rules.tagItem(tag);
                if (itemTarget != null) {
                    out.addItem(itemTarget, input.dish(), rules, usable);
                    ResourceLocation target = rules.alias(itemTarget);
                    if (out.sources.containsKey(target)) for (ResourceLocation original : actual) {
                        if (usable.test(original)) out.sources.get(target).add(original);
                    }
                    tagResults.put(tag, "merged_into_item"); tagTargets.put(tag, rules.alias(itemTarget).toString());
                    continue;
                }
                if (rules.dissolvedTag(tag)) {
                    for (ResourceLocation item : actual) out.addItem(item, input.dish(), rules, usable);
                    tagResults.put(tag, "dissolved"); tagTargets.put(tag, "members"); continue;
                }
                IngredientCompatRules.Merge merge = rules.mergeFor(tag);
                ResourceLocation logical = merge == null ? IngredientCompatRules.canonical(tag) : merge.id();
                ResourceLocation key = displayKeys.get(logical);
                Set<ResourceLocation> effective = groupMembers.computeIfAbsent(key, k -> new TreeSet<>());
                List<ResourceLocation> fullMembers = members.apply(tag);
                List<ResourceLocation> displayMembers = fullMembers.isEmpty() ? actual : fullMembers;
                if (displayMembers.isEmpty()) {
                    tagResults.put(tag, "hidden_empty"); tagTargets.put(tag, "");
                    continue;
                }
                groupIds.computeIfAbsent(key, k -> new TreeSet<>()).add(tag);
                displayMembers.forEach(item -> out.member(item, effective, rules, usable));
                if (merge != null) {
                    for (ResourceLocation real : merge.tags()) {
                        IngredientCompatRules.Merge active = rules.mergeFor(real);
                        if (hidden.test(real) || active == null || !active.id().equals(logical)
                                || rules.dissolvedTag(real) || rules.tagItem(real) != null) continue;
                        if (members.apply(real).isEmpty()) continue;
                        groupIds.get(key).add(real);
                        members.apply(real).forEach(item -> out.member(item, effective, rules, usable));
                    }
                }
                out.tagDishes.computeIfAbsent(key, k -> new TreeSet<>()).add(input.dish());
                originalUses.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(new TagUse(
                        input.dish(), input.recipe(), input.slotIndex(), tag,
                        actual.stream().filter(hostVisible).distinct().sorted().toList()));
                tagResults.put(tag, merge == null ? "kept" : "merged"); tagTargets.put(tag, key.toString());
            }
        }
        mergeIdenticalTags(groupIds, groupMembers, out.tagDishes, logicalKeys, originalUses, tagResults, tagTargets, rules, members);
        mergeNamedTags(groupIds, groupMembers, out.tagDishes, logicalKeys, originalUses, tagResults, tagTargets, rules);
        for (ResourceLocation key : groupIds.keySet()) {
            Set<ResourceLocation> effective = groupMembers.get(key);
            if (effective.isEmpty()) { out.tagDishes.remove(key); continue; }
            boolean grouped = rules.mergeFor(logicalKeys.get(key)) != null;
            if (effective.size() == 1 && !grouped) {
                ResourceLocation only = effective.iterator().next();
                out.itemDishes.computeIfAbsent(only, k -> new TreeSet<>()).addAll(out.tagDishes.remove(key));
                for (ResourceLocation real : rawTags) if (key.toString().equals(tagTargets.get(real))) {
                    tagResults.put(real, "dissolved_single_member"); tagTargets.put(real, only.toString());
                }
                continue;
            }
            String name = rules.displayName(logicalKeys.get(key));
            if (name == null) for (ResourceLocation real : groupIds.get(key)) {
                name = rules.displayName(real); if (name != null) break;
            }
            out.tags.put(key, new CulinaryRegistry.TagEntry(key, List.copyOf(groupIds.get(key)), List.copyOf(effective), name));
            out.tagSources.put(key, groupIds.get(key).stream().map(real -> new TagSource(real,
                    members.apply(real).stream().filter(hostVisible).distinct().sorted().toList())).toList());
            out.tagUses.put(key, List.copyOf(originalUses.getOrDefault(key, Set.of())));
            for (ResourceLocation item : effective) out.itemTags.computeIfAbsent(item, k -> new TreeSet<>()).add(key);
        }
        Set<ResourceLocation> visible = new TreeSet<>(out.itemDishes.keySet()); visible.addAll(out.itemTags.keySet());
        out.sources.keySet().retainAll(visible);
        out.sources.forEach((key, originals) -> rules.itemSources(key).stream().filter(usable).forEach(originals::add));
        Map<ResourceLocation,ResourceLocation> sourceTargets=new HashMap<>();
        out.sources.forEach((target,originals)->originals.forEach(original->sourceTargets.putIfAbsent(original,target)));
        for (ResourceLocation item : rawItems) {
            ResourceLocation target = rules.alias(item);
            String result = !usable.test(item) || !usable.test(target) ? "hidden"
                    : !visible.contains(target) ? "not_listed" : item.equals(target) ? "kept" : "merged";
            if (result.equals("not_listed") && sourceTargets.containsKey(item)) {
                result="merged";target=sourceTargets.get(item);
            }
            out.audit.add(new Audit(item.toString(), result, target.toString(), rules.sources(item, false)));
        }
        for (ResourceLocation tag : rawTags) {
            String target = tagTargets.getOrDefault(tag, "");
            String result = tagResults.getOrDefault(tag, "hidden");
            ResourceLocation finalId = ResourceLocation.tryParse(target);
            if ((result.equals("kept") || result.startsWith("merged")) && (finalId == null || !out.tags.containsKey(finalId)))
                result = "hidden_empty";
            out.audit.add(new Audit("#" + tag, result, target, rules.sources(tag, true)));
        }
        Set<String> seen = new HashSet<>(); out.audit.forEach(a -> seen.add(a.original()));
        for (String affected : rules.affected()) if (!seen.contains(affected)) {
            boolean tag = affected.startsWith("#"); ResourceLocation id = ResourceLocation.parse(tag ? affected.substring(1) : affected);
            out.audit.add(new Audit(affected, "rule_only", "", rules.sources(id, tag)));
        }
        return out;
    }
    /** Same Chinese names group display entries; original recipe branches remain separate. */
    private static void mergeNamedTags(Map<ResourceLocation, Set<ResourceLocation>> ids,
            Map<ResourceLocation, Set<ResourceLocation>> effective,
            Map<ResourceLocation, Set<ResourceLocation>> dishes,
            Map<ResourceLocation, ResourceLocation> logical,
            Map<ResourceLocation, Set<TagUse>> uses, Map<ResourceLocation, String> results,
            Map<ResourceLocation, String> targets, IngredientCompatRules rules) {
        Map<String, ResourceLocation> winners = new HashMap<>();
        List<ResourceLocation> keys = new ArrayList<>(ids.keySet());
        keys.sort(Comparator.comparing((ResourceLocation key) -> rules.mergeFor(logical.get(key)) == null)
                .thenComparing(key -> !logical.get(key).getNamespace().equals("forge"))
                .thenComparing(ResourceLocation::toString));
        for (ResourceLocation key : keys) {
            if (ids.get(key).stream().anyMatch(rules::keptTag)) continue;
            String name = rules.chineseName(logical.get(key));
            if (name == null) for (ResourceLocation real : ids.get(key)) {
                name = rules.chineseName(real);
                if (name != null) break;
            }
            if (name == null) continue;
            ResourceLocation winner = winners.putIfAbsent(name, key);
            if (winner == null) continue;
            ids.get(winner).addAll(ids.remove(key));
            effective.get(winner).addAll(effective.remove(key));
            dishes.get(winner).addAll(dishes.remove(key));
            uses.computeIfAbsent(winner, unused -> new LinkedHashSet<>()).addAll(uses.getOrDefault(key, Set.of()));
            uses.remove(key);
            for (var entry : targets.entrySet()) if (entry.getValue().equals(key.toString())) {
                entry.setValue(winner.toString());
                results.put(entry.getKey(), "merged_same_name");
            }
        }
    }

    /** Compare complete real member sets, never the filtered/aliased display members. */
    private static void mergeIdenticalTags(Map<ResourceLocation, Set<ResourceLocation>> ids,
            Map<ResourceLocation, Set<ResourceLocation>> effective,
            Map<ResourceLocation, Set<ResourceLocation>> dishes,
            Map<ResourceLocation, ResourceLocation> logical,
            Map<ResourceLocation, Set<TagUse>> uses, Map<ResourceLocation, String> results,
            Map<ResourceLocation, String> targets, IngredientCompatRules rules,
            Function<ResourceLocation, List<ResourceLocation>> members) {
        Map<List<ResourceLocation>, List<ResourceLocation>> identical = new LinkedHashMap<>();
        for (ResourceLocation key : ids.keySet()) {
            if (rules.keptTag(logical.get(key)) || ids.get(key).stream().anyMatch(rules::keptTag)) continue;
            Set<ResourceLocation> full = new TreeSet<>();
            ids.get(key).forEach(tag -> full.addAll(members.apply(tag)));
            if (full.size() < 2) continue;
            identical.computeIfAbsent(List.copyOf(full), unused -> new ArrayList<>()).add(key);
        }
        for (List<ResourceLocation> candidates : identical.values()) {
            if (candidates.size() < 2) continue;
            Set<String> names = new HashSet<>();
            Set<List<Object>> flavors = new HashSet<>();
            Set<List<ResourceLocation>> types = new HashSet<>();
            for (ResourceLocation key : candidates) {
                Set<ResourceLocation> metadataIds = new LinkedHashSet<>(ids.get(key));
                metadataIds.add(logical.get(key));
                for (ResourceLocation real : metadataIds) {
                    String name = rules.displayName(real); if (name != null) names.add(name);
                    var flavor = IngredientCatalogueConfig.current().tagFlavor(List.of(real));
                    if (flavor == null) flavor = rules.tagFlavor(real);
                    if (flavor != null) flavors.add(java.util.Arrays.asList(
                            flavor.source(), flavor.effect(), flavor.stages(), flavor.flavors()));
                    var type = IngredientCatalogueConfig.current().tagTypeOverride(List.of(real));
                    if (type.isEmpty()) type = rules.tagTypes(real);
                    if (!type.isEmpty()) types.add(type.stream().sorted().toList());
                }
            }
            if (names.size() > 1 || flavors.size() > 1 || types.size() > 1) continue;
            candidates.sort(Comparator.comparing((ResourceLocation key) -> rules.mergeFor(logical.get(key)) == null)
                    .thenComparing(key ->
                            ids.get(key).stream().noneMatch(tag -> rules.displayName(tag) != null))
                    .thenComparing(key -> !logical.get(key).getNamespace().equals("forge"))
                    .thenComparing(ResourceLocation::toString));
            ResourceLocation winner = candidates.get(0);
            for (ResourceLocation loser : candidates.subList(1, candidates.size())) {
                ids.get(winner).addAll(ids.remove(loser));
                effective.get(winner).addAll(effective.remove(loser));
                dishes.get(winner).addAll(dishes.remove(loser));
                uses.computeIfAbsent(winner, unused -> new LinkedHashSet<>()).addAll(uses.getOrDefault(loser, Set.of()));
                uses.remove(loser);
                for (var entry : targets.entrySet()) if (entry.getValue().equals(loser.toString())) {
                    entry.setValue(winner.toString()); results.put(entry.getKey(), "merged_identical");
                }
            }
        }
    }
    private void member(ResourceLocation item, Set<ResourceLocation> members, IngredientCompatRules rules,
                        Predicate<ResourceLocation> usable) {
        ResourceLocation target = rules.alias(item);
        if (item.toString().equals("minecraft:barrier") || !usable.test(item) || !usable.test(target)) return;
        members.add(target); sources.computeIfAbsent(target, k -> new TreeSet<>()).add(item);
    }
    private void addItem(ResourceLocation item, ResourceLocation dish, IngredientCompatRules rules,
                         Predicate<ResourceLocation> usable) {
        ResourceLocation target = rules.alias(item);
        if (!usable.test(item) || !usable.test(target)) return;
        sources.computeIfAbsent(target, k -> new TreeSet<>()).add(item);
        itemDishes.computeIfAbsent(target, k -> new TreeSet<>()).add(dish);
    }
    public Map<ResourceLocation, Set<ResourceLocation>> itemDishes() { return Collections.unmodifiableMap(itemDishes); }
    public Map<ResourceLocation, Set<ResourceLocation>> allDishes() { return Collections.unmodifiableMap(allDishes); }
    public Map<ResourceLocation, Set<ResourceLocation>> sources() { return Collections.unmodifiableMap(sources); }
    public Map<ResourceLocation, Set<ResourceLocation>> tagDishes() { return Collections.unmodifiableMap(tagDishes); }
    public Map<ResourceLocation, Set<ResourceLocation>> itemTags() { return Collections.unmodifiableMap(itemTags); }
    public Map<ResourceLocation, CulinaryRegistry.TagEntry> tags() { return Collections.unmodifiableMap(tags); }
    public Set<ResourceLocation> items() { return Collections.unmodifiableSet(sources.keySet()); }
    public List<ResourceLocation> sources(ResourceLocation item) { return List.copyOf(sources.getOrDefault(item, Set.of())); }
    public List<Audit> audit() { return List.copyOf(audit); }
    public List<TagSource> tagSources(ResourceLocation entry) { return tagSources.getOrDefault(entry, List.of()); }
    public List<TagUse> tagUses(ResourceLocation entry) { return tagUses.getOrDefault(entry, List.of()); }
}
