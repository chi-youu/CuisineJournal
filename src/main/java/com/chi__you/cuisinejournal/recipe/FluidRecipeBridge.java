package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;

/** Recover actual bottle outputs from the host mod's fluid/container relationship. */
public final class FluidRecipeBridge {
    private static volatile Map<Fluid,ItemStack> bottles=Map.of();
    private FluidRecipeBridge() {}
    public static Runnable rollbackPoint() { var previous=bottles; return () -> bottles=previous; }
    public static void rebuild(Collection<? extends Recipe<?>> recipes,RegistryAccess access) {
        Map<Fluid,ItemStack> result=new LinkedHashMap<>();
        for(Recipe<?> recipe:recipes) {
            String name=recipe.getClass().getName();
            if(!name.equals("umpaz.brewinandchewin.common.crafting.KegPouringRecipe")
                    &&!name.equals("umpaz.farmersrespite.common.crafting.KettlePouringRecipe")
                    &&!name.equals("com.gumillea.cosmopolitan.core.misc.recipes.TubExtractRecipe"))continue;
            Object raw=HostReflection.invoke(recipe,name.contains("farmersrespite")||name.contains("cosmopolitan")?"getFluid":"getRawFluid");
            ItemStack item=name.contains("farmersrespite")?(ItemStack)HostReflection.invoke(recipe,"getOutput"):recipe.getResultItem(access);
            if(raw instanceof FluidStack stack)raw=stack.getFluid();
            if(raw instanceof Fluid fluid&&!item.isEmpty())result.putIfAbsent(fluid,item.copyWithCount(1));
        }
        bottles=Map.copyOf(result);
    }
    public static ItemStack bottle(FluidStack fluid) {
        if(fluid==null||fluid.isEmpty())return ItemStack.EMPTY;
        ItemStack stored=bottles.get(fluid.getFluid());if(stored!=null)return stored.copy();
        try {
            Class<?> type=Class.forName("dev.xkmc.youkaishomecoming.content.item.fluid.YHFluidHandler");
            Object holder=type.getMethod("of",FluidStack.class).invoke(null,fluid);
            Object result=HostReflection.invoke(holder,"asStack",1);
            return result instanceof ItemStack item?item:ItemStack.EMPTY;
        } catch(ReflectiveOperationException | LinkageError ignored){return ItemStack.EMPTY;}
    }
    public static ItemStack output(Recipe<?> recipe) {
        Object fluid=null;String name=recipe.getClass().getName();
        switch(name) {
            case "umpaz.brewinandchewin.common.crafting.KegFermentingRecipe":fluid=HostReflection.invoke(recipe,"getResultFluid");break;
            case "umpaz.farmersrespite.common.crafting.KettleRecipe":fluid=HostReflection.invoke(recipe,"getFluidOut");break;
            case "com.gumillea.cosmopolitan.core.misc.recipes.TubInteractingRecipe":fluid=HostReflection.invoke(recipe,"getResult");break;
            case "dev.xkmc.youkaishomecoming.content.pot.kettle.KettleRecipe":fluid=HostReflection.field(recipe,"result");break;
            case "dev.xkmc.youkaishomecoming.content.pot.basin.SimpleBasinRecipe":fluid=HostReflection.field(recipe,"output");break;
            case "dev.xkmc.youkaishomecoming.content.pot.ferment.SimpleFermentationRecipe":fluid=HostReflection.field(recipe,"outputFluid");break;
            default:return ItemStack.EMPTY;
        }
        return fluid instanceof FluidStack stack?bottle(stack):fluid instanceof Fluid raw?bottle(new FluidStack(raw,250)):ItemStack.EMPTY;
    }
    public static List<Ingredient> inputs(Recipe<?> recipe) {
        if(recipe.getClass().getName().equals("io.github.tt432.kitchenkarrot.recipes.recipe.CocktailRecipe")){
            var raw=HostReflection.invoke(HostReflection.field(recipe,"content"),"getRecipe");
            if(raw instanceof List<?> list)return list.stream().filter(Ingredient.class::isInstance).map(Ingredient.class::cast).toList();
        }
        if(recipe.getClass().getName().startsWith("net.satisfy."))return LetsDoRecipes.inputs(recipe);
        if (YoukaiCuisineInputs.supports(recipe)) return YoukaiCuisineInputs.read(recipe);
        if(recipe.getClass().getName().equals("com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.TeapotRecipe")){
            Object raw=HostReflection.invoke(recipe,"ingredient");
            return raw instanceof Ingredient ingredient?List.of(ingredient):List.of();
        }
        if (potCooking(recipe)) {
            Object raw = HostReflection.invoke(recipe, "getInput");
            if (!(raw instanceof List<?> list)) throw new IllegalStateException("Missing pot recipe input");
            List<Ingredient> inputs = new ArrayList<>();
            for (Object value : list) {
                if (!(value instanceof Ingredient ingredient)) throw new IllegalStateException("Invalid pot recipe ingredient");
                inputs.add(ingredient);
            }
            return inputs;
        }
        List<Ingredient> result=new ArrayList<>(recipe.getIngredients());
        if(recipe.getClass().getName().startsWith("com.github.ysbbbbbb.kaleidoscope")){
            if(result.isEmpty())for(String method:List.of("ingredient","getIngredient")){
                Object raw=HostReflection.invoke(recipe,method);if(raw instanceof Ingredient ingredient){result.add(ingredient);break;}
            }
            return result;
        }
        for(String name:new String[]{"input","ingredients"}) {
            Object raw=HostReflection.field(recipe,name);
            if(raw instanceof Ingredient ingredient&&!result.contains(ingredient))result.add(ingredient);
            if(raw instanceof List<?> list)for(Object value:list)if(value instanceof Ingredient ingredient&&!result.contains(ingredient))result.add(ingredient);
        }
        return result;
    }

    /** Youkai JEI presents a pot and its bowls as alternative views of the same recipe. */
    public static ItemStack servingOutput(Recipe<?> recipe, ItemStack output) {
        if (!potCooking(recipe) || output == null || output.isEmpty()
                || !(output.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return output;
        boolean pot = false;
        for (Class<?> type = blockItem.getBlock().getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("dev.xkmc.youkaishomecoming.content.block.food.PotFoodBlock")) {
                pot = true;
                break;
            }
        }
        if (!pot) return output;
        Object bowls = HostReflection.invoke(blockItem.getBlock(), "asBowls");
        if (!(bowls instanceof ItemStack serving) || serving.isEmpty())
            throw new IllegalStateException("Missing pot-to-bowls output");
        ItemStack result = serving.copy();
        result.setCount(Math.multiplyExact(output.getCount(), serving.getCount()));
        return result;
    }

    private static boolean potCooking(Recipe<?> recipe) {
        for (Class<?> type = recipe.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("dev.xkmc.youkaishomecoming.content.pot.cooking.core.PotCookingRecipe")) return true;
        return false;
    }
    public static void clear(){bottles=Map.of();}
}
