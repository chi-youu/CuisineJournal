package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.tracking.VerifiedWorkstations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class LetsDoPlacedFoods {
    public static final String PIE="net.satisfy.bakery.core.block.cake.PieBlock";
    public static final String CHEESE="net.satisfy.meadow.core.block.CheeseBlock";
    public static final String TEA="net.satisfy.farm_and_charm.core.block.TeaJugBlock";
    public static final String TABLE="net.satisfy.candlelight.core.block.TableSetBlock";
    public static final String BOX="net.satisfy.farm_and_charm.core.block.EatableBoxBlock";
    public static final String COOKIE="net.satisfy.bakery.core.block.CookieBlock";
    private LetsDoPlacedFoods(){}
    public static boolean known(Block block){return DelightPlacedFoods.is(block,PIE,CHEESE,TEA,TABLE,BOX,COOKIE,
            "net.satisfy.farm_and_charm.core.block.FoodBlock","net.satisfy.farm_and_charm.core.block.EffectFoodBlock",
            "net.satisfy.meadow.core.block.FoodBlock","net.satisfy.bakery.core.block.CupcakeBlock");}
    public static ItemStack serving(Block block,BlockState state){
        Object raw=null;
        if(DelightPlacedFoods.is(block,PIE))raw=HostReflection.invoke(block,"getPieSliceItem");
        else if(DelightPlacedFoods.is(block,CHEESE))raw=HostReflection.field(block,"slice");
        else if(DelightPlacedFoods.is(block,TEA))raw=HostReflection.invokeDeclared(block,"getTeaItem",state);
        else if(DelightPlacedFoods.is(block,COOKIE,"net.satisfy.bakery.core.block.CupcakeBlock")){
            var id=BuiltInRegistries.BLOCK.getKey(block);String path=id.getPath();
            if(path.endsWith("_block")){path=path.substring(0,path.length()-6);
                if(DelightPlacedFoods.is(block,COOKIE))path=path.replace("_cookie","_glazed_cookie");
                raw=BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(id.getNamespace(),path));}
        }
        if(raw instanceof java.util.function.Supplier<?> supplier)raw=supplier.get();
        ItemStack result=raw instanceof ItemStack stack?stack.copyWithCount(1):raw instanceof Item item?new ItemStack(item):new ItemStack(block.asItem());
        if(result.isEmpty())for(Item item:BuiltInRegistries.ITEM)if(item instanceof BlockItem placed&&placed.getBlock()==block){result=new ItemStack(item);break;}
        if(result.isEmpty())return result;
        if(DelightPlacedFoods.is(block,"net.satisfy.bakery.core.block.BreadBasketBlock"))markServing(result,6,.8F);
        else if(DelightPlacedFoods.is(block,BOX))markServing(result,1,.4F);
        Object props=HostReflection.field(block,"foodComponent");
        if(props instanceof FoodProperties food)markServing(result,food.getNutrition(),food.getSaturationModifier());
        else if(DelightPlacedFoods.is(block,"net.satisfy.meadow.core.block.FoodBlock")){
            Object n=HostReflection.field(block,"nutrition"),s=HostReflection.field(block,"saturationMod");
            if(n instanceof Number nutrition&&s instanceof Number saturation)markServing(result,nutrition.intValue(),saturation.floatValue());
        }
        return result;
    }
    private static void markServing(ItemStack stack,int nutrition,float saturation){
        var row=new net.minecraft.nbt.CompoundTag();row.putInt("nutrition",nutrition);row.putFloat("saturation",saturation);stack.getOrCreateTag().put(LetsDoFood.SERVING,row);
    }
    public static ItemStack eating(Block block,BlockState state,ItemStack held,boolean sneak){
        if(DelightPlacedFoods.is(block,CHEESE,TEA,TABLE,COOKIE,"net.satisfy.bakery.core.block.CupcakeBlock"))return ItemStack.EMPTY;
        if(DelightPlacedFoods.is(block,PIE)&&(!sneak||!held.isEmpty()&&!held.is(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.parse("bakery:knives")))))return ItemStack.EMPTY;
        return serving(block,state);
    }
    public static boolean output(Block block,BlockState state,ItemStack item){
        if(item.isEmpty())return false;
        if(item.getItem() instanceof BlockItem placed&&placed.getBlock()==block)return true;
        var serving=serving(block,state);return !serving.isEmpty()&&ItemStack.isSameItem(serving,item);
    }
    public static int remaining(BlockState state){
        Block block=state.getBlock();int used=DelightPlacedFoods.value(state,DelightPlacedFoods.is(block,PIE,CHEESE)?"cuts":"bites");
        if(DelightPlacedFoods.is(block,TEA))return DelightPlacedFoods.value(state,"fill");
        if(DelightPlacedFoods.is(block,"net.satisfy.bakery.core.block.BreadBasketBlock"))return Math.max(0,5-used);
        if(DelightPlacedFoods.is(block,BOX))return Math.max(0,6-DelightPlacedFoods.value(state,"cuts"));
        if(DelightPlacedFoods.is(block,PIE)){Object raw=HostReflection.invoke(block,"getMaxCuts");return Math.max(0,(raw instanceof Number n?n.intValue():4)-used);}
        if(DelightPlacedFoods.is(block,CHEESE,COOKIE,"net.satisfy.bakery.core.block.CupcakeBlock"))return Math.max(0,4-used);
        Object max=HostReflection.field(block,"maxBites");
        int total=max instanceof Number n?n.intValue()+(DelightPlacedFoods.is(block,"net.satisfy.farm_and_charm.core.block.EffectFoodBlock")?1:0):4;
        return Math.max(0,total-used);
    }
    public static ItemStack table(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,BlockState state,ItemStack held,boolean sneak){
        if(!DelightPlacedFoods.is(state.getBlock(),TABLE))return ItemStack.EMPTY;
        var accessories=HostReflection.invokeDeclared(state.getBlock(),"itemHashMap");
        if(accessories instanceof java.util.Map<?,?> map&&map.containsKey(held.getItem()))return ItemStack.EMPTY;
        if(!held.isEmpty()&&held.is(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.parse("candlelight:all_effects"))))return ItemStack.EMPTY;
        for(String name:new String[]{"cloche","glass_drink","wine_glass_drink"}){var property=state.getBlock().getStateDefinition().getProperty(name);
            if(property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag&&state.getValue(flag))return ItemStack.EMPTY;}
        var be=level.getBlockEntity(pos);if(be==null)return ItemStack.EMPTY;
        ItemStack result=VerifiedWorkstations.stack(be,0).copyWithCount(1);
        var raw=result.isEmpty()?null:result.getItem().getFoodProperties(result,null);if(raw==null)return ItemStack.EMPTY;
        markServing(result,Math.round(raw.getNutrition()*1.3F),raw.getSaturationModifier()*1.3F);return result;
    }
}
