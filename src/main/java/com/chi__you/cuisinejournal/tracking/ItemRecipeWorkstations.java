package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.recipe.CookingMethod;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Item-recipe layouts; virtual slots represent real host buffers, never preview recipes. */
public final class ItemRecipeWorkstations {
    private ItemRecipeWorkstations() {}
    private static VerifiedWorkstations.Layout layout(int[] in,int[] out,String method){
        return new VerifiedWorkstations.Layout(in,out,-1,CookingMethod.of(net.minecraft.resources.ResourceLocation.parse(method)),WorkstationDefinitions.Adapter.ITEM_RECIPE_CONTAINER,false);
    }
    public static VerifiedWorkstations.Layout find(String name){return switch(name){
        case "com.sihenzhang.crockpot.block.entity.CrockPotBlockEntity" -> layout(new int[]{0,1,2,3},new int[]{5,100},"crockpot:crock_pot_cooking");
        case "com.doggystudio.chirencqr.ltc.server.block.entity.LatiaoOvenBlockEntity" -> layout(new int[]{0,1,2},new int[]{4},"ltc2:latiao_oven");
        case "com.doggystudio.chirencqr.ltc.server.block.entity.MillBlockEntity" -> layout(new int[]{0},new int[]{2},"ltc2:mill");
        case "net.hecco.bountifulfares.block.entity.GristmillBlockEntity" -> layout(new int[]{0},new int[]{1},"bountifulfares:milling");
        case "net.mcreator.simplecream.block.entity.IceCreamMachineBlockEntity" -> layout(new int[]{0,1,2,3},new int[]{4},"simple_cream:ice_cream_making");
        case "io.github.tt432.kitchenkarrot.blockentity.AirCompressorBlockEntity" -> layout(new int[]{0,1,2,3},new int[]{5},"kitchenkarrot:air_compressing");
        case "io.github.tt432.kitchenkarrot.blockentity.BrewingBarrelBlockEntity" -> layout(new int[]{0,1,2,3,4,5},new int[]{6},"kitchenkarrot:brewing_barrel");
        case "umpaz.farmersrespite.common.block.entity.KettleBlockEntity" -> layout(new int[]{0,1},new int[]{4},"farmersrespite:brewing");
        case "lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity" -> layout(new int[]{0,1,2,3},new int[]{100},"drinkbeer:brewing");
        case "com.va11halla.casualness_delight.enity.DeepFryingPanEnity" -> layout(new int[]{0},new int[0],"casualness_delight:deep_frying");
        case "com.kazi_cat.papercraft_magic_decoration.blockentity.CopperBartenderBlockEntity" -> layout(new int[]{0,1,2,3},new int[]{4},"papercraft_magic_decoration:mixology");
        case "com.kazi_cat.papercraft_magic_decoration.blockentity.CopperStillBlockEntity" -> layout(new int[]{0,1,2,3,4,5,6,7,8,9},new int[]{100},"papercraft_magic_decoration:distillation");
        default -> null;};}
    public static boolean host(BlockEntity be){return be!=null&&find(be.getClass().getName())!=null;}
    public static boolean ownsSlot(BlockEntity be,net.minecraft.world.inventory.Slot slot,int expected){
        if(be.getClass().getName().equals("lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity"))
            return slot.container==HostReflection.invoke(be,"getBrewingInventory")&&slot.getContainerSlot()==(expected==100?5:expected);
        if(be instanceof net.minecraft.world.Container)return slot.container==be&&slot.getContainerSlot()==expected;
        Object handler=null;int nativeSlot=expected;String name=be.getClass().getName();
        if(name.equals("io.github.tt432.kitchenkarrot.blockentity.AirCompressorBlockEntity")&&expected==5){handler=HostReflection.field(be,"output");nativeSlot=0;}
        else if(name.equals("io.github.tt432.kitchenkarrot.blockentity.BrewingBarrelBlockEntity")&&expected==6){handler=HostReflection.field(be,"result");nativeSlot=0;}
        else handler=HostReflection.inventory(be);
        return slot instanceof net.minecraftforge.items.SlotItemHandler forge&&forge.getItemHandler()==handler&&forge.getSlotIndex()==nativeSlot;
    }
    public static ItemStack virtual(BlockEntity be,int slot){
        if(be.getClass().getName().equals("com.kazi_cat.papercraft_magic_decoration.blockentity.CopperStillBlockEntity")&&(slot==9||slot==100)){
            Object raw=HostReflection.field(be,slot==9?"wineBase":"result");return raw instanceof ItemStack value?value:ItemStack.EMPTY;}
        if(be.getClass().getName().equals("lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity")){
            Object raw=HostReflection.invoke(be,"getBrewingInventory"),status=HostReflection.field(be,"statusCode");
            if(slot==100&&(!(status instanceof Number n)||n.intValue()==0))return ItemStack.EMPTY;
            return raw instanceof net.minecraft.world.Container inventory?inventory.getItem(slot==100?5:slot):ItemStack.EMPTY;
        }
        if(be.getClass().getName().equals("com.sihenzhang.crockpot.block.entity.CrockPotBlockEntity")&&slot==100){
            Object raw=HostReflection.field(be,"result");return raw instanceof ItemStack item?item:ItemStack.EMPTY;}
        if(be.getClass().getName().equals("io.github.tt432.kitchenkarrot.blockentity.AirCompressorBlockEntity")){
            Object raw=HostReflection.field(be,slot==5?"output":"input1");
            return raw instanceof IItemHandler handler?handler.getStackInSlot(slot==5?0:slot):ItemStack.EMPTY;}
        if(be.getClass().getName().equals("io.github.tt432.kitchenkarrot.blockentity.BrewingBarrelBlockEntity")){
            Object raw=HostReflection.field(be,slot==6?"result":"input");
            return raw instanceof IItemHandler handler?handler.getStackInSlot(slot==6?0:slot):ItemStack.EMPTY;}
        return null;
    }
    public static void replaceOutput(BlockEntity be,int slot,ItemStack stack){
        var old=virtual(be,slot);
        if(old!=null){old.setTag(stack.getTag());return;}
        if(be instanceof net.minecraft.world.Container container)container.setItem(slot,stack);
        else {Object raw=HostReflection.inventory(be);if(raw instanceof net.minecraftforge.items.IItemHandlerModifiable inventory)inventory.setStackInSlot(slot,stack);}
    }
}
