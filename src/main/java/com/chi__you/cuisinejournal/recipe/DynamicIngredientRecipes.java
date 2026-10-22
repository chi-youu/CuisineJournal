package com.chi__you.cuisinejournal.recipe;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.RecipeHolder;

/** Dynamic raw processing has no single truthful static yield and never earns cooking credit. */
public final class DynamicIngredientRecipes {
    private static final String DOUGH="net.mehdinoui.veggiesdelight.common.crafting.SweetPotatoDoughRecipe";
    private DynamicIngredientRecipes() {}
    public static boolean rawProcessing(Recipe<?> recipe){
        return recipe!=null&&(recipe.getClass().getName().equals("com.teammetallurgy.aquaculture.item.crafting.FishFilletRecipe")
                ||recipe.getClass().getName().equals(DOUGH));
    }
    public static boolean dough(Recipe<?> recipe){return recipe.getClass().getName().equals(DOUGH);}
    public static net.minecraft.world.item.ItemStack output(Recipe<?> recipe){
        if(!dough(recipe))return net.minecraft.world.item.ItemStack.EMPTY;
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse("veggiesdelight:sweet_potato_dough"))
                .map(item->new net.minecraft.world.item.ItemStack(item,3)).orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }
    public static java.util.List<net.minecraft.world.item.crafting.Ingredient> inputs(Recipe<?> recipe){
        var potato=net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.parse("forge:crops/sweet_potato")));
        var water=net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.parse("forge:buckets/water")));
        return java.util.List.of(potato,potato,net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.WHEAT),water);
    }
    public static boolean rawProcessing(Slot slot){
        return slot.container instanceof RecipeHolder holder&&rawProcessing(holder.getRecipeUsed());
    }
}
