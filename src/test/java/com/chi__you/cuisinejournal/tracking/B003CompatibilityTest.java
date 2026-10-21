package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.food.KaleidoscopeFood;
import com.chi__you.cuisinejournal.food.KaleidoscopeLunchBag;
import com.chi__you.cuisinejournal.food.CuisineFoodPlacement;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public final class B003CompatibilityTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        int[] stars={5,4,2,0};double[] ratios={1.2,.9,.6,.3};
        for(int q=0;q<4;q++){
            check(KaleidoscopeFood.qualityStars(q)==stars[q],"native quality display mapping");
            check(Math.abs(KaleidoscopeFood.qualityRatio(q)-ratios[q])<1e-9,"native quality nutrition ratio");
        }
        check(KaleidoscopeFood.minimumStars(List.of())==2,"potion-only cocktail default");
        check(KaleidoscopeFood.minimumStars(List.of(5,4,2))==2,"cocktail uses weakest ingredient");
        check(KaleidoscopeFood.minimumStars(List.of(5,0,4))==0,"zero star ingredient participates");
        for(int capacity=0;capacity<=5;capacity++)for(int used=0;used<=capacity;used++)for(int cost=1;cost<=3;cost++){
            var occupied=used==0?List.<CuisineFoodPlacement.Slot>of():List.of(new CuisineFoodPlacement.Slot(used,100,100,false));
            check(KaleidoscopeLunchBag.foodFits(occupied,cost,capacity)==(used+cost<=capacity),"bag only fills vacancy");
        }
        check(!KaleidoscopeLunchBag.foodFits(List.of(new CuisineFoodPlacement.Slot(1,1,100,true)),1,5),"bag does not refresh almost-expired same food");
        var stored=new CompoundTag();stored.putInt("cuisinejournal:cocktail_stars",4);stored.putInt("Color",1);
        var live=new CompoundTag();live.putInt("Color",2);live.putString("Effects","native dynamic payload");
        var merged=PlacedFoodVariants.merge(stored,live);
        check(merged.getInt("cuisinejournal:cocktail_stars")==4,"stored journal provenance survives");
        check(merged.getInt("Color")==2&&merged.getString("Effects").equals("native dynamic payload"),"live cocktail payload wins");
        merged.putInt("Color",3);check(stored.getInt("Color")==1&&live.getInt("Color")==2,"merge does not mutate templates");
        var a=new UUID(0,1);var b=new UUID(0,2);var ledger=new ProductionLedger();
        ledger.syncInput(0,"apple",3,a);ledger.syncInput(1,"sugar",1,b);
        var batch=ledger.produce(Map.of(0,3,1,1),"preserve",4,"kaleidoscope_chinesefood:pickle_jar");
        check(batch.contributors().equals(Set.of(a,b)),"whole container production shares contributors");
        check(ProductionLedger.readBatch(ProductionLedger.writeBatch(batch)).equals(batch),"async contributors persist");
        var placed=new org.objectweb.asm.tree.ClassNode();
        try(var input=B003CompatibilityTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/tracking/PlacedFoodVariants.class")){
            new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(placed,0);
        }
        for(String method:List.of("drops","preview"))for(var body:placed.methods)if(body.name.equals(method)){
            for(var instruction:body.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call)
                check(!Set.of("takeCup","plateServing","setDirty","remove","clear","store").contains(call.name),"loot preview never consumes persistent sources");
        }
        var meal=new com.chi__you.cuisinejournal.culinary.MealRecord(net.minecraft.resources.ResourceLocation.parse("test:brewed"));
        meal.recordPreparation(1,1,com.chi__you.cuisinejournal.recipe.CookingMethod.of("kaleidoscope_tavern","barrel"));
        meal.recordBrewedStars(4);meal.recordBrewedStars(1);
        check(meal.highestBrewedStars()==4,"lower later batches do not lower recorded brew stars");
        check(com.chi__you.cuisinejournal.culinary.MealRecord.load(meal.target(),meal.save()).highestBrewedStars()==4,"brew record survives save/load");
        System.out.println("B003CompatibilityTest: "+checks+" checks passed");
    }
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
