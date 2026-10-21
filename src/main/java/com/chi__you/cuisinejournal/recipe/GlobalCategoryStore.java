package com.chi__you.cuisinejournal.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.chi__you.cuisinejournal.CuisineJournalLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Server-wide category edits shared by every player. */
public final class GlobalCategoryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "cuisinejournal-categories.json";
    private static final ResourceLocation OTHER_FOOD_ID = FoodCategory.OTHER_FOOD_ID;
    private static final Set<UUID> EDITING_PLAYERS = new HashSet<>();

    private static Path file;
    private static boolean writable = true;
    /**
     * Effective edits: {@link #packState} (category overlays exported into data packs) with
     * {@link #localState} (this server's config file) on top. Every read uses this; every edit
     * changes only the local layer, which records removals of pack entries as negations.
     */
    private static State state = new State();
    private static State localState = new State();
    private static State packState = new State();
    private static List<FoodCategory> baseDefinitions = List.of();
    private static int revision;
    public static synchronized Runnable rollbackPoint() {
        var previous=copy(state); var previousPack=copy(packState); var previousLocal=copy(localState);
        var previousBase=baseDefinitions; int previousRevision=revision;
        return () -> { synchronized(GlobalCategoryStore.class) {
            state=previous; packState=previousPack; localState=previousLocal;
            baseDefinitions=previousBase; revision=previousRevision;
        } };
    }

    private GlobalCategoryStore() {
    }

    public record CategoryMetadata(ResourceLocation id, ResourceLocation parent, String titleKey,
                                   boolean literalTitle, ResourceLocation icon, byte source,
                                   boolean protectedEntry, boolean hidden, String originalTitleKey,
                                   boolean originalLiteralTitle, ResourceLocation originalIcon) {
    }

    public static synchronized void load(Path directory) {
        file = directory.resolve(FILE_NAME);
        writable = true;
        if (!Files.exists(file)) {
            localState = new State();
            revision = 0;
            save(localState, revision);
            state = merge(packState, localState);
            return;
        }
        try {
            var raw=com.google.gson.JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
            normalisedJson(raw);
            State loaded = GSON.fromJson(raw, State.class);
            if(loaded==null)throw new IllegalArgumentException("Missing category configuration object");
            loaded.normalise();
            localState = loaded;
            revision = Math.max(0, localState.revision);
        } catch (IOException | RuntimeException failure) {
            CuisineJournalLog.LOGGER.error("Could not read global category file {}; retaining last good layer and disabling edits", file, failure);
            com.chi__you.cuisinejournal.data.UnreadableFiles.preserve(file);
            writable = false;
            return;
        }
        state = merge(packState, localState);
    }

    /** Category overlays shipped in data packes ({@code cuisinejournal/category_overlays/*.json}), lowest first. */
    public static synchronized void setPackOverlays(List<com.google.gson.JsonObject> overlays) {
        State combined = new State();
        for (com.google.gson.JsonObject json : overlays) {
            try {
                State next = GSON.fromJson(json, State.class);
                if (next == null) continue;
                next.normalise();
                combined = merge(combined, next);
            } catch (RuntimeException failure) {
                CuisineJournalLog.LOGGER.warn("Skipping category overlay: {}", failure.getMessage());
            }
        }
        packState = combined;
        state = merge(packState, localState);
    }

    /** The local edits as JSON (revision zeroed), for saving a category scheme. */
    public static synchronized com.google.gson.JsonObject localJson() {
        State value = copy(localState);
        value.revision = 0;
        return GSON.toJsonTree(value).getAsJsonObject();
    }

    /** The same edits in canonical form (defaults filled, revision zeroed), for comparing schemes. */
    public static com.google.gson.JsonObject normalisedJson(com.google.gson.JsonObject json) {
        if (json == null) throw new IllegalArgumentException("Missing category layer");
        Set<String> arrays = Set.of("roots","subcategories","blockedRoots","blockedSubcategories",
                "removedRoots","removedSubcategories","deletedRoots","deletedSubcategories",
                "unblockedRoots","unblockedSubcategories","unassigned","resetOverrides");
        Set<String> maps = Set.of("assignments","parentOverrides","rootOverrides","subcategoryOverrides");
        if(!json.entrySet().isEmpty()&&json.keySet().stream().noneMatch(k->arrays.contains(k)||maps.contains(k)||k.equals("revision")))
            throw new IllegalArgumentException("Unrecognised category layer");
        for (var entry : json.entrySet()) {
            if (arrays.contains(entry.getKey()) && !entry.getValue().isJsonArray()
                    || maps.contains(entry.getKey()) && !entry.getValue().isJsonObject())
                throw new IllegalArgumentException("Invalid category field: " + entry.getKey());
        }
        State value = GSON.fromJson(json, State.class);
        value.normalise();
        value.revision = 0;
        return GSON.toJsonTree(value).getAsJsonObject();
    }

    /** Replaces the whole local layer (loading a category scheme). */
    public static synchronized boolean replaceLocal(com.google.gson.JsonObject json) {
        State candidate;
        try { candidate = GSON.fromJson(normalisedJson(json), State.class); }
        catch (RuntimeException invalid) { return false; }
        candidate.normalise();
        if (!save(candidate, revision + 1)) return false;
        localState = candidate;
        state = merge(packState, localState);
        revision++;
        return true;
    }

    /** Whether this server has category edits that are not yet in a data pack. */
    public static synchronized boolean hasLocalEdits() {
        State empty = new State();
        empty.normalise();
        State copy = copy(localState);
        copy.revision = 0;
        return !GSON.toJson(copy).equals(GSON.toJson(empty));
    }

    /** The local edits merged over an earlier exported overlay, ready to be written into a pack. */
    public static synchronized com.google.gson.JsonObject exportOverlay(com.google.gson.JsonObject previous) {
        State base = new State();
        if (previous != null) {
            try { State parsed = GSON.fromJson(previous, State.class); if (parsed != null) { parsed.normalise(); base = parsed; } }
            catch (RuntimeException ignored) { }
        }
        State merged = merge(base, localState);
        merged.revision = 0;
        return GSON.toJsonTree(merged).getAsJsonObject();
    }

    /** Layers {@code top} over {@code base}; negation sets in {@code top} remove entries of {@code base}. */
    private static State merge(State base, State top) {
        State m = new State();
        Set<String> topRoots = new LinkedHashSet<>(), topSubs = new LinkedHashSet<>();
        top.roots.forEach(r -> topRoots.add(r.id));
        top.subcategories.forEach(c -> topSubs.add(c.id));
        for (Root r : base.roots)
            if (!top.deletedRoots.contains(r.id) && !topRoots.contains(r.id)) m.roots.add(new Root(r.id, r.name, r.icon, r.order));
        for (Root r : top.roots) m.roots.add(new Root(r.id, r.name, r.icon, r.order));
        for (Subcategory c : base.subcategories)
            if (!top.deletedSubcategories.contains(c.id) && !topSubs.contains(c.id))
                m.subcategories.add(copySubcategory(c));
        for (Subcategory c : top.subcategories) m.subcategories.add(copySubcategory(c));
        base.assignments.forEach((k, v) -> { if (!top.unassigned.contains(k)) m.assignments.put(k, v); });
        m.assignments.putAll(top.assignments);
        base.parentOverrides.forEach((k, v) -> { if (!top.resetOverrides.contains("sub:" + k)) m.parentOverrides.put(k, v); });
        m.parentOverrides.putAll(top.parentOverrides);
        base.rootOverrides.forEach((k, v) -> { if (!top.resetOverrides.contains("root:" + k)) m.rootOverrides.put(k, v); });
        m.rootOverrides.putAll(top.rootOverrides);
        base.subcategoryOverrides.forEach((k, v) -> { if (!top.resetOverrides.contains("sub:" + k)) m.subcategoryOverrides.put(k, v); });
        m.subcategoryOverrides.putAll(top.subcategoryOverrides);
        base.blockedRoots.forEach(id -> { if (!top.unblockedRoots.contains(id)) m.blockedRoots.add(id); });
        m.blockedRoots.addAll(top.blockedRoots);
        base.blockedSubcategories.forEach(id -> { if (!top.unblockedSubcategories.contains(id)) m.blockedSubcategories.add(id); });
        m.blockedSubcategories.addAll(top.blockedSubcategories);
        // Negations stay on the merged result so a later layer can still see what was removed.
        m.deletedRoots.addAll(base.deletedRoots); m.deletedRoots.addAll(top.deletedRoots);
        m.deletedSubcategories.addAll(base.deletedSubcategories); m.deletedSubcategories.addAll(top.deletedSubcategories);
        m.unassigned.addAll(base.unassigned); m.unassigned.addAll(top.unassigned);
        m.unassigned.removeAll(top.assignments.keySet());
        m.resetOverrides.addAll(base.resetOverrides); m.resetOverrides.addAll(top.resetOverrides);
        m.unblockedRoots.addAll(base.unblockedRoots); m.unblockedRoots.addAll(top.unblockedRoots);
        m.unblockedSubcategories.addAll(base.unblockedSubcategories); m.unblockedSubcategories.addAll(top.unblockedSubcategories);
        m.normalise();
        return m;
    }

    /** A pack-layer custom root / subcategory copied into the local layer so it can be edited. */
    private static Root localRoot(State candidate, String id) {
        Root value = findRoot(candidate, id);
        if (value != null) return value;
        Root pack = findRoot(packState, id);
        if (pack == null) return null;
        value = new Root(pack.id, pack.name, pack.icon, pack.order);
        candidate.roots.add(value);
        return value;
    }

    private static Subcategory localSubcategory(State candidate, String id) {
        Subcategory value = findSubcategory(candidate, id);
        if (value != null) return value;
        Subcategory pack = findSubcategory(packState, id);
        if (pack == null) return null;
        value = new Subcategory(pack.id, pack.parent, pack.name, pack.icon, pack.order);
        value.drinkFallback = pack.drinkFallback;
        candidate.subcategories.add(value);
        return value;
    }

    private static Subcategory copySubcategory(Subcategory source) {
        Subcategory copy = new Subcategory(source.id, source.parent, source.name, source.icon, source.order);
        copy.drinkFallback = source.drinkFallback;
        return copy;
    }

    public static synchronized int revision() {
        return revision;
    }

    public static synchronized List<String> rootIds() {
        return state.roots.stream().map(root -> root.id).toList();
    }

    public static synchronized List<String> subcategoryIds() {
        return state.subcategories.stream().map(sub -> sub.id).toList();
    }

    public static synchronized boolean hasCustomRoot(String id) {
        return customRoot(id) != null && baseRoot(id) == null;
    }

    public static synchronized boolean hasCustomSubcategory(String id) {
        return customSubcategory(id) != null && baseSubcategory(id) == null;
    }

    public static synchronized Set<String> manuallyAssignedItems() {
        return Set.copyOf(state.assignments.keySet());
    }

    public static synchronized boolean toggleEditing(UUID player) {
        if (!EDITING_PLAYERS.add(player)) EDITING_PLAYERS.remove(player);
        return EDITING_PLAYERS.contains(player);
    }

    public static synchronized void clearEditing(UUID player) {
        EDITING_PLAYERS.remove(player);
    }
    public static synchronized void clearEditing() { EDITING_PLAYERS.clear(); }

    public static synchronized boolean createRoot(String id, String name, String icon) {
        if(metadata().size()>=16_384)return false;
        if (!validId(id) || !validName(name) || invalidIcon(icon)
                || effectiveRoot(id) != null || blockedRoot(id)) return false;
        return mutate(candidate -> {
            candidate.roots.add(new Root(id, name, icon, nextOrder(state.roots)));
            candidate.blockedRoots.remove(id);
            candidate.deletedRoots.remove(id);
            return true;
        });
    }

    public static synchronized boolean editRoot(String id, String name, String icon) {
        if (!validId(id) || !validName(name) || invalidIcon(icon)) return false;
        FoodCategory base = baseRoot(id);
        Root custom = customRoot(id);
        if (base == null && custom == null) return false;
        return mutate(candidate -> {
            if (base != null) { candidate.rootOverrides.put(id, new Override(name, icon)); candidate.resetOverrides.remove("root:" + id); }
            else {
                Root value = localRoot(candidate, id);
                value.name = name;
                value.icon = icon;
            }
            return true;
        });
    }

    /** Removes a datapack root from the effective tree while retaining it for restore. */
    public static synchronized boolean blockRoot(String id) {
        if (!validId(id) || baseRoot(id) == null || protectedRoot(id)) return false;
        return mutate(candidate -> { candidate.unblockedRoots.remove(id); return candidate.blockedRoots.add(id); });
    }

    /** Deletes an in-game root. Datapack roots must use {@link #blockRoot(String)}. */
    public static synchronized boolean deleteRoot(String id) {
        if (!validId(id) || baseRoot(id) != null || protectedRoot(id)) return false;
        if (customRoot(id) == null) return false;
        return mutate(candidate -> {
            candidate.roots.removeIf(root -> root.id.equals(id));
            if (findRoot(packState, id) != null) candidate.deletedRoots.add(id);
            List<String> children = state.subcategories.stream()
                    .filter(sub -> id.equals(effectiveParent(state, sub.id)))
                    .map(sub -> sub.id).toList();
            candidate.subcategories.removeIf(sub -> children.contains(sub.id));
            for (String child : children) if (findSubcategory(packState, child) != null) candidate.deletedSubcategories.add(child);
            state.assignments.forEach((item, target) -> { if (children.contains(target) && packState.assignments.containsKey(item)) candidate.unassigned.add(item); });
            candidate.assignments.entrySet().removeIf(entry -> children.contains(entry.getValue()));
            candidate.parentOverrides.keySet().removeAll(children);
            candidate.subcategoryOverrides.keySet().removeAll(children);
            candidate.parentOverrides.entrySet().removeIf(entry -> id.equals(entry.getValue()));
            return true;
        });
    }

    public static synchronized boolean restoreRoot(String id) {
        if (!validId(id) || !blockedRoot(id)) return false;
        return mutate(candidate -> {
            candidate.blockedRoots.remove(id);
            if (packState.blockedRoots.contains(id)) candidate.unblockedRoots.add(id);
            return true;
        });
    }

    public static synchronized boolean resetRoot(String id) {
        return resetRootAppearance(id);
    }

    public static synchronized boolean resetRootAppearance(String id) {
        if (!validId(id) || baseRoot(id) == null || !state.rootOverrides.containsKey(id)) return false;
        return mutate(candidate -> {
            candidate.rootOverrides.remove(id);
            if (packState.rootOverrides.containsKey(id)) candidate.resetOverrides.add("root:" + id);
            return true;
        });
    }

    public static synchronized boolean createSubcategory(String id, String parent, String name, String icon) {
        if(metadata().size()>=16_384)return false;
        if (!validId(id) || !validId(parent) || !validName(name) || invalidIcon(icon)
                || effectiveSubcategory(id) != null || blockedSub(id) || effectiveRoot(parent) == null) return false;
        return mutate(candidate -> {
            candidate.subcategories.add(new Subcategory(id, parent, name, icon, nextOrder(state.subcategories)));
            candidate.blockedSubcategories.remove(id);
            candidate.deletedSubcategories.remove(id);
            return true;
        });
    }

    public static synchronized boolean editSubcategory(String id, String name, String icon) {
        return editSubcategory(id, name, icon, null);
    }

    /** Edits name, icon and optional parent in one validation and one disk commit. */
    public static synchronized boolean editSubcategory(String id, String name, String icon, String parent) {
        if (!validId(id) || !validName(name) || invalidIcon(icon)) return false;
        FoodCategory.Subcategory base = baseSubcategory(id);
        Subcategory custom = customSubcategory(id);
        if (base == null && custom == null) return false;
        String currentParent = effectiveParent(id);
        String targetParent = parent == null ? currentParent : parent;
        // Protected subcategories may be renamed/re-iconed; only moving them is forbidden.
        if (!validId(targetParent) || effectiveRoot(targetParent) == null
                || (!targetParent.equals(currentParent) && protectedSub(id))) return false;
        return mutate(candidate -> {
            if (base != null) {
                candidate.subcategoryOverrides.put(id, new Override(name, icon));
                candidate.resetOverrides.remove("sub:" + id);
                if (!targetParent.equals(currentParent)) candidate.parentOverrides.put(id, targetParent);
            } else {
                Subcategory value = localSubcategory(candidate, id);
                value.name = name;
                value.icon = icon;
                value.parent = targetParent;
            }
            return true;
        });
    }

    public static synchronized boolean moveSubcategory(String id, String newParent) {
        if (!validId(id) || !validId(newParent) || effectiveSubcategory(id) == null
                || effectiveRoot(newParent) == null || protectedSub(id)) return false;
        String oldParent = effectiveParent(id);
        if (oldParent.equals(newParent)) return false;
        return mutate(candidate -> {
            if (baseSubcategory(id) != null) candidate.parentOverrides.put(id, newParent);
            else localSubcategory(candidate, id).parent = newParent;
            return true;
        });
    }

    public static synchronized boolean blockSubcategory(String id) {
        if (!validId(id) || baseSubcategory(id) == null || protectedSub(id)) return false;
        return mutate(candidate -> { candidate.unblockedSubcategories.remove(id); return candidate.blockedSubcategories.add(id); });
    }

    /** Deletes an in-game subcategory. Datapack definitions must use blockSubcategory. */
    public static synchronized boolean deleteSubcategory(String id) {
        if (!validId(id) || baseSubcategory(id) != null || protectedSub(id)
                || customSubcategory(id) == null) return false;
        return mutate(candidate -> {
            candidate.subcategories.removeIf(sub -> sub.id.equals(id));
            if (findSubcategory(packState, id) != null) candidate.deletedSubcategories.add(id);
            packState.assignments.forEach((item, target) -> { if (target.equals(id)) candidate.unassigned.add(item); });
            candidate.assignments.entrySet().removeIf(entry -> entry.getValue().equals(id));
            candidate.parentOverrides.remove(id);
            candidate.subcategoryOverrides.remove(id);
            return true;
        });
    }

    public static synchronized boolean restoreSubcategory(String id) {
        if (!validId(id) || !blockedSub(id)) return false;
        return mutate(candidate -> {
            candidate.blockedSubcategories.remove(id);
            if (packState.blockedSubcategories.contains(id)) candidate.unblockedSubcategories.add(id);
            return true;
        });
    }

    public static synchronized boolean resetSubcategory(String id) {
        return resetSubcategoryAppearance(id);
    }

    public static synchronized boolean resetSubcategoryAppearance(String id) {
        if (!validId(id) || baseSubcategory(id) == null) return false;
        if (!state.subcategoryOverrides.containsKey(id) && !state.parentOverrides.containsKey(id)) return false;
        return mutate(candidate -> {
            candidate.subcategoryOverrides.remove(id);
            candidate.parentOverrides.remove(id);
            if (packState.subcategoryOverrides.containsKey(id) || packState.parentOverrides.containsKey(id))
                candidate.resetOverrides.add("sub:" + id);
            return true;
        });
    }

    public static synchronized boolean resetAppearance(String id) {
        return resetRootAppearance(id) || resetSubcategoryAppearance(id);
    }

    public static synchronized boolean assignFood(String item, String subcategory) {
        if (!validId(item) || !validId(subcategory) || !isAssignableSubcategory(subcategory)) return false;
        return mutate(candidate -> {
            candidate.assignments.put(item, subcategory);
            candidate.unassigned.remove(item);
            return true;
        });
    }

    /** Assigns all items in one validation and one commit. */
    public static synchronized boolean assignFoods(List<String> items, String subcategory) {
        if (!validId(subcategory) || !isAssignableSubcategory(subcategory) || items == null || items.isEmpty()) return false;
        LinkedHashSet<String> unique = new LinkedHashSet<>(items);
        if (unique.size() != items.size() || unique.stream().anyMatch(id -> !validId(id))) return false;
        return mutate(candidate -> {
            unique.forEach(item -> { candidate.assignments.put(item, subcategory); candidate.unassigned.remove(item); });
            return true;
        });
    }

    public static synchronized boolean unassignFood(String item) {
        if (!validId(item) || !state.assignments.containsKey(item)) return false;
        return mutate(candidate -> {
            candidate.assignments.remove(item);
            if (packState.assignments.containsKey(item)) candidate.unassigned.add(item);
            return true;
        });
    }

    /**
     * Sends foods back to their mod's fallback category. A manual assignment is simply
     * dropped when no datapack rule claims the food; otherwise the food is pinned to the
     * "other food" fallback so the datapack rule no longer pulls it back in.
     */
    public static synchronized boolean returnToModFallback(List<String> items) {
        if (items == null || items.isEmpty() || items.stream().anyMatch(id -> !validId(id))) return false;
        return mutate(candidate -> {
            for (String item : new LinkedHashSet<>(items)) {
                if (datapackClaims(item)) { candidate.assignments.put(item, OTHER_FOOD_ID.toString()); candidate.unassigned.remove(item); }
                else {
                    candidate.assignments.remove(item);
                    if (packState.assignments.containsKey(item)) candidate.unassigned.add(item);
                }
            }
            return true;
        });
    }

    /** Manually placed foods, excluding those only pinned back to their mod fallback. */
    public static synchronized Set<String> visibleManualItems() {
        Set<String> result = new LinkedHashSet<>();
        state.assignments.forEach((item, target) -> { if (!OTHER_FOOD_ID.toString().equals(target)) result.add(item); });
        return result;
    }

    /** Whether an unblocked datapack category would claim the item, ignoring manual assignments. */
    private static boolean datapackClaims(String item) {
        ResourceLocation id = parse(item);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(id));
        for (FoodCategory root : baseDefinitions) {
            if (state.blockedRoots.contains(root.id().toString())) continue;
            if (root.subcategories().isEmpty()) {
                if (root.matches(stack)) return true;
                continue;
            }
            for (FoodCategory.Subcategory sub : root.subcategories()) {
                if (!state.blockedSubcategories.contains(sub.id().toString())
                        && effectiveRoot(effectiveParent(sub.id().toString())) != null
                        && (sub.matches(stack) || sub.matchesFallback(stack))) return true;
            }
        }
        return false;
    }

    public static synchronized void reset() {
        clearLocal();
    }

    public static synchronized boolean clearLocal() {
        State candidate = new State();
        if (save(candidate, revision + 1)) {
            localState = candidate;
            state = merge(packState, localState);
            revision++;
            return true;
        }
        return false;
    }

    /** Merge saved edits on top of raw datapack definitions. */
    public static synchronized List<FoodCategory> apply(List<FoodCategory> base) {
        baseDefinitions = List.copyOf(base);
        Map<ResourceLocation, FoodCategory> roots = new LinkedHashMap<>();
        Map<ResourceLocation, List<FoodCategory.Subcategory>> children = new LinkedHashMap<>();
        for (FoodCategory category : base) {
            if (state.blockedRoots.contains(category.id().toString())) continue;
            roots.putIfAbsent(category.id(), applyRootAppearance(category));
            children.putIfAbsent(category.id(), new ArrayList<>());
        }
        for (Root root : state.roots) {
            ResourceLocation id = parse(root.id);
            if (id == null || roots.containsKey(id) || state.blockedRoots.contains(root.id)) continue;
            roots.put(id, new FoodCategory(id, List.of(), -100_000 + root.order, Set.of(), parse(root.icon),
                    List.of(), root.name, true));
            children.put(id, new ArrayList<>());
        }
        for (FoodCategory category : base) {
            if (!roots.containsKey(category.id())) continue;
            for (FoodCategory.Subcategory sub : effectiveBaseSubcategories(category)) {
                if (state.blockedSubcategories.contains(sub.id().toString())) continue;
                ResourceLocation parent = parse(effectiveParent(sub.id().toString()));
                if (parent != null && children.containsKey(parent)) children.get(parent).add(withAssignments(applySubAppearance(sub)));
            }
        }
        for (Subcategory sub : state.subcategories) {
            ResourceLocation id = parse(sub.id);
            ResourceLocation parent = parse(effectiveParent(state, sub.id));
            if (id == null || parent == null || !roots.containsKey(parent)
                    || state.blockedSubcategories.contains(sub.id) || baseSubcategory(sub.id) != null) continue;
            children.get(parent).add(new FoodCategory.Subcategory(id, sub.name, true, parse(sub.icon), List.of(), assignedItems(sub.id), sub.drinkFallback));
        }
        Map<ResourceLocation, FoodCategory> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, FoodCategory> entry : roots.entrySet()) {
            ResourceLocation id = entry.getKey();
            FoodCategory root = entry.getValue();
            List<FoodCategory.Subcategory> subs = List.copyOf(children.getOrDefault(id, List.of()));
            Set<ResourceLocation> items = new LinkedHashSet<>(root.items());
            for (FoodCategory.Subcategory sub : subs) items.addAll(sub.items());
            result.put(id, new FoodCategory(id, root.tags(), root.priority(), items, root.icon(), subs,
                    root.titleKey(), root.literalTitle()));
        }
        return new ArrayList<>(result.values());
    }

    /** Exact assignment first, then normal effective subcategory/category matching. */
    public static synchronized FoodCategory resolve(Function<ResourceLocation, FoodCategory> byId,
                                                    List<FoodCategory> definitions,
                                                    net.minecraft.world.item.ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String assigned = itemId == null ? null : state.assignments.get(itemId.toString());
        if (assigned != null && OTHER_FOOD_ID.toString().equals(assigned)) return FoodCategory.OTHER_FOOD;
        if (assigned != null && isAssignableSubcategory(assigned)) {
            ResourceLocation parent = parse(effectiveParent(assigned));
            FoodCategory category = parent == null ? null : byId.apply(parent);
            if (category != null) return category;
        }
        for (FoodCategory category : definitions) {
            if (category.subcategories().stream().anyMatch(sub -> sub.matches(stack))) return category;
            if (category.subcategories().isEmpty() && category.matches(stack)) return category;
        }
        // Catch-all rules run only after every explicit category rule has missed.
        for (FoodCategory category : definitions) {
            if (category.subcategories().stream().anyMatch(sub -> sub.matchesFallback(stack))) return category;
        }
        return FoodCategory.UNCATEGORISED;
    }

    /** Effective metadata, including implicit fallbacks and hidden datapack entries. */
    public static synchronized List<CategoryMetadata> metadata() {
        Map<String, CategoryMetadata> result = new LinkedHashMap<>();
        for (FoodCategory root : baseDefinitions) {
            String rid = root.id().toString();
            boolean hiddenRoot = state.blockedRoots.contains(rid);
            Override rootOverride = state.rootOverrides.get(rid);
            result.put(rid, new CategoryMetadata(root.id(), null,
                    rootOverride == null || rootOverride.name == null ? root.translationKey() : rootOverride.name,
                    rootOverride == null || rootOverride.name == null ? root.literalTitle() : true,
                    rootOverride == null || rootOverride.icon == null ? root.icon() : parse(rootOverride.icon),
                    (byte) (rootOverride == null ? 0 : 2), protectedRoot(rid), hiddenRoot,
                    root.translationKey(), root.literalTitle(), root.icon()));
            for (FoodCategory.Subcategory sub : effectiveBaseSubcategories(root)) {
                String sid = sub.id().toString();
                if (sid.equals(rid) && root.subcategories().isEmpty()) continue;
                Override edit = state.subcategoryOverrides.get(sid);
                boolean hidden = hiddenRoot || state.blockedSubcategories.contains(sid);
                result.put(sid, new CategoryMetadata(sub.id(), parse(effectiveParent(sid)),
                        edit == null || edit.name == null ? subTitle(sub) : edit.name,
                        edit == null || edit.name == null ? sub.literalTitle() : true,
                        edit == null || edit.icon == null ? sub.icon() : parse(edit.icon),
                        (byte) (edit == null && !state.parentOverrides.containsKey(sid) ? 0 : 2),
                        protectedSub(sid), hidden, subTitle(sub), sub.literalTitle(), sub.icon()));
            }
            if (!root.subcategories().isEmpty()) {
                ResourceLocation fallback = new ResourceLocation(root.id().getNamespace(), root.id().getPath() + "/_other");
                result.putIfAbsent(fallback.toString(), new CategoryMetadata(fallback, root.id(),
                        "cuisinejournal.category.fallback", false, root.icon(), (byte) 0, true,
                        hiddenRoot, "cuisinejournal.category.fallback", false, root.icon()));
            }
        }
        for (Root root : state.roots) {
            if (baseRoot(root.id) != null) continue;
            ResourceLocation id = parse(root.id);
            if (id != null) result.putIfAbsent(root.id, new CategoryMetadata(id, null, root.name, true,
                    parse(root.icon), (byte) (findRoot(localState, root.id) != null ? 1 : 0), protectedRoot(root.id), state.blockedRoots.contains(root.id),
                    root.name, true, parse(root.icon)));
        }
        for (Subcategory sub : state.subcategories) {
            if (baseSubcategory(sub.id) != null) continue;
            ResourceLocation id = parse(sub.id);
            if (id != null) result.putIfAbsent(sub.id, new CategoryMetadata(id, parse(effectiveParent(state, sub.id)),
                    sub.name, true, parse(sub.icon), (byte) (findSubcategory(localState, sub.id) != null ? 1 : 0), protectedSub(sub.id),
                    state.blockedSubcategories.contains(sub.id), sub.name, true, parse(sub.icon)));
        }
        return List.copyOf(result.values());
    }

    private static FoodCategory applyRootAppearance(FoodCategory category) {
        Override edit = state.rootOverrides.get(category.id().toString());
        if (edit == null) return category;
        return new FoodCategory(category.id(), category.tags(), category.priority(), category.items(),
                edit.icon == null ? category.icon() : parse(edit.icon), category.subcategories(),
                edit.name == null ? category.titleKey() : edit.name,
                edit.name == null ? category.literalTitle() : true);
    }

    private static FoodCategory.Subcategory applySubAppearance(FoodCategory.Subcategory sub) {
        Override edit = state.subcategoryOverrides.get(sub.id().toString());
        if (edit == null) return sub;
        return new FoodCategory.Subcategory(sub.id(), edit.name == null ? sub.titleKey() : edit.name,
                edit.name == null ? sub.literalTitle() : true, edit.icon == null ? sub.icon() : parse(edit.icon),
                sub.tags(), sub.items(), sub.drinkFallback());
    }

    private static List<FoodCategory.Subcategory> effectiveBaseSubcategories(FoodCategory category) {
        if (!category.subcategories().isEmpty()) return category.subcategories();
        return List.of(new FoodCategory.Subcategory(category.id(), category.translationKey(), category.literalTitle(),
                category.icon(), category.tags(), category.items()));
    }

    /** Manual assignments win over datapack membership: add this sub's foods, drop foods assigned elsewhere. */
    private static FoodCategory.Subcategory withAssignments(FoodCategory.Subcategory sub) {
        String sid = sub.id().toString();
        Set<ResourceLocation> items = new LinkedHashSet<>();
        for (ResourceLocation item : sub.items()) {
            String assigned = state.assignments.get(item.toString());
            if (assigned == null || assigned.equals(sid)) items.add(item);
        }
        items.addAll(assignedItems(sid));
        if (items.equals(sub.items())) return sub;
        return new FoodCategory.Subcategory(sub.id(), sub.titleKey(), sub.literalTitle(), sub.icon(), sub.tags(), items,
                sub.drinkFallback());
    }

    private static Set<ResourceLocation> assignedItems(String subcategory) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : state.assignments.entrySet()) {
            if (entry.getValue().equals(subcategory)) {
                ResourceLocation id = parse(entry.getKey());
                if (id != null) result.add(id);
            }
        }
        return result;
    }

    private static String subTitle(FoodCategory.Subcategory sub) {
        return sub.titleKey() != null ? sub.titleKey()
                : "cuisinejournal.category." + sub.id().getNamespace() + "." + sub.id().getPath();
    }

    /** Built-in roots without configured children own one implicit subcategory whose id is the root id. */
    private static boolean legacyImplicitSub(String id) {
        return id != null && baseDefinitions.stream()
                .anyMatch(root -> root.subcategories().isEmpty() && root.id().toString().equals(id));
    }

    private static boolean isAssignableSubcategory(String id) {
        // "Other food" is the runtime fallback root, never a datapack definition, but it is a valid target.
        if (OTHER_FOOD_ID.toString().equals(id)) return true;
        // Implicit subcategories are not listed in metadata (their id equals the root id) but are valid targets.
        if (legacyImplicitSub(id)) return !state.blockedRoots.contains(id);
        if (id == null || id.endsWith("/_other")) return false;
        CategoryMetadata value = metadata().stream().filter(entry -> entry.id().toString().equals(id)).findFirst().orElse(null);
        return value != null && value.parent() != null && !value.hidden();
    }

    private static boolean protectedRoot(String id) {
        return OTHER_FOOD_ID.toString().equals(id);
    }

    private static boolean protectedSub(String id) {
        return id != null && (id.endsWith("/_other") || baseDefinitions.stream()
                .anyMatch(root -> root.subcategories().isEmpty() && root.id().toString().equals(id)));
    }

    private static boolean validName(String value) {
        return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= 24;
    }

    private static boolean invalidIcon(String value) {
        return value != null && parse(value) == null;
    }

    private static boolean validId(String value) {
        return value != null && parse(value) != null;
    }

    private static ResourceLocation parse(String value) {
        return value == null ? null : ResourceLocation.tryParse(value);
    }

    private static FoodCategory baseRoot(String id) {
        return baseDefinitions.stream().filter(value -> value.id().toString().equals(id)).findFirst().orElse(null);
    }

    private static FoodCategory.Subcategory baseSubcategory(String id) {
        for (FoodCategory root : baseDefinitions) {
            for (FoodCategory.Subcategory sub : effectiveBaseSubcategories(root)) {
                if (sub.id().toString().equals(id)) return sub;
            }
        }
        return null;
    }

    private static Root customRoot(String id) {
        return state.roots.stream().filter(value -> value.id.equals(id)).findFirst().orElse(null);
    }

    private static Subcategory customSubcategory(String id) {
        return state.subcategories.stream().filter(value -> value.id.equals(id)).findFirst().orElse(null);
    }

    private static Root findRoot(State source, String id) {
        return source.roots.stream().filter(value -> value.id.equals(id)).findFirst().orElse(null);
    }

    private static Subcategory findSubcategory(State source, String id) {
        return source.subcategories.stream().filter(value -> value.id.equals(id)).findFirst().orElse(null);
    }

    private static FoodCategory effectiveRoot(String id) {
        if (id == null || state.blockedRoots.contains(id)) return null;
        if (baseRoot(id) != null) return baseRoot(id);
        return customRoot(id) == null ? null : FoodCategory.OTHER_FOOD;
    }

    private static FoodCategory.Subcategory effectiveSubcategory(String id) {
        return isAssignableSubcategory(id) ? baseSubcategory(id) : customSubcategory(id) == null ? null
                : new FoodCategory.Subcategory(parse(id), customSubcategory(id).name, true,
                parse(customSubcategory(id).icon), List.of(), assignedItems(id));
    }

    private static boolean blockedRoot(String id) {
        return state.blockedRoots.contains(id);
    }

    private static boolean blockedSub(String id) {
        return state.blockedSubcategories.contains(id);
    }

    private static String effectiveParent(String id) {
        String override = state.parentOverrides.get(id);
        if (override != null) return override;
        for (FoodCategory root : baseDefinitions) {
            if (effectiveBaseSubcategories(root).stream().anyMatch(sub -> sub.id().toString().equals(id))) return root.id().toString();
        }
        Subcategory custom = customSubcategory(id);
        return custom == null ? null : custom.parent;
    }

    private static String effectiveParent(State source, String id) {
        String override = source.parentOverrides.get(id);
        if (override != null) return override;
        Subcategory custom = findSubcategory(source, id);
        return custom == null ? effectiveParent(id) : custom.parent;
    }

    private static int nextOrder(List<?> values) {
        return values.size();
    }

    private static <T> boolean mutate(java.util.function.Function<State, Boolean> operation) {
        State candidate = copy(localState);
        try {
            if (!Boolean.TRUE.equals(operation.apply(candidate))) return false;
            candidate.normalise();
            int nextRevision = revision + 1;
            if (!save(candidate, nextRevision)) return false;
            localState = candidate;
            state = merge(packState, localState);
            revision = nextRevision;
            return true;
        } catch (RuntimeException failure) {
            CuisineJournalLog.LOGGER.error("Category edit transaction failed", failure);
            return false;
        }
    }

    private static State copy(State source) {
        State value = GSON.fromJson(GSON.toJson(source), State.class);
        value.normalise();
        return value;
    }

    private static boolean save(State value, int nextRevision) {
        if (file == null || !writable) return false;
        value.revision = nextRevision;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            CuisineJournalLog.LOGGER.error("Could not save global category file {}", file, failure);
            return false;
        }
    }

    private static final class State {
        int revision;
        List<Root> roots = new ArrayList<>();
        List<Subcategory> subcategories = new ArrayList<>();
        Map<String, String> assignments = new LinkedHashMap<>();
        Map<String, String> parentOverrides = new LinkedHashMap<>();
        Set<String> blockedRoots = new LinkedHashSet<>();
        Set<String> blockedSubcategories = new LinkedHashSet<>();
        Map<String, Override> rootOverrides = new LinkedHashMap<>();
        Map<String, Override> subcategoryOverrides = new LinkedHashMap<>();
        Set<String> removedRoots;
        Set<String> removedSubcategories;
        /** Negations of pack-overlay entries (see {@link #merge}). */
        Set<String> deletedRoots = new LinkedHashSet<>();
        Set<String> deletedSubcategories = new LinkedHashSet<>();
        Set<String> unblockedRoots = new LinkedHashSet<>();
        Set<String> unblockedSubcategories = new LinkedHashSet<>();
        Set<String> unassigned = new LinkedHashSet<>();
        Set<String> resetOverrides = new LinkedHashSet<>();

        void normalise() {
            if (deletedRoots == null) deletedRoots = new LinkedHashSet<>();
            if (deletedSubcategories == null) deletedSubcategories = new LinkedHashSet<>();
            if (unblockedRoots == null) unblockedRoots = new LinkedHashSet<>();
            if (unblockedSubcategories == null) unblockedSubcategories = new LinkedHashSet<>();
            if (unassigned == null) unassigned = new LinkedHashSet<>();
            if (resetOverrides == null) resetOverrides = new LinkedHashSet<>();
            if (roots == null) roots = new ArrayList<>();
            if (subcategories == null) subcategories = new ArrayList<>();
            if (assignments == null) assignments = new LinkedHashMap<>();
            if (parentOverrides == null) parentOverrides = new LinkedHashMap<>();
            if (blockedRoots == null) blockedRoots = new LinkedHashSet<>();
            if (blockedSubcategories == null) blockedSubcategories = new LinkedHashSet<>();
            if (removedRoots != null) blockedRoots.addAll(removedRoots);
            if (removedSubcategories != null) blockedSubcategories.addAll(removedSubcategories);
            if (rootOverrides == null) rootOverrides = new LinkedHashMap<>();
            if (subcategoryOverrides == null) subcategoryOverrides = new LinkedHashMap<>();
            assignments = normaliseMap(assignments);
            parentOverrides = normaliseMap(parentOverrides);
            blockedRoots = normaliseSet(blockedRoots);
            blockedSubcategories = normaliseSet(blockedSubcategories);
            roots.removeIf(root -> root == null);
            subcategories.removeIf(sub -> sub == null);
        }

        private static Map<String, String> normaliseMap(Map<String, String> source) {
            Map<String, String> result = new LinkedHashMap<>();
            source.forEach((key, value) -> {
                String k = normaliseId(key), v = normaliseId(value);
                if (k != null && v != null) result.put(k, v);
            });
            return result;
        }

        private static Set<String> normaliseSet(Set<String> source) {
            Set<String> result = new LinkedHashSet<>();
            source.forEach(value -> { String id = normaliseId(value); if (id != null) result.add(id); });
            return result;
        }

        private static String normaliseId(String value) {
            if (value == null || value.isBlank()) return value;
            ResourceLocation parsed = ResourceLocation.tryParse(value);
            return parsed == null ? value : parsed.toString();
        }
    }

    private static final class Root {
        String id, name, icon;
        int order;
        Root(String id, String name, String icon, int order) { this.id = id; this.name = name; this.icon = icon; this.order = order; }
    }

    private static final class Subcategory {
        String id, parent, name, icon;
        int order;
        boolean drinkFallback;
        Subcategory(String id, String parent, String name, String icon, int order) {
            this.id = id; this.parent = parent; this.name = name; this.icon = icon; this.order = order;
        }
    }

    private static final class Override {
        String name, icon;
        Override(String name, String icon) { this.name = name; this.icon = icon; }
    }
}
