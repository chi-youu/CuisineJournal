package com.chi__you.cuisinejournal.challenge;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.ActivityEntry;
import com.chi__you.cuisinejournal.culinary.CulinaryClock;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.event.CulinaryEvents;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.MealEntry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Advances challenges in response to culinary events.
 *
 * <p>Listens rather than being called: tracking has no idea challenges exist,
 * which is what lets a datapack add objectives without any code changing.</p>
 */
public final class ChallengeManager {
    private static boolean registered;

    private ChallengeManager() {
    }

    public static synchronized void install() {
        if (registered) {
            return;
        }
        registered = true;
        CulinaryEvents.MEAL_PREPARED.register(ChallengeManager::onMealPrepared);
        CulinaryEvents.MEAL_EATEN.register(ChallengeManager::onMealEaten);
        CulinaryEvents.FOOD_EATEN.register(ChallengeManager::onFoodEaten);
        CulinaryEvents.RECIPE_DISCOVERED.register(ChallengeManager::onRecipeDiscovered);
        CulinaryEvents.MASTERY_RANK_CHANGED.register(ChallengeManager::onMasteryChanged);
        CulinaryEvents.COOKING_LEVEL_CHANGED.register(ChallengeManager::onLevelChanged);
        CulinaryEvents.PORTIONS_SERVED.register(ChallengeManager::onPortionsServed);
        CulinaryEvents.INGREDIENT_USED.register(ChallengeManager::onIngredientUsed);
    }

    // ------------------------------------------------------------- listeners

    private static void onMealPrepared(CulinaryEvents.MealPrepared event) {
        MealEntry entry = CulinaryRegistries.current().entry(event.meal());
        forEachActive(event.player(), event.profile(), (definition, state) -> {
            long objectiveDay=event.day();
            int variety=state.variety(event.meal()),daily=state.dailyVariety(event.meal(),objectiveDay);
            List<ChallengeObjective> objectives = definition.objectives();
            for (int i = 0; i < objectives.size(); i++) {
                ChallengeObjective objective = objectives.get(i);
                switch (objective.type()) {
                    case COOK_RECIPE -> {
                        if (!objective.hasValue() || event.meal().toString().equals(objective.value())) {
                            if (objective.uniqueOnly()) {
                                state.advanceUnique(i, event.meal());
                            } else {
                                state.advance(i, event.amount());
                            }
                        }
                    }
                    case COOK_CATEGORY -> {
                        if (entry != null && entry.category().id().toString().equals(objective.value())) {
                            advance(state, i, objective, event.meal(), event.amount());
                        }
                    }
                    case COOK_FROM_MOD -> {
                        if (event.meal().getNamespace().equals(objective.value())) {
                            advance(state, i, objective, event.meal(), event.amount());
                        }
                    }
                    case COOK_PERFECT -> {
                        advanceSpecialPreparation(state,i,objective,event.meal(),event.amount(),event.stamp());
                    }
                    case COOK_TODAY -> {
                        advanceSpecialPreparation(state,i,objective,event.meal(),event.amount(),event.stamp());
                    }
                    case USE_METHOD -> {
                        if (event.method().id().toString().equals(objective.value())) {
                            advance(state,i,objective,event.meal(),event.amount());
                        }
                    }
                    case PREPARE_VARIETY ->
                            state.raise(i, variety);
                    case UNIQUE_RECIPES_IN_ONE_DAY ->
                            state.raise(i, daily);
                    default -> {
                    }
                }
            }
        });
    }

    private static void onMealEaten(CulinaryEvents.MealEaten event) {
        forEachActive(event.player(), event.profile(), (definition, state) ->
                forEachObjective(definition, ChallengeObjective.Type.EAT_RECIPE, (index, objective) -> {
                    if (!objective.hasValue() || event.meal().toString().equals(objective.value())) {
                        advance(state, index, objective, event.meal(), 1);
                    }
                }));
    }

    private static void onFoodEaten(CulinaryEvents.FoodEaten event){
        forEachActive(event.player(),event.profile(),(definition,state)->
                forEachObjective(definition,ChallengeObjective.Type.EAT_FOOD,(index,objective)->state.advance(index,1)));
    }

    private static void onRecipeDiscovered(CulinaryEvents.RecipeDiscovered event) {
        forEachActive(event.player(), event.profile(), (definition, state) ->
                forEachObjective(definition, ChallengeObjective.Type.DISCOVER_RECIPE,
                        (index, objective) -> advance(state, index, objective, event.meal(), 1)));
    }

    static void advanceSpecialPreparation(ChallengeState state,int index,ChallengeObjective objective,ResourceLocation meal,
                                         int amount,com.chi__you.cuisinejournal.culinary.MealStamp stamp){
        if(amount<=0)return;
        if(objective.type()==ChallengeObjective.Type.COOK_PERFECT){
            if(stamp!=null&&stamp.perfect())state.advance(index,1);
        }else if(objective.type()==ChallengeObjective.Type.COOK_TODAY&&objective.valueAsId()!=null
                &&CulinaryRegistries.current().preparationTarget(meal).equals(CulinaryRegistries.current().preparationTarget(objective.valueAsId())))
            state.advance(index,1);
    }

    private static void onIngredientUsed(CulinaryEvents.IngredientUsed event) {
        forEachActive(event.player(), event.profile(), (definition, state) -> {
            List<ChallengeObjective> objectives = definition.objectives();
            for (int i = 0; i < objectives.size(); i++) {
                ChallengeObjective objective = objectives.get(i);
                if (objective.type() == ChallengeObjective.Type.USE_INGREDIENT
                        && event.ingredient().toString().equals(objective.value())) {
                    advance(state, i, objective, event.ingredient(), event.amount());
                } else if (objective.type() == ChallengeObjective.Type.USE_INGREDIENT_TAG
                        && matchesTag(event.ingredient(), objective.valueAsId())) {
                    advance(state, i, objective, event.ingredient(), event.amount());
                }
            }
        });
    }

    private static void onMasteryChanged(CulinaryEvents.MasteryRankChanged event) {
        if (!event.to().isMastered()) {
            return;
        }
        forEachActive(event.player(), event.profile(), (definition, state) ->
                forEachObjective(definition, ChallengeObjective.Type.MASTER_RECIPE,
                        (index, objective) -> advance(state, index, objective, event.meal(), 1)));
    }

    private static void onLevelChanged(CulinaryEvents.CookingLevelChanged event) {
        forEachActive(event.player(), event.profile(), (definition, state) ->
                forEachObjective(definition, ChallengeObjective.Type.REACH_LEVEL,
                        (index, objective) -> state.raise(index, event.to())));
    }

    private static void onPortionsServed(CulinaryEvents.PortionsServed event) {
        forEachActive(event.player(), event.profile(), (definition, state) ->
                forEachObjective(definition, ChallengeObjective.Type.SERVE_PORTIONS,
                        (index, objective) -> state.advance(index, event.portions())));
    }

    // -------------------------------------------------------------- internals

    private interface ChallengeVisitor {
        void visit(ChallengeDefinition definition, ChallengeState state);
    }

    private interface ObjectiveVisitor {
        void visit(int index, ChallengeObjective objective);
    }

    private static void advance(ChallengeState state, int index, ChallengeObjective objective,
                                ResourceLocation subject, int amount) {
        if (objective.uniqueOnly()) {
            state.advanceUnique(index, subject);
        } else {
            state.advance(index, amount);
        }
    }

    private static void forEachObjective(ChallengeDefinition definition,
                                         ChallengeObjective.Type type,
                                         ObjectiveVisitor visitor) {
        List<ChallengeObjective> objectives = definition.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (objectives.get(i).type() == type) {
                visitor.visit(i, objectives.get(i));
            }
        }
    }

    private static void forEachActive(ServerPlayer player, CulinaryProfile profile,
                                      ChallengeVisitor visitor) {
        ServerConfig config = ConfigManager.server();
        if (!config.challenges.enabled) {
            return;
        }
        long day = CulinaryClock.day(player.server);
        prepare(player,profile);
        for (ChallengeDefinition definition : ChallengeRegistry.current().all()) {
            if (!isAvailable(definition, profile)) {
                continue;
            }
            ChallengeState state = profile.challenge(definition.id(), definition.objectives().size());
            if (state.isCompleted() || state.failed() || !state.started()) {
                continue;
            }
            try {
                visitor.visit(definition, state);
            } catch (RuntimeException failure) {
                CuisineJournalLog.LOGGER.error("Challenge {} failed to advance", definition.id(), failure);
                continue;
            }
            if (state.satisfies(definition.objectives())) {
                complete(player, profile, definition, state, day);
            }
        }
        profile.markDirty();
        com.chi__you.cuisinejournal.network.ServerPacketHandler.syncChallengeProgress(player);
    }

    public static boolean isAvailable(ChallengeDefinition definition, CulinaryProfile profile) {
        ServerConfig config = ConfigManager.server();
        if(definition.prerequisite()!=null&&!profile.hasCompletedChallenge(definition.prerequisite()))return false;
        var registry=CulinaryRegistries.current();
        for(var objective:definition.objectives()){
            if(definition.scope()==ChallengeDefinition.Scope.DAILY&&objective.type()==ChallengeObjective.Type.COOK_CATEGORY
                    &&(!config.mastery.enabled||config.mastery.perDish()))return false;
            if(objective.type()==ChallengeObjective.Type.COOK_PERFECT&&(!config.mastery.enabled||!config.mastery.bonuses.enabled))return false;
            if(objective.type()==ChallengeObjective.Type.MASTER_RECIPE&&!config.mastery.enabled)return false;
            if(objective.type()==ChallengeObjective.Type.REACH_LEVEL&&!config.progression.enabled)return false;
            if(!objective.hasValue())continue;
            switch(objective.type()){
                case COOK_RECIPE,COOK_TODAY->{if(registry.preparationRecipes(objective.valueAsId()).isEmpty())return false;}
                case EAT_RECIPE->{if(!registry.isTracked(objective.valueAsId()))return false;}
                case COOK_CATEGORY->{if(registry.byCategory(objective.valueAsId()).isEmpty())return false;}
                case COOK_FROM_MOD->{if(registry.byMod(objective.value()).isEmpty())return false;}
                case USE_METHOD->{if(!registry.methods().contains(com.chi__you.cuisinejournal.recipe.CookingMethod.of(objective.valueAsId())))return false;}
                case COMPLETE_CHALLENGE->{if(ChallengeRegistry.current().get(objective.valueAsId())==null)return false;}
                case USE_INGREDIENT->{if(!com.chi__you.cuisinejournal.recipe.IngredientCatalogue.isUsableIngredient(objective.valueAsId()))return false;}
                case USE_INGREDIENT_TAG->{if(registry.entries().stream().flatMap(e->e.ingredients().stream())
                        .noneMatch(item->matchesTag(item,objective.valueAsId())))return false;}
                default->{}
            }
        }
        return switch (definition.scope()) {
            case DAILY -> config.challenges.dailyEnabled;
            case CHAIN -> definition.prerequisite() == null
                    || profile.hasCompletedChallenge(definition.prerequisite());
            case PERMANENT -> definition.prerequisite() == null
                    || profile.hasCompletedChallenge(definition.prerequisite());
        };
    }

    public static void prepare(ServerPlayer player,CulinaryProfile profile){
        if(player.tickCount%20==0)RewardDelivery.drain(player,profile);
        if(!ConfigManager.server().challenges.enabled)return;
        DailyRotation.tick(player.server);
        long now=player.server.overworld().getGameTime();
        var calendar=com.chi__you.cuisinejournal.culinary.DailyCalendar.current(player.server);
        for(var id:java.util.List.copyOf(profile.challenges().keySet()))
            if(expiredGeneratedPeriod(id,calendar.day()))profile.clearChallenge(id);
        for(var definition:ChallengeRegistry.current().all()){
            if(!isAvailable(definition,profile))continue;
            var state=profile.challenge(definition.id(),definition.objectives().size());
            if(definition.scope()==ChallengeDefinition.Scope.DAILY){
                if(state.preparePeriod(calendar.day(),now,Long.MAX_VALUE,true))profile.markDirty();
            }else {
                boolean perDay=definition.objectives().stream().anyMatch(o->o.type()==ChallengeObjective.Type.UNIQUE_RECIPES_IN_ONE_DAY);
                if(state.prepareAutomatic(calendar.day(),now,Long.MAX_VALUE,0,perDay))profile.markDirty();
            }
                for(int i=0;i<definition.objectives().size();i++){
                    var objective=definition.objectives().get(i);
                    if(objective.type()==ChallengeObjective.Type.COMPLETE_CHALLENGE&&profile.hasCompletedChallenge(objective.valueAsId()))state.raise(i,1);
                }
        }
    }

    public static boolean expiredGeneratedPeriod(ResourceLocation id,long day){
        boolean daily=id.getNamespace().equals("cuisinejournal")&&id.getPath().startsWith("daily/");
        if(!daily)return false;
        String path=id.getPath();int slash=path.indexOf('/'),separator=path.indexOf('_',slash+1);
        if(separator<=slash)return false;
        try{return Long.parseLong(path.substring(slash+1,separator))<day;}
        catch(NumberFormatException invalid){return false;}
    }

    private static boolean matchesTag(ResourceLocation itemId, ResourceLocation tagId) {
        if (tagId == null) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null) {
            return false;
        }
        TagKey<Item> tag = TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagId);
        return new ItemStack(item).is(tag);
    }

    private static void complete(ServerPlayer player, CulinaryProfile profile,
                                 ChallengeDefinition definition, ChallengeState state, long day) {
        if(state.isCompleted())return;
        state.complete(day);
        RewardDelivery.enqueue(profile,definition.id(),day,definition.rewards());
        profile.markDirty();
        profile.pushActivity(new ActivityEntry(
                ActivityEntry.Type.CHALLENGE_COMPLETED, definition.id(), 0L, day));
        RewardDelivery.drain(player,profile);
        com.chi__you.cuisinejournal.network.ServerPacketHandler.sendProfile(player);
        com.chi__you.cuisinejournal.network.ServerPacketHandler.sendStatistics(player);
        CulinaryEvents.CHALLENGE_COMPLETED.fire(new CulinaryEvents.ChallengeCompleted(
                player, profile, definition.id()));

        // Chains unlock in one step, so a follow-up challenge whose only
        // objective was "complete the previous one" resolves immediately.
        for (ChallengeDefinition candidate : ChallengeRegistry.current().all()) {
            if (!definition.id().equals(candidate.prerequisite())) {
                continue;
            }
            ChallengeState next = profile.challenge(candidate.id(), candidate.objectives().size());
            if(!isAvailable(candidate,profile))continue;
            if(!next.started())next.start(player.server.overworld().getGameTime(),candidate.durationTicks());
            if(next.failed())continue;
            forEachObjective(candidate, ChallengeObjective.Type.COMPLETE_CHALLENGE,
                    (index, objective) -> {
                        if (definition.id().toString().equals(objective.value())) {
                            next.advance(index, 1);
                        }
                    });
            if (next.satisfies(candidate.objectives()) && !next.isCompleted()) {
                complete(player, profile, candidate, next, day);
            }
        }
    }

    /** Wipes the progress of rotating challenges whose period has rolled over. */
    public static void rotate(CulinaryProfile profile, ChallengeDefinition.Scope scope) {
        for (ChallengeDefinition definition : ChallengeRegistry.current().byScope(scope)) {
            profile.clearChallenge(definition.id());
        }
    }

    static CommandSourceStack rewardSource(ServerPlayer player) {
        return player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
    }
}
