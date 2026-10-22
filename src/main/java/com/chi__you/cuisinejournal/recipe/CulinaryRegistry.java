package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The cached view of every dish this server knows how to make.
 *
 * <p>Rebuilt once per recipe reload and never touched again, which is what
 * keeps the design's performance rules satisfiable: nothing scans the
 * recipe registry outside a reload, and every journal query is a map lookup
 * against these indexes.</p>
 *
 * <p>Instances are immutable. Player progress is deliberately <em>not</em> here:
 * removing an addon rebuilds this registry without its dishes while the
 * profiles keep their records intact.</p>
 */
public final class CulinaryRegistry {

    public static final CulinaryRegistry EMPTY = new CulinaryRegistry(
            Map.of(), List.of(), CulinaryAudit.EMPTY, List.of(), Map.of(), Map.of());

    /**
     * An ingredient tag as the journal shows it. {@code c:} and {@code forge:} tags with the
     * same path are one entry.
     *
     * @param key     stable entry id ({@code forge:<path>} for common tags, the tag id otherwise)
     * @param ids     every real tag id merged into this entry
     * @param members usable items in the tag
     */
    public record TagEntry(ResourceLocation key, List<ResourceLocation> ids, List<ResourceLocation> members,
                           String displayName) {
        public TagEntry {
            ids = List.copyOf(ids);
            members = List.copyOf(members);
        }
        public TagEntry(ResourceLocation key, List<ResourceLocation> ids, List<ResourceLocation> members) {
            this(key, ids, members, null);
        }
    }

    private final Map<ResourceLocation, MealEntry> byTarget;
    private Map<ResourceLocation,ResourceLocation> portionOrigins=Map.of();
    private Map<ResourceLocation,MealEntry> preparationOnly=Map.of();
    public ResourceLocation preparationTarget(ResourceLocation item) {
        return portionOrigins.getOrDefault(item,item);
    }
    public Map<ResourceLocation,ResourceLocation> portionOrigins(){return portionOrigins;}
    public MealEntry progressEntry(ResourceLocation item) {
        MealEntry entry=entry(item);
        if(preparationOnly.containsKey(item)) {
            for(var origin:portionOrigins.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())if(origin.getValue().equals(item)) {
                MealEntry portion=entry(origin.getKey());
                return new MealEntry(portion.target(),portion.item(),portion.sourceModId(),entry.recipes(),
                        entry.methods(),entry.ingredients(),portion.category(),portion.nutrition(),portion.saturation());
            }
        }
        return entry;
    }
    public List<RecipeEntry> preparationRecipes(ResourceLocation item) {
        MealEntry source=entry(preparationTarget(item));
        return source==null?List.of():source.recipes();
    }
    private Map<ResourceLocation,List<RecipeEntry>> interactionRecipes=Map.of();
    private Map<ResourceLocation,List<RecipeEntry.IngredientSlot>> flavorInputs=Map.of();
    public List<RecipeEntry.IngredientSlot> flavorInputs(ResourceLocation item) {
        ResourceLocation target=preparationTarget(item);
        ResourceLocation stored = FruitCauldronBridge.storedFood(BuiltInRegistries.ITEM.get(target));
        if (stored != null && !stored.equals(target)) return List.of(new RecipeEntry.IngredientSlot(List.of(stored)));
        return flavorInputs.getOrDefault(target,preparationRecipes(item).stream().flatMap(recipe->recipe.ingredients().stream()).toList());
    }
    public List<RecipeEntry> interactionRecipes(ResourceLocation item) {
        return interactionRecipes.getOrDefault(item,List.of());
    }
    private final List<MealEntry> ordered;
    private final CulinaryAudit audit;
    private final Map<ResourceLocation, FoodCategory> categoryDefinitions;

    private final Map<CookingMethod, List<MealEntry>> byMethod;
    private final Map<String, List<MealEntry>> byMod;
    private final Map<ResourceLocation, List<MealEntry>> byCategory;
    private final Map<ResourceLocation, List<MealEntry>> bySubcategory;
    private final Map<ResourceLocation, List<MealEntry>> byIngredient;
    private final Set<ResourceLocation> allIngredients;
    /** Dishes whose recipe names the item itself (not through a tag). */
    private final Map<ResourceLocation, List<MealEntry>> byExplicitItem;
    /** Dishes whose recipe names the tag, keyed by {@link TagEntry#key()}. */
    private final Map<ResourceLocation, List<MealEntry>> byTag;
    private final Map<ResourceLocation, TagEntry> tags;
    private final Map<ResourceLocation, List<ResourceLocation>> tagsOfItem;
    /** Item entries of the ingredient journal: named items plus members of named tags. */
    private final Set<ResourceLocation> catalogueItems;
    /** Processing recipe output → its inputs (cutting pork gives bacon: bacon → pork). */
    private final Map<ResourceLocation, List<ResourceLocation>> processingParents;
    /** A processed item that is a portion of a dish (cake slice → cake). */
    private final Map<ResourceLocation, ResourceLocation> aliases;
    private final List<IngredientJournalIndex.Audit> ingredientAudit;
    private final Map<ResourceLocation, List<ResourceLocation>> ingredientSources;
    private final IngredientJournalIndex ingredientIndex;

    private CulinaryRegistry(Map<ResourceLocation, MealEntry> byTarget,
                             List<MealEntry> ordered,
                             CulinaryAudit audit,
                             List<FoodCategory> definitions,
                             Map<ResourceLocation, List<ResourceLocation>> processingParents,
                             Map<ResourceLocation, ResourceLocation> aliases) {
        this(byTarget, ordered, audit, definitions, processingParents, aliases, Map.of(), Set.of());
    }

    private CulinaryRegistry(Map<ResourceLocation, MealEntry> byTarget, List<MealEntry> ordered,
                             CulinaryAudit audit, List<FoodCategory> definitions,
                             Map<ResourceLocation, List<ResourceLocation>> processingParents,
                             Map<ResourceLocation, ResourceLocation> aliases,
                             Map<ResourceLocation,List<IngredientStorageCycles.Production>> productions,
                             Set<ResourceLocation> unreadableProductions) {
        this.byTarget = Map.copyOf(byTarget);
        this.ordered = List.copyOf(ordered);
        this.audit = audit;
        this.processingParents = Map.copyOf(processingParents);
        this.aliases = Map.copyOf(aliases);
        List<IngredientJournalIndex.Input> ingredientInputs = new ArrayList<>();
        for (MealEntry entry : ordered) for (RecipeEntry recipe : entry.recipes())
            for (int slot = 0; slot < recipe.ingredients().size(); slot++)
                ingredientInputs.add(new IngredientJournalIndex.Input(entry.target(), recipe.recipeId(), slot,
                        recipe.ingredients().get(slot)));
        Set<ResourceLocation> storageOnly = IngredientStorageCycles.hidden(productions, ingredientInputs, unreadableProductions);
        IngredientJournalIndex ingredientIndex = IngredientJournalIndex.build(ingredientInputs,
                IngredientCompatRules.current(), CulinaryRegistry::resolveTag,
                item -> !storageOnly.contains(item) && IngredientCatalogue.isUsableIngredient(item),
                tag -> IngredientCatalogueConfig.current().excludedTag(tag), HostItemAvailability::visible);
        this.ingredientIndex = ingredientIndex;
        this.byIngredient = resolveMeals(ingredientIndex.allDishes());
        this.allIngredients = Set.copyOf(ingredientIndex.items());
        this.byExplicitItem = resolveMeals(ingredientIndex.itemDishes());
        this.byTag = resolveMeals(ingredientIndex.tagDishes());
        this.tags = ingredientIndex.tags();
        this.tagsOfItem = freezeReverse(ingredientIndex.itemTags());
        this.catalogueItems = Collections.unmodifiableSet(new LinkedHashSet<>(ingredientIndex.items()));
        this.ingredientAudit = ingredientIndex.audit();
        this.ingredientSources = freezeReverse(ingredientIndex.sources());
        Map<ResourceLocation, FoodCategory> categoryMap = new LinkedHashMap<>();
        for (FoodCategory category : definitions) categoryMap.put(category.id(), category);
        this.categoryDefinitions = Map.copyOf(categoryMap);

        Map<CookingMethod, List<MealEntry>> methods = new LinkedHashMap<>();
        Map<String, List<MealEntry>> mods = new LinkedHashMap<>();
        Map<ResourceLocation, List<MealEntry>> categories = new LinkedHashMap<>();
        Map<ResourceLocation, List<MealEntry>> subcategories = new LinkedHashMap<>();

        for (MealEntry entry : this.ordered) {
            for (CookingMethod method : entry.methods()) {
                methods.computeIfAbsent(method, unused -> new ArrayList<>()).add(entry);
            }
            mods.computeIfAbsent(entry.sourceModId(), unused -> new ArrayList<>()).add(entry);
            categories.computeIfAbsent(entry.category().id(), unused -> new ArrayList<>()).add(entry);
            subcategories.computeIfAbsent(entry.masteryGroup(), unused -> new ArrayList<>()).add(entry);
        }
        for (FoodCategory category : definitions) {
            categories.computeIfAbsent(category.id(), unused -> new ArrayList<>());
        }

        this.byMethod = freeze(methods);
        this.byMod = freeze(mods);
        this.byCategory = freeze(categories);
        this.bySubcategory = freeze(subcategories);
    }

    /** {@code c:} and {@code forge:} tags with the same path share one journal entry. */
    public static ResourceLocation tagKey(ResourceLocation tag) {
        return "c".equals(tag.getNamespace()) || "forge".equals(tag.getNamespace())
                ? ResourceLocation.fromNamespaceAndPath("forge", tag.getPath()) : tag;
    }

    /** Actual recipe tag members, before journal exclusions, aliases or grouping. */
    public List<ResourceLocation> flavorTagMembers(ResourceLocation id) {
        return resolveTag(id);
    }

    private static List<ResourceLocation> resolveTag(ResourceLocation id) {
        try {
            TagKey<Item> key = TagKey.create(Registries.ITEM, id);
            return BuiltInRegistries.ITEM.getTag(key)
                    .map(set -> set.stream().map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()))
                            .filter(java.util.Objects::nonNull).toList())
                    .orElse(List.of());
        } catch (RuntimeException | LinkageError failure) {
            return List.of();
        }
    }

    private static <K> Map<K, List<MealEntry>> freeze(Map<K, List<MealEntry>> source) {
        Map<K, List<MealEntry>> frozen = new LinkedHashMap<>(source.size());
        source.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(frozen);
    }

    private Map<ResourceLocation, List<MealEntry>> resolveMeals(Map<ResourceLocation, Set<ResourceLocation>> source) {
        Map<ResourceLocation, List<MealEntry>> result = new LinkedHashMap<>();
        source.forEach((key, ids) -> result.put(key, ids.stream().map(byTarget::get)
                .filter(java.util.Objects::nonNull).toList()));
        return Collections.unmodifiableMap(result);
    }

    private static Map<ResourceLocation, List<ResourceLocation>> freezeReverse(Map<ResourceLocation, Set<ResourceLocation>> source) {
        Map<ResourceLocation, List<ResourceLocation>> result = new LinkedHashMap<>();
        source.forEach((key, ids) -> result.put(key, List.copyOf(ids)));
        return Collections.unmodifiableMap(result);
    }

    public List<IngredientJournalIndex.Audit> ingredientAudit() { return ingredientAudit; }
    public List<ResourceLocation> ingredientSources(ResourceLocation item) {
        return ingredientSources.getOrDefault(item, List.of(item));
    }

    // --------------------------------------------------------------- queries

    public MealEntry entry(ResourceLocation target) {
        if (HostItemAvailability.hiddenFromCatalogue(target)) return null;
        target=DishServingVariants.target(target,byTarget.keySet());
        MealEntry entry=byTarget.get(target);
        return entry==null?preparationOnly.get(target):entry;
    }

    public boolean isTracked(ResourceLocation target) {
        if (HostItemAvailability.hiddenFromCatalogue(target)) return false;
        target=DishServingVariants.target(target,byTarget.keySet());
        return byTarget.containsKey(target)||preparationOnly.containsKey(target);
    }

    public Set<ResourceLocation> targets() {
        return byTarget.keySet();
    }

    public List<MealEntry> entries() {
        return ordered;
    }

    public int size() {
        return ordered.size();
    }

    public CulinaryAudit audit() {
        return audit;
    }

    public List<MealEntry> byMethod(CookingMethod method) {
        return byMethod.getOrDefault(method, List.of());
    }

    public Set<CookingMethod> methods() {
        return byMethod.keySet();
    }

    public List<MealEntry> byMod(String modId) {
        return byMod.getOrDefault(modId, List.of());
    }

    public Set<String> sourceMods() {
        return byMod.keySet();
    }

    public List<MealEntry> byCategory(ResourceLocation categoryId) {
        return byCategory.getOrDefault(categoryId, List.of());
    }

    public Set<ResourceLocation> categories() {
        return byCategory.keySet();
    }

    public List<MealEntry> bySubcategory(ResourceLocation id) {
        return bySubcategory.getOrDefault(id, List.of());
    }

    public FoodCategory categoryDefinition(ResourceLocation id) {
        return categoryDefinitions.get(id);
    }

    public List<MealEntry> usingIngredient(ResourceLocation ingredient) {
        return byIngredient.getOrDefault(ingredient, List.of());
    }

    public Set<ResourceLocation> ingredientIds() {
        return byIngredient.keySet();
    }

    /** Every ingredient that appears in at least one tracked recipe. */
    public Set<ResourceLocation> allIngredients() {
        return allIngredients;
    }

    /** Dishes whose recipe names this item directly. */
    public List<MealEntry> explicitUses(ResourceLocation item) {
        return byExplicitItem.getOrDefault(item, List.of());
    }

    /** Dishes whose recipe names this tag entry. */
    public List<MealEntry> tagUses(ResourceLocation tagKey) {
        return byTag.getOrDefault(tagKey, List.of());
    }

    public Map<ResourceLocation, TagEntry> tags() {
        return tags;
    }

    public List<IngredientJournalIndex.TagSource> tagSources(ResourceLocation entry) {
        return ingredientIndex.tagSources(entry);
    }

    public List<IngredientJournalIndex.TagUse> tagRecipeUses(ResourceLocation entry) {
        return ingredientIndex.tagUses(entry);
    }

    /** Tag entries this item belongs to. */
    public List<ResourceLocation> tagsOf(ResourceLocation item) {
        return tagsOfItem.getOrDefault(item, List.of());
    }

    /** Item entries of the ingredient journal. */
    public Set<ResourceLocation> catalogueItems() {
        return catalogueItems;
    }

    /** Independent catalogue entries, excluding members that are only referenced through tags. */
    public Set<ResourceLocation> listedIngredients() {
        return byExplicitItem.keySet();
    }

    /** Inputs of the processing recipes that produce this item (for flavor tracing). */
    public List<ResourceLocation> processingParents(ResourceLocation item) {
        return processingParents.getOrDefault(item, List.of());
    }

    /** The dish a processed portion belongs to (cake slice → cake), or the item itself. */
    public ResourceLocation dishIdentity(ResourceLocation item) {
        item=DishServingVariants.target(item,byTarget.keySet());
        return aliases.getOrDefault(item, item);
    }

    /** Resolves an output to the tracked dish it represents, including a placed-food source block. */
    public ResourceLocation productionIdentity(ResourceLocation item) {
        if (item == null) return null;
        ResourceLocation normalized = DishServingVariants.target(item, byTarget.keySet());
        if (isTracked(normalized)) return normalized;
        ResourceLocation found = null;
        for (var link : portionOrigins.entrySet()) {
            if (normalized.equals(link.getValue())) {
                if (found != null && !found.equals(link.getKey())) return normalized;
                found = link.getKey();
            }
        }
        return found == null ? normalized : found;
    }

    public boolean isPortionOf(ResourceLocation source, ResourceLocation output) {
        if (source == null || output == null) return false;
        if(com.chi__you.cuisinejournal.food.FarmersSpellFoods.isPortion(source,output))return true;
        if (preparationTarget(output).equals(source) && !output.equals(source)) return true;
        ResourceLocation portion = productionIdentity(source);
        return portion.equals(DishServingVariants.target(output, byTarget.keySet()))
                || portion.equals(dishIdentity(output));
    }

    public boolean isRepacking(ResourceLocation whole, Set<ResourceLocation> inputs) {
        return !inputs.isEmpty() && inputs.stream().allMatch(item -> !item.equals(whole)
                && preparationTarget(item).equals(whole));
    }

    // ----------------------------------------------------------------- build

    /**
     * Classifies every recipe on the server into journal entries.
     *
     * @param recipes        the full recipe list, straight from the recipe manager
     * @param registryAccess needed to resolve recipe results
     * @param rules          eligibility configuration
     * @param dishCategories category definitions, most specific first
     */
    public static CulinaryRegistry build(Collection<? extends Recipe<?>> recipes,
                                         RegistryAccess registryAccess,
                                         EligibilityRules rules,
                                         List<FoodCategory> dishCategories) {
        long startedAt = System.nanoTime();
        HostItemAvailability.rebuild();
        FluidRecipeBridge.rebuild(recipes,registryAccess);
        RecipeClassifier classifier = new RecipeClassifier(rules, registryAccess);
        RecipeClassifier flavorClassifier = new RecipeClassifier(EligibilityRules.builder().requireEdibleOutput(false).build(), registryAccess);
        Map<ResourceLocation,List<RecipeEntry.IngredientSlot>> flavorInputs = new LinkedHashMap<>();

        Map<ResourceLocation, List<RecipeEntry>> grouped = new LinkedHashMap<>();
        Map<CookingMethod, Integer> perMethod = new LinkedHashMap<>();
        Map<EligibilityRules.Verdict, Integer> verdictCounts = new EnumMap<>(EligibilityRules.Verdict.class);
        // Processing (cutting, milling…) is not cooking: its outputs are not dishes and earn
        // nothing, but the input → output edge is kept for flavor tracing.
        Map<ResourceLocation, Set<ResourceLocation>> processingEdges = new LinkedHashMap<>();
        Map<ResourceLocation,Set<ResourceLocation>> portionCandidates = new LinkedHashMap<>();
        Map<ResourceLocation,List<RecipeEntry>> allPreparations = new LinkedHashMap<>();
        List<RecipeEntry> interactionDisplays = new ArrayList<>();
        Map<ResourceLocation,List<IngredientStorageCycles.Production>> ingredientProductions = new LinkedHashMap<>();
        Set<ResourceLocation> unreadableProductions = new LinkedHashSet<>();
        int failed = 0;

        for (Recipe<?> recipe : recipes) {
            if (!com.chi__you.foodinteractions.LycheeDisplayAccess.generated(recipe)) {
                List<ItemStack> outputs;
                try { outputs = RecipeClassifier.outputs(recipe,registryAccess); }
                catch (RuntimeException | LinkageError failure) { outputs = List.of(); }
                for (ItemStack output : outputs) {
                    ResourceLocation target = BuiltInRegistries.ITEM.getKey(output.getItem());
                    try {
                        boolean crafting = recipe.getType() == net.minecraft.world.item.crafting.RecipeType.CRAFTING;
                        ingredientProductions.computeIfAbsent(target, unused -> new ArrayList<>()).add(
                                new IngredientStorageCycles.Production(crafting, output.getCount(), RecipeClassifier.readIngredients(recipe)));
                    } catch (RuntimeException | LinkageError failure) { unreadableProductions.add(target); }
                }
            }
            if (!com.chi__you.foodinteractions.LycheeDisplayAccess.generated(recipe)) {
                RecipeEntry preparation = classifier.classifyPreparation(recipe).entry();
                if (preparation != null && !IngredientCatalogueConfig.current().processing(preparation.recipeTypeId()))
                    allPreparations.computeIfAbsent(preparation.masteryTarget(), unused -> new ArrayList<>()).add(preparation);
            }
            // Catalogue exclusions do not remove edible intermediates or processing from the flavor graph.
            if (!com.chi__you.foodinteractions.LycheeDisplayAccess.generated(recipe)) {
                for (var tracedClassification : flavorClassifier.classifyAll(recipe)) {
                    RecipeEntry traced = tracedClassification.entry();
                    if (traced != null) {
                        ItemStack resultStack = new ItemStack(BuiltInRegistries.ITEM.get(traced.masteryTarget()));
                        if (com.chi__you.cuisinejournal.food.CuisineEdibility.isEdible(resultStack)
                                || resultStack.is(net.minecraft.world.item.Items.CAKE)
                                || IngredientCatalogueConfig.current().processing(traced.recipeTypeId())) {
                            flavorInputs.computeIfAbsent(traced.masteryTarget(),unused->new ArrayList<>()).addAll(traced.ingredients());
                        }
                    }
                }
            }
            for (RecipeClassifier.Classification classification : classifier.classifyAll(recipe)) {
                if (classification.failed()) {
                    failed++;
                    continue;
                }
                verdictCounts.merge(classification.verdict(), 1, Integer::sum);
                RecipeEntry entry = classification.entry();
                if (entry == null) continue;
                if(com.chi__you.foodinteractions.LycheeDisplayAccess.freezing(recipe)) {
                    flavorInputs.computeIfAbsent(entry.masteryTarget(), unused -> new ArrayList<>()).addAll(entry.ingredients());
                } else if(com.chi__you.foodinteractions.LycheeDisplayAccess.generated(recipe)) {
                    interactionDisplays.add(entry);
                    continue;
                }
                if (IngredientCatalogueConfig.current().processing(entry.recipeTypeId())) {
                    Set<ResourceLocation> inputs = new LinkedHashSet<>();
                    for (RecipeEntry.IngredientSlot slot : entry.ingredients()) inputs.addAll(slot.items());
                    processingEdges.computeIfAbsent(entry.masteryTarget(), unused -> new LinkedHashSet<>()).addAll(inputs);
                    if (entry.method().equals(CookingMethod.CUTTING_BOARD) && inputs.size() == 1) {
                        ResourceLocation source = inputs.iterator().next();
                        if (BuiltInRegistries.ITEM.get(source) instanceof net.minecraft.world.item.BlockItem blockItem
                                && blockItem.getBlock().getStateDefinition().getProperty("bites") != null)
                            portionCandidates.computeIfAbsent(entry.masteryTarget(), unused -> new LinkedHashSet<>()).add(source);
                    }
                    continue;
                }
                grouped.computeIfAbsent(entry.masteryTarget(), unused -> new ArrayList<>()).add(entry);
                perMethod.merge(entry.method(), 1, Integer::sum);
            }
        }

        List<RecipeEntry> specialPreparations=new ArrayList<>(FruitCauldronBridge.read());
        specialPreparations.addAll(LetsDoRecipes.preparations());
        specialPreparations.addAll(GrillingRecipes.preparations());
        specialPreparations.addAll(CheesePreparations.read());
        specialPreparations.addAll(NeapolitanCauldrons.recipes());
        for (RecipeEntry entry : specialPreparations) {
            ingredientProductions.computeIfAbsent(entry.masteryTarget(), unused -> new ArrayList<>()).add(
                    new IngredientStorageCycles.Production(false, entry.outputCount(), entry.ingredients()));
            flavorInputs.computeIfAbsent(entry.masteryTarget(), unused -> new ArrayList<>()).addAll(entry.ingredients());
            ItemStack output = new ItemStack(BuiltInRegistries.ITEM.get(entry.masteryTarget()));
            if(rules.evaluate(entry.recipeId(),entry.recipeTypeId(),entry.masteryTarget(),true,output::is)==EligibilityRules.Verdict.ELIGIBLE)
                allPreparations.computeIfAbsent(entry.masteryTarget(), unused -> new ArrayList<>()).add(0, entry);
            if (rules.evaluate(entry.recipeId(), entry.recipeTypeId(), output) != EligibilityRules.Verdict.ELIGIBLE) continue;
            grouped.computeIfAbsent(entry.masteryTarget(), unused -> new ArrayList<>()).add(0, entry);
            perMethod.merge(entry.method(), 1, Integer::sum);
        }

        // Explicit category membership also lists foods made by non-item recipes
        // (for example kettle fluids). This does not invent cooking recipes.
        int catalogueOnly = includeConfiguredItems(grouped, dishCategories, target -> {
            if (!BuiltInRegistries.ITEM.containsKey(target)) return false;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(target));
            return com.chi__you.cuisinejournal.food.CuisineEdibility.isEdible(stack)
                    && rules.evaluateItem(target, true, stack::is) == EligibilityRules.Verdict.ELIGIBLE;
        });
        catalogueOnly += includeFallbackDrinks(grouped, dishCategories,
                BuiltInRegistries.ITEM.keySet(), target -> {
                    ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(target));
                    return com.chi__you.cuisinejournal.food.CuisineFoodDefinition.isDrink(stack)
                            && rules.evaluateItem(target, true, stack::is) == EligibilityRules.Verdict.ELIGIBLE;
                });

        // Display-only obtaining paths add a food page, never a cooking recipe or tool ingredient.
        for(RecipeEntry display:interactionDisplays)
            grouped.computeIfAbsent(display.masteryTarget(),unused->new ArrayList<>());

        // Resolve portions before constructing pages: a valid whole recipe is enough to
        // admit an eligible slice even when no category explicitly lists that slice.
        for (RecipeEntry display : interactionDisplays) {
            if (!display.recipeId().getPath().startsWith("cutting/")) continue;
            Set<ResourceLocation> sources = portionCandidates.computeIfAbsent(display.masteryTarget(), unused -> new LinkedHashSet<>());
            display.ingredients().forEach(slot -> sources.addAll(slot.items()));
        }
        for(var relation:PlacedServingRelations.read()) {
            var source=relation.whole();var portion=relation.serving();
            if(allPreparations.getOrDefault(source,List.of()).isEmpty()||!BuiltInRegistries.ITEM.containsKey(portion))continue;
            if (PortionOrigins.packedServing(source, portion, allPreparations.get(source))) continue;
            if(!flavorInputs.containsKey(source))for(var preparation:allPreparations.get(source))
                flavorInputs.computeIfAbsent(source,unused->new ArrayList<>()).addAll(preparation.ingredients());
            ItemStack serving=new ItemStack(BuiltInRegistries.ITEM.get(portion));
            if(rules.evaluateItem(portion,true,serving::is)!=EligibilityRules.Verdict.ELIGIBLE)continue;
            portionCandidates.computeIfAbsent(portion,unused->new LinkedHashSet<>()).add(source);
            processingEdges.computeIfAbsent(portion,unused->new LinkedHashSet<>()).add(source);
            flavorInputs.computeIfAbsent(portion,unused->new ArrayList<>()).add(new RecipeEntry.IngredientSlot(List.of(source)));
            interactionDisplays.add(new RecipeEntry(ResourceLocation.fromNamespaceAndPath("cuisinejournal","serving/"+source.getNamespace()+"/"+source.getPath()+"/"+portion.getPath()),
                    ResourceLocation.parse("cuisinejournal:feast_serving"),CookingMethod.FEAST_SERVING,portion,relation.count(),
                    List.of(new RecipeEntry.IngredientSlot(List.of(source))),portion.getNamespace()));
        }
        Map<ResourceLocation,ResourceLocation> configuredOrigins = new LinkedHashMap<>();
        IngredientCompatRules.current().portionOrigins().forEach((portion, parent) -> {
            if (processingEdges.getOrDefault(portion, Set.of()).contains(parent)) configuredOrigins.put(portion, parent);
        });
        Set<ResourceLocation> independentlyCooked = new LinkedHashSet<>();
        grouped.forEach((id, values) -> { if (!values.isEmpty()) independentlyCooked.add(id); });
        Map<ResourceLocation,ResourceLocation> origins = PortionOrigins.resolve(portionCandidates, independentlyCooked, configuredOrigins);
        Map<ResourceLocation,List<RecipeEntry>> parentPreparations = new LinkedHashMap<>();
        for (var link : origins.entrySet()) {
            ResourceLocation parent = link.getValue();
            parentPreparations.computeIfAbsent(parent,
                    id -> PortionOrigins.preparations(id, allPreparations.getOrDefault(id, List.of()), origins));
        }
        Map<ResourceLocation,ResourceLocation> usableOrigins = new LinkedHashMap<>(PortionOrigins.admit(grouped, parentPreparations, origins));
        // A visible whole must also use its genuine preparation recipes, not repacking.
        parentPreparations.forEach((parent, preparations) -> {
            if (grouped.containsKey(parent) && !preparations.isEmpty()) grouped.put(parent, new ArrayList<>(preparations));
        });

        // A processed item made from a dish is a portion of that dish (cake → cake slice).
        // Runs after configured items so a slice placed in a category keeps its own page.
        Map<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> processingParents = new LinkedHashMap<>();
        grouped.keySet().removeIf(target -> !HostItemAvailability.visible(target));
        usableOrigins.entrySet().removeIf(link -> !HostItemAvailability.visible(link.getKey())
                || !HostItemAvailability.visible(link.getValue()));
        processingEdges.forEach((output, inputs) -> {
            processingParents.put(output, List.copyOf(inputs));
            if (!HostItemAvailability.visible(output)) return;
            if (grouped.containsKey(output)) return;
            for (ResourceLocation input : inputs) {
                if (grouped.containsKey(input)) { aliases.put(output, input); break; }
            }
        });

        List<MealEntry> ordered = new ArrayList<>(grouped.size());
        List<ResourceLocation> uncategorised = new ArrayList<>();
        Set<String> sourceMods = new LinkedHashSet<>();

        grouped.forEach((target, entries) -> {
            Item item = BuiltInRegistries.ITEM.get(target);
            if (item == null) {
                return;
            }
            Set<CookingMethod> methods = new LinkedHashSet<>();
            Set<ResourceLocation> ingredients = new LinkedHashSet<>();
            for (RecipeEntry entry : entries) {
                methods.add(entry.method());
                for (RecipeEntry.IngredientSlot slot : entry.ingredients()) {
                    ingredients.addAll(slot.items());
                }
            }

            ItemStack stack = new ItemStack(item);
            FoodProperties food = item.getFoodProperties();

            FoodCategory category = GlobalCategoryStore.resolve(
                    id -> byIdFor(id, dishCategories), dishCategories, stack);
            if (category == null) category = categorise(stack, dishCategories);
            if (category == FoodCategory.UNCATEGORISED) {
                uncategorised.add(target);
                // Edible but untagged is the common case, not an error.
                category = food == null ? FoodCategory.UNCATEGORISED : FoodCategory.OTHER_FOOD;
            }
            sourceMods.add(target.getNamespace());

            ordered.add(new MealEntry(
                    target,
                    item,
                    target.getNamespace(),
                    entries,
                    methods,
                    ingredients,
                    category,
                    food == null ? 0 : food.getNutrition(),
                    food == null ? 0.0F : food.getSaturationModifier()));
        });

        // Alphabetical by id keeps the journal, the audit and the exported
        // profile listing every dish in the same stable order.
        ordered.sort((left, right) -> left.target().compareTo(right.target()));

        Map<ResourceLocation, MealEntry> byTarget = new LinkedHashMap<>(ordered.size());
        for (MealEntry entry : ordered) {
            byTarget.put(entry.target(), entry);
        }

        CulinaryAudit audit = new CulinaryAudit(
                ordered.size(),
                verdictCounts.getOrDefault(EligibilityRules.Verdict.ELIGIBLE, 0),
                verdictCounts.getOrDefault(EligibilityRules.Verdict.NOT_FOOD, 0),
                verdictCounts.getOrDefault(EligibilityRules.Verdict.EXCLUDED, 0),
                failed,
                perMethod,
                sourceMods,
                uncategorised);

        CulinaryRegistry result = new CulinaryRegistry(byTarget, ordered, audit, dishCategories, processingParents, aliases,
                ingredientProductions, unreadableProductions);
        Map<ResourceLocation,List<RecipeEntry.IngredientSlot>> immutableInputs=new LinkedHashMap<>();
        flavorInputs.forEach((item,slots)->immutableInputs.put(item,List.copyOf(slots)));
        result.flavorInputs=Map.copyOf(immutableInputs);
        Map<ResourceLocation,MealEntry> hidden=new LinkedHashMap<>();
        for(var link:usableOrigins.entrySet()) {
            MealEntry portion=byTarget.get(link.getKey());
            if(portion==null)continue;
            ResourceLocation source=link.getValue();
            if(!byTarget.containsKey(source)&&!hidden.containsKey(source)) {
                if(!BuiltInRegistries.ITEM.containsKey(source))continue;
                List<RecipeEntry> preparations=parentPreparations.getOrDefault(source, List.of());
                if(preparations.isEmpty())continue;
                Set<CookingMethod> methods=new LinkedHashSet<>();Set<ResourceLocation> inputs=new LinkedHashSet<>();
                for(RecipeEntry recipe:preparations){methods.add(recipe.method());recipe.ingredients().forEach(slot->inputs.addAll(slot.items()));}
                hidden.put(source,new MealEntry(source,BuiltInRegistries.ITEM.get(source),source.getNamespace(),preparations,
                        methods,inputs,portion.category(),0,0));
            }
        }
        result.portionOrigins=Map.copyOf(usableOrigins);
        result.preparationOnly=Map.copyOf(hidden);
        Map<ResourceLocation,List<RecipeEntry>> displays=new LinkedHashMap<>();
        for(RecipeEntry display:interactionDisplays)
            displays.computeIfAbsent(display.masteryTarget(),unused->new ArrayList<>()).add(display);
        Map<ResourceLocation,List<RecipeEntry>> frozen=new LinkedHashMap<>();
        displays.forEach((id,values)->frozen.put(id,List.copyOf(values)));
        result.interactionRecipes=Map.copyOf(frozen);
        CuisineJournalLog.LOGGER.info(
                "Culinary registry rebuilt: {} dishes from {} eligible recipes across {} mods "
                        + "({} methods, {} uncategorised) in {} ms",
                ordered.size(), audit.eligibleRecipes(), sourceMods.size(),
                perMethod.size(), uncategorised.size(),
                (System.nanoTime() - startedAt) / 1_000_000L);
        if (catalogueOnly > 0) CuisineJournalLog.LOGGER.info(
                "Added {} configured foods and fallback drinks without standard item recipes", catalogueOnly);
        if (!processingEdges.isEmpty()) CuisineJournalLog.LOGGER.info(
                "{} processed items are ingredients, not dishes ({} are portions of a dish)",
                processingEdges.size(), aliases.size());

        return result;
    }

    static int includeConfiguredItems(Map<ResourceLocation, List<RecipeEntry>> grouped,
                                      List<FoodCategory> definitions,
                                      java.util.function.Predicate<ResourceLocation> eligible) {
        Set<ResourceLocation> configured = new LinkedHashSet<>();
        for (FoodCategory category : definitions) {
            configured.addAll(category.items());
            for (FoodCategory.Subcategory sub : category.subcategories()) configured.addAll(sub.items());
        }
        int added = 0;
        for (ResourceLocation target : configured) {
            if (!grouped.containsKey(target) && eligible.test(target)) {
                grouped.put(target, List.of());
                added++;
            }
        }
        return added;
    }

    static int includeFallbackDrinks(Map<ResourceLocation, List<RecipeEntry>> grouped,
                                     List<FoodCategory> definitions, Collection<ResourceLocation> candidates,
                                     java.util.function.Predicate<ResourceLocation> eligibleDrink) {
        if (definitions.stream().flatMap(category -> category.subcategories().stream())
                .noneMatch(FoodCategory.Subcategory::drinkFallback)) return 0;
        int added = 0;
        for (ResourceLocation target : candidates) {
            if (!grouped.containsKey(target) && eligibleDrink.test(target)) {
                grouped.put(target, List.of());
                added++;
            }
        }
        return added;
    }

    private static FoodCategory categorise(ItemStack stack, List<FoodCategory> definitions) {
        for (FoodCategory category : definitions) {
            if (category.matches(stack)) {
                return category;
            }
        }
        return FoodCategory.UNCATEGORISED;
    }

    private static FoodCategory byIdFor(ResourceLocation id, List<FoodCategory> definitions) {
        if (id == null) return null;
        return definitions.stream().filter(category -> category.id().equals(id)).findFirst().orElse(null);
    }
}
