package com.chi__you.cuisinejournal.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.CulinaryClock;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.network.ServerPacketHandler;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

/**
 * Administrative subcommands.
 *
 * <p>Every one of these is gated on the configured admin permission level, and
 * the destructive ones require the scope to be named explicitly — there is no
 * bare "reset" that wipes a profile by accident.</p>
 */
public final class AdminCommands {

    private AdminCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build(net.minecraft.commands.CommandBuildContext commandContext) {
        return Commands.literal("admin")
                .requires(source -> source.hasPermission(CuisineJournalCommands.adminLevel()))
                .then(Commands.literal("quality")
                        .then(Commands.argument("stars",IntegerArgumentType.integer(0,5))
                                .executes(ctx->quality(ctx,null))
                                .then(Commands.argument("perfect",com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                        .executes(ctx->quality(ctx,com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx,"perfect"))))))
                .then(Commands.literal("sample")
                        .then(Commands.argument("item",net.minecraft.commands.arguments.item.ItemArgument.item(commandContext))
                                .then(Commands.argument("stars",IntegerArgumentType.integer(0,5))
                                        .executes(ctx->sample(ctx,1,false))
                                        .then(Commands.argument("count",IntegerArgumentType.integer(1,64))
                                                .executes(ctx->sample(ctx,IntegerArgumentType.getInteger(ctx,"count"),false))
                                                .then(Commands.argument("perfect",com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                                        .executes(ctx->sample(ctx,IntegerArgumentType.getInteger(ctx,"count"),
                                                                com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx,"perfect"))))))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(AdminCommands::inspect)))
                .then(Commands.literal("addxp")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg())
                                        .executes(AdminCommands::addXp))))
                .then(Commands.literal("setlevel")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(AdminCommands::setLevel))))
                .then(Commands.literal("discover")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("dish", StringArgumentType.string())
                                        .executes(AdminCommands::discover))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("scope", StringArgumentType.word())
                                        .executes(AdminCommands::reset))))
                .then(Commands.literal("purgeorphans")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(AdminCommands::purgeOrphans)));
    }

    private static int inspect(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        CommandSourceStack source = context.getSource();
        if (!source.hasPermission(ConfigManager.server().multiplayer.inspectPermissionLevel)) {
            source.sendFailure(Component.translatable("cuisinejournal.command.admin.no_permission"));
            return 0;
        }
        CulinaryProfile profile = ProfileManager.get().peekOffline(target.getUUID());
        ProfileReport.lines(profile, target.getGameProfile().getName())
                .forEach(line -> source.sendSuccess(() -> line, false));
        return 1;
    }
    private static int quality(CommandContext<CommandSourceStack> context,Boolean perfect)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=context.getSource().getPlayerOrException();
        var stack=player.getMainHandItem();
        if(stack.isEmpty()||!com.chi__you.cuisinejournal.food.CuisineEdibility.isEdible(stack)) {
            context.getSource().sendFailure(Component.translatable("cuisinejournal.command.quality.not_food"));return 0;
        }
        var previous=com.chi__you.cuisinejournal.culinary.MealStamp.read(stack);
        var stamp=com.chi__you.cuisinejournal.food.CuisineFoodQuality.editedStamp(previous,IntegerArgumentType.getInteger(context,"stars"),perfect);
        com.chi__you.cuisinejournal.food.CuisineFoodQuality.markQualitySample(stack,stamp.stars(),stamp.perfect(),stamp.cookId(),stamp.cookName());
        player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();player.containerMenu.broadcastChanges();
        context.getSource().sendSuccess(()->Component.translatable("cuisinejournal.command.quality.changed",stack.getHoverName(),stamp.stars(),stamp.perfect()),false);
        return 1;
    }

    private static int sample(CommandContext<CommandSourceStack> context,int count,boolean perfect)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=context.getSource().getPlayerOrException();
        var item=net.minecraft.commands.arguments.item.ItemArgument.getItem(context,"item");
        var probe=item.createItemStack(1,false);
        if(!com.chi__you.cuisinejournal.food.CuisineEdibility.isEdible(probe)) {
            context.getSource().sendFailure(Component.translatable("cuisinejournal.command.sample.not_food"));return 0;
        }
        int stars=IntegerArgumentType.getInteger(context,"stars");
        int remaining=count;
        while(remaining>0) {
            int amount=Math.min(remaining,probe.getMaxStackSize());
            var stack=item.createItemStack(amount,false);
            com.chi__you.cuisinejournal.food.CuisineFoodQuality.markQualitySample(stack,stars,perfect,player.getUUID(),player.getGameProfile().getName());
            player.getInventory().add(stack);
            if(!stack.isEmpty())player.drop(stack,false);
            remaining-=amount;
        }
        player.containerMenu.broadcastChanges();
        context.getSource().sendSuccess(()->Component.translatable("cuisinejournal.command.sample.given",count,probe.getHoverName(),stars),false);
        return count;
    }

    private static int addXp(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        long amount = LongArgumentType.getLong(context, "amount");
        ServerConfig config = ConfigManager.server();

        CulinaryProfile profile = ProfileManager.get().of(target);
        if (amount >= 0L) {
            profile.addCookingXp(amount, config.progression.toCurve());
        } else {
            profile.setCookingXp(Math.max(0L, profile.cookingXp() + amount));
        }
        finish(context.getSource(), target, profile,
                "Cooking XP is now " + profile.cookingXp());
        return 1;
    }

    private static int setLevel(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        int level = IntegerArgumentType.getInteger(context, "level");
        ServerConfig config = ConfigManager.server();
        if(level>config.progression.toCurve().maxLevel()){
            context.getSource().sendFailure(Component.literal("Maximum cooking level is "+config.progression.toCurve().maxLevel()));
            return 0;
        }

        CulinaryProfile profile = ProfileManager.get().of(target);
        profile.setCookingXp(config.progression.toCurve().totalXpForLevel(level));
        finish(context.getSource(), target, profile, "Cooking level set to " + level);
        return 1;
    }

    private static int discover(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        ResourceLocation dish = parseDish(context);
        if (dish == null) {
            return 0;
        }
        CulinaryProfile profile = ProfileManager.get().of(target);
        boolean changed = profile.meal(dish).discover(CulinaryClock.day(target.server));
        profile.markDirty();
        finish(context.getSource(), target, profile,
                changed ? dish + " marked as discovered" : dish + " was already discovered");
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        String rawScope = StringArgumentType.getString(context, "scope");
        CulinaryProfile.ResetScope scope;
        try {
            scope = CulinaryProfile.ResetScope.valueOf(rawScope.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            context.getSource().sendFailure(Component.translatable(
                    "cuisinejournal.command.admin.bad_scope",
                    List.of(CulinaryProfile.ResetScope.values()).toString()));
            return 0;
        }
        CulinaryProfile profile = ProfileManager.get().of(target);
        profile.reset(scope);
        finish(context.getSource(), target, profile, "Reset " + scope + " for "
                + target.getGameProfile().getName());
        return 1;
    }

    private static int purgeOrphans(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        CulinaryProfile profile = ProfileManager.get().of(target);
        int purged = profile.purgeOrphans(CulinaryRegistries.current().targets());
        finish(context.getSource(), target, profile,
                "Purged " + purged + " orphaned record(s)");
        return 1;
    }

    private static ResourceLocation parseDish(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "dish");
        ResourceLocation dish = ResourceLocation.tryParse(raw);
        if (dish == null || !BuiltInRegistries.ITEM.containsKey(dish)
                || !com.chi__you.cuisinejournal.recipe.CulinaryRegistries.current().isTracked(dish)) {
            context.getSource().sendFailure(Component.translatable(
                    "cuisinejournal.command.admin.unknown_dish", raw));
            return null;
        }
        return dish;
    }

    /** Persists the change immediately and re-syncs the player's client. */
    private static void finish(CommandSourceStack source, ServerPlayer target,
                               CulinaryProfile profile, String message) {
        ProfileManager.get().flush(target.getUUID());
        ServerPacketHandler.sendProfile(target);
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), true);
    }
}
