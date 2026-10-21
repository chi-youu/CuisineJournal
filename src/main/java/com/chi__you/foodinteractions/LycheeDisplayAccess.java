package com.chi__you.foodinteractions;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Lychee stores display outputs in post actions rather than Recipe.getResultItem. */
public final class LycheeDisplayAccess {
    private LycheeDisplayAccess() {}
    public static boolean generated(Recipe<?> recipe) {
        return recipe.getId().getNamespace().equals(InteractionDisplay.NAMESPACE);
    }
    public static boolean freezing(Recipe<?> recipe) {
        return generated(recipe) && recipe.getId().getPath().startsWith("freezing/trailandtales_delight/ice_bucket/");
    }
    public static List<ItemStack> outputs(Recipe<?> recipe) {
        List<ItemStack> result=new ArrayList<>();
        try {
            Object raw=recipe.getClass().getMethod("getPostActions").invoke(recipe);
            if(raw instanceof Stream<?> stream)try(stream) {
                for(Object action:stream.toList()) {
                    Object items=action.getClass().getMethod("getItemOutputs").invoke(action);
                    if(items instanceof List<?> list)for(Object item:list)
                        if(item instanceof ItemStack stack&&!stack.isEmpty())result.add(stack.copy());
                }
            }
        } catch(ReflectiveOperationException|RuntimeException|LinkageError ignored) {}
        return result;
    }
    public static List<ResourceLocation> sourceItems(Recipe<?> recipe) {
        if(!generated(recipe))return List.of();
        try {
            Object predicate=recipe.getClass().getMethod("getBlock").invoke(recipe);
            var method=Class.forName("snownee.lychee.core.def.BlockPredicateHelper")
                    .getMethod("getMatchedBlocks",net.minecraft.advancements.critereon.BlockPredicate.class);
            Object raw=method.invoke(null,predicate);
            if(raw instanceof java.util.Set<?> list)return list.stream().filter(net.minecraft.world.level.block.Block.class::isInstance)
                    .map(net.minecraft.world.level.block.Block.class::cast).map(FoodInteractionScanner::source)
                    .filter(java.util.Objects::nonNull).distinct().toList();
        } catch(ReflectiveOperationException|RuntimeException|LinkageError ignored) {}
        return List.of();
    }
}
