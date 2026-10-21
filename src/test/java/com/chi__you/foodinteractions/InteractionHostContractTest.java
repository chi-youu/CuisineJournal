package com.chi__you.foodinteractions;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/** Confirms read-only adapters and the optional Lychee mixin against actual installed jars. */
public final class InteractionHostContractTest {
    public static void main(String[] args)throws Exception {
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(directory==null){System.out.println("InteractionHostContractTest: installed jars not configured");return;}
        List<JarFile> jars=new ArrayList<>();int checks=0;
        try {
            try(var files=Files.list(Path.of(directory))) {
                for(Path file:files.filter(path->path.toString().endsWith(".jar")).toList())jars.add(new JarFile(file.toFile()));
            }
            String[][] methods={
                    {"dev/xkmc/youkaishomecoming/content/pot/table/recipe/CuisineRecipe","base","()Lnet/minecraft/resources/ResourceLocation;"},
                    {"dev/xkmc/youkaishomecoming/content/pot/table/recipe/CuisineRecipe","getCustomIngredients","()Ljava/util/List;"},
                    {"dev/xkmc/youkaishomecoming/content/pot/table/item/IngredientTableItem","collectIngredients","(Ljava/util/List;)V"},
                    {"dev/xkmc/youkaishomecoming/content/pot/table/item/VariantTableItemBase","collectIngredients","(Ljava/util/List;Ljava/util/List;)V"},
                    {"dev/xkmc/fruitsdelight/content/cauldrons/CauldronRecipe","getBlockInput","()Lnet/minecraft/world/item/ItemStack;"},
                    {"dev/xkmc/fruitsdelight/content/cauldrons/CauldronRecipe","getItemInput","()Lnet/minecraft/world/item/ItemStack;"},
                    {"dev/xkmc/fruitsdelight/content/cauldrons/CauldronRecipe","getBlockOutput","()Lnet/minecraft/world/item/ItemStack;"},
                    {"dev/xkmc/fruitsdelight/content/cauldrons/CauldronRecipe","getItemOutput","()Lnet/minecraft/world/item/ItemStack;"},
                    {"dev/xkmc/fruitsdelight/init/food/FruitType","getJelly","()Lnet/minecraft/world/item/Item;"},
                    {"dev/xkmc/fruitsdelight/init/food/FruitType","getJello","()Lnet/minecraft/world/item/Item;"},
                    {"show/tatd/mod/block/ice_cream/IceBucketBlock","getIceCreamMixItem","(I)Lnet/minecraft/world/item/Item;"},
                    {"show/tatd/mod/block/ice_cream/IceBucketBlock","getIceCreamBucketBlock","(I)Lnet/minecraft/world/level/block/Block;"},
                    {"vectorwing/farmersdelight/common/block/FeastBlock","getServingItem","(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;"},
                    {"vectorwing/farmersdelight/common/block/FeastBlock","getServingsProperty","()Lnet/minecraft/world/level/block/state/properties/IntegerProperty;"},
                    {"vectorwing/farmersdelight/common/block/PieBlock","getPieSliceItem","()Lnet/minecraft/world/item/ItemStack;"},
                    {"vectorwing/farmersdelight/common/utility/ItemUtils","isKnife","(Lnet/minecraft/world/item/ItemStack;)Z"},
                    {"com/starmeow/smc/blocks/BaseFeastBlock","getServingItem","(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;"},
                    {"com/starmeow/smc/blocks/BaseFeastBlock","getServingsProperty","()Lnet/minecraft/world/level/block/state/properties/IntegerProperty;"},
                    {"com/renyigesai/immortalers_delight/util/ItemUtils","isKnives","(Lnet/minecraft/world/item/ItemStack;)Z"},
                    {"com/renyigesai/immortalers_delight/block/StackedFoodBlock","isCuttable","(Lnet/minecraft/world/item/ItemStack;)Z"},
                    {"com/renyigesai/immortalers_delight/block/food/LargeColumnBlock","isCuttable","(Lnet/minecraft/world/item/ItemStack;)Z"},
                    {"snownee/lychee/core/def/BlockPredicateHelper","getMatchedItemStacks","(Lnet/minecraft/advancements/critereon/BlockPredicate;)Ljava/util/List;"},
                    {"snownee/lychee/core/def/BlockPredicateHelper","getMatchedBlocks","(Lnet/minecraft/advancements/critereon/BlockPredicate;)Ljava/util/Set;"},
                    {"snownee/lychee/core/recipe/LycheeRecipe","getPostActions","()Ljava/util/stream/Stream;"},
                    {"snownee/lychee/core/post/DropItem","getItemOutputs","()Ljava/util/List;"},
                    {"snownee/lychee/core/recipe/ItemAndBlockRecipe","getBlock","()Lnet/minecraft/advancements/critereon/BlockPredicate;"}
            };
            for(String[] contract:methods) {
                ClassNode node=read(jars,contract[0]);
                if(node==null)continue;
                if((node.access&Opcodes.ACC_INTERFACE)!=0)throw new AssertionError("Expected class target: "+contract[0]);
                if(node.methods.stream().noneMatch(method->method.name.equals(contract[1])&&method.desc.equals(contract[2])))
                    throw new AssertionError("Missing adapter API: "+java.util.Arrays.toString(contract));
                checks++;
            }
            var fruitTable=read(jars,"dev/xkmc/fruitsdelight/content/cauldrons/CauldronRecipe");
            var chorus=read(jars,"dev/xkmc/fruitsdelight/content/effects/ChorusEffect");
            if(chorus!=null) {
                var trigger=chorus.methods.stream().filter(method->method.name.equals("m_6584_")&&method.desc.equals("(II)Z")).findFirst().orElseThrow();
                boolean oneTick=false;
                for(var instruction:trigger.instructions)if(instruction.getOpcode()==Opcodes.ICONST_1)oneTick=true;
                boolean teleport=false;
                for(var method:chorus.methods)if(method.name.equals("m_6742_"))for(var instruction:method.instructions)
                    if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.name.equals("teleport"))teleport=true;
                if(!oneTick||!teleport)throw new AssertionError("Chorus one-tick teleport implementation changed");
                checks++;
            }
            if(fruitTable!=null) {
                if(fruitTable.fields.stream().noneMatch(field->field.name.equals("LIST")&&field.desc.equals("Ljava/util/List;")
                        &&(field.access&Opcodes.ACC_PUBLIC)!=0&&(field.access&Opcodes.ACC_STATIC)!=0))
                    throw new AssertionError("Fruit cauldron table contract changed");
                checks++;
                for(String type:List.of("JellyCraftShapelessRecipe","JellyCraftShapedRecipe")) {
                    var recipe=read(jars,"dev/xkmc/fruitsdelight/content/recipe/"+type);
                    boolean root=false,fruitName=false;
                    for(var method:recipe.methods)for(var instruction:method.instructions) {
                        if(instruction instanceof org.objectweb.asm.tree.LdcInsnNode value&&"JellyEffectRoot".equals(value.cst))root=true;
                        if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.owner.endsWith("/FruitType")&&call.name.equals("name"))fruitName=true;
                    }
                    if(!root||!fruitName)throw new AssertionError("Host no longer writes fruit names to JellyEffectRoot: "+type);
                    checks++;
                }
            }
            ClassNode serializer=read(jars,"snownee/lychee/interaction/BlockInteractingRecipe$Serializer");
            if(serializer!=null) {
                var method=serializer.methods.stream().filter(value->value.name.equals("fromJson")
                        &&value.desc.startsWith("(Lsnownee/lychee/interaction/BlockInteractingRecipe;")).findFirst().orElseThrow();
                boolean firstHand=false,ingredientParser=false;
                for(var instruction:method.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call) {
                    if(call.owner.equals("com/google/gson/JsonArray")&&call.name.equals("get"))firstHand=true;
                    if(call.name.equals("parseIngredientOrAir")&&call.desc.equals("(Lcom/google/gson/JsonElement;)Lnet/minecraft/world/item/crafting/Ingredient;"))ingredientParser=true;
                }
                if(!firstHand||!ingredientParser)throw new AssertionError("Lychee no longer parses each hand as an independent ingredient");
                checks++;
                var parser=read(jars,"snownee/lychee/core/recipe/LycheeRecipe$Serializer").methods.stream()
                        .filter(value->value.name.equals("parseIngredientOrAir")).findFirst().orElseThrow();
                boolean delegates=false;
                for(var instruction:parser.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        &&call.owner.equals("net/minecraft/world/item/crafting/Ingredient")&&call.name.equals("m_43917_"))delegates=true;
                if(!delegates)throw new AssertionError("Lychee ingredient alternatives no longer delegate to Forge Ingredient.fromJson");
                checks++;
            }
            for(String type:List.of("show/tatd/mod/block/StuffedEggSnifferEggBlock","show/tatd/mod/block/SnifferEggSoupBlock")) {
                ClassNode wrapper=read(jars,type);if(wrapper==null)continue;
                var method=wrapper.methods.stream().filter(value->value.name.equals("takeServing")).findFirst().orElseThrow();
                boolean delegates=false;
                for(var instruction:method.instructions)if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        &&call.owner.equals("vectorwing/farmersdelight/common/block/FeastBlock")&&call.name.equals("takeServing"))delegates=true;
                if(!delegates)throw new AssertionError("Verified egg serving no longer delegates to FeastBlock: "+type);
                checks++;
            }
            ClassNode zea=read(jars,"com/renyigesai/immortalers_delight/block/food/ZeaPancakeBLock");
            if(zea!=null) {
                var cut=zea.methods.stream().filter(value->value.name.equals("cut")).findFirst().orElseThrow();
                boolean actualOutput=false;
                for(var instruction:cut.instructions)if(instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                        &&field.name.equals("ZEA_PANCAKE_SLICE"))actualOutput=true;
                if(!actualOutput)throw new AssertionError("Pancake cut output changed");
                checks++;
            }
            ClassNode stacked=read(jars,"com/renyigesai/immortalers_delight/block/StackedFoodBlock");
            if(stacked!=null)for(String field:List.of("servingItem","pileItem")) {
                if(stacked.fields.stream().noneMatch(value->value.name.equals(field)&&value.desc.equals("Ljava/util/function/Supplier;")&&(value.access&Opcodes.ACC_PUBLIC)!=0))
                    throw new AssertionError("Missing source/output supplier: "+field);
                checks++;
            }
            ClassNode jeiCategory=read(jars,"snownee/lychee/compat/jei/category/BaseJEICategory");
            if(jeiCategory!=null) {
                var click=jeiCategory.methods.stream().filter(method->method.name.equals("clickBlock")
                        &&method.desc.equals("(Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/platform/InputConstants$Key;)Z"))
                        .findFirst().orElseThrow();
                long sourceCalls=java.util.stream.StreamSupport.stream(click.instructions.spliterator(),false)
                        .filter(instruction->instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                                &&call.owner.equals("net/minecraft/world/level/block/Block")&&call.name.equals("m_5456_")
                                &&call.desc.equals("()Lnet/minecraft/world/item/Item;")).count();
                if(sourceCalls!=1)throw new AssertionError("Lychee block click source redirect must have exactly one target");
                checks++;
            }
            ClassNode predicateHelper=read(jars,"snownee/lychee/core/def/BlockPredicateHelper");
            if(predicateHelper!=null) {
                var tooltip=predicateHelper.methods.stream().filter(method->method.name.equals("getTooltips")
                        &&method.desc.equals("(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/advancements/critereon/BlockPredicate;)Ljava/util/List;"))
                        .findFirst().orElseThrow();
                long nameCalls=java.util.stream.StreamSupport.stream(tooltip.instructions.spliterator(),false)
                        .filter(instruction->instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                                &&call.owner.equals("net/minecraft/world/level/block/Block")&&call.name.equals("m_49954_")
                                &&call.desc.equals("()Lnet/minecraft/network/chat/MutableComponent;")).count();
                if(nameCalls!=1)throw new AssertionError("Lychee block tooltip name redirect must have exactly one target");
                checks++;
            }
            for(String config:List.of("src/main/resources/mixins.food_interaction_recipes.json",
                    "src/standalone/resources/mixins.food_interaction_recipes_standalone.json")) {
                var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of(config))).getAsJsonObject();
                var client=json.getAsJsonArray("client").asList().stream().map(com.google.gson.JsonElement::getAsString).toList();
                if(!client.containsAll(List.of("LycheeJeiBlockClickMixin","LycheeBlockNameMixin")))
                    throw new AssertionError("Missing client-only preview compatibility in "+config);
                checks++;
            }
            ClassNode pizza=read(jars,"net/jadenxgamer/netherexp/registry/block/custom/NetherPizzaBlock");
            if(pizza!=null) {
                var use=pizza.methods.stream().filter(method->method.name.equals("m_6227_")).findFirst().orElseThrow();
                boolean result=false,toolCheck=false;
                for(var instruction:use.instructions) {
                    if(instruction instanceof org.objectweb.asm.tree.FieldInsnNode field&&field.name.equals("NETHER_PIZZA_SLICE"))result=true;
                    if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                            &&(call.name.equals("isKnife")||call.name.equals("m_204117_")||call.name.equals("m_150930_")))toolCheck=true;
                }
                if(!result||toolCheck)throw new AssertionError("Pizza no-tool serving contract changed");
                checks++;
                if(pizza.fields.stream().noneMatch(field->field.name.equals("SLICES")&&(field.access&Opcodes.ACC_PUBLIC)!=0))
                    throw new AssertionError("Pizza SLICES no longer readable");
                checks++;
            }
            ClassNode cake=read(jars,"io/github/rcneg/legendarydelicacies/blocks/CommonCakeBlock");
            if(cake!=null) {
                if(cake.fields.stream().noneMatch(field->field.name.equals("sliceItem")&&field.desc.equals("Ljava/util/function/Supplier;")&&(field.access&Opcodes.ACC_PUBLIC)!=0))
                    throw new AssertionError("Common cake slice supplier no longer readable");
                checks++;
                var use=cake.methods.stream().filter(method->method.name.equals("m_6227_")).findFirst().orElseThrow();
                var literals=new java.util.HashSet<String>();
                for(var instruction:use.instructions)if(instruction instanceof org.objectweb.asm.tree.LdcInsnNode literal&&literal.cst instanceof String text)literals.add(text);
                if(!literals.containsAll(List.of("farmersdelight:tools/knives","forge:tools/knives")))
                    throw new AssertionError("Common cake knife tag contract changed");
                checks++;
                var cut=cake.methods.stream().filter(method->method.name.equals("cutSlice")).findFirst().orElseThrow();
                boolean supplier=false;
                for(var instruction:cut.instructions)if(instruction instanceof org.objectweb.asm.tree.FieldInsnNode field&&field.name.equals("sliceItem"))supplier=true;
                if(!supplier)throw new AssertionError("Common cake cuts a different output");
                checks++;
            }
            ClassNode machete=read(jars,"auviotre/enigmatic/delicacy/contents/items/EtheriumMachete");
            if(machete!=null) {
                var use=machete.methods.stream().filter(method->method.name.equals("onCakeInteraction")).findFirst().orElseThrow();
                boolean originalCake=false,slice=false,exactTool=false;
                for(var instruction:use.instructions) {
                    if(instruction instanceof org.objectweb.asm.tree.FieldInsnNode field) {
                        if(field.name.equals("COSMIC_CAKE")&&field.owner.equals("com/aizistral/enigmaticlegacy/registries/EnigmaticBlocks"))originalCake=true;
                        if(field.name.equals("COSMIC_CAKE_SLICE"))slice=true;
                    }
                    if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.name.equals("m_150930_"))exactTool=true;
                }
                if(!originalCake||!slice||!exactTool)throw new AssertionError("Cosmic cake exact-machete interaction changed");
                checks++;
                ClassNode items=read(jars,"auviotre/enigmatic/delicacy/registries/EnigmaticDelightItems");
                for(String name:List.of("ETHERIUM_MACHETE","COSMIC_CAKE_SLICE")) {
                    if(items.fields.stream().noneMatch(field->field.name.equals(name)&&(field.access&Opcodes.ACC_PUBLIC)!=0&&(field.access&Opcodes.ACC_STATIC)!=0))
                        throw new AssertionError("Cosmic cake registry field missing: "+name);
                    checks++;
                }
            }
            System.out.println("InteractionHostContractTest: "+checks+" installed-jar APIs passed");
        }finally{for(JarFile jar:jars)jar.close();}
    }
    private static ClassNode read(List<JarFile> jars,String name)throws Exception {
        for(JarFile jar:jars) {
            var entry=jar.getJarEntry(name+".class");if(entry==null)continue;
            try(var stream=jar.getInputStream(entry)) {
                ClassNode node=new ClassNode();new ClassReader(stream).accept(node,0);return node;
            }
        }
        return null;
    }
}
