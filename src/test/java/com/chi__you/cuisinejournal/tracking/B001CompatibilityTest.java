package com.chi__you.cuisinejournal.tracking;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Installed host APIs and multi-stage/multi-output provenance, without starting Minecraft. */
public final class B001CompatibilityTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        var first=new UUID(0,1);var second=new UUID(0,2);
        var ledger=new ProductionLedger();ledger.syncInput(0,"fruit",2,first);ledger.syncInput(6,"bottle",1,second);
        var primary=ledger.produce(Map.of(0,1,6,1),"drink",2,"fermenting");
        var secondary=new ProductionLedger.Batch("pickle",1,primary.contributors(),primary.ingredients(),primary.method());
        ledger.append(7,primary);ledger.append(8,secondary);
        var restored=ProductionLedger.load(ledger.save());
        check(restored.take(7,1).get(0).contributors().equals(Set.of(first,second)),"primary output preserves real ingredient and container contributors");
        check(restored.take(8,1).get(0).ingredients().equals(Set.of("fruit","bottle")),"secondary output retains the same consumed batch");
        check(restored.produce(Map.of(0,1),"next",1,"fermenting").contributors().equals(Set.of(first)),"second output does not consume the next recipe's inputs");
        var saved=ProductionLedger.readBatch(ProductionLedger.writeBatch(primary));
        check(saved.equals(primary),"intermediate baking stage batch survives serialization");
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(directory==null){System.out.println("SKIPPED: B001 installed-host checks; "+checks+" local checks passed (CUISINEJOURNAL_COMPAT_MODS unset)");return;}
        List<JarFile> jars=new ArrayList<>();
        try {
            try(var files=Files.list(Path.of(directory))){for(Path file:files.filter(p->p.toString().endsWith(".jar")).toList())jars.add(new JarFile(file.toFile()));}
            String[][] methods={
                {"com/phantomwing/rusticdelight/block/custom/PancakeBlock","getServingItem"},
                {"com/baisylia/culturaldelights/block/custom/EggplantFeastBlock","getServingItem"},
                {"com/chefmooon/ubesdelight/common/block/DrinkableFeastBlock","getServingItem"},
                {"com/chefmooon/ubesdelight/common/block/UbesDelightCakeBlock","getCakeSliceStack"},
                {"com/chefmooon/ubesdelight/common/block/LecheFlanFeastBlock","getCakeSliceStack"},
                {"com/chefmooon/ubesdelight/common/crafting/forge/BakingMatRecipeImpl","getResultList"},
                {"com/chefmooon/ubesdelight/common/block/entity/forge/BakingMatBlockEntityImpl","processItemUsingTool"},
                {"com/chefmooon/ubesdelight/common/block/entity/forge/BakingMatBlockEntityImpl","spawnRolledResults"},
                {"com/chefmooon/ubesdelight/common/block/entity/forge/UniversalLeafFeastBlockEntityImpl","removeItem"},
                {"net/ribs/vintagedelight/recipe/FermentingRecipe","getSecondaryResultItem"},
                {"net/ribs/vintagedelight/block/entity/FermentingJarBlockEntity","craftItem"},
                {"com/soytutta/mynethersdelight/common/block/entity/BlazierBlockEntity","cookTick"},
                {"com/soytutta/mynethersdelight/common/block/BreadLoafBlock","getBreadSliceItem"},
                {"com/soytutta/mynethersdelight/common/block/MagmaCakeBlock","getPieSliceItem"},
                {"net/brdle/collectorsreap/common/block/EffectCakeBlock","getSlice"},
                {"net/brdle/collectorsreap/common/block/EffectCandleCakeBlock","getCake"},
                {"net/brdle/collectorsreap/common/block/PanettoneBlock","getSliceItem"},
                {"com/dannbrown/braziliandelight/common/content/blocks/PieBlock","getPieSliceItem"},
                {"com/renyigesai/luncheonmeatsdelight/block/LuncheonMeatBlock","getSpawnLuncheonMeat"}};
            for(var contract:methods){var type=read(jars,contract[0]);if(type==null)continue;check(type.methods.stream().anyMatch(m->m.name.equals(contract[1])),"host getter/production target: "+Arrays.toString(contract));}
            String[][] fields={
                {"com/dannbrown/braziliandelight/common/content/blocks/CustomCakeBlock","sliceItem"},
                {"com/dannbrown/braziliandelight/common/content/blocks/CustomCandleCakeBlock","cakeBlock"},
                {"com/chefmooon/ubesdelight/common/block/leaf_feast/base/SimpleLeafFeastBlock","servingItem"},
                {"net/ribs/vintagedelight/block/entity/FermentingJarBlockEntity","itemHandler"},
                {"net/ribs/vintagedelight/screen/FermentingJarMenu","blockEntity"},
                {"com/soytutta/mynethersdelight/common/block/entity/BlazierBlockEntity","cookingProgress"}};
            for(var contract:fields){var type=read(jars,contract[0]);if(type==null)continue;check(type.fields.stream().anyMatch(f->f.name.equals(contract[1])),"audited field: "+Arrays.toString(contract));}
            for(String path:List.of("net/mcreator/oceanicdelight/procedures/SpongeCakeSliceEatenProcedure","net/mcreator/unusualdelight/procedures/GlowGummyOnBlockRightClickedProcedure")){
                var type=read(jars,path);if(type==null)continue;
                check(calls(type,"net/minecraft/world/food/FoodData","m_38705_")&&calls(type,"net/minecraft/world/food/FoodData","m_38717_"),"procedure still uses direct food/saturation setters: "+path);
            }
            var mold=read(jars,"net/ribs/vintagedelight/block/custom/CheeseMoldBlock");
            if(mold!=null)check(mold.methods.stream().flatMap(m->Arrays.stream(m.instructions.toArray())).anyMatch(i->i instanceof org.objectweb.asm.tree.IntInsnNode constant&&constant.operand==8400),"mold aging still uses native 8400 ticks");
            for(String path:List.of("com/chi__you/cuisinejournal/tracking/BrazilPotProduction","com/chi__you/cuisinejournal/tracking/CheeseMoldProduction")){
                var node=readOwn(path);check(node.methods.stream().anyMatch(m->m.name.equals("save"))&&node.methods.stream().anyMatch(m->m.name.equals("load")),"non-block-entity cooking contributions survive restart");
            }
            var wrapper=readOwn("com/chi__you/cuisinejournal/mixin/production/PlacedFoodForgeGiveMixin");
            check(calls(wrapper,"net/minecraftforge/items/IItemHandler","insertItem")
                    &&calls(wrapper,"net/minecraftforge/items/ItemHandlerHelper","insertItemStacked"),"Forge giving retains native insertion paths");
            var standalone=readOwn("com/chi__you/foodinteractions/B001InteractionAdapter");
            for(var method:standalone.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call)
                check(!call.owner.startsWith("com/chi__you/cuisinejournal/"),"standalone display module has no journal dependency");
            System.out.println("B001CompatibilityTest: "+checks+" checks passed");
        }finally{for(var jar:jars)jar.close();}
    }
    private static boolean calls(ClassNode node,String owner,String name){for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))return true;return false;}
    private static ClassNode read(List<JarFile> jars,String path)throws Exception {for(var jar:jars){var entry=jar.getJarEntry(path+".class");if(entry!=null)try(var in=jar.getInputStream(entry)){var node=new ClassNode();new ClassReader(in).accept(node,0);return node;}}return null;}
    private static ClassNode readOwn(String path)throws Exception {try(var in=B001CompatibilityTest.class.getClassLoader().getResourceAsStream(path+".class")){var node=new ClassNode();new ClassReader(Objects.requireNonNull(in)).accept(node,0);return node;}}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
