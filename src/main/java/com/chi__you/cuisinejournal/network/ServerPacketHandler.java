package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.CuisineJournalLog;

import com.chi__you.cuisinejournal.food.CuisineFoodDefinitions;
import com.chi__you.cuisinejournal.challenge.ChallengeDefinition;
import com.chi__you.cuisinejournal.challenge.ChallengeRegistry;
import com.chi__you.cuisinejournal.challenge.ChallengeState;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.culinary.CulinaryBadge;
import com.chi__you.cuisinejournal.culinary.MealRecord;
import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.event.CulinaryEvents;
import com.chi__you.cuisinejournal.mastery.MasteryCurve;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistry;
import com.chi__you.cuisinejournal.recipe.FoodCategory;
import com.chi__you.cuisinejournal.recipe.MealEntry;
import com.chi__you.cuisinejournal.recipe.RecipeEntry;
import com.chi__you.cuisinejournal.recipe.GlobalCategoryStore;
import com.chi__you.cuisinejournal.recipe.IngredientCatalogue;
import com.chi__you.cuisinejournal.CuisineJournalServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes journal requests and publishes authoritative profile/catalogue views.
 * Editing, settings, schemes and journal transfers delegate to their own handlers.
 * Client requests cannot supply earned progression; edits are checked server-side.
 */
public final class ServerPacketHandler {
    private static final RequestBudget<java.util.UUID> REQUESTS = new RequestBudget<>();
    private static boolean installed;
    private static final java.util.Set<ServerPlayer> DISCOVERY_UPDATES =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private ServerPacketHandler() {
    }

    public static void install() {
        if (installed) return; installed = true;
        CulinaryEvents.RECIPE_DISCOVERED.register(event -> {
            Network.toPlayer(event.player(),new Packets.Notify(Packets.Notify.Kind.DISCOVERY,event.meal(),0L));
            sendRecordDelta(event.player(),event.profile(),event.meal());
            DISCOVERY_UPDATES.add(event.player());
            com.chi__you.cuisinejournal.progression.DiscoveryService.onDiscovered(event);
        });
        CulinaryEvents.MASTERY_RANK_CHANGED.register(event -> Network.toPlayer(event.player(),
                new Packets.Notify(event.to().isMastered()
                        ? Packets.Notify.Kind.MASTERED : Packets.Notify.Kind.MASTERY_RANK,
                        event.meal(), event.to().ordinal())));
        CulinaryEvents.COOKING_LEVEL_CHANGED.register(event -> Network.toPlayer(event.player(),
                new Packets.Notify(Packets.Notify.Kind.LEVEL_UP, null, event.to())));
        CulinaryEvents.CHALLENGE_COMPLETED.register(event -> Network.toPlayer(event.player(),
                new Packets.Notify(Packets.Notify.Kind.CHALLENGE, event.challenge(), 0L)));
        CulinaryEvents.MILESTONE_REACHED.register(event -> {
            Network.toPlayer(event.player(),new Packets.Notify(Packets.Notify.Kind.MILESTONE,event.milestone(),0L));
            syncChallengeProgress(event.player());
        });
        CulinaryEvents.COOKING_XP_GAINED.register(event -> Network.toPlayer(event.player(),
                new Packets.Notify(Packets.Notify.Kind.XP, event.reason(), event.amount())));

        // Any preparation changes at least one record, so the client is kept in
        // step with a delta rather than a fresh full profile.
        CulinaryEvents.MEAL_PREPARED.register(event -> {
            sendRecordDelta(event.player(), event.profile(), event.meal());
            sendExtras(event.player());
            sendStatistics(event.player());
        });
        CulinaryEvents.MEAL_EATEN.register(event ->
                sendRecordDelta(event.player(), event.profile(), event.meal()));
        CulinaryEvents.PORTIONS_SERVED.register(event -> {
            sendRecordDelta(event.player(), event.profile(), event.meal());
            sendStatistics(event.player());
        });
    }

    public static void handle(ServerPlayer player, CuisineJournalPacket packet) {
        long cooldown = packet instanceof Packets.RequestProfile ? 2000
                : packet instanceof Packets.RequestDishDetail || packet instanceof Packets.FlavorPreviewRequest ? 75 : 0;
        if (!REQUESTS.allow(player.getUUID(),packet.id().toString(),System.nanoTime()/1_000_000,cooldown)) return;
        if(packet instanceof JournalFeaturePackets.Track request){JournalFeatureHandler.track(player,request);
        } else if(packet instanceof JournalFeaturePackets.Signature request){JournalFeatureHandler.signature(player,request.dish());
        } else if (packet instanceof JournalSyncPackets.TagRequest request) {
            JournalSyncServer.sendTag(player, request);
        } else if (packet instanceof JournalSyncPackets.Request) {
            JournalSyncServer.resync(player);
        } else if (packet instanceof Packets.RequestProfile) {
            sendEverything(player);
        } else if (packet instanceof com.chi__you.cuisinejournal.skills.SkillPackets.Open) {
            com.chi__you.cuisinejournal.skills.SkillsIntegration.open(player);
        } else if (packet instanceof Packets.SetServerSetting request) {
            ServerSettingsHandler.handle(player, request);
        } else if (packet instanceof Packets.RequestDishDetail request) {
            sendDishDetail(player, request.target());
        } else if (packet instanceof Packets.SetPreference preference) {
            applyPreference(player, preference);
        } else if (packet instanceof Packets.CategoryEditRequest request) {
            applyCategoryEdit(player, request);
        } else if (packet instanceof Packets.CategoryEditModeRequest request) {
            applyCategoryEditMode(player, request.enabled());
        } else if (packet instanceof Packets.FlavorEditRequest request) {
            FlavorEditHandler.handle(player, request);
        } else if (packet instanceof Packets.SchemeRequest request) {
            SchemeHandler.handle(player, request);
        } else if (packet instanceof Packets.FlavorPreviewRequest request) {
            FlavorEditHandler.preview(player, request);
        } else if (packet instanceof com.chi__you.cuisinejournal.food.CuisineFoodInfoPackets.HostRequest request) {
            com.chi__you.cuisinejournal.food.CuisineFoodCatalogueSync.sendHost(player,request);
        } else if (packet instanceof com.chi__you.cuisinejournal.food.CuisineFoodInfoPackets.FruitRequest request) {
            com.chi__you.cuisinejournal.food.CuisineFoodCatalogueSync.sendFruit(player, request);
        }
    }

    public static void flushDiscoveryUpdates() {
        for(var player:java.util.List.copyOf(DISCOVERY_UPDATES)) {
            if(player.hasDisconnected())continue;
            sendExtras(player);
            sendStatistics(player);
        }
        DISCOVERY_UPDATES.clear();
    }

    public static void clearDiscoveryUpdates() {
        DISCOVERY_UPDATES.clear(); JournalSyncServer.clear(); REQUESTS.clear();
        com.chi__you.cuisinejournal.forge.ForgeNetwork.clearInbound();
    }
    public static void forgetJournalSync(ServerPlayer player) {
        GlobalCategoryStore.clearEditing(player.getUUID());
        player.getPersistentData().remove(EDIT_MODE_KEY);
        REQUESTS.remove(player.getUUID());
        com.chi__you.cuisinejournal.forge.ForgeNetwork.forgetInbound(player.getUUID());
        DISCOVERY_UPDATES.remove(player); JournalSyncServer.disconnect(player);
    }
    public static void syncChallengeProgress(ServerPlayer player){DISCOVERY_UPDATES.add(player);}

    // ------------------------------------------------------------- outgoing

    /** Sent on join and whenever the journal asks for a refresh. */
    public static void sendEverything(ServerPlayer player) {
        if(ProfileManager.get()!=null)com.chi__you.cuisinejournal.skills.SkillsIntegration.sync(player);
        Network.toPlayer(player, new Packets.SyncCategoryPermissions(
                player.hasPermissions(ConfigManager.server().multiplayer.adminPermissionLevel)));
        Network.toPlayer(player, new Packets.SyncCategoryEditMode(isCategoryEditing(player)));
        sendRules(player);
        com.chi__you.cuisinejournal.progression.TodayDishService.sync(player);
        sendProfile(player);
        sendExtras(player, true);
        sendStatistics(player);
        sendCategoryEditor(player);
        FlavorEditHandler.sendFlavors(player);
        // Search effects can depend on rule switches, so refresh those even with a cached graph.
        DishSearchSync.send(player);
        SchemeHandler.sendSchemes(player);
        ServerSettingsHandler.send(player);
    }

    /** Personal records, streaks, today's count, title and the full achievement list. */
    public static void sendStatistics(ServerPlayer player) {
        JournalFeatureHandler.send(player);
        ProfileManager profiles = ProfileManager.get();
        if (profiles == null) return;
        CulinaryProfile profile = profiles.of(player);
        ServerConfig config = ConfigManager.server();
        var records = profile.records();
        var streaks = profile.streaks();
        long day = com.chi__you.cuisinejournal.culinary.CulinaryClock.day(player.server);

        List<Packets.SyncStatistics.Achievement> achievements = new ArrayList<>();
        for (com.chi__you.cuisinejournal.milestone.Milestone milestone
                : com.chi__you.cuisinejournal.milestone.MilestoneTracker.all()) {
            if(milestone.method()!=null&&!CulinaryRegistries.current().methods().contains(CookingMethod.of(milestone.method())))continue;
            long current;
            try { current = milestone.valueFor(profile, config.progression.toCurve(), config.mastery.toCurve()); }
            catch (RuntimeException failure) { current = 0L; }
            achievements.add(new Packets.SyncStatistics.Achievement(milestone.id(), false,
                    profile.milestones().contains(milestone.id()), milestone.typeKey(), milestone.threshold(),
                    null, Math.max(0L, current),
                    milestone.badge()));
        }
        // Badges: every badge a challenge or milestone can award, plus any already earned.
        java.util.Map<ResourceLocation, String> badgeSources = new LinkedHashMap<>();
        for (ChallengeDefinition definition : ChallengeRegistry.current().all()) {
            if (definition.rewards() != null && definition.rewards().badge() != null)
                badgeSources.putIfAbsent(definition.rewards().badge(), definition.titleKey());
        }
        for (com.chi__you.cuisinejournal.milestone.Milestone milestone
                : com.chi__you.cuisinejournal.milestone.MilestoneTracker.all()) {
            if (milestone.badge() != null) badgeSources.putIfAbsent(milestone.badge(), milestone.translationKey());
        }
        for (ResourceLocation earned : profile.badges()) badgeSources.putIfAbsent(earned, "");
        for(var badge:CulinaryBadge.all())badgeSources.putIfAbsent(badge.id(),"");
        badgeSources.forEach((badge, source) -> achievements.add(new Packets.SyncStatistics.Achievement(
                badge, true, profile.badges().contains(badge), source, 0L,CulinaryBadge.icon(badge),0L,null)));

        // The chosen title, or the highest unlocked one.
        ResourceLocation title = profile.selectedBadge();
        if(title!=null&&!profile.badges().contains(title))title=null;
        Network.toPlayer(player, new Packets.SyncStatistics(
                day, streaks.mealsOn(day),
                profile.journalIngredientUses(), profile.stats().feastsPrepared(),
                records.mostMealsInOneDay(), records.mostMealsDay(),
                records.mostUniqueMealsInOneDay(), records.mostUniqueMealsDay(),
                records.fastestMasteredMeal(), records.fastestMasteryDays(),
                records.firstMasteredMeal(), records.firstMasteredDay(),
                records.mostFeastPortionsServedInOneDay(),
                streaks.discoveryStreak(), records.longestCookingStreak(),
                records.longestVarietyStreak(), records.longestDiscoveryStreak(),
                title, achievements));
    }

    private static final String EDIT_MODE_KEY = "cuisinejournal:category_edit_mode";
    private static boolean isCategoryEditing(ServerPlayer player) {
        return player.getPersistentData().getBoolean(EDIT_MODE_KEY);
    }
    private static void applyCategoryEditMode(ServerPlayer player, boolean enabled) {
        if (!player.hasPermissions(ConfigManager.server().multiplayer.adminPermissionLevel)) {
            Network.toPlayer(player, new Packets.SyncCategoryEditMode(false));
            return;
        }
        player.getPersistentData().putBoolean(EDIT_MODE_KEY, enabled);
        Network.toPlayer(player, new Packets.SyncCategoryEditMode(enabled));
    }

    private static void sendCategoryEditor(ServerPlayer player) {
        List<Packets.CategoryMetadata> metadata = GlobalCategoryStore.metadata().stream()
                .map(value -> new Packets.CategoryMetadata(value.id(), value.parent(), value.titleKey(),
                        value.literalTitle(), value.icon(), value.source(), value.protectedEntry(), value.hidden(),
                        value.originalTitleKey(), value.originalLiteralTitle(), value.originalIcon())).toList();
        Network.toPlayer(player, new Packets.SyncCategoryEditor(metadata,
                GlobalCategoryStore.visibleManualItems().stream().map(ResourceLocation::tryParse)
                        .filter(java.util.Objects::nonNull).sorted().toList(), EditVersions.snapshot()));
    }

    /**
     * Challenges, collections, the ingredient journal and method statistics.
     *
     * <p>All of these mix definitions with the player's own progress, so they
     * are computed here and sent as finished numbers rather than shipping the
     * definitions and letting the client work them out.</p>
     */
    public static void sendExtras(ServerPlayer player) { sendExtras(player, false); }

    private static void sendExtras(ServerPlayer player, boolean force) {
        ProfileManager profiles = ProfileManager.get();
        if (profiles == null) {
            return;
        }
        CulinaryProfile profile = profiles.of(player);
        ServerConfig config = ConfigManager.server();
        MasteryCurve masteryCurve = config.mastery.toCurve();
        CulinaryRegistry registry = CulinaryRegistries.current();
        scanKnownIngredients(player, profile, registry);

        List<Packets.ChallengeView> challenges = new ArrayList<>();
        com.chi__you.cuisinejournal.challenge.ChallengeManager.prepare(player,profile);
        for (ChallengeDefinition definition : ChallengeRegistry.current().all()) {
            if(!config.challenges.enabled||definition.scope()==ChallengeDefinition.Scope.DAILY&&!config.challenges.dailyEnabled)continue;
            if(definition.scope()==ChallengeDefinition.Scope.DAILY&&definition.objectives().stream().anyMatch(objective->
                    objective.type()==com.chi__you.cuisinejournal.challenge.ChallengeObjective.Type.COOK_CATEGORY
                    &&(!config.mastery.enabled||config.mastery.perDish())))continue;
            ChallengeState state = profile.peekChallenge(definition.id());
            boolean completed = state != null && state.isCompleted();
            boolean locked=!com.chi__you.cuisinejournal.challenge.ChallengeManager.isAvailable(definition,profile);
            if (definition.hidden() && locked && !completed) {
                continue;
            }
            challenges.add(new Packets.ChallengeView(definition.id(), definition.titleKey(),
                    definition.descriptionKey(), completed,
                    state == null ? 0 : state.progress(0),
                    definition.objectives().get(0).amount(),definition.scope().name(),definition.manual(),state!=null&&state.started(),
                    false,locked,-1,
                    java.util.stream.IntStream.range(0,definition.objectives().size()).mapToObj(i->{var objective=definition.objectives().get(i);
                        return new Packets.ChallengeView.ObjectiveView(objective.type().name(),objective.value(),state==null?0:state.progress(i),objective.amount(),objective.uniqueOnly());}).toList(),
                    (int)Math.min(Integer.MAX_VALUE,com.chi__you.cuisinejournal.challenge.ChallengeRewards.cookingXp(definition.rewards(),config)),
                    definition.rewards().badge(),definition.rewards().items()));
        }

        var directory = JournalSyncServer.directory(registry, () -> {
        // The journal's category page is driven by the actual dish categories,
        // rather than Meal Master's older themed/per-mod collections. A
        // category only appears when at least one tracked dish belongs to it.
        List<Packets.CollectionView> collections = new ArrayList<>();

        // Foods that no datapack/manual category claims fall back to one category per source mod.
        Map<String, List<MealEntry>> foodsByMod = new HashMap<>();
        for (MealEntry entry : registry.entries()) {
            if (usesModFallback(entry)) {
                foodsByMod.computeIfAbsent(entry.sourceModId(), k -> new ArrayList<>()).add(entry);
            }
        }

        // Configured (datapack + in-game) categories first; the runtime fallbacks are replaced by mod categories.
        List<ResourceLocation> categoryIds = new ArrayList<>(registry.categories());
        categoryIds.removeIf(id -> id.equals(FoodCategory.OTHER_FOOD_ID) || id.equals(FoodCategory.UNCATEGORISED_ID));
        categoryIds.sort(ResourceLocation::compareTo);
        for (ResourceLocation categoryId : categoryIds) {
            List<MealEntry> members = registry.byCategory(categoryId);
            FoodCategory category = members.isEmpty()
                    ? registry.categoryDefinition(categoryId)
                    : members.get(0).category();
            if (category == null) {
                continue;
            }
            int discovered = 0;
            int mastered = 0;
            for (MealEntry member : members) {
                if (profile.isDiscovered(member.target())) {
                    discovered++;
                }
                if (profile.rankOf(member.target(), masteryCurve).isMastered()) {
                    mastered++;
                }
            }
            ResourceLocation icon = category.icon();
            if (icon == null && !members.isEmpty()) {
                icon = members.get(0).target();
            }
            Map<ResourceLocation, Packets.SubcategoryView> subcategoryViews = new LinkedHashMap<>();
            if (category.subcategories().isEmpty() && !GlobalCategoryStore.hasCustomRoot(categoryId.toString())) {
                FoodCategory.Subcategory implicit = category.subcategoryOf(ItemStack.EMPTY);
                subcategoryViews.put(implicit.id(), new Packets.SubcategoryView(implicit.id(),
                        implicit.titleKey(), implicit.literalTitle(), implicit.icon()));
            }
            for (FoodCategory.Subcategory subcategory : category.subcategories()) {
                subcategoryViews.putIfAbsent(subcategory.id(), new Packets.SubcategoryView(
                        subcategory.id(), subcategory.titleKey(), subcategory.literalTitle(),
                        subcategory.icon()));
            }
            for (MealEntry member : members) {
                FoodCategory.Subcategory subcategory = category.subcategoryOf(new ItemStack(member.item()));
                subcategoryViews.putIfAbsent(subcategory.id(), new Packets.SubcategoryView(
                        subcategory.id(), subcategory.titleKey(), subcategory.literalTitle(),
                        subcategory.icon() == null ? member.target() : subcategory.icon()));
            }
            List<Packets.SubcategoryView> sortedSubcategories = new ArrayList<>(subcategoryViews.values());
            sortedSubcategories.sort(Comparator.comparing(Packets.SubcategoryView::id));
            collections.add(new Packets.CollectionView(categoryId,
                    category.translationKey(), category.literalTitle(), icon,
                    members.size(), discovered, mastered, sortedSubcategories, false));
        }

        // Mod fallback categories, after the configured ones, ordered by display name.
        List<String> modIds = new ArrayList<>(foodsByMod.keySet());
        modIds.sort(Comparator.comparing((String id) -> modName(id).toLowerCase(java.util.Locale.ROOT))
                .thenComparing(id -> id));
        for (String modId : modIds) {
            List<MealEntry> modFoods = foodsByMod.get(modId);
            if (modFoods.isEmpty()) continue;
            ResourceLocation modCategoryId = modCategoryId(modId);
            int discovered = 0;
            int mastered = 0;
            for (MealEntry entry : modFoods) {
                if (profile.isDiscovered(entry.target())) discovered++;
                if (profile.rankOf(entry.target(), masteryCurve).isMastered()) mastered++;
            }
            ResourceLocation icon = modFoods.get(0).target();
            String name = modName(modId);
            // A mod category has exactly one implicit subcategory sharing its id and name.
            Packets.SubcategoryView modSub = new Packets.SubcategoryView(modCategoryId, name, true, icon);
            collections.add(new Packets.CollectionView(modCategoryId, name, true, icon,
                    modFoods.size(), discovered, mastered, List.of(modSub), true));
        }

        List<Packets.IngredientView> ingredients = new ArrayList<>();
        // Item entries: dishes that name the item itself. Tag matches belong to the tag entry.
        for (ResourceLocation item : registry.catalogueItems()) {
            var record = com.chi__you.cuisinejournal.recipe.IngredientProgress.collect(
                    registry.ingredientSources(item), profile::peekIngredient);
            List<ResourceLocation> meals = registry.explicitUses(item).stream()
                    .map(MealEntry::target).distinct().sorted().toList();
            int discovered = (int) meals.stream().filter(profile::isDiscovered).count();
            var flavor = com.chi__you.cuisinejournal.recipe.IngredientCatalogue.flavor(item);
            ingredients.add(new Packets.IngredientView(
                    item,
                    record.known(),
                    record.timesUsed(),
                    record.firstUsedDay(),
                    record.lastUsedDay(),
                    meals.size(),
                    discovered,
                    IngredientCatalogue.kind(item, registry),
                    IngredientCatalogue.types(item),
                    flavor != null,
                    flavor == null ? null : flavor.effect(),
                    record.methods(),
                    meals, false, List.of(), registry.tagsOf(item), null, List.of(), List.of(),
                    FlavorEditHandler.infoFor(item, false, registry)));
        }
        // Tag entries aggregate only preparation records of dishes whose recipes name the tag.
        for (CulinaryRegistry.TagEntry tag : registry.tags().values()) {
            List<ResourceLocation> meals = registry.tagUses(tag.key()).stream()
                    .map(MealEntry::target).distinct().sorted().toList();
            int discovered = (int) meals.stream().filter(profile::isDiscovered).count();
            var record = com.chi__you.cuisinejournal.recipe.TagIngredientProgress.collect(registry, tag.key(), profile::peekMeal);
            List<ResourceLocation> tagRuleIds = new ArrayList<>();
            tagRuleIds.add(tag.key()); tagRuleIds.addAll(tag.ids());
            var flavor = IngredientCatalogue.tagFlavor(tagRuleIds);
            ingredients.add(new Packets.IngredientView(
                    tag.key(), record.known(), record.timesUsed(), record.firstUsedDay(), record.lastUsedDay(), meals.size(), discovered,
                    (byte) 3, IngredientCatalogue.tagTypes(tag.members(), tagRuleIds),
                    flavor != null, flavor == null ? null : flavor.effect(), record.methods(),
                    meals, true, tag.members(), tag.ids(), tag.displayName(),
                    registry.tagSources(tag.key()).stream().map(source -> new Packets.IngredientTagSource(
                            source.tag(), source.members())).toList(),
                    registry.tagRecipeUses(tag.key()).stream().map(use -> new Packets.IngredientTagUse(
                            use.dish(), use.recipe(), use.slotIndex(), use.tag(), use.acceptedItems())).toList(),
                    FlavorEditHandler.infoFor(tag.key(), true, registry)));
        }

        return new Packets.SyncJournalExtras(List.of(), collections, ingredients, List.of(), List.of(),
                registry.listedIngredients().size());
        });

        List<Packets.MethodView> methods = new ArrayList<>();
        profile.methods().forEach((method, record) -> {
            int mastered = 0;
            ResourceLocation mostPrepared = null;
            long best = 0L;
            for (ResourceLocation meal : record.uniqueMeals()) {
                if (profile.rankOf(meal, masteryCurve).isMastered()) {
                    mastered++;
                }
                MealRecord mealRecord = profile.peekMeal(meal);
                if (mealRecord != null && mealRecord.prepared() > best) {
                    best = mealRecord.prepared();
                    mostPrepared = meal;
                }
            }
            methods.add(new Packets.MethodView(method.id(), record.preparations(),
                    record.uniqueMealCount(), mastered, mostPrepared));
        });

        // The activity feed is capped here rather than client-side: there is no
        // reason to ship history the journal will never draw.
        List<Packets.ActivityView> activity = new ArrayList<>();
        for (com.chi__you.cuisinejournal.culinary.ActivityEntry entry : profile.activity()) {
            // Enough history that the overview can fold repeats ("made X ×6") and still fill a column.
            if (activity.size() >= 40) {
                break;
            }
            activity.add(new Packets.ActivityView(entry.type().id(), entry.subject(),
                    entry.detail(), entry.day(), entry.stars(), entry.cookName(), entry.amount(),
                    entry.perfect(), entry.starCap(), entry.cookId()));
        }

        JournalSyncServer.send(player, profile, masteryCurve, directory, challenges, methods, activity, force);
    }

    private static void scanKnownIngredients(ServerPlayer player, CulinaryProfile profile,
                                             CulinaryRegistry registry) {
        java.util.Set<ResourceLocation> present = new java.util.HashSet<>();
        player.getInventory().items.forEach(stack -> present.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())));
        player.getInventory().armor.forEach(stack -> present.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())));
        player.getInventory().offhand.forEach(stack -> present.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())));
        for (ResourceLocation item : registry.catalogueItems()) {
            for (ResourceLocation original : registry.ingredientSources(item))
                if (present.contains(original)) profile.markIngredientKnown(original);
        }
    }


    public static void sendRules(ServerPlayer player) {
        ServerConfig config = ConfigManager.server();
        Network.toPlayer(player, new Packets.SyncRules(
                config.progression.levelCurveBase,
                config.progression.levelCurveLinear,
                config.progression.levelCurveQuadratic,
                config.progression.maxLevel,
                config.mastery.perDish() ? config.mastery.dishThresholds : config.mastery.thresholds,
                config.mastery.rewards.name(),
                config.multiplayer.leaderboardsEnabled,
                config.mastery.bonuses.enabled && config.mastery.enabled,
                0.0,
                config.mastery.bonuses.perfectChanceAtMaxRank,
                0.0,
                config.mastery.bonuses.cookingSpeedAtMaxRank,
                0.0F,
                0,
                config.mastery.perDish(),
                !com.chi__you.cuisinejournal.food.CuisineFoodRuntime.active(),
                0.0,
                config.mastery.bonuses.vanillaSaturationMultiplierPerStar,
                config.mastery.bonuses.vanillaMaxStarSaturation,
                config.mastery.bonuses.vanillaPerfectNutritionMultiplier,config.mastery.enabled,config.foodEffects.enabled,
                config.flavors.enabled,config.nutritionDisplay.enabled,config.mastery.bonuses.minimumPerfectChance));
    }

    /**
     * The dish catalogue, chunked. A thousand-dish modpack is several hundred
     * kilobytes, so it is split rather than sent as one oversized packet.
     */
    public static void sendCatalogue(ServerPlayer player) {
        CulinaryRegistry registry = CulinaryRegistries.current();
        int pageSize = Math.max(8, ConfigManager.server().advanced.journalPageSize);

        List<MealEntry> entries = registry.entries();
        if (entries.isEmpty()) {
            Network.toPlayer(player, new Packets.SyncCatalogue(true, true, List.of()));
            return;
        }

        for (int start = 0; start < entries.size(); start += pageSize) {
            int end = Math.min(entries.size(), start + pageSize);
            List<Packets.DishView> page = new ArrayList<>(end - start);
            for (MealEntry entry : entries.subList(start, end)) {
                ResourceLocation categoryId;
                ResourceLocation subcategoryId;
                // entry.category() already applies manual assignment > datapack rules;
                // only foods nothing claims drop into their mod's fallback category.
                if (usesModFallback(entry)) {
                    categoryId = modCategoryId(entry.sourceModId());
                    subcategoryId = categoryId;
                } else {
                    categoryId = entry.category().id();
                    subcategoryId = entry.category().subcategoryOf(new ItemStack(entry.item())).id();
                }

                CuisineFoodDefinitions.Values values = CuisineFoodDefinitions.get(entry.target());
                ResourceLocation dimension = values == null
                        ? CuisineFoodDefinitions.DEFAULT_DIMENSION : values.dimension();
                page.add(new Packets.DishView(
                        entry.target(),
                        entry.sourceModId(),
                        categoryId,
                        entry.methods().stream().map(CookingMethod::id).toList(),
                        entry.nutrition(),
                        entry.saturation(),
                        subcategoryId,
                        dimension,CulinaryRegistries.current().preparationTarget(entry.target())));
            }
            Network.toPlayer(player, new Packets.SyncCatalogue(start == 0, end == entries.size(), page));
        }
    }

    /** True when no datapack rule or manual assignment claims the food. */
    static boolean usesModFallback(MealEntry entry) {
        ResourceLocation id = entry.category().id();
        return id.equals(FoodCategory.OTHER_FOOD_ID) || id.equals(FoodCategory.UNCATEGORISED_ID);
    }

    /** Reserved id of a mod's fallback category; the "_mod" path keeps it clear of datapack ids. */
    static ResourceLocation modCategoryId(String modId) {
        return new ResourceLocation(modId, "_mod");
    }

    private static String modName(String modId) {
        String name = com.chi__you.cuisinejournal.platform.Services.PLATFORM.modDisplayName(modId);
        return name == null || name.isBlank() ? modId : name;
    }

    public static void sendProfile(ServerPlayer player) {
        JournalFeatureHandler.send(player);
        ProfileManager profiles = ProfileManager.get();
        if (profiles == null) {
            return;
        }
        CulinaryProfile profile = profiles.of(player);
        List<Packets.RecordView> records = new ArrayList<>();
        profile.meals().forEach((target, record) -> records.add(view(target, record)));

        Network.toPlayer(player, new Packets.SyncProfile(
                true,
                profile.cookingXp(),
                profile.stats().mealsPrepared(),
                profile.stats().mealsEaten(),
                profile.stats().portionsServed(),
                profile.journalIngredientsDiscoveredCount(),
                profile.streaks().cookingStreak(),
                profile.streaks().varietyStreak(),
                records,
                List.copyOf(profile.badges()),
                List.copyOf(profile.milestones())));
    }

    private static void sendRecordDelta(ServerPlayer player, CulinaryProfile profile,
                                        ResourceLocation target) {
        MealRecord record = profile.peekMeal(target);
        if (record == null) {
            return;
        }
        List<Packets.RecordView> records=new ArrayList<>();records.add(view(target,record));
        ResourceLocation source=CulinaryRegistries.current().preparationTarget(target);
        MealRecord prepared=profile.peekMeal(source);
        if(!source.equals(target)&&prepared!=null)records.add(view(source,prepared));
        Network.toPlayer(player, new Packets.SyncProfile(
                false,
                profile.cookingXp(),
                profile.stats().mealsPrepared(),
                profile.stats().mealsEaten(),
                profile.stats().portionsServed(),
                profile.journalIngredientsDiscoveredCount(),
                profile.streaks().cookingStreak(),
                profile.streaks().varietyStreak(),
                records,
                List.copyOf(profile.badges()),
                List.copyOf(profile.milestones())));
    }

    private static Packets.RecordView view(ResourceLocation target, MealRecord record) {
        return new Packets.RecordView(target, record.isDiscovered(), record.prepared(),
                record.eaten(), record.served(), record.isFavorite(),
                record.isPinned(), record.firstPreparedDay(), record.lastPreparedDay(), record.masteryPoints(), record.bonusMasteryPoints(),record.highestBrewedStars());
    }

    private static void sendDishDetail(ServerPlayer player, ResourceLocation target) {
        MealEntry entry = CulinaryRegistries.current().entry(target);
        if (entry == null) {
            return;
        }
        var preparations=CulinaryRegistries.current().preparationRecipes(target);
        List<Packets.DetailRecipe> grouped = new ArrayList<>();
        for (RecipeEntry recipe : java.util.stream.Stream.concat(preparations.stream(),
                CulinaryRegistries.current().interactionRecipes(target).stream()).toList()) {
            if (grouped.size() >= 32) break;
            grouped.add(detailRecipe(recipe));
        }
        Network.toPlayer(player, new Packets.SyncDishDetail(
                target,
                java.util.stream.Stream.concat(preparations.stream().flatMap(recipe->recipe.ingredients().stream())
                                .flatMap(slot->slot.items().stream()),
                        CulinaryRegistries.current().interactionRecipes(target).stream()
                                .flatMap(recipe->recipe.ingredients().stream()).flatMap(slot->slot.items().stream()))
                        .filter(com.chi__you.cuisinejournal.recipe.HostItemAvailability::visible).distinct().toList(),
                java.util.stream.Stream.concat(preparations.stream(),
                        CulinaryRegistries.current().interactionRecipes(target).stream())
                        .map(RecipeEntry::recipeId).distinct().toList(),
                grouped, ConfigManager.server().discovery.hideUndiscoveredDetails));
    }

    /**
     * A recipe's positions for the detail panel. A position naming one tag points at that tag's
     * journal entry; identical consecutive positions are merged into one with a count.
     */
    private static Packets.DetailRecipe detailRecipe(RecipeEntry recipe) {
        CulinaryRegistry registry = CulinaryRegistries.current();
        List<Packets.DetailSlot> slots = new ArrayList<>();
        for (RecipeEntry.IngredientSlot slot : recipe.ingredients()) {
            if (slot.items().isEmpty()) continue;
            ResourceLocation tag = null;
            if (slot.explicitItems().isEmpty() && !slot.tags().isEmpty()) {
                ResourceLocation key = CulinaryRegistry.tagKey(slot.tags().get(0));
                if (registry.tags().containsKey(key)) tag = key;
            }
            List<ResourceLocation> items = slot.items().stream()
                    .filter(com.chi__you.cuisinejournal.recipe.HostItemAvailability::visible).limit(24).toList();
            if (items.isEmpty()) {
                CuisineJournalLog.LOGGER.warn("Recipe {} has no visible candidates for ingredient {}; omitting detail slot",
                        recipe.recipeId(), slot.items());
                continue;
            }
            if (!slots.isEmpty()) {
                Packets.DetailSlot last = slots.get(slots.size() - 1);
                if (last.items().equals(items) && java.util.Objects.equals(last.tag(), tag)) {
                    slots.set(slots.size() - 1, new Packets.DetailSlot(items, tag, last.count() + 1));
                    continue;
                }
            }
            if (slots.size() >= 24) break;
            slots.add(new Packets.DetailSlot(items, tag, 1));
        }
        return new Packets.DetailRecipe(recipe.recipeId(), recipe.method() == null ? null : recipe.method().id(), slots);
    }

    // ------------------------------------------------------------- incoming

    private static void applyCategoryEdit(ServerPlayer player, Packets.CategoryEditRequest request) {
        boolean allowed = player.hasPermissions(ConfigManager.server().multiplayer.adminPermissionLevel);
        if (!allowed) {
            editResult(player, request, false, "cuisinejournal.edit.failed.permission", List.of());
            return;
        }
        if (!isCategoryEditing(player)) {
            editResult(player, request, false, "cuisinejournal.edit.failed.mode", List.of());
            return;
        }
        var lease = com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.begin(
                com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.Kind.EDIT);
        if (lease == null) {
            editResult(player, request, false, "cuisinejournal.edit.failed.busy", List.of());
            return;
        }
        try {
        if (!EditVersions.matches(request.expectedVersions(), CategoryEditVersions.keys(request))) {
            sendCategoryEditor(player);
            editResult(player, request, false, "cuisinejournal.edit.failed.conflict", List.of());
            return;
        }
        String error = validateCategoryEdit(request);
        if (error != null) { editResult(player, request, false, error, List.of()); return; }
        boolean success;
        var previousCategories = com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.checkpoint(
                com.chi__you.cuisinejournal.platform.Services.PLATFORM.configDirectory());
        try {
            success = switch (request.action()) {
                case CREATE_ROOT -> GlobalCategoryStore.createRoot(request.targetId().toString(), request.name(),
                        request.icon() == null ? null : request.icon().toString());
                case CREATE_SUB -> GlobalCategoryStore.createSubcategory(request.targetId().toString(),
                        request.parent().toString(), request.name(),
                        request.icon() == null ? null : request.icon().toString());
                case EDIT_ROOT -> GlobalCategoryStore.editRoot(request.targetId().toString(), request.name(),
                        request.icon() == null ? null : request.icon().toString());
                case EDIT_SUB -> GlobalCategoryStore.editSubcategory(request.targetId().toString(), request.name(),
                        request.icon() == null ? null : request.icon().toString(),
                        request.parent() == null ? null : request.parent().toString());
                case MOVE_SUB -> GlobalCategoryStore.moveSubcategory(request.targetId().toString(),
                        request.parent().toString());
                // Datapack entries are blocked; in-game entries are deleted.
                // The client's source flag can be stale after a reload, so
                // try the requested action and fall back only if it returns false.
                case DELETE_ROOT -> {
                    String rid = request.targetId().toString();
                    yield GlobalCategoryStore.deleteRoot(rid) || GlobalCategoryStore.blockRoot(rid);
                }
                case BLOCK_ROOT -> {
                    String rid = request.targetId().toString();
                    yield GlobalCategoryStore.blockRoot(rid) || GlobalCategoryStore.deleteRoot(rid);
                }
                case DELETE_SUB -> {
                    String sid = request.targetId().toString();
                    yield GlobalCategoryStore.deleteSubcategory(sid) || GlobalCategoryStore.blockSubcategory(sid);
                }
                case BLOCK_SUB -> {
                    String sid = request.targetId().toString();
                    yield GlobalCategoryStore.blockSubcategory(sid) || GlobalCategoryStore.deleteSubcategory(sid);
                }
                case RESTORE_ROOT -> GlobalCategoryStore.restoreRoot(request.targetId().toString());
                case RESTORE_SUB -> GlobalCategoryStore.restoreSubcategory(request.targetId().toString());
                case RESET_ROOT -> GlobalCategoryStore.resetRootAppearance(request.targetId().toString());
                case RESET_SUB -> GlobalCategoryStore.resetSubcategoryAppearance(request.targetId().toString());
                case ASSIGN_FOOD -> request.items().size() == 1
                        && GlobalCategoryStore.assignFood(request.items().get(0).toString(), request.targetId().toString());
                case ASSIGN_FOODS -> GlobalCategoryStore.assignFoods(
                        request.items().stream().map(ResourceLocation::toString).toList(),
                        request.targetId().toString());
                case UNASSIGN_FOOD -> GlobalCategoryStore.returnToModFallback(
                        request.items().stream().map(ResourceLocation::toString).toList());
            };
        } catch (RuntimeException failure) {
            com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.rollback(previousCategories,failure);
            success = false;
        }
        if (success) {
            try {
                CuisineJournalServer.rebuildCulinaryRegistry();
                EditVersions.snapshot();
            } catch (RuntimeException failure) {
                CuisineJournalLog.LOGGER.error("Category reload failed; restoring previous layer",failure);
                com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.rollback(previousCategories,failure);
                success=false;
            }
        }
        String key = switch (request.action()) {
            case CREATE_ROOT -> "created_root"; case CREATE_SUB -> "created_sub";
            case DELETE_ROOT, DELETE_SUB -> "removed"; case BLOCK_ROOT, BLOCK_SUB -> "blocked";
            case RESTORE_ROOT, RESTORE_SUB, RESET_ROOT, RESET_SUB -> "restored";
            case ASSIGN_FOOD, ASSIGN_FOODS -> "assigned";
            case UNASSIGN_FOOD -> "unassigned";
            default -> "saved";
        };
        List<String> args = key.equals("saved") ? List.of() : List.of(key.equals("assigned") || key.equals("unassigned")
                ? Integer.toString(request.items().size()) : request.name() == null ? request.targetId().toString() : request.name());
        editResult(player, request, success, success ? "cuisinejournal.edit.toast." + key
                : "cuisinejournal.edit.failed.persistence", success ? args : List.of());
        } finally {
            lease.close();
        }
    }

    private static void editResult(ServerPlayer player, Packets.CategoryEditRequest request,
                                   boolean success, String key, List<String> args) {
        Network.toPlayer(player, new Packets.CategoryEditResult(request.requestId(), success, key, args));
    }

    private static String validateCategoryEdit(Packets.CategoryEditRequest request) {
        if (request.action() == Packets.CategoryEditRequest.Action.UNASSIGN_FOOD) {
            // Returning foods to their mod fallback has no target category.
            if (request.items().isEmpty() || request.items().size() > 4096
                    || request.items().stream().anyMatch(item -> !CulinaryRegistries.current().isTracked(item)))
                return "cuisinejournal.edit.failed.invalid";
            return null;
        }
        if (request.targetId() == null) return "cuisinejournal.edit.failed.invalid";
        var entries = GlobalCategoryStore.metadata();
        var target = entries.stream().filter(value -> value.id().equals(request.targetId()))
                .findFirst().orElse(null);
        var parent = entries.stream().filter(value -> value.id().equals(request.parent()) && value.parent() == null
                && !value.hidden()).findFirst().orElse(null);
        boolean creating = request.action() == Packets.CategoryEditRequest.Action.CREATE_ROOT
                || request.action() == Packets.CategoryEditRequest.Action.CREATE_SUB;
        if (creating) {
            boolean rootCreate = request.action() == Packets.CategoryEditRequest.Action.CREATE_ROOT;
            boolean occupied = entries.stream().anyMatch(value -> !value.hidden()
                    && value.id().equals(request.targetId())
                    && (value.parent() == null) == rootCreate);
            if (occupied) return "cuisinejournal.edit.failed.id";
        }
        boolean assigning = request.action() == Packets.CategoryEditRequest.Action.ASSIGN_FOOD
                || request.action() == Packets.CategoryEditRequest.Action.ASSIGN_FOODS;
        // "Other food" only exists as the runtime fallback, so it has no metadata entry.
        boolean otherFood = assigning && com.chi__you.cuisinejournal.recipe.FoodCategory.OTHER_FOOD_ID.equals(request.targetId());
        if (!creating && target == null && !otherFood) return "cuisinejournal.edit.failed.missing";
        if (request.icon() != null && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(request.icon()))
            return "cuisinejournal.edit.failed.invalid";
        boolean form = creating || request.action() == Packets.CategoryEditRequest.Action.EDIT_ROOT
                || request.action() == Packets.CategoryEditRequest.Action.EDIT_SUB;
        if (form && (request.name() == null || request.name().isBlank()
                || request.name().codePointCount(0, request.name().length()) > 24)) return "cuisinejournal.edit.failed.invalid";
        if (target != null && target.hidden() && request.action() != Packets.CategoryEditRequest.Action.RESTORE_ROOT
                && request.action() != Packets.CategoryEditRequest.Action.RESTORE_SUB && !creating)
            return "cuisinejournal.edit.failed.missing";
        boolean destructive = switch (request.action()) {
            case DELETE_ROOT, DELETE_SUB, BLOCK_ROOT, BLOCK_SUB, MOVE_SUB -> true;
            default -> false;
        };
        if (target != null && target.protectedEntry() && destructive) return "cuisinejournal.edit.failed.protected";
        if (request.action() == Packets.CategoryEditRequest.Action.CREATE_SUB
                || request.action() == Packets.CategoryEditRequest.Action.MOVE_SUB
                || request.action() == Packets.CategoryEditRequest.Action.EDIT_SUB && request.parent() != null) {
            if (parent == null) return "cuisinejournal.edit.failed.missing";
            if (target != null && target.protectedEntry() && !java.util.Objects.equals(target.parent(), request.parent()))
                return "cuisinejournal.edit.failed.protected";
        }
        if (request.action() == Packets.CategoryEditRequest.Action.RESTORE_SUB && target != null
                && entries.stream().noneMatch(value -> value.id().equals(target.parent()) && !value.hidden()))
            return "cuisinejournal.edit.failed.missing";
        if (request.action() == Packets.CategoryEditRequest.Action.ASSIGN_FOOD
                || request.action() == Packets.CategoryEditRequest.Action.ASSIGN_FOODS) {
            if (request.items().isEmpty() || request.items().size() > 4096
                    || request.items().stream().anyMatch(item -> !CulinaryRegistries.current().isTracked(item)))
                return "cuisinejournal.edit.failed.invalid";
        }
        return null;
    }

    private static void applyPreference(ServerPlayer player, Packets.SetPreference preference) {
        ProfileManager profiles = ProfileManager.get();
        if (profiles == null || preference.target() == null && preference.kind()!=Packets.SetPreference.Kind.BADGE) {
            return;
        }
        CulinaryProfile profile = profiles.of(player);

        switch (preference.kind()) {
            case FAVORITE -> {
                // A client may not favourite something this server cannot make.
                if (!CulinaryRegistries.current().isTracked(preference.target())) {
                    return;
                }
                profile.toggleFavorite(preference.target());
                sendRecordDelta(player, profile, preference.target());
            }
            case PIN -> {
                if (!CulinaryRegistries.current().isTracked(preference.target())) {
                    return;
                }
                if(profile.recipeTracks().containsKey(preference.target()))JournalFeatureHandler.track(player,new JournalFeaturePackets.Track(preference.target(),null,0));
                else{var recipes=JournalFeatureHandler.recipes(preference.target());if(!recipes.isEmpty())JournalFeatureHandler.track(player,
                        new JournalFeaturePackets.Track(preference.target(),recipes.get(0).recipeId(),1));}
            }
            case SIGNATURE -> {
                JournalFeatureHandler.signature(player,preference.target());
            }
            case BADGE -> {
                if(profile.selectBadge(preference.target()))sendStatistics(player);
            }
        }
    }
}
