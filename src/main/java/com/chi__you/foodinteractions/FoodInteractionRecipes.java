package com.chi__you.foodinteractions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server-only recipe generation, before the normal recipe packet and JEI reload. */
public final class FoodInteractionRecipes {
    private static final Logger LOG=LoggerFactory.getLogger("Food Interaction Recipes");
    private static boolean registered;
    private static Object lastRecipeSnapshot;
    private static net.minecraft.world.item.crafting.RecipeManager lastManager;
    private FoodInteractionRecipes() {}

    public static synchronized void register(boolean standalone) {
        if(registered||!ModList.get().isLoaded("lychee"))return;
        if(!standalone&&ModList.get().isLoaded(InteractionDisplay.NAMESPACE))return;
        registered=true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,FoodInteractionRecipes::starting);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,FoodInteractionRecipes::tags);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,FoodInteractionRecipes::sync);
    }
    private static void starting(ServerAboutToStartEvent event){rebuild(event.getServer(),true);}
    private static void tags(TagsUpdatedEvent event) {
        if(event.getUpdateCause()!=TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD)return;
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();
        if(server!=null)rebuild(server,true);
    }
    private static void sync(OnDatapackSyncEvent event){rebuild(event.getPlayerList().getServer(),false);}

    public static synchronized void rebuild(MinecraftServer server,boolean force) {
        if(server==null||!ModList.get().isLoaded("lychee"))return;
        var manager=server.getRecipeManager();
        Object marker=manager.getRecipes().stream().filter(LycheeDisplayAccess::generated).findFirst().orElse(null);
        if(!force&&manager==lastManager&&marker==lastRecipeSnapshot)return;
        List<Recipe<?>> recipes=new ArrayList<>(manager.getRecipes());
        recipes.removeIf(LycheeDisplayAccess::generated);
        Set<ResourceLocation> existing=new HashSet<>(); List<String> report=new ArrayList<>();
        for(Recipe<?> recipe:recipes) {
            try {
                List<ItemStack> outputs=new ArrayList<>();outputs.add(recipe.getResultItem(server.registryAccess()));
                outputs.addAll(LycheeDisplayAccess.outputs(recipe));
                try {
                    Object raw=recipe.getClass().getMethod("getResults").invoke(recipe);
                    if(raw instanceof List<?> list)for(Object value:list)if(value instanceof ItemStack stack)outputs.add(stack);
                }catch(NoSuchMethodException ignored){}
                for(ItemStack output:outputs)if(output!=null&&!output.isEmpty())existing.add(BuiltInRegistries.ITEM.getKey(output.getItem()));
            }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){report.add(recipe.getId()+"\tunreadable_existing_recipe\t"+failure.getMessage());}
        }
        var rules=InteractionRules.load(server.getResourceManager());
        var scan=FoodInteractionScanner.scan(rules);report.addAll(scan.report());
        int added=0;
        for(InteractionDisplay entry:scan.entries()) {
            if(!InteractionRules.shouldGenerate(entry.output(),existing,true)) {
                report.add(entry.recipeId()+"\tskipped_existing_recipe\t"+entry.output());continue;
            }
            try {
                Recipe<?> recipe=net.minecraft.world.item.crafting.RecipeManager.fromJson(entry.recipeId(),entry.json());
                recipes.add(recipe);added++;
                report.add(entry.recipeId()+"\tgenerated\t"+entry.output()+"\t"+entry.evidence());
            }catch(RuntimeException|LinkageError failure){report.add(entry.recipeId()+"\tfailed_generation\t"+failure.getMessage());}
        }
        manager.replaceRecipes(recipes);
        lastManager=manager;
        lastRecipeSnapshot=manager.getRecipes().stream().filter(LycheeDisplayAccess::generated).findFirst().orElse(null);
        try {
            Files.writeString(FMLPaths.CONFIGDIR.get().resolve("food-interaction-recipes-audit.tsv"),
                    "entry\tstatus\tdetail\n"+String.join("\n",report)+"\n",StandardCharsets.UTF_8);
        }catch(java.io.IOException failure){LOG.warn("Could not write interaction audit",failure);}
        LOG.info("Generated {} missing food-interaction display recipes ({} candidates)",added,scan.entries().size());
    }
}
