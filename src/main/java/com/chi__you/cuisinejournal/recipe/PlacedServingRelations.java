package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.food.DelightPlacedFoods;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Portion relations from audited host getters, independent of JEI/Lychee installation. */
public final class PlacedServingRelations {
    public record Relation(ResourceLocation whole,ResourceLocation serving,int count) {}
    private PlacedServingRelations() {}
    public static List<Relation> read(){
        List<Relation> result=new ArrayList<>();
        for(var block:BuiltInRegistries.BLOCK){
            if(com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.is(block,com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.TURKEY)){
                var serving=com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.serving(block);
                var whole=BuiltInRegistries.ITEM.getKey(block.asItem());
                if(!serving.isEmpty()&&!whole.getPath().equals("air"))
                    result.add(new Relation(whole,BuiltInRegistries.ITEM.getKey(serving.getItem()),5));
                continue;
            }
            if(com.chi__you.cuisinejournal.food.CustomPlacedFoods.known(block)){
                var serving=com.chi__you.cuisinejournal.food.CustomPlacedFoods.serving(block);var whole=BuiltInRegistries.ITEM.getKey(block.asItem());
                if(!serving.isEmpty()&&!whole.getPath().equals("air")&&!whole.equals(BuiltInRegistries.ITEM.getKey(serving.getItem())))
                    result.add(new Relation(whole,BuiltInRegistries.ITEM.getKey(serving.getItem()),com.chi__you.cuisinejournal.food.CustomPlacedFoods.remaining(block.defaultBlockState())));
                continue;
            }
            if(com.chi__you.cuisinejournal.food.LetsDoPlacedFoods.known(block)){
                var serving=com.chi__you.cuisinejournal.food.LetsDoPlacedFoods.serving(block,block.defaultBlockState());
                var whole=BuiltInRegistries.ITEM.getKey(block.asItem());
                if(!serving.isEmpty()&&!whole.getPath().equals("air")&&!whole.equals(BuiltInRegistries.ITEM.getKey(serving.getItem())))
                    result.add(new Relation(whole,BuiltInRegistries.ITEM.getKey(serving.getItem()),com.chi__you.cuisinejournal.food.LetsDoPlacedFoods.remaining(block.defaultBlockState())));
                continue;
            }
            if(com.chi__you.cuisinejournal.food.FarmersSpellFoods.known(block)){
                var whole=BuiltInRegistries.ITEM.getKey(block.asItem());
                com.chi__you.cuisinejournal.food.FarmersSpellFoods.portions(whole.getPath()).forEach((path,count)->{
                    var portion=ResourceLocation.fromNamespaceAndPath("farmers_spell",path);
                    if(BuiltInRegistries.ITEM.containsKey(portion))result.add(new Relation(whole,portion,count));
                });
                continue;
            }
            if(com.chi__you.cuisinejournal.tracking.KaleidoscopeServings.plate(block.defaultBlockState())){
                Object raw=HostReflection.field(block,"items");
                Object count=HostReflection.invoke(block,"getMaxCount");
                if(raw instanceof List<?> list&&count instanceof Number number)for(Object value:list)
                    if(value instanceof java.util.function.Supplier<?> supplier&&supplier.get() instanceof net.minecraft.world.item.Item item)
                        result.add(new Relation(BuiltInRegistries.ITEM.getKey(block.asItem()),BuiltInRegistries.ITEM.getKey(item),number.intValue()));
                continue;
            }
            if(com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.is(block,com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.EXQUISITO)){
                var part=com.chi__you.cuisinejournal.food.AbnormalsPlacedFoods.serving(block);
                if(!part.isEmpty())result.add(new Relation(BuiltInRegistries.ITEM.getKey(block.asItem()),BuiltInRegistries.ITEM.getKey(part.getItem()),7));
                continue;
            }
            if(!DelightPlacedFoods.known(block)){
                if(com.chi__you.cuisinejournal.food.CakeFoodCompat.cake(block)){
                    var serving=com.chi__you.cuisinejournal.food.CakeFoodCompat.serving(block);
                    var whole=BuiltInRegistries.ITEM.getKey(block.asItem());
                    if(!serving.isEmpty()&&!whole.getPath().equals("air")&&!whole.equals(BuiltInRegistries.ITEM.getKey(serving.getItem())))
                        result.add(new Relation(whole,BuiltInRegistries.ITEM.getKey(serving.getItem()),7));
                }
                continue;
            }
            if(DelightPlacedFoods.is(block,DelightPlacedFoods.HOGLIN)){
                var whole=BuiltInRegistries.ITEM.getKey(block.asItem());
                for(var pair:Map.of("roast_ear",2,"plate_of_stuffed_hoglin_snout",1,"plate_of_stuffed_hoglin_ham",4,"plate_of_stuffed_hoglin",4).entrySet()){
                    var part=ResourceLocation.fromNamespaceAndPath("mynethersdelight",pair.getKey());
                    if(BuiltInRegistries.ITEM.containsKey(part))result.add(new Relation(whole,part,pair.getValue()));
                }
                continue;
            }
            var unusual=com.chi__you.cuisinejournal.food.UnusualServingRelations.get(block);
            ItemStack serving=DelightPlacedFoods.serving(block,block.defaultBlockState());
            if(serving.isEmpty()||unusual==null&&block.asItem()==net.minecraft.world.item.Items.AIR)continue;
            var whole=unusual==null?BuiltInRegistries.ITEM.getKey(block.asItem()):ResourceLocation.fromNamespaceAndPath("unusual_delight",unusual.whole());
            var part=BuiltInRegistries.ITEM.getKey(serving.getItem());
            int count=DelightPlacedFoods.remaining(block.defaultBlockState());
            if(unusual!=null&&unusual.output().equals("pumpkin_mousse_bowl"))count=1;
            if(!whole.equals(part)&&count>0&&result.stream().noneMatch(r->r.whole.equals(whole)&&r.serving.equals(part)))result.add(new Relation(whole,part,count));
        }
        return List.copyOf(result);
    }
}
