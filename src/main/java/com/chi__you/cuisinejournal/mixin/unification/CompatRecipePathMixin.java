package com.chi__you.cuisinejournal.mixin.unification;

import com.chi__you.cuisinejournal.compat.CompatRecipePathPatch;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Mixin(RecipeManager.class)
public abstract class CompatRecipePathMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("HEAD"))
    private void cuisinejournal$repair(Map<ResourceLocation, JsonElement> recipes, ResourceManager resources,
                                      ProfilerFiller profiler, CallbackInfo ci) {
        CompatRecipePathPatch.apply(recipes, resources);
        int changed = com.chi__you.cuisinejournal.compat.SquidRecipeCompat.apply(recipes,
                net.minecraft.core.registries.BuiltInRegistries.ITEM::containsKey);
        if (changed > 0) com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.info(
                "Applied {} squid recipe compatibility changes", changed);
        int unified = com.chi__you.cuisinejournal.compat.UnificationRecipes.apply(recipes,
                com.chi__you.cuisinejournal.compat.UnificationContent.read(resources));
        if (unified > 0) com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.info(
                "Applied {} selected food unification recipe changes", unified);
    }
}
