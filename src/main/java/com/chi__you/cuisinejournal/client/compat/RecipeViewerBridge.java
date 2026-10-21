package com.chi__you.cuisinejournal.client.compat;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Soft-dependency bridge to whichever recipe viewer is installed (JEI, then REI, then EMI).
 *
 * <p>Everything is reflective so the mod runs without any of them. The viewer itself is
 * resolved on every call: JEI's runtime only exists after the client finishes loading, so
 * caching a "not available yet" answer would break the feature for the whole session.</p>
 */
public final class RecipeViewerBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeViewerBridge.class);
    private static boolean warned;

    private RecipeViewerBridge() { }

    /** Recipes that produce the stack (JEI's R key). */
    public static void showRecipes(ItemStack stack) { show(stack, true); }

    /** Recipes that use the stack (JEI's U key). */
    public static void showUses(ItemStack stack) { show(stack, false); }

    /** Opens the effect's JEED description page (journal keeps its place; ESC returns). */
    public static void showEffect(MobEffect effect) {
        if (effect == null) return;
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var journal = minecraft.screen instanceof com.chi__you.cuisinejournal.client.journal.JournalScreen screen ? screen : null;
        if (journal != null) journal.leavingForRecipeViewer(true);
        try {
            openEffect(effect);
        } finally {
            if (journal != null && minecraft.screen == journal) journal.leavingForRecipeViewer(false);
        }
    }

    private static void show(ItemStack stack, boolean recipes) {
        if (stack == null || stack.isEmpty()) return;
        if(recipes) {
            var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            var source=com.chi__you.cuisinejournal.client.ClientJournalState.preparationTarget(id);
            if(!source.equals(id)&&net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(source))
                stack=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(source));
        }
        // Tell the journal it is only stepping out, so it keeps its selection for the return trip.
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var journal = minecraft.screen instanceof com.chi__you.cuisinejournal.client.journal.JournalScreen screen ? screen : null;
        if (journal != null) journal.leavingForRecipeViewer(true);
        try {
            open(stack, recipes);
        } finally {
            // Nothing opened (no viewer, no recipes): a later close is a real close again.
            if (journal != null && minecraft.screen == journal) journal.leavingForRecipeViewer(false);
        }
    }

    private static void open(ItemStack stack, boolean recipes) {
        try {
            if (ModList.get().isLoaded("jei") && showJei(stack, recipes)) return;
            if (ModList.get().isLoaded("roughlyenoughitems") && showRei(stack, recipes)) return;
            if (ModList.get().isLoaded("emi")) showEmi(stack, recipes);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Could not open the recipe viewer for {}", stack, failure);
            }
        }
    }

    /** Effect pages come from JEED; without it effect clicks do nothing. */
    public static boolean canShowEffect() {
        return ModList.get().isLoaded("jeed");
    }

    private static void openEffect(MobEffect effect) {
        try {
            // JEED registers effects as its own ingredient type and routes the click to JEI/REI/EMI itself.
            if (ModList.get().isLoaded("jeed")) showEffectJeed(effect);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Could not open the recipe viewer for effect {}", effect, failure);
            }
        }
    }

    // ------------------------------------------------------------------ JEI

    private static boolean showJei(ItemStack stack, boolean recipes) throws ReflectiveOperationException {
        Object runtime = jeiRuntime();
        if (runtime == null) return false;
        Class<?> roleType = Class.forName("mezz.jei.api.recipe.RecipeIngredientRole");
        Class<?> ingredientType = Class.forName("mezz.jei.api.ingredients.IIngredientType");
        Class<?> focusType = Class.forName("mezz.jei.api.recipe.IFocus");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object role = Enum.valueOf((Class<Enum>) roleType, recipes ? "OUTPUT" : "INPUT");
        Object itemType = Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
        Object helpers = call(runtime, "mezz.jei.api.runtime.IJeiRuntime", "getJeiHelpers");
        Object factory = call(helpers, "mezz.jei.api.helpers.IJeiHelpers", "getFocusFactory");
        // IFocusFactory.createFocus(RecipeIngredientRole, IIngredientType<V>, V)
        Method create = Class.forName("mezz.jei.api.recipe.IFocusFactory")
                .getMethod("createFocus", roleType, ingredientType, Object.class);
        Object focus = create.invoke(factory, role, itemType, stack.copy());
        Object gui = call(runtime, "mezz.jei.api.runtime.IJeiRuntime", "getRecipesGui");
        Class.forName("mezz.jei.api.runtime.IRecipesGui").getMethod("show", focusType).invoke(gui, focus);
        return true;
    }

    /** JeedAPI.invokeEffectClicked(MobEffectInstance, mouseX, mouseY, button) — the same path as clicking an effect in the inventory. */
    private static void showEffectJeed(MobEffect effect) throws ReflectiveOperationException {
        Class<?> api = Class.forName("net.mehvahdjukaar.jeed.api.JeedAPI");
        var mouse = net.minecraft.client.Minecraft.getInstance().mouseHandler;
        api.getMethod("invokeEffectClicked", net.minecraft.world.effect.MobEffectInstance.class, double.class, double.class, int.class)
                .invoke(null, new net.minecraft.world.effect.MobEffectInstance(effect, 600), mouse.xpos(), mouse.ypos(), 0);
    }

    /** JEI's runtime from its internal holder; null until JEI has finished starting. */
    private static Object jeiRuntime() {
        try {
            Class<?> internal = Class.forName("mezz.jei.common.Internal");
            try {
                Object optional = internal.getMethod("getOptionalJeiRuntime").invoke(null);
                if (optional instanceof java.util.Optional<?> value) return value.orElse(null);
            } catch (NoSuchMethodException ignored) {
                // Older JEI builds only have the throwing getter.
            }
            return internal.getMethod("getJeiRuntime").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notReady) {
            return null;
        }
    }

    /** Invokes a no-arg interface method; interface lookup avoids access errors on JEI's private impl classes. */
    private static Object call(Object target, String type, String method) throws ReflectiveOperationException {
        return Class.forName(type).getMethod(method).invoke(target);
    }

    // ------------------------------------------------------------------ REI

    private static boolean showRei(ItemStack stack, boolean recipes) throws ReflectiveOperationException {
        Class<?> entryStacks = Class.forName("me.shedaniel.rei.api.common.util.EntryStacks");
        Class<?> entryStack = Class.forName("me.shedaniel.rei.api.common.entry.EntryStack");
        Class<?> builderType = Class.forName("me.shedaniel.rei.api.client.view.ViewSearchBuilder");
        Object entry = entryStacks.getMethod("of", ItemStack.class).invoke(null, stack);
        Object builder = builderType.getMethod("builder").invoke(null);
        builder = builderType.getMethod(recipes ? "addRecipesFor" : "addUsagesFor", entryStack).invoke(builder, entry);
        builderType.getMethod("open").invoke(builder);
        return true;
    }

    // ------------------------------------------------------------------ EMI

    private static void showEmi(ItemStack stack, boolean recipes) throws ReflectiveOperationException {
        Class<?> emiStack = Class.forName("dev.emi.emi.api.stack.EmiStack");
        Class<?> emiIngredient = Class.forName("dev.emi.emi.api.stack.EmiIngredient");
        Object value = emiStack.getMethod("of", ItemStack.class).invoke(null, stack);
        Class.forName("dev.emi.emi.api.EmiApi")
                .getMethod(recipes ? "displayRecipes" : "displayUses", emiIngredient).invoke(null, value);
    }
}
