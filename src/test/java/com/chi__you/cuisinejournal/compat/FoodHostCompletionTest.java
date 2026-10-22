package com.chi__you.cuisinejournal.compat;

import com.chi__you.cuisinejournal.food.*;
import com.chi__you.cuisinejournal.tracking.*;
import com.chi__you.foodinteractions.UndergardenServings;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Native policy tables, actual host bytecode and production adapter contracts; no game startup. */
public final class FoodHostCompletionTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        for(String pie:List.of("blisterberry_pie","droopfruit_pie","gloomgourd_pie","stuffed_gloomgourd")){
            for(int stage=0;stage<4;stage++){
                var block=id("undergardendelight:"+pie+(stage==0?"":"_"+stage));
                var rule=ProcedurePlacedFoods.rule(block);
                check(rule!=null&&rule.whole().equals(id("undergardendelight:"+pie))&&rule.remaining()==4-stage,"canonical identity and exact native stage: "+block);
                check(rule.portion().equals(id("undergardendelight:"+pie+(pie.equals("stuffed_gloomgourd")?"_bowl":"_slice"))),"real native slice/bowl identity");
                check(rule.direct()!=pie.equals("stuffed_gloomgourd"),"knife pies may be eaten, stuffed gourd requires a bowl");
                check(UndergardenServings.relation(block).whole().equals(rule.whole()),"standalone display and runtime use same stage contract");
            }
        }
        check(ProcedurePlacedFoods.rule(id("test:blisterberry_pie"))==null
                &&ProcedurePlacedFoods.rule(id("undergardendelight:blisterberry_pie_4"))==null,"unreviewed host/stage never inferred");
        for(boolean garlic:List.of(false,true))for(int remaining=1;remaining<=7;remaining++){
            var food=ProcedurePlacedFoods.dumplingNutrition(remaining,garlic);
            int raw=remaining>=6?5:remaining>=3?6:8;float gain=remaining>=6?7F:remaining>=3?8.4F:14.4F;
            check(food.getNutrition()==raw+(garlic?1:0),"native dumpling nutrition for remaining "+remaining);
            check(Math.abs(food.getNutrition()*food.getSaturationModifier()*2-gain)<.0001,"garlic raises hunger only, not saturation");
        }
        ledgerTransfers();
        check(ProcedurePlacedFoods.nutritionIncrement(22,24,24)==5,"native hunger read below extended cap is bounded to 19");
        check(ProcedurePlacedFoods.nutritionIncrement(24,24,25)==1,"second garlic setter at cap adds only its true increment");
        check(ProcedurePlacedFoods.nutritionIncrement(18,20,23)==5,"ordinary hunger increment remains native");
        for(int stage=1;stage<=7;stage++){
            var rule=ProcedurePlacedFoods.rule(id("slime_delight:grass_block_cake_"+stage));
            check(rule!=null&&rule.remaining()==8-stage&&rule.nutrition()==3&&rule.direct(),"seven native slime cake stages");
        }
        for(int stage=1;stage<=4;stage++){
            var rule=ProcedurePlacedFoods.rule(id("slime_delight:nether_stew_"+stage));
            check(rule!=null&&rule.remaining()==5-stage&&!rule.direct(),"slime stew is bowl retrieval, not direct eating");
        }
        for(int stage=0;stage<4;stage++){
            var rule=ProcedurePlacedFoods.rule(id("festive_delight:festive_chicken_stage_"+stage));
            check(rule!=null&&rule.remaining()==4-stage&&!rule.direct(),"festive chicken serves four bowls through an event");
        }
        var declarations=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/cuisinejournal/cuisinejournal/compat/workstations/cookingforblockheads.json"))).getAsJsonObject();
        var defs=WorkstationDefinitions.resolve(List.of(new WorkstationDefinitions.RuleFile(id("test:kitchen"),"test",declarations)),mod->true);
        check(!defs.get(id("cookingforblockheads:oven")).capabilities().contains(WorkstationDefinitions.Capability.TIMED_PROCESSING),"oven explicitly has no speed support");
        check(defs.get(id("cookingforblockheads:toaster")).capabilities().contains(WorkstationDefinitions.Capability.TIMED_PROCESSING),"toaster native wait participates in speed");
        check(WorkstationDefinitions.resolve(List.of(new WorkstationDefinitions.RuleFile(id("test:kitchen"),"test",declarations)),mod->false).isEmpty(),"absent kitchen does not install phantom workstations");
        var recipes=own("com/chi__you/cuisinejournal/recipe/DynamicIngredientRecipes");
        check(literals(recipes).contains("com.teammetallurgy.aquaculture.item.crafting.FishFilletRecipe"),"only reviewed dynamic fish processor excluded");
        var tracker=own("com/chi__you/cuisinejournal/tracking/ProductionTracker");
        check(calls(tracker,"prepare","rawProcessing"),"actual raw fish extraction cannot earn cooking credit");
        check(literals(tracker).contains("net.firemuffin303.thaidelight.common.menu.MortarMenu")&&literals(tracker).contains("mortar"),"mortar keeps native result slot but records its real method");
        installed(System.getenv("CUISINEJOURNAL_COMPAT_MODS"));
        System.out.println("FoodHostCompletionTest: "+checks+" checks passed");
    }
    private static void ledgerTransfers(){
        var ledger=new ProductionLedger();var cook=new UUID(1,2);
        ledger.syncInput(0,"raw_food",4,cook);
        var transit=ledger.produce(Map.of(0,1),"raw_food",1,"cookingforblockheads:oven");
        ledger.adoptInput(7,"raw_food",1,transit);
        check(transit.belongsTo(cook)&&ledger.peek(7)==null,"raw relocation is input provenance, not a finished cooking batch");
        ledger=ProductionLedger.load(ledger.save());
        var meal=ledger.produce(Map.of(7,1),"cooked_food",1,"cookingforblockheads:oven");
        check(meal.belongsTo(cook)&&meal.ingredients().equals(Set.of("raw_food")),"oven cooking preserves exact raw contributor after save/reload");
        var empty=ledger.produce(Map.of(7,1),"cooked_food",1,"cookingforblockheads:oven");
        check(empty.contributors().isEmpty(),"empty processing input cannot mint another contributor");
        var automatic=new ProductionLedger();automatic.syncInput(0,"raw",1,null);
        check(automatic.produce(Map.of(0,1),"cooked",1,"cookingforblockheads:toaster").contributors().isEmpty(),"unobserved automation never guesses nearby maker");
    }
    private static void installed(String directory)throws Exception{
        if(directory==null){System.out.println("SKIPPED: installed food host bytecode contracts");return;}
        var classes=new HashMap<String,ClassNode>();
        var wanted=Set.of("cookingforblockheads","dumplings_delight","undergardendelight","muffins_thaidelight","aquaculture","aetherdelight","abnormals_delight","displaydelight","jellyfishingdelight","slime_delight","festive_delight","vanillacookbook","natures_delight","veggiesdelight");
        var snapshot=JsonParser.parseString(Files.readString(Path.of("docs/compatibility/installed.json"))).getAsJsonObject();
        for(var raw:snapshot.getAsJsonArray("jars")){var row=raw.getAsJsonObject();boolean read=false;
            for(var mod:row.getAsJsonArray("mods"))if(wanted.contains(mod.getAsJsonObject().get("id").getAsString()))read=true;
            if(!read)continue;Path file=Path.of(directory,row.get("file").getAsString());
            try(var jar=new JarFile(file.toFile())){for(var entry:Collections.list(jar.entries()))if(entry.getName().endsWith(".class"))
                try(var input=jar.getInputStream(entry)){var type=new ClassNode();new ClassReader(input).accept(type,0);classes.put(type.name,type);}}
        }
        for(var pair:Map.of("BlockheadsCraftProductionMixin","menu/inventory/InventoryCraftBook",
                "BlockheadsOvenProductionMixin","tile/OvenBlockEntity","BlockheadsToasterProductionMixin","tile/ToasterBlockEntity").entrySet()){
            var adapter=own("com/chi__you/cuisinejournal/mixin/production/"+pair.getKey());
            var target=classes.get("net/blay09/mods/cookingforblockheads/"+pair.getValue());
            check(target!=null&&OptionalMixinContracts.incompatibility(adapter,target,classes::get)==null,"actual kitchen hooks/fields/arguments match "+pair.getKey());
        }
        var mortar=classes.get("net/firemuffin303/thaidelight/common/menu/MortarMenu$MortarResultSlot");
        var breakAdapter=own("com/chi__you/cuisinejournal/mixin/production/PlacedFoodBreakDropsMixin");
        var burger=classes.get("club/someoneice/jellyfishingdelight/block/KrabbyPatty");
        check(OptionalMixinContracts.incompatibility(breakAdapter,burger,classes::get)==null,"native burger destruction scope matches real callback");
        check(calls(burger,"onDestroyedByPlayer","m_7967_"),"burger really emits directly rather than through loot tables");
        for(int stage=1;stage<=7;stage++){
            var block=classes.get("net/mcreator/slimedelight/block/GrassBlockCake"+stage+"Block");
            check(OptionalMixinContracts.incompatibility(breakAdapter,block,classes::get)==null,"native cake destruction callback: "+stage);
            var procedure=classes.get("net/mcreator/slimedelight/procedures/GrassBlockCake"+stage+"DangFangKuaiBeiWanJiaPoPiShiProcedure");
            int emissions=0;for(var method:procedure.methods)for(var instruction:method.instructions)
                if(instruction instanceof MethodInsnNode call&&call.name.equals("m_7967_"))emissions++;
            check(emissions==8-stage,"native cake destruction emits exactly remaining slices: "+stage);
        }
        var drops=own("com/chi__you/cuisinejournal/tracking/PlacedFoodDrops");
        for(var method:drops.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call)
            check(!call.owner.endsWith("/CookingRewards")&&!call.owner.endsWith("/ProgressionService")
                    &&!call.name.equals("claimBatch"),"destruction inheritance has no quality roll or cooking reward side effect");
        check(calls(own("com/chi__you/cuisinejournal/food/PlacedFoodConsumption"),"itemEntitySpawned","spawned"),"real spawn observer connects native direct drops to stamp scope");
        check(calls(own("com/chi__you/cuisinejournal/forge/ForgeEventBridge"),"onFoodBlockBroken","supports"),"break event retains supported stamps until actual removal, including canceled breaks");
        for(var pair:Map.of("DisplayInteractionStorageMixin","com/jkvin114/displaydelight/events/InterationManager",
                "DisplayFoodDropsMixin","com/jkvin114/displaydelight/block/AbstractItemBlock",
                "JellyfishGrillProductionMixin","club/someoneice/jellyfishingdelight/block/GrillTile",
                "FestiveChickenServingMixin","net/toopa/festivedelight/procedures/ChickenEatProcedure",
                "FestiveChickenPlacementMixin","net/toopa/festivedelight/procedures/FestiveChickenPlaceProcedure").entrySet()){
            var adapter=own("com/chi__you/cuisinejournal/mixin/production/"+pair.getKey());
            check(OptionalMixinContracts.incompatibility(adapter,Objects.requireNonNull(classes.get(pair.getValue())),classes::get)==null,"real storage/grill contracts: "+pair.getKey());
        }
        for(var pair:Map.of("JellyfishBurgerBoundsMixin","KrabbyPatty","JellyfishPlatterBoundsMixin","ColorfulKrabbyPattys").entrySet()){
            var adapter=own("com/chi__you/cuisinejournal/mixin/food/"+pair.getKey());
            check(OptionalMixinContracts.incompatibility(adapter,classes.get("club/someoneice/jellyfishingdelight/block/"+pair.getValue()),classes::get)==null,"native bounds repair contract: "+pair.getKey());
        }
        check(mortar!=null&&mortar.superName.equals("net/minecraft/world/inventory/ResultSlot"),"real mortar output inherits common extraction hooks");
        check(mortar.methods.stream().anyMatch(m->m.name.equals("m_142406_")),"real mortar retains native consumption callback");
        var dumplings=classes.get("com/tyxcnjiu/main/dumplingsdelight/block/DumplingMedleyBlock");
        check(dumplings!=null&&literals(dumplings).contains(7),"actual platter has seven servings");
        check(calls(dumplings,"takeServing","m_38705_")&&calls(dumplings,"takeServing","m_38717_"),"actual platter direct nutrition setters explain the bridge");
        for(String name:List.of("BlisterberryPie","DroopfruitPie","GloomgourdPie")){
            var procedure=classes.get("net/mcreator/undergardendelight/procedures/"+name+"OnBlockRightClickedProcedure");
            check(procedure!=null&&calls(procedure,"execute","m_7731_")&&calls(procedure,"execute","m_38705_"),"native independent block transition/direct bite: "+name);
        }
        var nativeSlice=classes.get("com/teamabnormals/abnormals_delight/common/item/CakeSliceItem");
        check(nativeSlice!=null&&calls(nativeSlice,"applyHealing","m_5634_"),"native slice healing stays host-owned; tooltip only adds its amount");
        for(String block:List.of("BrownieTrayBlock","PancakeStackBlock"))
            check(calls(classes.get("com/morallenplay/vanillacookbook/block/"+block),"eatCake","m_38707_"),"cookbook independent food uses ordinary nutrition overload: "+block);
        var manakish=classes.get("net/hibiscus/naturesdelight/ManakishBlock");
        check(manakish!=null&&manakish.methods.stream().anyMatch(m->m.name.equals("getMaxBites")),"native pie override must determine portion count");
        var remainder=classes.get("com/morallenplay/vanillacookbook/setup/SetRemainderRecipe");
        check(remainder!=null&&remainder.interfaces.contains("net/minecraft/world/item/crafting/CraftingRecipe")&&remainder.methods.stream().anyMatch(m->m.name.equals("m_7527_")),"custom remainder recipe exposes actual ingredients through standard crafting contract");
    }
    private static Set<Object> literals(ClassNode node){var result=new HashSet<Object>();for(var method:node.methods)for(var instruction:method.instructions){
        if(instruction instanceof LdcInsnNode literal)result.add(literal.cst);if(instruction instanceof IntInsnNode value)result.add(value.operand);}return result;}
    private static boolean calls(ClassNode node,String name,String target){for(var method:node.methods)if(method.name.equals(name))
        for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.name.equals(target))return true;return false;}
    private static ClassNode own(String name)throws Exception{try(var input=FoodHostCompletionTest.class.getClassLoader().getResourceAsStream(name+".class")){var node=new ClassNode();new ClassReader(Objects.requireNonNull(input,name)).accept(node,0);return node;}}
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
