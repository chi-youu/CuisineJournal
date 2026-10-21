package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.recipe.CookingMethod;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;

/** Verified layouts; fuel and serving containers are not recipe ingredients. */
public final class LetsDoStations {
    private LetsDoStations() {}
    private static int[] range(int from,int to){return java.util.stream.IntStream.range(from,to).toArray();}
    private static VerifiedWorkstations.Layout layout(int[] inputs,int[] outputs,String method){
        return new VerifiedWorkstations.Layout(inputs,outputs,-1,CookingMethod.of(net.minecraft.resources.ResourceLocation.parse(method)),
                WorkstationDefinitions.Adapter.ITEM_RECIPE_CONTAINER,false);
    }
    public static VerifiedWorkstations.Layout find(String name){
        return switch(name){
            case "net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity","net.satisfy.bakery.core.block.entity.SmallCookingPotBlockEntity","net.satisfy.candlelight.core.block.entity.LargeCookingPotBlockEntity" -> layout(range(0,6),new int[]{7},"farm_and_charm:pot_cooking");
            case "net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity","net.satisfy.candlelight.core.block.entity.CookingPanBlockEntity" -> layout(range(0,6),new int[]{7},"farm_and_charm:roaster");
            case "net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity" -> layout(range(1,4),new int[]{0},"farm_and_charm:stove");
            case "net.satisfy.farm_and_charm.core.block.entity.CraftingBowlBlockEntity" -> layout(range(0,4),new int[]{4},"farm_and_charm:crafting_bowl");
            case "net.satisfy.farm_and_charm.core.block.entity.MincerBlockEntity" -> layout(new int[]{0},new int[]{1},"farm_and_charm:mincer");
            case "net.satisfy.farm_and_charm.core.block.entity.SiloBlockEntity" -> layout(range(0,81),range(81,162),"farm_and_charm:drying");
            case "net.satisfy.meadow.core.block.entity.CheeseFormBlockEntity" -> layout(new int[]{1,2},new int[]{0},"meadow:cheese");
            case "net.satisfy.meadow.core.block.entity.CookingCauldronBlockEntity" -> layout(range(1,7),new int[]{0},"meadow:cooking");
            case "net.satisfy.beachparty.core.block.entity.PalmBarBlockEntity" -> layout(range(1,5),new int[]{0},"beachparty:palm_bar_mixing");
            case "net.satisfy.beachparty.core.block.entity.MiniFridgeBlockEntity" -> layout(new int[]{1},new int[]{0},"beachparty:mini_fridge_freezing");
            case "net.satisfy.herbalbrews.core.blocks.entity.TeaKettleBlockEntity" -> layout(range(1,6),new int[]{0},"herbalbrews:kettle_brewing");
            case "net.satisfy.herbalbrews.core.blocks.entity.TeaLeafBlockEntity" -> layout(new int[0],new int[0],"herbalbrews:leaf_drying");
            case "net.satisfy.vinery.core.block.entity.FermentationBarrelBlockEntity" -> layout(new int[]{0,1,2,3},new int[]{5},"vinery:wine_fermentation");
            case "net.satisfy.vinery.core.block.entity.ApplePressBlockEntity" -> layout(new int[]{0,1},new int[]{1,3},"vinery:apple_fermenting");
            case "net.satisfy.brewery.core.block.entity.BrewstationBlockEntity" -> layout(range(0,3),new int[]{100},"brewery:brewing");
            case "net.satisfy.camping.core.world.block.entity.GrillBlockEntity" -> new VerifiedWorkstations.Layout(range(0,4),new int[0],-1,CookingMethod.CAMPFIRE,WorkstationDefinitions.Adapter.CAMPFIRE,false);
            default -> null;
        };
    }
    public static boolean host(BlockEntity be){return be!=null&&method(be)!=null;}
    public static BlockEntity inputOwner(BlockEntity be){
        if(be!=null&&"farm_and_charm:drying".equals(method(be))){var controller=HostReflection.invoke(be,"getControllerBE");if(controller instanceof BlockEntity owner)return owner;}
        return be;
    }
    public static String method(BlockEntity be){
        if(be==null)return null;
        for(Class<?> type=be.getClass();type!=null;type=type.getSuperclass()){var layout=find(type.getName());if(layout!=null)return layout.method().id().toString();}
        return null;
    }
    public static ItemStack virtual(BlockEntity be,int slot){
        if(slot==100&&"brewery:brewing".equals(method(be))){var raw=HostReflection.field(be,"beer");if(raw instanceof ItemStack stack)return stack;}
        return null;
    }
    public static void replaceOutput(BlockEntity be,int slot,ItemStack value){
        if(slot==100&&"brewery:brewing".equals(method(be))){
            for(Class<?> type=be.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField("beer");field.setAccessible(true);field.set(be,value);return;}
            catch(NoSuchFieldException ignored){}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        }else if(be instanceof net.minecraft.world.Container container)container.setItem(slot,value);
    }
}
