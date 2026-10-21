package com.chi__you.cuisinejournal.food;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Check the observed eating signals and source accessors, not only class names. */
public final class PlacedFoodContractTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(directory==null){System.out.println("SKIPPED: PlacedFoodContractTest installed-jar checks (CUISINEJOURNAL_COMPAT_MODS unset)");return;}
        List<JarFile> jars=new ArrayList<>();
        try {
            try(var files=Files.list(Path.of(directory))) {
                for(Path path:files.filter(file->file.toString().endsWith(".jar")).toList())jars.add(new JarFile(path.toFile()));
            }
            String[][] contracts={
                    {"net/brnbrd/delightful/common/block/SlicedMelonBlock","consumeBite","getSliceItem"},
                    {"net/brnbrd/delightful/common/block/SlicedMiniMelonBlock","consumeBite","getSliceItem"},
                    {"net/brnbrd/delightful/common/block/SlicedGourdBlock","consumeBite","getSliceItem"},
                    {"io/github/rcneg/legendarydelicacies/blocks/CommonCakeBlock","eatSlice","sliceItem"},
                    {"com/starmeow/smc/blocks/CommonCakeBlock","eatSlice","sliceItem"},
                    {"net/donne431/ice_and_fire_delight/block/DragonSpecialPieBlock","consumeBite","getPieSliceItem"},
                    {"net/donne431/ice_and_fire_delight/block/ElectricPieBlock","consumeBite","getPieSliceItem"},
                    {"net/donne431/ice_and_fire_delight/block/FieryHotPieBlock","consumeBite","getPieSliceItem"},
                    {"net/donne431/ice_and_fire_delight/block/FrostPieBlock","consumeBite","getPieSliceItem"},
                    {"com/renyigesai/immortalers_delight/block/food/SuperKwatBurgerBlock","eat","SUPER_KWAT_WHEAT_HAMBURGER_SLICE"},
                    {"com/renyigesai/immortalers_delight/block/food/ZeaPancakeBLock","eat","ZEA_PANCAKE_SLICE"},
                    {"com/renyigesai/immortalers_delight/block/StackedFoodBlock","eat","servingItem"},
                    {"com/renyigesai/immortalers_delight/block/food/BraisedSpiderEyesBlock","eat","BRAISED_SPIDER_EYES_IN_GRAVY"},
                    {"com/renyigesai/immortalers_delight/block/food/ScarletDevilsCakeBlock","eat","SCARLET_DEVILS_CAKE_SLICE"},
                    {"com/renyigesai/immortalers_delight/block/food/TartareChickenBigMealBlock","eat","TARTARE_CHICKEN"},
                    {"com/scouter/silentsdelight/blocks/PieBaseBlockEntity","consumeBite","getPieSliceItem"},
                    {"com/starmeow/smc/blocks/BasePieBlock","consumeBite","getPieSliceItem"},
                    {"cn/foggyhillside/ends_delight/block/ChorusFruitPieBlock","consumeBite","getPieSliceItem"},
                    {"womp/tinfoilknight/aquamirae_delight/blocks/BreadFoodBlock","consumeBite","getPieSliceItem"},
                    {"vectorwing/farmersdelight/common/block/PieBlock","consumeBite","getPieSliceItem"},
                    {"net/yirmiri/dungeonsdelight/common/block/MonsterCakeBlock","m_51185_","MONSTER_CAKE_SLICE"},
                    {"net/yirmiri/dungeonsdelight/common/block/SpiderDonutBlock","takeServing","m_5456_"},
                    {"twilightforest/block/Experiment115Block","eatCake","EXPERIMENT_115"},
                    {"dev/xkmc/fruitsdelight/content/block/MangosteenCakeBlock","eat","MANGOSTEEN_CAKE"}
            };
            for(String[] contract:contracts) {
                ClassNode node=read(jars,contract[0]);if(node==null)continue;
                MethodNode eat=node.methods.stream().filter(method->method.name.equals(contract[1])).findFirst().orElseThrow();
                boolean nutrition=false,source=false;
                for(var instruction:eat.instructions) {
                    if(instruction instanceof MethodInsnNode call) {
                        if(call.owner.equals("net/minecraft/world/food/FoodData")&&Set.of("eat","m_38707_","m_38712_").contains(call.name)
                                ||call.owner.equals("net/minecraft/world/item/Item")&&call.name.equals("m_5922_")
                                ||call.owner.equals("net/minecraft/world/item/ItemStack")&&call.name.equals("m_41671_"))nutrition=true;
                        if(call.name.equals(contract[2]))source=true;
                    }
                    if(instruction instanceof FieldInsnNode field&&field.name.equals(contract[2]))source=true;
                }
                check(nutrition,"nutrition signal still observable: "+contract[0]);
                check(source,"actual bite identity still readable: "+contract[0]);
            }
            ClassNode twilight=read(jars,"twilightforest/block/Experiment115Block");
            if(twilight!=null)check(literals(twilight).contains("omnomnom"),"experiment's real bite property name remains omnomnom");
            ClassNode fruitBase=read(jars,"dev/xkmc/fruitsdelight/content/block/BaseCakeBlock");
            if(fruitBase!=null)check(fruitBase.fields.stream().anyMatch(field->field.name.equals("plate")),"fruit empty-plate behavior remains host owned");
            checkScopeBridge();
            checkVanillaBridge();
            checkCombatBridge();
            checkFoodSlotBridge();
            System.out.println("PlacedFoodContractTest: "+checks+" checks passed");
        }finally{for(JarFile jar:jars)jar.close();}
    }
    private static void checkScopeBridge()throws Exception {
        ClassNode hook=new ClassNode();
        try(var input=PlacedFoodContractTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/mixin/food/PlacedFoodContextMixin.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(hook,0);
        }
        String expected="Lnet/minecraft/world/level/block/Block;use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;";
        boolean found=false;
        for(MethodNode method:hook.methods) {
            List<AnnotationNode> annotations=new ArrayList<>();if(method.visibleAnnotations!=null)annotations.addAll(method.visibleAnnotations);if(method.invisibleAnnotations!=null)annotations.addAll(method.invisibleAnnotations);
            for(AnnotationNode annotation:annotations)if(annotation.desc.endsWith("/Redirect;")) {
                var at=(AnnotationNode)value(annotation,"at");found=expected.equals(value(at,"target"));
            }
        }
        check(found,"scope surrounds the actual Block.use delegate");
    }
    private static Set<String> literals(ClassNode node) {
        Set<String> values=new HashSet<>();for(var method:node.methods)for(var instruction:method.instructions)
            if(instruction instanceof LdcInsnNode literal&&literal.cst instanceof String text)values.add(text);return values;
    }
    private static void checkVanillaBridge() throws Exception {
        ClassNode item=new ClassNode();
        try(var input=PlacedFoodContractTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/mixin/food/VanillaFoodPropertiesMixin.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(item,0);
        }
        var method=item.methods.stream().filter(m->m.name.equals("getFoodProperties")&&m.desc.equals(
                "(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/food/FoodProperties;")).findFirst().orElseThrow();
        boolean nativeLookup=false,quality=false;
        for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call) {
            if(call.owner.equals("net/minecraft/world/item/Item")&&call.name.equals("getFoodProperties"))nativeLookup=true;
            if(call.owner.endsWith("/CuisineVanillaBonuses")&&call.name.equals("properties"))quality=true;
        }
        check(nativeLookup&&quality,"stack quality retains host stack-aware food properties for actual eating and AppleSkin");
        ClassNode food=new ClassNode();
        try(var input=PlacedFoodContractTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/mixin/food/FoodDataMixin.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(food,0);
        }
        var redirect=food.methods.stream().filter(m->m.name.equals("cuisinejournal$itemNutrition")).findFirst().orElseThrow();
        var annotation=redirect.visibleAnnotations.stream().filter(a->a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        check(value(annotation,"method").toString().contains("LivingEntity;"),"quality scope covers Forge three-argument FoodData overload");
        var at=(AnnotationNode)value(annotation,"at");
        check(Boolean.TRUE.equals(value(at,"remap")),"native nutrition delegate is remapped in release jars");
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/mixins.cuisinejournal.json"))).getAsJsonObject();
        boolean furnaceHook=false,smokerHook=false;
        for(var entry:config.getAsJsonArray("mixins")) {
            if(entry.getAsString().equals("speed.FurnaceSpeedMixin"))furnaceHook=true;
            if(entry.getAsString().equals("speed.SmokerSpeedMixin"))smokerHook=true;
        }
        check(!furnaceHook,"ordinary furnace acceleration hook is absent");
        check(smokerHook,"dedicated smoker acceleration hook is installed");
        ClassNode smoker=new ClassNode();
        try(var input=PlacedFoodContractTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/mixin/speed/SmokerSpeedMixin.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(smoker,0);
        }
        boolean smokerGuard=false,sharedTicker=false;
        for(var handler:smoker.methods) {
            for(var instruction:handler.instructions)if(instruction instanceof TypeInsnNode type
                    &&type.getOpcode()==org.objectweb.asm.Opcodes.INSTANCEOF
                    &&type.desc.equals("net/minecraft/world/level/block/entity/SmokerBlockEntity"))smokerGuard=true;
            if(handler.visibleAnnotations!=null)for(var injection:handler.visibleAnnotations)
                if(injection.desc.endsWith("/Redirect;")) {
                    sharedTicker=value(injection,"method").toString().contains("serverTick");
                    var progress=(AnnotationNode)value(injection,"at");
                    check(value(progress,"target").toString().endsWith(";cookingProgress:I"),
                            "smoker bonus only redirects native cooking progress");
                }
        }
        check(smokerGuard&&sharedTicker,"shared furnace ticker has an explicit smoker-only bonus guard");
    }
    private static Object value(AnnotationNode node,String name){for(int i=0;i<node.values.size();i+=2)if(node.values.get(i).equals(name))return node.values.get(i+1);return null;}
    private static void checkCombatBridge() throws Exception {
        ClassNode bridge=new ClassNode();
        try(var input=PlacedFoodContractTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/forge/ForgeEventBridge.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(bridge,0);
        }
        var attack=bridge.methods.stream().filter(m->m.name.equals("onFoodAttack")).findFirst().orElseThrow();
        check(attack.desc.contains("AttackEntityEvent;"),"attempted melee attack refreshes food combat delay");
        var hit=bridge.methods.stream().filter(m->m.name.equals("onFoodDamage")).findFirst().orElseThrow();
        check(hit.desc.contains("LivingAttackEvent;"),"incoming hit precedes absorption and final health loss");
        boolean owner=false;int refreshes=0;
        for(var instruction:hit.instructions)if(instruction instanceof MethodInsnNode call) {
            if(call.owner.equals("net/minecraft/world/damagesource/DamageSource")&&call.name.equals("getEntity"))owner=true;
            if(call.owner.endsWith("/CuisineFoodEvents")&&Set.of("enteredCombat","damaged").contains(call.name))refreshes++;
        }
        check(owner&&refreshes==2,"hit refreshes both defender and resolved player attacker including projectiles");
    }
    private static ClassNode read(List<JarFile> jars,String name)throws Exception {
        for(JarFile jar:jars){var entry=jar.getJarEntry(name+".class");if(entry==null)continue;try(var input=jar.getInputStream(entry)){ClassNode node=new ClassNode();new ClassReader(input).accept(node,0);return node;}}return null;
    }
    private static void checkFoodSlotBridge() throws Exception {
        ClassNode definition=compiled("CuisineFoodDefinition");
        var from=definition.methods.stream().filter(m->m.name.equals("from")).findFirst().orElseThrow();
        boolean explicit=false,automatic=false,healthUsesSlots=false,rounding=false;
        for(var instruction:from.instructions)if(instruction instanceof MethodInsnNode call) {
            if(call.owner.endsWith("CuisineFoodDefinitions$Values")&&call.name.equals("slotCost"))explicit=true;
            if(call.owner.endsWith("/CuisineFoodConfig")&&call.name.equals("slotsFor")&&call.desc.contains(";IF)"))automatic=true;
            if(call.owner.endsWith("/CuisineFoodMath")&&call.name.equals("foodHealthPoints")&&call.desc.equals("(IFID)F"))healthUsesSlots=true;
            if(call.owner.endsWith("/CuisineFoodMath")&&call.name.equals("roundedHealthPoints"))rounding=true;
        }
        check(explicit&&automatic&&healthUsesSlots&&rounding,"runtime resolves slot override before grade-specific rounded health");
        ClassNode config=compiled("CuisineFoodConfig");
        var slots=config.methods.stream().filter(m->m.name.equals("slotsFor")&&m.desc.endsWith(";IF)I")).findFirst().orElseThrow();
        int automaticIndex=-1,lastOverride=-1,index=0;
        for(var instruction:slots.instructions) {
            if(instruction instanceof FieldInsnNode field&&Set.of("TWO_SLOT_ITEMS","THREE_SLOT_ITEMS","TWO_SLOT","THREE_SLOT").contains(field.name))lastOverride=index;
            if(instruction instanceof MethodInsnNode call&&call.name.equals("automaticSlots"))automaticIndex=index;
            index++;
        }
        check(lastOverride>=0&&automaticIndex>lastOverride,"configured and tagged slot overrides precede automatic score fallback");
        ClassNode sync=compiled("CuisineFoodCatalogueSync");
        boolean syncedCost=false;
        for(var method:sync.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call
                &&call.owner.endsWith("/CuisineFoodDefinition")&&call.name.equals("slotCost"))syncedCost=true;
        check(syncedCost,"food catalogue sends resolved automatic slot cost to client preflight and tooltip");
        ClassNode service=compiled("CuisineFoodService");
        var consume=service.methods.stream().filter(m->m.name.equals("consume")).findFirst().orElseThrow();
        List<String> calls=new ArrayList<>();
        for(var instruction:consume.instructions)if(instruction instanceof MethodInsnNode call)calls.add(call.name);
        int eligibility=calls.indexOf("fillsCapacity"),before=calls.indexOf("synchronizeHealth"),after=calls.lastIndexOf("synchronizeHealth"),fill=calls.indexOf("fillNewCapacity");
        check(eligibility>calls.indexOf("findPlacement")&&before>eligibility&&after>before&&fill>after,
                "capacity reward measures authoritative max before and after successful food insertion");
        ClassNode events=compiled("CuisineFoodEvents");
        var tick=events.methods.stream().filter(m->m.name.equals("tick")).findFirst().orElseThrow();
        boolean periodicReward=false;
        for(var instruction:tick.instructions)if(instruction instanceof MethodInsnNode call&&call.name.equals("fillNewCapacity"))periodicReward=true;
        check(!periodicReward,"periodic food attribute updates never pay capacity reward");
    }
    private static ClassNode compiled(String name) throws Exception {
        ClassNode result=new ClassNode();
        try(var input=PlacedFoodContractTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/food/"+name+".class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(result,0);
        }
        return result;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
