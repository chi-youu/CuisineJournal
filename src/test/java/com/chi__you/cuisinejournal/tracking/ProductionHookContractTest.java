package com.chi__you.cuisinejournal.tracking;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Checks injection targets against installed jars without bootstrapping a game. */
public final class ProductionHookContractTest {
    private static final String PACKAGE="com/chi__you/cuisinejournal/mixin/";
    public static void main(String[] args)throws Exception {
        checkIceRemovalTarget();
        checkIceServingBridge();
        checkCuisineBoardBridge();
        checkEffectLifecycleTargets();
        checkExtractedStamp();
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(directory==null){System.out.println("SKIPPED: ProductionHookContractTest installed-jar contracts (CUISINEJOURNAL_COMPAT_MODS unset)");return;}
        List<JarFile> jars=new ArrayList<>();int checks=0;
        try {
            try(var files=Files.list(Path.of(directory))) {for(Path path:files.filter(p->p.toString().endsWith(".jar")).toList())jars.add(new JarFile(path.toFile()));}
            var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/mixins.cuisinejournal.json"))).getAsJsonObject();
            List<String> failures=new ArrayList<>();
            for(var item:config.getAsJsonArray("mixins")) {
                String name=item.getAsString().replace('.','/');ClassNode hook=new ClassNode();
                try(var input=ProductionHookContractTest.class.getClassLoader().getResourceAsStream(PACKAGE+name+".class")){new ClassReader(Objects.requireNonNull(input,name)).accept(hook,0);}
                AnnotationNode mixin=annotations(hook.visibleAnnotations,hook.invisibleAnnotations).stream().filter(a->a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
                for(String target:strings(value(mixin,"targets"))) {
                    ClassNode actual=read(jars,target.replace('.','/'));if(actual==null)continue;
                    if(((hook.access ^ actual.access)&Opcodes.ACC_INTERFACE)!=0)
                        failures.add(name+" -> "+target+" class/interface target mismatch");
                    checks++;
                    for(MethodNode handler:hook.methods)for(AnnotationNode injection:annotations(handler.visibleAnnotations,handler.invisibleAnnotations)) {
                        if(!injection.desc.endsWith("/Inject;")&&!injection.desc.endsWith("/ModifyArg;")&&!injection.desc.endsWith("/Redirect;")&&!injection.desc.endsWith("/ModifyVariable;"))continue;
                        List<MethodNode> methods=new ArrayList<>();
                        for(String selector:strings(value(injection,"method")))for(MethodNode method:actual.methods)if(selector.equals(method.name)||selector.equals(method.name+method.desc))methods.add(method);
                        if(methods.isEmpty()){failures.add(name+" -> "+target+" missing "+value(injection,"method"));continue;}
                        for(MethodNode method:methods) {
                            if(((method.access^handler.access)&Opcodes.ACC_STATIC)!=0)
                                failures.add(name+" static/instance injection mismatch for "+target+"."+method.name);
                            if(injection.desc.endsWith("/Inject;")) {
                                boolean returns=Type.getReturnType(method.desc).getSort()!=Type.VOID;
                                if(returns!=handler.desc.contains("CallbackInfoReturnable;"))failures.add(name+" wrong callback for "+target+"."+method.name+method.desc);
                                if(List.of("Kaleidoscope","LetsDo","KitchenKarrot","Barbeques","DrinkBeer","CrockPot",
                                        "Casualness","Bountiful","SimpleCream","Latiao","Papercraft","FarmersRespite","Cosmopolitan","Grilling")
                                        .stream().anyMatch(name::startsWith)){
                                    Type[] expected=Type.getArgumentTypes(method.desc),provided=Type.getArgumentTypes(handler.desc);
                                    int callback=-1;
                                    for(int i=0;i<provided.length;i++)if(provided[i].getClassName().startsWith("org.spongepowered.asm.mixin.injection.callback.CallbackInfo")){callback=i;break;}
                                    if(callback!=0&&callback!=expected.length)failures.add(name+" wrong captured argument count for "+target+"."+method.name);
                                    if(callback==expected.length)for(int i=0;i<expected.length;i++)
                                        if(!provided[i].equals(expected[i])&&!provided[i].getDescriptor().equals("Ljava/lang/Object;")
                                                &&!provided[i].getDescriptor().equals("Lnet/minecraft/world/level/block/entity/BlockEntity;"))
                                            failures.add(name+" argument "+i+" mismatch for "+target+"."+method.name+": "+provided[i]+" != "+expected[i]);
                                }
                                checks++;
                            }
                            Object at=value(injection,"at");List<?> ats=at instanceof List<?> list?list:at==null?List.of():List.of(at);
                            for(Object raw:ats)if(raw instanceof AnnotationNode annotation&&"INVOKE".equals(value(annotation,"value"))) {
                                String invocation=(String)value(annotation,"target");int matches=0;
                                for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&invocation.equals("L"+call.owner+";"+call.name+call.desc))matches++;
                                Object ordinal=value(annotation,"ordinal");
                                boolean found=matches>0&&(ordinal==null||((Integer)ordinal)<0||matches>((Integer)ordinal));
                                if(!found)failures.add(name+" -> "+target+"."+method.name+" missing call "+invocation);checks++;
                            }
                            for(Object raw:ats)if(raw instanceof AnnotationNode annotation&&"FIELD".equals(value(annotation,"value"))) {
                                String selector=(String)value(annotation,"target");
                                Object opcode=value(annotation,"opcode");int matches=0;
                                for(var instruction:method.instructions)if(instruction instanceof FieldInsnNode field
                                        &&selector.equals("L"+field.owner+";"+field.name+":"+field.desc)
                                        &&(opcode==null||field.getOpcode()==((Integer)opcode)))matches++;
                                Object ordinal=value(annotation,"ordinal");
                                if(matches==0||ordinal instanceof Integer index&&index>=matches)
                                    failures.add(name+" -> "+target+"."+method.name+" missing field access "+selector);
                                checks++;
                            }
                        }
                    }
                }
            }
            ClassNode cake=read(jars,"com/aizistral/enigmaticlegacy/blocks/BlockCosmicCake");
            if(cake!=null) {
                var eat=cake.methods.stream().filter(method->method.name.equals("eat")&&method.desc.equals(
                        "(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/InteractionResult;"))
                        .findFirst().orElseThrow();
                if((eat.access&Opcodes.ACC_STATIC)==0)failures.add("Cosmic cake eat is no longer static");
                boolean directHunger=false,regeneration=false;
                for(var instruction:eat.instructions)if(instruction instanceof MethodInsnNode call
                        &&call.owner.equals("net/minecraft/world/food/FoodData")&&call.name.equals("m_38707_"))directHunger=true;
                for(MethodNode method:cake.methods)if(method.name.equals("m_213898_"))regeneration=true;
                if(!directHunger||!regeneration)failures.add("Cosmic cake direct-eating/regeneration contract changed");
                checks+=2;
            }
            ClassNode potRecipe=read(jars,"dev/xkmc/youkaishomecoming/content/pot/cooking/core/PotCookingRecipe");
            ClassNode potBlock=read(jars,"dev/xkmc/youkaishomecoming/content/block/food/PotFoodBlock");
            ClassNode potCategory=read(jars,"dev/xkmc/youkaishomecoming/compat/jei/PotCookingRecipeCategory");
            if(potRecipe!=null&&potBlock!=null&&potCategory!=null) {
                if(potRecipe.methods.stream().noneMatch(method -> method.name.equals("getInput")
                        && method.desc.equals("()Ljava/util/List;")))failures.add("Pot recipe getInput contract changed");
                if(potBlock.methods.stream().noneMatch(method -> method.name.equals("asBowls")
                        && method.desc.equals("()Lnet/minecraft/world/item/ItemStack;")))failures.add("Pot bowl output contract changed");
                boolean bowlsInJei=false;
                for(var method:potCategory.methods)if(method.name.equals("setRecipe"))for(var instruction:method.instructions)
                    if(instruction instanceof MethodInsnNode call&&call.owner.equals(potBlock.name)
                            &&call.name.equals("asBowls"))bowlsInJei=true;
                if(!bowlsInJei)failures.add("Youkai JEI no longer maps pot recipes to bowls");
                var bowls=potBlock.methods.stream().filter(method -> method.name.equals("asBowls")).findFirst().orElseThrow();
                boolean servingItem=false,servingCount=false;
                for(var instruction:bowls.instructions)if(instruction instanceof FieldInsnNode field) {
                    if(field.name.equals("food"))servingItem=true;
                    if(field.name.equals("serve"))servingCount=true;
                }
                if(!servingItem||!servingCount)failures.add("Pot asBowls no longer reads actual food and serving count");
                checks+=4;
                var recipeFile="data/youkaishomecoming/recipes/pot_of_power_soup.json";
                for(JarFile jar:jars) {
                    var row=jar.getJarEntry(recipeFile);if(row==null)continue;
                    try(var reader=new java.io.InputStreamReader(jar.getInputStream(row),java.nio.charset.StandardCharsets.UTF_8)) {
                        var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        boolean boar=false;
                        for(var raw:json.getAsJsonArray("input"))if(raw.isJsonObject()&&raw.getAsJsonObject().has("tag")
                                &&raw.getAsJsonObject().get("tag").getAsString().equals("forge:raw_boar"))boar=true;
                        if(!boar||!json.getAsJsonObject("result").get("item").getAsString()
                                .equals("youkaishomecoming:pot_of_power_soup"))failures.add("Power soup source recipe changed");
                        checks++;
                    }
                }
            }
            ClassNode delightfulUtil=read(jars,"net/brnbrd/delightful/Util");
            ClassNode configured=read(jars,"net/brnbrd/delightful/common/item/IConfigured");
            ClassNode tea=read(jars,"net/brnbrd/delightful/common/item/food/GreenTeaLeavesItem");
            if(delightfulUtil!=null&&configured!=null&&tea!=null) {
                var enabled=delightfulUtil.methods.stream().filter(method -> method.name.equals("enabled")
                        &&method.desc.equals("(Lnet/minecraft/world/item/Item;)Z")).findFirst().orElseThrow();
                boolean delegates=false;
                for(var instruction:enabled.instructions)if(instruction instanceof MethodInsnNode call
                        &&call.owner.equals(configured.name)&&call.name.equals("enabled"))delegates=true;
                if(!delegates)failures.add("Delightful availability no longer delegates to IConfigured.enabled");
                var configuredEnabled=configured.methods.stream().filter(method -> method.name.equals("enabled")).findFirst().orElseThrow();
                Set<String> guards=new HashSet<>();
                for(var instruction:configuredEnabled.instructions)if(instruction instanceof MethodInsnNode call)guards.add(call.name);
                if(!guards.containsAll(Set.of("configEnabled","isLoaded","hasConflict","isDependencyTag","isEmptyTag")))
                    failures.add("Delightful enabled no longer checks all host conditions");
                var conflicts=tea.methods.stream().filter(method -> method.name.equals("getConflicts")).findFirst().orElseThrow();
                Set<String> mods=new HashSet<>();
                for(var instruction:conflicts.instructions)if(instruction instanceof FieldInsnNode field)mods.add(field.name);
                if(!mods.containsAll(Set.of("FR","YH","CT")))failures.add("Green tea leaf conflict rules changed");
                checks+=3;
            }
            ClassNode cuttingRecipe = read(jars,"vectorwing/farmersdelight/common/crafting/CuttingBoardRecipe");
            if (cuttingRecipe != null) {
                if (cuttingRecipe.methods.stream().noneMatch(method -> method.name.equals("getResults")
                        && method.desc.equals("()Ljava/util/List;"))) failures.add("Cutting recipe multi-output API changed");
                var classifier = new ClassNode();
                try (var input = ProductionHookContractTest.class.getClassLoader().getResourceAsStream(
                        "com/chi__you/cuisinejournal/recipe/RecipeClassifier.class")) {
                    new ClassReader(Objects.requireNonNull(input)).accept(classifier,0);
                }
                if (classifier.methods.stream().noneMatch(method -> method.name.equals("classifyAll")))
                    failures.add("Catalogue no longer exposes multi-output classification");
                checks += 2;
            }
            String[][] portionChecks = {
                    {"trailandtales_delight", "cutting/cooked_sniffer_egg_block", "fried_sniffer_egg", "cooked_sniffer_egg_block"},
                    {"aquamirae_delight", "cutting/fishermans_delicacy", "fishermans_delicacy_slice", "fishermans_delicacy"},
                    {"trailandtales_delight", "cutting/cherry_cheese_pie", "cherry_cheese_pie_slice", "cherry_cheese_pie"}
            };
            for (String[] relation : portionChecks) {
                String path = "data/"+relation[0]+"/recipes/"+relation[1]+".json";
                for (JarFile jar : jars) {
                    var file = jar.getJarEntry(path);
                    if (file == null) continue;
                    try (var reader = new java.io.InputStreamReader(jar.getInputStream(file),java.nio.charset.StandardCharsets.UTF_8)) {
                        var recipe = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        boolean output = false;
                        for (var value : recipe.getAsJsonArray("result"))
                            if ((relation[0]+":"+relation[2]).equals(value.getAsJsonObject().get("item").getAsString())) output=true;
                        String source = recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString();
                        if (!output || !source.equals(relation[0]+":"+relation[3])) failures.add("Portion relationship changed: "+path);
                        if (relation[2].equals("fried_sniffer_egg") && !recipe.getAsJsonArray("result").get(1)
                                .getAsJsonObject().get("item").getAsString().equals("trailandtales_delight:fried_sniffer_egg"))
                            failures.add("Sniffer egg secondary output changed");
                    }
                    checks++;
                    break;
                }
            }
            var tracker = new ClassNode();
            try (var input = ProductionHookContractTest.class.getClassLoader().getResourceAsStream(
                    "com/chi__you/cuisinejournal/tracking/ProductionTracker.class")) {
                new ClassReader(Objects.requireNonNull(input)).accept(tracker,0);
            }
            boolean realStamp=false, markedMenu=false;
            for (var method : tracker.methods) for (var instruction : method.instructions) {
                if (method.name.equals("credit") && instruction instanceof MethodInsnNode call
                        && call.owner.endsWith("/MealStamp") && call.name.equals("read")) realStamp=true;
                if (method.name.equals("finish") && instruction instanceof FieldInsnNode field
                        && field.name.equals("marked")) markedMenu=true;
            }
            if(!realStamp||!markedMenu)failures.add("Preparation activities no longer receive the actual stamped result");
            checks+=2;
            var ice=read(jars,"show/tatd/mod/block/ice_cream/IceBucketBlock");
            var feast=read(jars,"vectorwing/farmersdelight/common/block/FeastBlock");
            if (ice!=null && feast!=null) {
                boolean freezeTransition=false, actualServe=false;
                for (var method:ice.methods) if(method.name.equals("m_213898_")) for(var instruction:method.instructions)
                    if(instruction instanceof MethodInsnNode call&&call.name.equals("getIceCreamBucketBlock"))freezeTransition=true;
                for(var method:feast.methods) if(method.name.equals("takeServing")) for(var instruction:method.instructions)
                    if(instruction instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/world/entity/player/Inventory")
                            &&call.name.equals("m_36054_"))actualServe=true;
                if(!freezeTransition||!actualServe)failures.add("Ice bucket native freezing/serving entry changed");
                checks+=2;
            }
            if(!failures.isEmpty())throw new AssertionError(String.join("\n",failures));
            System.out.println("ProductionHookContractTest: "+checks+" installed-jar contracts passed");
        } finally {for(JarFile jar:jars)jar.close();}
    }
    /** An inherited Java method is not a valid injection target on the subclass. */
    private static void checkCuisineBoardBridge() throws Exception {
        ClassNode board=new ClassNode();
        try(var input=ProductionHookContractTest.class.getClassLoader().getResourceAsStream(PACKAGE+"production/YoukaiCuisineBoardProductionMixin.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(board,0);
        }
        var cut=board.methods.stream().filter(method->method.name.equals("cuisinejournal$cutOutput")).findFirst().orElseThrow();
        Set<String> calls=new HashSet<>();
        for(var instruction:cut.instructions)if(instruction instanceof MethodInsnNode call)calls.add(call.name);
        if(!calls.containsAll(Set.of("boardResult","read","write")))throw new AssertionError("Cuisine board cutting must claim once and inherit the completed meal stamp");
        boolean guarded=false;
        for(var instruction:cut.instructions)if(instruction instanceof FieldInsnNode field&&field.name.equals("cuisinejournal$cutClaimed"))guarded=true;
        if(!guarded)throw new AssertionError("Cuisine cutting multi-output guard missing");
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/cuisinejournal/cuisinejournal/compat/workstations/youkai_solid.json"))).getAsJsonObject();
        boolean mapped=false;
        for(var value:config.getAsJsonArray("workstations"))if(value.getAsJsonObject().get("block").getAsString().equals("youkaishomecoming:cuisine_board"))mapped=true;
        if(!mapped)throw new AssertionError("Cuisine board workstation mapping missing");
        System.out.println("Cuisine board: cutting claim, stamp inheritance and workstation mapping verified");
    }
    private static void checkEffectLifecycleTargets() throws Exception {
        var actual=new ClassNode();
        try(var input=ProductionHookContractTest.class.getClassLoader().getResourceAsStream("net/minecraft/world/entity/LivingEntity.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(actual,0);
        }
        Map<String,String> methods=Map.of("removeEffectNoUpdate","(Lnet/minecraft/world/effect/MobEffect;)Lnet/minecraft/world/effect/MobEffectInstance;",
                "onEffectRemoved","(Lnet/minecraft/world/effect/MobEffectInstance;)V","curePotionEffects","(Lnet/minecraft/world/item/ItemStack;)Z");
        methods.forEach((name,desc)->{
            if(actual.methods.stream().noneMatch(method->method.name.equals(name)&&method.desc.equals(desc)))
                throw new AssertionError("Effect lifecycle target missing: "+name+desc);
        });
        var instance=new ClassNode();
        try(var input=ProductionHookContractTest.class.getClassLoader().getResourceAsStream("net/minecraft/world/effect/MobEffectInstance.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(instance,0);
        }
        if(instance.fields.stream().noneMatch(field->field.name.equals("duration")&&field.desc.equals("I")))throw new AssertionError("Effect duration accessor field missing");
        System.out.println("Effect lifecycle: removal, curative item and duration targets verified");
    }
    private static void checkExtractedStamp() throws Exception {
        var hook=new ClassNode();
        try(var input=ProductionHookContractTest.class.getClassLoader().getResourceAsStream(PACKAGE+"production/ProductionSlotMixin.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(hook,0);
        }
        boolean stamp=false;
        for(var method:hook.methods)for(var instruction:method.instructions)
            if(instruction instanceof MethodInsnNode call&&call.name.equals("markExtracted"))stamp=true;
        if(!stamp)throw new AssertionError("Actual returned slot item must retain the prepared meal stamp");
        var tracker=new ClassNode();
        try(var input=ProductionHookContractTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/tracking/ProductionTracker.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(tracker,0);
        }
        var restore=tracker.methods.stream().filter(method->method.name.equals("restoreRemainder")).findFirst().orElseThrow();
        boolean identity=false,count=false;
        for(var instruction:restore.instructions) {
            if(instruction.getOpcode()==Opcodes.IF_ACMPEQ||instruction.getOpcode()==Opcodes.IF_ACMPNE)identity=true;
            if(instruction instanceof MethodInsnNode call&&call.name.equals("getCount"))count=true;
        }
        if(!identity||!count)throw new AssertionError("Remainder cleanup must verify original live object and remaining quantity");
        var mark=tracker.methods.stream().filter(method->method.name.equals("markExtracted")).findFirst().orElseThrow();
        boolean prepared=false,newCook=false;
        for(var instruction:mark.instructions)if(instruction instanceof MethodInsnNode call) {
            if(call.owner.endsWith("/MealStamp")&&call.name.equals("write"))prepared=true;
            if(call.name.equals("stampOutput"))newCook=true;
        }
        if(!prepared||newCook)throw new AssertionError("Returned result must reuse prepared ownership, not fabricate a new cook");
        System.out.println("Output stamp: returned result, exact remainder and provenance checks passed");
    }

    private static void checkIceRemovalTarget() throws Exception {
        ClassNode hook = new ClassNode();
        try (var input = ProductionHookContractTest.class.getClassLoader().getResourceAsStream(PACKAGE+"production/IceBucketRemovalMixin.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(hook,0);
        }
        var mixin = annotations(hook.visibleAnnotations,hook.invisibleAnnotations).stream()
                .filter(annotation -> annotation.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        var targets = (List<?>) value(mixin,"value");
        String target = ((Type) targets.get(0)).getInternalName();
        ClassNode actual = new ClassNode();
        try (var input = ProductionHookContractTest.class.getClassLoader().getResourceAsStream(target+".class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(actual,0);
        }
        String descriptor = "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                +"Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V";
        for (var handler : hook.methods) for (var annotation : annotations(handler.visibleAnnotations,handler.invisibleAnnotations)) {
            if (!annotation.desc.endsWith("/Inject;")) continue;
            for (String selector : strings(value(annotation,"method"))) {
                if (actual.methods.stream().noneMatch(method -> method.name.equals(selector) && method.desc.equals(descriptor)))
                    throw new AssertionError("Ice bucket removal target does not declare "+selector+descriptor+": "+target);
            }
        }
        System.out.println("Ice removal target: declared method verified");
    }
    private static void checkIceServingBridge() throws Exception {
        var config=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/mixins.cuisinejournal.json"))).getAsJsonObject();
        if(config.getAsJsonArray("mixins").asList().stream().anyMatch(entry -> entry.getAsString().endsWith(".TrailIceCreamServingMixin")))
            throw new AssertionError("Conflicting FeastBlock serving redirect is still registered");
        var spoon=new ClassNode();
        try(var input=ProductionHookContractTest.class.getClassLoader().getResourceAsStream(PACKAGE+"food/TrailIceCreamSpoonMixin.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(spoon,0);
        }
        for(var method:spoon.methods) for(var annotation:annotations(method.visibleAnnotations,method.invisibleAnnotations))
            if(annotation.desc.endsWith("/Redirect;"))throw new AssertionError("Spoon observer must not replace host calls");
        var bridge=new ClassNode();
        try(var input=ProductionHookContractTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/food/PlacedFoodConsumption.class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(bridge,0);
        }
        for(String name:List.of("inventoryOutput","itemEntitySpawned","iceCreamSpoonOutput")) {
            boolean observed=false;
            for(var method:bridge.methods) if(method.name.equals(name)) for(var instruction:method.instructions)
                if(instruction instanceof MethodInsnNode call&&call.name.equals("iceCreamOutput")) observed=true;
            if(!observed)throw new AssertionError("Missing ice cream delivery observer: "+name);
        }
        var delivery=bridge.methods.stream().filter(method -> method.name.equals("iceCreamOutput")).findFirst().orElseThrow();
        boolean duplicateGuard=false;
        for(var instruction:delivery.instructions)if(instruction instanceof MethodInsnNode call
                &&call.owner.equals("java/util/Set")&&call.name.equals("contains")) duplicateGuard=true;
        if(!duplicateGuard)throw new AssertionError("Inventory fallback drop can settle the same serving twice");
        System.out.println("Ice serving bridge: passive inventory/drop/spoon observers and duplicate guard verified");
    }
    private static ClassNode read(List<JarFile> jars,String name)throws Exception {
        for(JarFile jar:jars){var entry=jar.getJarEntry(name+".class");if(entry==null)continue;try(var input=jar.getInputStream(entry)){ClassNode result=new ClassNode();new ClassReader(input).accept(result,0);return result;}}return null;
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> first,List<AnnotationNode> second){List<AnnotationNode> result=new ArrayList<>();if(first!=null)result.addAll(first);if(second!=null)result.addAll(second);return result;}
    private static Object value(AnnotationNode annotation,String key){if(annotation.values!=null)for(int i=0;i<annotation.values.size();i+=2)if(annotation.values.get(i).equals(key))return annotation.values.get(i+1);return null;}
    private static List<String> strings(Object value){if(value instanceof List<?> list)return list.stream().map(Object::toString).toList();return value instanceof String text?List.of(text):List.of();}
}
