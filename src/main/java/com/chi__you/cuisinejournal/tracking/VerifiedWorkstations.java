package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.recipe.CookingMethod;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import static com.chi__you.cuisinejournal.compat.HostReflection.inventory;
import static com.chi__you.cuisinejournal.compat.HostReflection.invoke;
import java.util.Map;

/** Layouts come from verified production code, not block names or user-supplied slot numbers. */
public final class VerifiedWorkstations {
    public record Layout(int[] inputs, int[] outputs, int container, CookingMethod method,
                         WorkstationDefinitions.Adapter adapter, boolean preview) {}
    private static Layout layout(int inputs, int output, String method) {
        return new Layout(range(inputs), new int[]{output}, -1, CookingMethod.of(net.minecraft.resources.ResourceLocation.parse(method)),
                WorkstationDefinitions.Adapter.ITEM_RECIPE_CONTAINER, false);
    }
    private static final Map<String, Layout> LAYOUTS = Map.ofEntries(
            Map.entry("net.ribs.vintagedelight.block.entity.FermentingJarBlockEntity",
                    new Layout(range(7),new int[]{7,8},6,CookingMethod.of("vintagedelight","fermenting"),WorkstationDefinitions.Adapter.ITEM_RECIPE_CONTAINER,false)),
            Map.entry("com.soytutta.mynethersdelight.common.block.entity.BlazierBlockEntity",
                    new Layout(range(4),new int[0],-1,CookingMethod.CAMPFIRE,WorkstationDefinitions.Adapter.CAMPFIRE,false)),
            Map.entry("com.chefmooon.ubesdelight.common.block.entity.forge.BakingMatBlockEntityImpl",
                    new Layout(range(9),new int[0],-1,CookingMethod.of("ubesdelight","baking_mat"),WorkstationDefinitions.Adapter.CUTTING_BOARD,false)),
            Map.entry("com.freefish.torchesbecomesunlight.server.block.blockentity.OvenBlockEntity", layout(12,12,"torchesbecomesunlight:oven")),
            Map.entry("com.freefish.torchesbecomesunlight.server.block.blockentity.StewPotBlockEntity", layout(12,12,"torchesbecomesunlight:stew_pot")),
            Map.entry("com.freefish.torchesbecomesunlight.server.block.blockentity.CuttingBoradBlockEntity",
                    new Layout(range(12),new int[]{12},-1,CookingMethod.of("torchesbecomesunlight","cutting_board"),WorkstationDefinitions.Adapter.ITEM_RECIPE_CONTAINER,true)),
            Map.entry("com.renyigesai.immortalers_delight.block.enchantal_cooler.EnchantalCoolerBlockEntity", layout(5,5,"immortalers_delight:enchantal_cooler")),
            Map.entry("teamrazor.deepaether.entity.block.CombinerBlockEntity", layout(3,3,"deep_aether:combining")),
            Map.entry("umpaz.brewinandchewin.common.block.entity.KegBlockEntity",layout(5,5,"brewinandchewin:fermenting")),
            Map.entry("com.aetherteam.aether.blockentity.AltarBlockEntity",
                    new Layout(new int[]{0},new int[]{2},-1,CookingMethod.of("aether","enchanting"),WorkstationDefinitions.Adapter.FURNACE,false)),
            Map.entry("dev.xkmc.youkaishomecoming.content.pot.base.BasePotBlockEntity",
                    new Layout(new int[]{0,1,2,3,5},new int[]{4,6},5,CookingMethod.of("youkaishomecoming","moka_pot"),WorkstationDefinitions.Adapter.COOKING_POT,false)),
            Map.entry("com.sammy.minersdelight.content.block.copper_pot.CopperPotBlockEntity",
                    new Layout(new int[]{0,1,2,3,5},new int[]{4,6},5,CookingMethod.COOKING_POT,WorkstationDefinitions.Adapter.COOKING_POT,false)),
            Map.entry("dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackBlockEntity",
                    new Layout(range(4),new int[0],-1,CookingMethod.of("youkaishomecoming","drying_rack"),WorkstationDefinitions.Adapter.CAMPFIRE,false)),
            Map.entry("com.github.L_Ender.cataclysm.blockentities.AltarOfAmethyst_Block_Entity",
                    new Layout(new int[]{0},new int[]{0},-1,CookingMethod.of("cataclysm","amethyst_bless"),WorkstationDefinitions.Adapter.ITEM_RECIPE_CONTAINER,false)));
    private VerifiedWorkstations() {}
    private static int[] range(int count) { return java.util.stream.IntStream.range(0,count).toArray(); }
    static Layout find(String name) { var own=LAYOUTS.get(name);if(own==null)own=LetsDoStations.find(name);return own==null?ItemRecipeWorkstations.find(name):own; }
    public static Layout find(Class<?> type) {
        for (Class<?> parent=type;parent!=null;parent=parent.getSuperclass()) {
            Layout result=find(parent.getName()); if(result!=null)return result;
        }
        return null;
    }
    public static ItemStack stack(Object object,int slot) {
        if(object==null||slot<0)return ItemStack.EMPTY;
        if(object instanceof net.minecraft.world.level.block.entity.BlockEntity be){var virtual=LetsDoStations.virtual(be,slot);if(virtual!=null)return virtual;}
        if(object instanceof net.minecraft.world.level.block.entity.BlockEntity be){var virtual=ItemRecipeWorkstations.virtual(be,slot);if(virtual!=null)return virtual;}
        Object inventory=object instanceof Container||object instanceof IItemHandler?object:inventory(object);
        if(inventory instanceof Container container)return slot<container.getContainerSize()?container.getItem(slot):ItemStack.EMPTY;
        if(inventory instanceof IItemHandler handler)return slot<handler.getSlots()?handler.getStackInSlot(slot):ItemStack.EMPTY;
        if(inventory instanceof java.util.List<?> list&&slot<list.size()&&list.get(slot) instanceof ItemStack stack)return stack;
        return ItemStack.EMPTY;
    }
    public static boolean previewConsumes(Object owner,int slot,ItemStack item) {
        if(slot<9)return true;
        if(!(owner instanceof net.minecraft.world.level.block.entity.BlockEntity be))return false;
        try {
            Class<?> definitions=Class.forName("com.freefish.torchesbecomesunlight.server.init.recipe.FoodValuesDefinition");
            Object values=definitions.getMethod("getFoodValues",ItemStack.class,net.minecraft.world.level.Level.class).invoke(null,item,be.getLevel());
            Class<?> categories=Class.forName("com.freefish.torchesbecomesunlight.server.init.recipe.FoodCategory");
            for(Object category:categories.getEnumConstants())if(java.util.Set.of("VEGGIE","MEAT","FISH","EGG").contains(((Enum<?>)category).name())) {
                Object amount=invoke(values,"get",category);if(amount instanceof Number number&&number.doubleValue()!=0)return true;
            }
        } catch(ReflectiveOperationException | LinkageError ignored) {}
        return false;
    }
}
