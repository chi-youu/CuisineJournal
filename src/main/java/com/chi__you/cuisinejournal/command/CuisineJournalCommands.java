package com.chi__you.cuisinejournal.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.chi__you.cuisinejournal.Constants;
import com.chi__you.cuisinejournal.CuisineJournalServer;
import com.chi__you.cuisinejournal.challenge.ChallengeDefinition;
import com.chi__you.cuisinejournal.challenge.ChallengeRegistry;
import com.chi__you.cuisinejournal.challenge.ChallengeState;
import com.chi__you.cuisinejournal.challenge.DailyRotation;
import com.chi__you.cuisinejournal.compat.CompatibilityReport;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.culinary.MealRecord;
import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.multiplayer.Leaderboards;
import com.chi__you.cuisinejournal.network.Network;
import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.chi__you.cuisinejournal.recipe.CulinaryAudit;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistry;
import com.chi__you.cuisinejournal.recipe.MealEntry;
import com.chi__you.cuisinejournal.recipe.GlobalCategoryStore;
import com.chi__you.cuisinejournal.recipe.FoodCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.CommandBuildContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The player-facing command tree.
 *
 * <p>Registered under {@code /cuisinejournal}, with {@code /cj} as its alias.
 * Administrative subcommands live in
 * {@link AdminCommands} and are permission-gated there.</p>
 */
public final class CuisineJournalCommands {

    private CuisineJournalCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(Constants.COMMAND)
                .executes(source -> openJournal(source.getSource()))
                .then(Commands.literal("open")
                        .executes(source -> openJournal(source.getSource())))
                .then(Commands.literal("stats")
                        .executes(source -> stats(source.getSource())))
                .then(Commands.literal("skills")
                        .executes(ctx -> com.chi__you.cuisinejournal.skills.SkillsIntegration.open(ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal("洗点消耗5级原版经验，重置当前模式技能树；Valheim模式还会清空当前已吃食物。确认执行 /cj skills reset confirm"),false);
                                    return 1;
                                })
                                .then(Commands.literal("confirm").executes(ctx -> com.chi__you.cuisinejournal.skills.SkillsIntegration.reset(ctx.getSource().getPlayerOrException())))))
                .then(Commands.literal("help")
                        .executes(source -> help(source.getSource(), dispatcher)))
                .then(Commands.literal("recipe")
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(source -> recipe(source.getSource(),
                                        ItemArgument.getItem(source, "item").createItemStack(1, false)))))
                .then(Commands.literal("challenges")
                        .executes(source -> challenges(source.getSource())))
                .then(Commands.literal("badges")
                        .executes(source -> badges(source.getSource()))
                        .then(Commands.literal("clear").executes(ctx->{var player=ctx.getSource().getPlayerOrException();
                            ProfileManager.get().of(player).selectBadge(null);com.chi__you.cuisinejournal.network.ServerPacketHandler.sendStatistics(player);return 1;}))
                        .then(Commands.literal("wear").then(Commands.argument("badge",ResourceLocationArgument.id())
                            .executes(ctx->{var player=ctx.getSource().getPlayerOrException();
                                boolean changed=ProfileManager.get().of(player).selectBadge(ResourceLocationArgument.getId(ctx,"badge"));
                                com.chi__you.cuisinejournal.network.ServerPacketHandler.sendStatistics(player);return changed?1:0;}))))
                .then(Commands.literal("leaderboard")
                        .executes(source -> leaderboard(source.getSource())))
                .then(Commands.literal("compat")
                        .executes(source -> compatibility(source.getSource())))
                .then(Commands.literal("today")
                        .executes(source -> today(source.getSource())))
                .then(Commands.literal("export")
                        .executes(source -> export(source.getSource())))
                .then(Commands.literal("audit")
                        .requires(source -> source.hasPermission(adminLevel()))
                        .executes(source -> audit(source.getSource()))
                        .then(Commands.literal("ingredients")
                                .executes(source -> auditIngredients(source.getSource())))
                        .then(Commands.literal("workstations")
                                .executes(source -> auditWorkstations(source.getSource())))
                        .then(Commands.literal("recipe_workstations")
                                .executes(source -> auditRecipeWorkstations(source.getSource()))))
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(adminLevel()))
                        .executes(source -> debug(source.getSource())))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(adminLevel()))
                        .executes(source -> reload(source.getSource())))
                .then(categoryCommands())
                .then(AdminCommands.build(context));

        dispatcher.register(root);
        dispatcher.register(Commands.literal(Constants.COMMAND_ALIAS_SHORT)
                .executes(source -> openJournal(source.getSource()))
                .redirect(dispatcher.getRoot().getChild(Constants.COMMAND)));
    }

    private static int help(CommandSourceStack source, CommandDispatcher<CommandSourceStack> dispatcher) {
        source.sendSuccess(() -> Component.literal("/" + Constants.COMMAND + " (/" + Constants.COMMAND_ALIAS_SHORT + ")"), false);
        for (var child : dispatcher.getRoot().getChild(Constants.COMMAND).getChildren()) {
            if (child.canUse(source)) source.sendSuccess(() -> Component.translatable(
                    "cuisinejournal.command.help." + child.getName(), "/" + Constants.COMMAND + " " + child.getName()), false);
        }
        return 1;
    }

    static int adminLevel() {
        return ConfigManager.server().multiplayer.adminPermissionLevel;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> categoryCommands() {
        return Commands.literal("category").requires(source -> source.hasPermission(adminLevel()))
                .then(Commands.literal("edit").executes(source -> categoryEdit(source.getSource())))
                .then(Commands.literal("list").executes(source -> categoryList(source.getSource())))
                .then(Commands.literal("create_root")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(source -> categoryCreateRoot(source.getSource(),
                                                ResourceLocationArgument.getId(source, "id").toString(),
                                                StringArgumentType.getString(source, "name"))))))
                .then(Commands.literal("create_sub")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .then(Commands.argument("parent", ResourceLocationArgument.id())
                                        .suggests((source, builder) -> suggestIds(builder,
                                                effectiveRootIds()))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(source -> categoryCreateSub(source.getSource(),
                                                        ResourceLocationArgument.getId(source, "id").toString(),
                                                        ResourceLocationArgument.getId(source, "parent").toString(),
                                                        StringArgumentType.getString(source, "name")))))))
                .then(Commands.literal("move_sub")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests((source, builder) -> suggestIds(builder,
                                        effectiveSubcategoryIds()))
                                .then(Commands.argument("parent", ResourceLocationArgument.id())
                                        .suggests((source, builder) -> suggestIds(builder,
                                                effectiveRootIds()))
                                        .executes(source -> categoryMoveSub(source.getSource(),
                                                ResourceLocationArgument.getId(source, "id").toString(),
                                                ResourceLocationArgument.getId(source, "parent").toString())))))
                .then(Commands.literal("assign")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .then(Commands.argument("subcategory", ResourceLocationArgument.id())
                                        .suggests((source, builder) -> suggestIds(builder,
                                                effectiveSubcategoryIds()))
                                        .executes(source -> categoryAssign(source.getSource(),
                                                ResourceLocationArgument.getId(source, "item").toString(),
                                                ResourceLocationArgument.getId(source, "subcategory").toString())))))
                .then(Commands.literal("unassign")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(source -> categoryUnassign(source.getSource(),
                                        ResourceLocationArgument.getId(source, "item").toString()))))
                .then(Commands.literal("delete_root")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(source -> categoryDeleteRoot(source.getSource(),
                                        ResourceLocationArgument.getId(source, "id").toString()))))
                .then(Commands.literal("delete_sub")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests((source, builder) -> suggestIds(builder,
                                        effectiveSubcategoryIds()))
                                .executes(source -> categoryDeleteSub(source.getSource(),
                                        ResourceLocationArgument.getId(source, "id").toString()))))
                .then(Commands.literal("reset")
                        .executes(source -> categoryReset(source.getSource())));
    }

    private static CompletableFuture<Suggestions> suggestIds(SuggestionsBuilder builder,
                                                              List<String> ids) {
        String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
        for (String id : ids) {
            if (id.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    /**
     * Suggestions come from the same effective registry used by the journal.
     * This includes datapack definitions and global edits, while omitted
     * categories and subcategories have already been removed by the merge.
     */
    private static List<String> effectiveRootIds() {
        return CulinaryRegistries.current().categories().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
    }

    private static List<String> effectiveSubcategoryIds() {
        CulinaryRegistry registry = CulinaryRegistries.current();
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        registry.categories().stream().sorted().forEach(categoryId -> {
            FoodCategory definition = registry.categoryDefinition(categoryId);
            if (definition == null) return;
            if (definition.subcategories().isEmpty()) {
                // Legacy categories use the root id as their implicit subcategory.
                if (!registry.byCategory(categoryId).isEmpty()) ids.add(categoryId.toString());
            } else {
                definition.subcategories().stream()
                        .map(FoodCategory.Subcategory::id)
                        .map(ResourceLocation::toString)
                        .sorted()
                        .forEach(ids::add);
            }
        });
        return ids.stream().sorted().toList();
    }

    private static int categoryEdit(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = GlobalCategoryStore.toggleEditing(player.getUUID());
        source.sendSuccess(() -> Component.literal(enabled ? "Category editing enabled" : "Category editing disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        return 1;
    }

    private static int categoryList(CommandSourceStack source) {
        CulinaryRegistry registry = CulinaryRegistries.current();
        source.sendSuccess(() -> Component.literal("Effective categories; global override revision "
                + GlobalCategoryStore.revision()).withStyle(ChatFormatting.GOLD), false);
        if (registry.categories().isEmpty()) {
            source.sendFailure(Component.literal(
                    "No tracked food categories are active. Check the recipe/data reload and server log."));
        } else {
            for (ResourceLocation id : registry.categories()) {
                int count = registry.byCategory(id).size();
                source.sendSuccess(() -> Component.literal("  " + id + "  (" + count + " dishes)")
                        .withStyle(ChatFormatting.WHITE), false);
            }
        }
        return 1;
    }

    private static int categoryCreateRoot(CommandSourceStack source, String id, String name) {
        return categoryMutation(source, ()->GlobalCategoryStore.createRoot(id, name, null),
                "Created root category " + id);
    }

    private static int categoryCreateSub(CommandSourceStack source, String id, String parent, String name) {
        return categoryMutation(source, ()->GlobalCategoryStore.createSubcategory(id, parent, name, null),
                "Created subcategory " + id + " under " + parent);
    }

    private static int categoryMoveSub(CommandSourceStack source, String id, String parent) {
        return categoryMutation(source, ()->GlobalCategoryStore.moveSubcategory(id, parent),
                "Moved subcategory " + id + " under " + parent);
    }

    private static int categoryAssign(CommandSourceStack source, String item, String subcategory) {
        return categoryMutation(source, ()->GlobalCategoryStore.assignFood(item, subcategory),
                "Assigned " + item + " to " + subcategory);
    }

    private static int categoryUnassign(CommandSourceStack source, String item) {
        return categoryMutation(source, ()->GlobalCategoryStore.unassignFood(item),
                "Removed the global assignment for " + item);
    }

    private static int categoryDeleteRoot(CommandSourceStack source, String id) {
        return categoryMutation(source, ()->GlobalCategoryStore.deleteRoot(id), "Deleted root category " + id);
    }

    private static int categoryDeleteSub(CommandSourceStack source, String id) {
        return categoryMutation(source, ()->GlobalCategoryStore.deleteSubcategory(id), "Deleted subcategory " + id);
    }

    private static int categoryReset(CommandSourceStack source) {
        return categoryMutation(source,()->GlobalCategoryStore.replaceLocal(new com.google.gson.JsonObject()),
                "Global category overrides reset");
    }

    private static int categoryMutation(CommandSourceStack source, java.util.function.BooleanSupplier mutation, String message) {
        var lease = com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.begin(
                com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.Kind.EDIT);
        if (lease == null) {
            source.sendFailure(Component.literal("Category edit is busy; try again after reload"));
            return 0;
        }
        try {
            com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.apply(
                    com.chi__you.cuisinejournal.platform.Services.PLATFORM.configDirectory(),
                    () -> { if (!mutation.getAsBoolean()) throw new IllegalArgumentException("Invalid category change"); },
                    CuisineJournalServer::rebuildCulinaryRegistry,
                    () -> source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true));
            return 1;
        } catch (RuntimeException failure) {
            com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.error("Category command reload failed",failure);
            source.sendFailure(Component.literal("Category reload failed; previous edits restored (see server log)"));
            return 0;
        } finally { lease.close(); }
    }

    // ------------------------------------------------------------- commands

    private static int openJournal(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Network.toPlayer(player, new Packets.OpenJournal());
        return 1;
    }

    private static int stats(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CulinaryProfile profile = ProfileManager.get().of(player);
        ProfileReport.lines(profile, player.getGameProfile().getName())
                .forEach(line -> source.sendSuccess(() -> line, false));
        return 1;
    }

    private static int recipe(CommandSourceStack source, ItemStack stack)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation target = BuiltInRegistries.ITEM.getKey(stack.getItem());
        MealEntry entry = CulinaryRegistries.current().entry(target);
        if (entry == null) {
            source.sendFailure(Component.translatable("cuisinejournal.command.recipe.unknown",
                    stack.getHoverName()));
            return 0;
        }
        CulinaryProfile profile = ProfileManager.get().of(player);
        ProfileReport.recipeLines(entry, profile).forEach(line -> source.sendSuccess(() -> line, false));
        return 1;
    }

    private static int challenges(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CulinaryProfile profile = ProfileManager.get().of(player);
        ChallengeRegistry registry = ChallengeRegistry.current();

        if (registry.size() == 0) {
            source.sendSuccess(() -> Component.translatable("cuisinejournal.command.challenges.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> header("cuisinejournal.command.challenges.title"), false);
        for (ChallengeDefinition definition : registry.all()) {
            if (definition.hidden() && !profile.hasCompletedChallenge(definition.id())) {
                continue;
            }
            ChallengeState state = profile.peekChallenge(definition.id());
            boolean done = state != null && state.isCompleted();
            int progress = state == null ? 0 : state.progress(0);
            int goal = definition.objectives().get(0).amount();
            source.sendSuccess(() -> Component.literal(done ? "  ✔ " : "  • ")
                    .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                    .append(Component.translatable(definition.titleKey())
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(done ? "" : "  " + progress + " / " + goal)
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    private static int badges(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CulinaryProfile profile = ProfileManager.get().of(player);
        List<ResourceLocation> unlocked = profile.badges().stream().sorted().toList();

        source.sendSuccess(() -> header("cuisinejournal.command.titles.title"), false);
        if (unlocked.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("cuisinejournal.command.titles.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        unlocked.forEach(title -> source.sendSuccess(() -> Component.literal("  ")
                .append(Component.translatable("cuisinejournal.badge."+title.getNamespace()+"."+title.getPath())
                        .withStyle(ChatFormatting.GOLD)), false));
        return 1;
    }

    private static int export(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CulinaryProfile profile = ProfileManager.get().of(player);
        // Plain text rather than a file: readable in chat, copyable, and it
        // cannot write anywhere the player did not ask for.
        ProfileReport.exportLines(profile, player.getGameProfile().getName())
                .forEach(line -> source.sendSuccess(() -> line, false));
        return 1;
    }

    private static int leaderboard(CommandSourceStack source) {
        if (!Leaderboards.enabled()) {
            source.sendFailure(Component.translatable("cuisinejournal.command.leaderboard.disabled"));
            return 0;
        }
        source.sendSuccess(() -> header("cuisinejournal.command.leaderboard.title"), false);
        for (Leaderboards.Category category : Leaderboards.Category.values()) {
            List<Leaderboards.Entry> entries = Leaderboards.top(source.getServer(), category);
            if (entries.isEmpty()) {
                continue;
            }
            source.sendSuccess(() -> Component.literal("  " + category.name())
                    .withStyle(ChatFormatting.GRAY), false);
            int rank = 1;
            for (Leaderboards.Entry entry : entries) {
                final int position = rank++;
                source.sendSuccess(() -> Component.literal(
                                "    " + position + ". " + entry.name() + " — " + entry.value())
                        .withStyle(ChatFormatting.WHITE), false);
            }
        }
        return 1;
    }

    private static int compatibility(CommandSourceStack source) {
        source.sendSuccess(() -> header("cuisinejournal.command.compat.title"), false);
        for (CompatibilityReport.Line line : CompatibilityReport.build()) {
            source.sendSuccess(() -> Component.literal("  " + line.displayName())
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("  "))
                    .append(Component.translatable(line.status().translationKey())
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(line.dishCount() > 0
                                    ? "  (" + line.dishCount() + " dishes)" : "")
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }
        return 1;
    }

    private static int today(CommandSourceStack source) {
        ResourceLocation dish = DailyRotation.recipeOfTheDay();
        if (dish == null) {
            source.sendFailure(Component.translatable("cuisinejournal.command.today.none"));
            return 0;
        }
        source.sendSuccess(() -> header("cuisinejournal.command.today.title"), false);
        MealEntry entry = CulinaryRegistries.current().entry(dish);
        source.sendSuccess(() -> Component.literal("  ")
                .append(entry == null ? Component.literal(dish.toString())
                        : Component.translatable(entry.translationKey()))
                .withStyle(ChatFormatting.WHITE), false);
        for (var challenge : DailyRotation.dailyChallenges()) {
            source.sendSuccess(() -> Component.literal("  • ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable(
                                    challenge.objectives().get(0).translationKey())
                            .append(" x" + challenge.objectives().get(0).amount()))
                    .withStyle(ChatFormatting.WHITE), false);
        }
        return 1;
    }

    private static int audit(CommandSourceStack source) {
        CulinaryRegistry registry = CulinaryRegistries.current();
        CulinaryAudit audit = registry.audit();

        source.sendSuccess(() -> header("cuisinejournal.command.audit.title"), false);
        line(source, "Detected food items", audit.detectedFoodItems());
        line(source, "Eligible recipes", audit.eligibleRecipes());
        line(source, "Ignored recipes (no edible output)", audit.ignoredRecipes());
        line(source, "Excluded by configuration", audit.excludedRecipes());
        line(source, "Failed inspection", audit.failedRecipes());
        line(source, "Source mods", audit.sourceMods().size());
        line(source, "Unclassified dishes", audit.uncategorised().size());

        for (Map.Entry<CookingMethod, Integer> entry : audit.recipesPerMethod().entrySet()) {
            source.sendSuccess(() -> Component.literal("  " + entry.getKey().id() + ": ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(entry.getValue()))
                            .withStyle(ChatFormatting.WHITE)), false);
        }
        if (!audit.uncategorised().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  Uncategorised: "
                            + audit.uncategorised().stream().limit(10)
                            .map(ResourceLocation::toString).toList())
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }
        return 1;
    }

    /**
     * Writes every ingredient-journal entry to {@code config/cuisinejournal-ingredients-audit.tsv},
     * so a pack author can see what is left after the rules and pick more exclusions.
     */
    private static int auditIngredients(CommandSourceStack source) {
        CulinaryRegistry registry = CulinaryRegistries.current();
        StringBuilder out = new StringBuilder("entry\tkind\tmod\ttypes\tnamed_by_dishes\ttags_or_members\tresult\tfinal_entry\trules\n");
        int items = 0;
        for (ResourceLocation item : registry.catalogueItems()) {
            byte kind = com.chi__you.cuisinejournal.recipe.IngredientCatalogue.kind(item, registry);
            out.append(item).append('\t').append(kind == 0 ? "dish" : kind == 1 ? "pure" : "nonfood")
                    .append('\t').append(item.getNamespace())
                    .append('\t').append(com.chi__you.cuisinejournal.recipe.IngredientCatalogue.types(item).stream()
                            .map(ResourceLocation::getPath).toList())
                    .append('\t').append(registry.explicitUses(item).size())
                    .append('\t').append(registry.tagsOf(item)).append("\tkept\t").append(item)
                    .append("\t").append(com.chi__you.cuisinejournal.recipe.IngredientCompatRules.current().sources(item, false)).append('\n');
            items++;
        }
        for (CulinaryRegistry.TagEntry tag : registry.tags().values()) {
            out.append('#').append(tag.key()).append("\ttag\t-\t-\t").append(registry.tagUses(tag.key()).size())
                    .append('\t').append(tag.members().size()).append(" members ").append(tag.ids())
                    .append("\tkept\t#").append(tag.key()).append("\t").append(tsv(tag.displayName())).append('\n');
        }
        for (var row : registry.ingredientAudit()) {
            boolean tag = row.original().startsWith("#");
            var original = net.minecraft.resources.ResourceLocation.tryParse(tag ? row.original().substring(1) : row.original());
            List<String> appliedRules = new java.util.ArrayList<>(row.rules());
            if (original != null) appliedRules.addAll(com.chi__you.cuisinejournal.recipe.IngredientCatalogueConfig.current().localSources(original, tag));
            out.append(tsv(row.original())).append("\toriginal\t-\t-\t-\t-\t")
                    .append(tsv(row.result())).append('\t').append(tsv(row.target())).append('\t')
                    .append(tsv(String.join(" | ", appliedRules))).append('\n');
        }
        java.nio.file.Path file = com.chi__you.cuisinejournal.platform.Services.PLATFORM.configDirectory()
                .resolve("cuisinejournal-ingredients-audit.tsv");
        try {
            java.nio.file.Files.writeString(file, out, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            source.sendFailure(Component.literal("Could not write " + file + ": " + failure.getMessage()));
            return 0;
        }
        int itemCount = items, tagCount = registry.tags().size();
        source.sendSuccess(() -> Component.literal("Ingredient journal: " + itemCount + " items, " + tagCount
                + " tags → " + file.getFileName()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static String tsv(String text) {
        return text == null ? "" : text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    private static int auditWorkstations(CommandSourceStack source) {
        StringBuilder out=new StringBuilder("block\tenabled\tadapter\tmethod\tcapabilities\trecipe_types\tsources\n");
        com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.all().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).forEach(entry->{
            var d=entry.getValue();out.append(d.block()).append('\t').append(d.enabled()).append('\t')
                    .append(d.adapter().id()).append('\t').append(d.method()).append('\t')
                    .append(d.capabilities().stream().map(c->c.id()).sorted().toList()).append('\t')
                    .append(d.recipeTypes()).append('\t').append(tsv(String.join(" | ",d.sources()))).append('\n');
        });
        java.nio.file.Path file=com.chi__you.cuisinejournal.platform.Services.PLATFORM.configDirectory()
                .resolve("cuisinejournal-workstations-audit.tsv");
        try{java.nio.file.Files.writeString(file,out,java.nio.charset.StandardCharsets.UTF_8);}
        catch(java.io.IOException failure){source.sendFailure(Component.literal("Could not write "+file+": "+failure.getMessage()));return 0;}
        source.sendSuccess(()->Component.literal("Workstation definitions: "+com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.all().size()
                +" -> "+file.getFileName()+"; declarations do not prove installed production support"),false);return 1;
    }

    private static int auditRecipeWorkstations(CommandSourceStack source) {
        var directory=com.chi__you.cuisinejournal.platform.Services.PLATFORM.configDirectory();
        try {
            com.chi__you.cuisinejournal.tracking.RecipeWorkstationAudit.rebuild(
                    source.getServer().getRecipeManager().getRecipes(),source.getServer().registryAccess(),
                    CulinaryRegistries.current(),directory);
            com.chi__you.cuisinejournal.tracking.RecipeWorkstationAudit.writeCatalogueOnly(
                    directory.resolve("cuisinejournal-catalogue-only-audit.tsv"),CulinaryRegistries.current());
        } catch(RuntimeException | LinkageError | java.io.IOException failure) {
            source.sendFailure(Component.literal("Could not export recipe-workstation audit: "+failure.getMessage()));return 0;
        }
        var rows=com.chi__you.cuisinejournal.tracking.RecipeWorkstationAudit.current();
        long unmapped=rows.stream().filter(row->row.status().equals("needs_workstation_mapping")).count();
        source.sendSuccess(()->Component.literal("Recipe-workstation audit: "+rows.size()+" types, "+unmapped
                +" tracked types need mappings -> cuisinejournal-recipe-workstations-audit.tsv; catalogue-only dishes -> cuisinejournal-catalogue-only-audit.tsv"),false);
        return 1;
    }

    /**
     * Reports how Cuisine Journal classifies the held item. This is the
     * fastest way to find out why an addon's food is or is not in the journal.
     */
    private static int debug(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        // Reported before the held-item checks so it is available even with an
        // empty hand - this is what tells you whether the screen you have open
        // was recognised as a cooking surface.
        source.sendSuccess(() -> header("cuisinejournal.command.debug.title"), false);
        text(source, "Open menu", player.containerMenu == null
                ? "none" : player.containerMenu.getClass().getSimpleName());
        text(source, "Tracking",
                com.chi__you.cuisinejournal.tracking.CookingContextTracker.describe(player));
        text(source, "Detected workstations",
                String.valueOf(com.chi__you.cuisinejournal.tracking.WorkstationRegistry.all().size()));
        var hit=player.pick(6.0,0.0F,false);
        if(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            var state=player.level().getBlockState(blockHit.getBlockPos());
            var block=BuiltInRegistries.BLOCK.getKey(state.getBlock());
            var definition=com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.get(block);
            var be=player.level().getBlockEntity(blockHit.getBlockPos());
            var adapter=be==null?com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.Adapter.AUTO
                    :com.chi__you.cuisinejournal.tracking.ProductionTracker.adapterFor(be);
            text(source,"Target block",block.toString());text(source,"Detected production adapter",adapter.id());
            if(definition!=null) {
                text(source,"Declared capabilities",definition.capabilities().stream().map(c->c.id()).sorted().toList().toString());
                text(source,"Pending capabilities",com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.pending(definition,adapter).toString());
                text(source,"Workstation source",definition.sources().toString());
            }
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("cuisinejournal.command.debug.empty")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            return 1;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        MealEntry entry = CulinaryRegistries.current().entry(itemId);

        text(source, "Held item", String.valueOf(itemId));
        text(source, "Edible", String.valueOf(held.isEdible()));
        text(source, "Tracked", String.valueOf(entry != null));
        if (entry == null) {
            return 1;
        }
        text(source, "Source", entry.sourceModId());
        text(source, "Category", entry.category().id().toString());
        text(source, "Primary method", entry.primaryMethod().id().toString());
        text(source, "Detected recipes", String.valueOf(entry.recipes().size()));
        text(source, "Distinct ingredients", String.valueOf(entry.ingredients().size()));
        text(source, "Nutrition / saturation",
                entry.nutrition() + " / " + entry.saturation());

        MealRecord record = ProfileManager.get().of(player).peekMeal(itemId);
        text(source, "Your record", record == null ? "none"
                : record.prepared() + " prepared, "
                        + ProfileManager.get().of(player).masteryPointsOf(itemId)
                        + (ConfigManager.server().mastery.perDish() ? " mastery points" : " category preparations"));
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        try { CuisineJournalServer.reload(); }
        catch (RuntimeException failure) {
            com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.error("Cuisine Journal reload failed",failure);
            source.sendFailure(Component.literal("Cuisine Journal reload failed; previous runtime retained. Check the server log."));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("cuisinejournal.command.reload.done",
                CulinaryRegistries.current().size()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // --------------------------------------------------------------- output

    static Component header(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    private static void line(CommandSourceStack source, String label, int value) {
        text(source, label, String.valueOf(value));
    }

    private static void text(CommandSourceStack source, String label, String value) {
        source.sendSuccess(() -> Component.literal("  " + label + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
    }
}
