package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.food.*;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

public final class B005CompatibilityTest {
    private static int checks;
    private static final Set<String> HOSTS=Set.of("farm_and_charm","bakery","candlelight","brewery","vinery","meadow","herbalbrews","beachparty","camping","bloomingnature","wildernature");
    public static void main(String[] args)throws Exception {
        for(int age=0;age<=100;age++){
            int expected=age<6?0:age<12?1:age<18?2:age<24?3:age<30?4:5;
            check(LetsDoFood.wineStars(age,6)==expected,"wine threshold age "+age);
            check(LetsDoFood.winePerfect(age,6,1800,200,15000)==(age>=66),"seventh wine tier age "+age);
        }
        check(LetsDoFood.wineStars(-10,6)==0&&LetsDoFood.wineStars(20,0)==5,"wine age boundaries stay safe");
        check(LetsDoFood.winePerfect(33,3,1800,400,15000),"configured native thresholds not hardcoded to 66 years");
        for(int q=1;q<=3;q++)for(boolean today:new boolean[]{false,true})for(boolean perfect:new boolean[]{false,true}){
            int upgraded=LetsDoFood.upgradedBeer(q,today,perfect);
            check(upgraded==Math.min(3,q+(today?1:0)+(perfect?1:0)),"beer native grade increases and caps");
            int stars=LetsDoFood.beerStars(upgraded);
            check(new MealStamp(stars,perfect,stars,null,"").effectiveStars()==stars,"perfect marker cannot add another star above native mapping");
        }
        var pot=LetsDoStations.find("net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity");
        check(Arrays.equals(pot.inputs(),new int[]{0,1,2,3,4,5})&&Arrays.equals(pot.outputs(),new int[]{7}),"pot container 6 is not an ingredient");
        var stove=LetsDoStations.find("net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity");
        check(Arrays.equals(stove.inputs(),new int[]{1,2,3})&&stove.outputs()[0]==0,"stove fuel 4 excluded");
        var tea=LetsDoStations.find("net.satisfy.herbalbrews.core.blocks.entity.TeaKettleBlockEntity");
        check(Arrays.equals(tea.inputs(),new int[]{1,2,3,4,5})&&tea.outputs()[0]==0,"tea water/heating slots excluded");
        check(LetsDoStations.find("net.satisfy.herbalbrews.core.blocks.entity.CauldronBlockEntity")==null,"flask cauldron remains outside food integration");
        check(LetsDoStations.find("net.satisfy.meadow.core.block.entity.CheeseRackBlockEntity")==null,"display rack never becomes a fermentation station");
        var rules=new ArrayList<WorkstationDefinitions.RuleFile>();
        try(var files=Files.list(Path.of("src/main/resources/data/cuisinejournal/cuisinejournal/compat/workstations"))){
            for(var file:files.filter(p->p.getFileName().toString().startsWith("letsdo_")).toList()){
                var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                check(json.getAsJsonArray("required_mods").size()==1,"each host rules file optional independently");
                rules.add(new WorkstationDefinitions.RuleFile(ResourceLocation.parse("test:"+file.getFileName()),"test",json));
            }
        }
        check(WorkstationDefinitions.resolve(rules,mod->false).isEmpty(),"no host requires no block metadata");
        check(WorkstationDefinitions.resolve(rules,mod->true).size()==35,"installed workstation metadata count");
        var own=own("food/CuisineVanillaBonuses");
        check(calls(own,"com/chi__you/cuisinejournal/food/LetsDoFood","mapped"),"mapped wine and beer bypass generic nutrition growth");
        own=own("tracking/LetsDoSpeed");check(constants(own).contains("brewery:brewing"),"brew minigame explicitly excluded from speed");
        check(!constants(own("food/LetsDoFood")).contains("GrilledNutrition"),"Camping nutrition NBT not read or rebalanced");
        check(calls(own("mixin/food/LetsDoWineShareMixin"),"net/minecraft/nbt/CompoundTag","merge"),"wine share-tag merges instead of dropping native fields");
        check(calls(own("mixin/production/LetsDoMincerDropMixin"),"net/minecraft/world/item/ItemStack","setTag"),"mincer rebuilds dropped item but inherits its actual pending variant");
        check(calls(own("mixin/food/AquaItemStackMixin"),"com/chi__you/cuisinejournal/food/LetsDoFood","roasting"),"long roasting use is not committed as eating");
        check(calls(own("food/PlacedFoodConsumption"),"com/chi__you/cuisinejournal/food/LetsDoPlacedFoods","table"),"table food resolved from its real stored stack before host removal");
        check(calls(own("tracking/LetsDoJuice"),"com/chi__you/cuisinejournal/tracking/ProductionTracker","inputPreview"),"juice transfer retains real inserter instead of proximity owner");
        var outputCheck=method(own("tracking/ProductionTracker"),"isOutputSlot");boolean checksContainer=false;
        for(var instruction:outputCheck.instructions)if(instruction instanceof FieldInsnNode field
                &&field.owner.equals("net/minecraft/world/inventory/Slot")&&field.name.equals("container"))checksContainer=true;
        check(checksContainer,"player inventory slots sharing the same numeric index cannot consume station output provenance");
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(directory==null){System.out.println("SKIPPED: B005 installed-host contracts; local checks="+checks);return;}
        var jars=new ArrayList<JarFile>();
        try{
            try(var files=Files.list(Path.of(directory))){for(var file:files.filter(p->p.toString().endsWith(".jar")).toList())jars.add(new JarFile(file.toFile()));}
            var wine=read(jars,"net/satisfy/vinery/core/util/WineYears");
            check(wine!=null&&method(wine,"getWineAge").desc.equals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)I"),"wine age interface verified");
            check(constants(wine).containsAll(Set.of("Year","EffectAmplifier","EffectDuration")),"wine native tag keys verified");
            var drink=read(jars,"net/satisfy/brewery/core/item/DrinkBlockItem");
            check(drink!=null&&drink.fields.stream().anyMatch(f->f.name.equals("effect")&&f.desc.equals("Lnet/minecraft/world/effect/MobEffect;")),"beer dynamic effect read contract");
            check(constants(drink).contains(LetsDoFood.BEER_QUALITY),"beer grade NBT contract");
            var cstove=read(jars,"net/satisfy/candlelight/core/block/entity/CStoveBlockEntity");
            check(cstove.superName.equals("net/satisfy/farm_and_charm/core/block/entity/StoveBlockEntity"),"candle stove receives only parent hook");
            for(String type:List.of("net/satisfy/candlelight/core/block/entity/LargeCookingPotBlockEntity","net/satisfy/bakery/core/block/entity/SmallCookingPotBlockEntity"))
                check(read(jars,type).superName.equals("net/minecraft/world/level/block/entity/BlockEntity"),"independent pot has dedicated hook");
            var grill=read(jars,"net/satisfy/camping/core/world/block/entity/GrillBlockEntity");
            check(grill.superName.equals("net/minecraft/world/level/block/entity/BlockEntity")&&method(grill,"cookTick")!=null,"independent four-slot grill target");
            var helper=read(jars,"net/satisfy/farm_and_charm/core/item/food/EffectFoodHelper");
            check(method(helper,"getEffects").desc.equals("(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"),"native ingredient effects remain separate from flavors");
            for(var rule:rules)for(var raw:rule.json().getAsJsonArray("workstations")){
                var block=ResourceLocation.parse(raw.getAsJsonObject().get("block").getAsString());
                check(jars.stream().anyMatch(j->j.getJarEntry("assets/"+block.getNamespace()+"/blockstates/"+block.getPath()+".json")!=null),"actual installed block metadata "+block);
            }
        }finally{for(var jar:jars)jar.close();}
        System.out.println("B005CompatibilityTest: "+checks+" checks passed");
    }
    private static ClassNode own(String name)throws Exception{try(var input=B005CompatibilityTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/"+name+".class")){var node=new ClassNode();new ClassReader(Objects.requireNonNull(input)).accept(node,0);return node;}}
    private static ClassNode read(List<JarFile> jars,String name)throws Exception{for(var jar:jars){var entry=jar.getJarEntry(name+".class");if(entry!=null)try(var input=jar.getInputStream(entry)){var node=new ClassNode();new ClassReader(input).accept(node,0);return node;}}return null;}
    private static MethodNode method(ClassNode type,String name){return type.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static Set<String> constants(ClassNode type){Set<String> result=new HashSet<>();for(var method:type.methods)for(var instruction:method.instructions)if(instruction instanceof LdcInsnNode ldc&&ldc.cst instanceof String text)result.add(text);return result;}
    private static boolean calls(ClassNode type,String owner,String name){for(var method:type.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))return true;return false;}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
