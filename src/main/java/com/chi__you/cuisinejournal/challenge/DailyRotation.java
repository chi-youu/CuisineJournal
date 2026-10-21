package com.chi__you.cuisinejournal.challenge;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.culinary.ProfileManager;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generated daily challenges. Today's dish has its own independent service.
 *
 * <p>Everything is drawn from dishes the server can <em>actually</em> make, so
 * an objective is never impossible. The selection is seeded by the day number,
 * so every player sees the same dish without any extra state, and a server
 * restart does not reroll it.</p>
 *
 * <p>Real-time scheduling is deliberately unsupported: the clock is Minecraft
 * days, which keeps everything working on a world that is only played at
 * weekends.</p>
 */
public final class DailyRotation {

    private static volatile long currentDay = Long.MIN_VALUE;
    private static volatile List<ChallengeDefinition> dailyChallenges = List.of();
    public static void invalidate(){currentDay=Long.MIN_VALUE;}

    private DailyRotation() {
    }

    public static ResourceLocation recipeOfTheDay() {
        return com.chi__you.cuisinejournal.progression.TodayDishService.current(com.chi__you.cuisinejournal.CuisineJournalServer.server());
    }

    public static List<ChallengeDefinition> dailyChallenges() {
        return dailyChallenges;
    }

    /** Called each server tick; only does work when the day actually changes. */
    public static void tick(MinecraftServer server) {
        ServerConfig config = ConfigManager.server();
        if (!config.challenges.enabled) {
            return;
        }
        long day = com.chi__you.cuisinejournal.culinary.DailyCalendar.current(server).day();
        if (day == currentDay) {
            return;
        }
        if(CulinaryRegistries.current().entries().isEmpty())return;
        currentDay = day;
        DailyChallengeHistory.seed(server);
        var history=DailyChallengeHistory.get(server);
        if(history.day()!=day){
            history.select(day,generate(CulinaryRegistries.current(),config,history.methods(),
                    com.chi__you.cuisinejournal.progression.TodayDishService.current(server),day));
        }
        install(history.tasks(),config);

        ProfileManager profiles = ProfileManager.get();
        if (profiles == null) {
            return;
        }
        for(var player:server.getPlayerList().getPlayers()){
            ChallengeManager.prepare(player,profiles.of(player));
            com.chi__you.cuisinejournal.network.ServerPacketHandler.sendExtras(player);
        }
    }

    private static void roll(long day, ServerConfig config) {
        install(generate(CulinaryRegistries.current(),config,java.util.Set.of(),null,day),config);
    }

    private static void install(List<ChallengeDefinition> tasks,ServerConfig config){
        dailyChallenges=config.challenges.dailyEnabled?tasks.stream().filter(task->task.objectives().stream().noneMatch(objective->
                objective.type()==ChallengeObjective.Type.COOK_CATEGORY&&(!config.mastery.enabled||config.mastery.perDish())
                ||objective.type()==ChallengeObjective.Type.COOK_PERFECT&&(!config.mastery.enabled||!config.mastery.bonuses.enabled))).toList():List.of();
        ChallengeRegistry.setRotating(dailyChallenges);
    }

    /**
     * Builds one challenge out of what is installed.
     *
     * <p>Each shape is only offered when the server has enough content to make
     * it satisfiable — a "use this method" objective is never generated for a
     * method no installed recipe uses.</p>
     */
    public static List<ChallengeDefinition> generate(CulinaryRegistry registry,ServerConfig config,
                                                    java.util.Set<ResourceLocation> usedMethods,ResourceLocation today,long day) {
        var candidates=registry.entries().stream().filter(e->!registry.preparationRecipes(e.target()).isEmpty()).toList();
        if(candidates.isEmpty()||!config.challenges.dailyEnabled)return List.of();
        Random random=new Random(day*0x9E3779B97F4A7C15L);
        List<CookingMethod> methods = candidates.stream().flatMap(e->e.methods().stream()).filter(m->usedMethods.contains(m.id()))
                .distinct().sorted(java.util.Comparator.comparing(CookingMethod::id)).toList();
        List<ResourceLocation> categories = candidates.stream().map(e->e.category().id()).distinct().sorted().toList();
        var shapes=new ArrayList<ChallengeObjective.Type>();
        if(!methods.isEmpty())shapes.add(ChallengeObjective.Type.USE_METHOD);
        if(config.mastery.enabled&&!config.mastery.perDish()&&!categories.isEmpty())shapes.add(ChallengeObjective.Type.COOK_CATEGORY);
        shapes.add(ChallengeObjective.Type.COOK_RECIPE);
        shapes.add(ChallengeObjective.Type.UNIQUE_RECIPES_IN_ONE_DAY);
        if(config.mastery.enabled&&config.mastery.bonuses.enabled)shapes.add(ChallengeObjective.Type.COOK_PERFECT);
        if(today!=null&&!registry.preparationRecipes(today).isEmpty())shapes.add(ChallengeObjective.Type.COOK_TODAY);
        shapes.add(ChallengeObjective.Type.EAT_FOOD);
        java.util.Collections.shuffle(shapes,random);
        var tasks=new ArrayList<ChallengeDefinition>();
        for(int index=0;index<Math.min(config.challenges.dailyChallengeCount,shapes.size());index++){
        var shape=shapes.get(index);ChallengeObjective objective;
        if (shape == ChallengeObjective.Type.USE_METHOD) {
            CookingMethod method = methods.get(random.nextInt(methods.size()));
            objective = new ChallengeObjective(ChallengeObjective.Type.USE_METHOD,
                    method.id().toString(), 3 + random.nextInt(5), false);
        } else if (shape == ChallengeObjective.Type.COOK_CATEGORY) {
            ResourceLocation category = categories.get(random.nextInt(categories.size()));
            int available = (int)candidates.stream().filter(e->e.category().id().equals(category)).count();
            if (available == 0) {
                continue;
            }
            objective = new ChallengeObjective(ChallengeObjective.Type.COOK_CATEGORY,
                    category.toString(), Math.min(available, 2 + random.nextInt(3)), true);
        } else if (shape == ChallengeObjective.Type.COOK_RECIPE) {
            objective = new ChallengeObjective(ChallengeObjective.Type.COOK_RECIPE, "",
                    Math.min(candidates.size(), 3 + random.nextInt(4)), true);
        } else if(shape==ChallengeObjective.Type.UNIQUE_RECIPES_IN_ONE_DAY) {
            objective = new ChallengeObjective(ChallengeObjective.Type.UNIQUE_RECIPES_IN_ONE_DAY,
                    "", Math.min(candidates.size(), 3 + random.nextInt(3)), false);
        } else if(shape==ChallengeObjective.Type.COOK_PERFECT){
            objective=new ChallengeObjective(shape,"",1,false);
        } else if(shape==ChallengeObjective.Type.COOK_TODAY){
            objective=new ChallengeObjective(shape,today.toString(),5,false);
        } else {objective=new ChallengeObjective(ChallengeObjective.Type.EAT_FOOD,"",25+random.nextInt(16),false);}
        tasks.add(new ChallengeDefinition(ResourceLocation.parse("cuisinejournal:daily/"+day+"_"+shape.name().toLowerCase(java.util.Locale.ROOT)), ChallengeDefinition.Scope.DAILY,
                List.of(objective),
                new ChallengeReward(40, 0, 0, null, null, config.challenges.dailyRewardItems, List.of()),
                null, false));
        }return List.copyOf(tasks);
    }

    public static void reset() {
        currentDay = Long.MIN_VALUE;
        ChallengeRegistry.setRotating(List.of());
        dailyChallenges = List.of();
    }
}
