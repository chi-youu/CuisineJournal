package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.recipe.LatiaoMastery;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Policy and installed-JAR contracts, not a substitute for station/gameplay tests. */
public final class B006CompatibilityTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        var a=id("ltc2:latiao");var b=id("ltc2:rare_latiao");var c=id("ltc2:superior_latiao");var other=id("ltc2:pepper_latiao");
        var edges=LatiaoMastery.compile(Map.of(a,b,b,c,other,other));
        check(edges.get(a).equals(Set.of(a,b,c))&&edges.get(c).equals(edges.get(a)),"grade sharing symmetric and transitive");
        check(!edges.get(a).contains(other),"unrelated flavor is never shared");
        Map<ResourceLocation,Long> history=new HashMap<>(Map.of(a,12L,b,8L,c,0L));
        check(LatiaoMastery.points(edges.get(c),key->history.getOrDefault(key,0L))==20,"all existing grade records contribute without migration");
        check(history.get(c)==0&&history.size()==3,"query never grants discovery or copies counts");
        check(LatiaoMastery.points(Set.of(a,b),key->Long.MAX_VALUE)==Long.MAX_VALUE,"mastery sum saturates");
        sharedProfile(edges,a,b,c,other);
        var crock=ItemRecipeWorkstations.find("com.sihenzhang.crockpot.block.entity.CrockPotBlockEntity");
        check(Arrays.equals(crock.inputs(),new int[]{0,1,2,3})&&!Arrays.stream(crock.inputs()).anyMatch(i->i==4),"crock fuel excluded from real four-input provenance");
        check(Arrays.equals(crock.outputs(),new int[]{5,100}),"staged result and delivered output are separate ledger slots");
        var oven=ItemRecipeWorkstations.find("com.doggystudio.chirencqr.ltc.server.block.entity.LatiaoOvenBlockEntity");
        check(Arrays.equals(oven.inputs(),new int[]{0,1,2})&&oven.outputs()[0]==4,"latiao main and extra spice included but fuel excluded");
        check(ItemRecipeWorkstations.find("cn.breezeth.kaleidoscope_grilling.grill.GrillBlockEntity")==null,"manual grill is not accidentally driven by timed progress adapter");
        check(ItemRecipeWorkstations.find("com.mao.barbequesdelight.content.block.GrillBlockEntity")==null,"manual BBQ progress remains host-owned");
        var metadata=new ArrayList<WorkstationDefinitions.RuleFile>();
        try(var files=Files.list(Path.of("src/main/resources/data/cuisinejournal/cuisinejournal/compat/workstations"))){
            for(var file:files.filter(p->Set.of("simple_cream.json","farmersrespite.json","papercraft_magic_decoration.json",
                    "ltc2.json","kitchenkarrot.json","drinkbeer.json","crockpot.json","bountifulfares.json")
                    .contains(p.getFileName().toString())).toList())
                metadata.add(new WorkstationDefinitions.RuleFile(id("test:"+file.getFileName()),"test",JsonParser.parseString(Files.readString(file)).getAsJsonObject()));
        }
        check(WorkstationDefinitions.resolve(metadata,mod->false).isEmpty(),"no optional hosts creates no station declarations");
        check(WorkstationDefinitions.resolve(metadata,mod->true).size()==12,"twelve verified optional block declarations");
        for(String type:List.of("culinary/CulinaryProfile","client/ClientJournalState","progression/CookingRewards"))
            check(calls(own(type),"com/chi__you/cuisinejournal/recipe/LatiaoMastery",type.startsWith("progression")?"family":"points"),"family mastery wired without changing record identity "+type);
        check(calls(own("food/CuisineFoodEntry"),"com/chi__you/cuisinejournal/food/GrillingFoodCompat","snapshotHot"),"consumed heat bonus survives later item cooling");
        check(calls(own("mixin/food/AquaItemStackMixin"),"com/chi__you/cuisinejournal/food/GrillingFoodCompat","nested"),"native ingredient nested use does not start a second admission");
        check(calls(own("forge/ForgeEventBridge"),"com/chi__you/cuisinejournal/food/GrillingFoodCompat","nested"),"nested ingredient finish does not consume another food slot");
        check(calls(own("food/CuisineFlavorEffects"),"com/chi__you/cuisinejournal/food/GrillingFoodCompat","actualSources"),"secret skewer actual ingredient identities reach flavor resolver");
        check(calls(own("food/CuisineEffectAdapters"),"com/chi__you/cuisinejournal/food/NetherVineryFoodCompat","delayedNative"),"short delayed teleport excluded from finite reserve capture");
        check(calls(own("mixin/production/BarbequesGrillTakeMixin"),"com/chi__you/cuisinejournal/tracking/ManualGrillProduction","handOutput"),"empty-hand BBQ collection settles before direct hand delivery");
        var handOutput=own("tracking/ManualGrillProduction").methods.stream().filter(m->m.name.equals("handOutput")).findFirst().orElseThrow();
        int cleared=-1,credited=-1;
        for(int i=0;i<handOutput.instructions.size();i++)if(handOutput.instructions.get(i) instanceof MethodInsnNode call){
            if(call.name.equals("removeTagKey"))cleared=i;
            if(call.name.equals("claimBatch"))credited=i;
        }
        check(cleared>=0&&credited>cleared,"direct hand path clears pending before credit so later pickups cannot repeat it");
        check(java.util.stream.StreamSupport.stream(handOutput.instructions.spliterator(),false)
                .filter(i->i instanceof MethodInsnNode call&&call.name.equals("claimBatch")).count()==1,"direct hand path has only one credit call");
        check(own("mixin/production/KaleidoscopeGrillProductionMixin").fields.stream().anyMatch(f->f.name.equals("cuisinejournal$simulatedExtraction")&&f.desc.equals("Z")),"grill previews have a separate simulation guard");
        check(calls(own("mixin/production/KitchenKarrotShakerMixin"),"com/chi__you/cuisinejournal/tracking/KitchenKarrotShaker","shaken"),"portable shaker snapshots actual successful shaking inputs and actor");
        check(calls(own("mixin/production/KitchenKarrotShakerResultMixin"),"com/chi__you/cuisinejournal/tracking/KitchenKarrotShaker","shakerResult"),"shaker marks actual produced result before insertion or drop");
        check(calls(own("mixin/production/DrinkBeerMixingMixin"),"com/chi__you/cuisinejournal/tracking/DrinkBeerMixing","beerTaken"),"spiced beer defers credit to real removal, not spice previews");
        installed();
        System.out.println("B006CompatibilityTest: "+checks+" checks passed");
    }
    private static void sharedProfile(Map<ResourceLocation,Set<ResourceLocation>> families,ResourceLocation a,ResourceLocation b,ResourceLocation c,ResourceLocation other)throws Exception{
        var field=LatiaoMastery.class.getDeclaredField("families");field.setAccessible(true);var old=field.get(null);
        var config=com.chi__you.cuisinejournal.config.ConfigManager.server();var scope=config.mastery.scope;
        try{
            field.set(null,families);config.mastery.scope=com.chi__you.cuisinejournal.config.ConfigEnums.MasteryScope.DISH;
            var profile=new com.chi__you.cuisinejournal.culinary.CulinaryProfile(new UUID(0,21));
            profile.meal(a).discover(1);profile.meal(b).discover(1);profile.meal(a).addMasteryPoints(8);profile.meal(b).addMasteryPoints(7);
            var curve=config.mastery.toCurve();
            check(profile.masteryPointsOf(a)==15&&profile.masteryPointsOf(b)==15&&profile.masteryPointsOf(c)==15,"server profile shares points across actual grade family");
            check(profile.rankOf(a,curve)==profile.rankOf(b,curve),"discovered grade variants show synchronized rank");
            check(!profile.isDiscovered(c)&&profile.peekMeal(c)==null,"unmade grade stays undiscovered with no fabricated record");
            check(profile.masteryPointsOf(other)==0,"other latiao flavor stays independent");
            check(profile.peekMeal(a).masteryPoints()==8&&profile.peekMeal(b).masteryPoints()==7,"individual stored points never copied or migrated");
            var rewards=com.chi__you.cuisinejournal.progression.CookingRewards.class.getDeclaredMethod("starsFor",
                    com.chi__you.cuisinejournal.culinary.CulinaryProfile.class,ResourceLocation.class,com.chi__you.cuisinejournal.config.ServerConfig.class);rewards.setAccessible(true);
            check(((Integer)rewards.invoke(null,profile,c,config))==profile.rankOf(a,curve).ordinal(),"first high-grade output borrows family rank without discovering it");
            config.mastery.scope=com.chi__you.cuisinejournal.config.ConfigEnums.MasteryScope.SUBCATEGORY;
            check(profile.masteryPointsOf(a)==0&&profile.masteryPointsOf(b)==0,"category mode does not read family pool when its category is absent");
            check(profile.meals().size()==2,"mode switch leaves per-item records intact");
        }finally{config.mastery.scope=scope;field.set(null,old);}
    }
    private static void installed()throws Exception{
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");if(directory==null)return;
        var jars=new ArrayList<JarFile>();try{
            try(var files=Files.list(Path.of(directory))){for(var file:files.filter(p->p.toString().endsWith(".jar")).toList())jars.add(new JarFile(file.toFile()));}
            for(String[] contract:List.of(
                    new String[]{"com/sihenzhang/crockpot/block/entity/CrockPotBlockEntity","result","Lnet/minecraft/world/item/ItemStack;"},
                    new String[]{"com/doggystudio/chirencqr/ltc/server/block/entity/LatiaoOvenBlockEntity","cookingProgress","I"},
                    new String[]{"net/hecco/bountifulfares/block/entity/GristmillBlockEntity","progress","I"},
                    new String[]{"io/github/tt432/kitchenkarrot/recipes/recipe/CocktailRecipe$Content","hunger","I"},
                    new String[]{"io/github/tt432/kitchenkarrot/recipes/recipe/CocktailRecipe$Content","saturation","I"})){
                var type=read(jars,contract[0]);if(type!=null)check(type.fields.stream().anyMatch(f->f.name.equals(contract[1])&&f.desc.equals(contract[2])),"host field contract "+Arrays.toString(contract));
            }
            var source=read(jars,"cn/breezeth/kaleidoscope_grilling/skewer/SkeweringHandler");
            if(source!=null)check(source.methods.stream().anyMatch(m->m.name.equals("readIngredientStacks")&&m.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;")),"skewer actual stack source reader exists");
            var upgrades=read(jars,"com/doggystudio/chirencqr/ltc/server/item/UpgradableLatiaoItem");
            if(upgrades!=null)check(upgrades.methods.stream().anyMatch(m->m.name.equals("findNextGrade")&&m.desc.equals("()Lnet/minecraft/world/item/Item;")),"host upgrade edge reader exists");
            for(var file:WorkstationDefinitions.resolve(List.of(),mod->true).values())check(file!=null,"empty metadata resolution stable");
        }finally{for(var jar:jars)jar.close();}
    }
    private static ClassNode read(List<JarFile> jars,String name)throws Exception{for(var jar:jars){var file=jar.getJarEntry(name+".class");if(file!=null)try(var input=jar.getInputStream(file)){var node=new ClassNode();new ClassReader(input).accept(node,0);return node;}}return null;}
    private static ClassNode own(String name)throws Exception{var node=new ClassNode();try(var in=B006CompatibilityTest.class.getResourceAsStream("/com/chi__you/cuisinejournal/"+name+".class")){new ClassReader(Objects.requireNonNull(in)).accept(node,0);}return node;}
    private static boolean calls(ClassNode node,String owner,String name){for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))return true;return false;}
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
