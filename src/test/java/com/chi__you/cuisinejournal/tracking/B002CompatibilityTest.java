package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.food.CuisineFoodPlacement;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** B002 host contracts and release hook boundaries without loading third-party classes. */
public final class B002CompatibilityTest {
    private static int checks;
    private static void parfaitPlacementTests() {
        var fresh=new CuisineFoodPlacement.Slot(1,4000,4800,true);
        var ending=new CuisineFoodPlacement.Slot(1,100,4800,true);
        var expired=new CuisineFoodPlacement.Slot(1,0,4800,true);
        for(int bite=0;bite<3;bite++) {
            check(parfait(bite,List.of(),List.of(),2,1)==CuisineFoodPlacement.ADD,"without ordinary entry any bite starts ordinary");
            check(parfait(bite,List.of(fresh),List.of(),2,1)==(bite==0?CuisineFoodPlacement.REJECT:CuisineFoodPlacement.TEMPORARY),"only later bites bypass same-food restriction");
            check(parfait(bite,List.of(ending),List.of(),2,1)==(bite==0?0:CuisineFoodPlacement.TEMPORARY),"full cup refreshes ending ordinary record while later bites stay temporary");
            check(parfait(bite,List.of(expired),List.of(),2,1)==0,"expired ordinary record can be replaced by remaining bite");
        }
        check(parfait(1,List.of(fresh),List.of(),2,0)==CuisineFoodPlacement.REJECT,"no hidden temporary capacity granted");
        check(parfait(0,List.of(ending),List.of(),2,0)==0,"full cup refresh needs no temporary capacity");
        check(parfait(0,List.of(new CuisineFoodPlacement.Slot(1,1200,4800,true)),List.of(),2,1)==CuisineFoodPlacement.REJECT,"full cup follows strict one-minute boundary");
        check(parfait(0,List.of(new CuisineFoodPlacement.Slot(1,1199,4800,true)),List.of(),2,0)==0,"full cup refreshes below one minute");
        check(parfait(1,List.of(ending),List.of(),2,0)==CuisineFoodPlacement.REJECT,"later bite cannot use ordinary early-refresh window");
        check(parfait(0,List.of(new CuisineFoodPlacement.Slot(2,100,4800,false)),List.of(),2,0)==0,"full cup can replace another ending food");
        check(parfait(0,List.of(fresh),List.of(),2,1)==CuisineFoodPlacement.REJECT,"full cup cannot bypass fresh ordinary record using temporary capacity");
        check(parfait(2,List.of(fresh),List.of(fresh),2,1)==CuisineFoodPlacement.TEMPORARY,"third bite refreshes same temporary entry");
        check(parfait(1,List.of(fresh),List.of(new CuisineFoodPlacement.Slot(1,200,200,false)),2,1)==CuisineFoodPlacement.TEMPORARY,"other temporary food can be replaced");
        check(parfait(1,List.of(new CuisineFoodPlacement.Slot(2,4000,4800,false)),List.of(),2,1)==CuisineFoodPlacement.REJECT,"no ordinary room cannot send first admitted bite to temporary");
        check(com.chi__you.cuisinejournal.food.ExquisitoParfaits.matches(net.minecraft.resources.ResourceLocation.parse("exquisito:ether_bulb_parfait")),"exact ether parfait selected");
        check(com.chi__you.cuisinejournal.food.ExquisitoParfaits.matches(net.minecraft.resources.ResourceLocation.parse("exquisito:nightshade_berry_parfait")),"exact nightshade parfait selected");
        check(!com.chi__you.cuisinejournal.food.ExquisitoParfaits.matches(net.minecraft.resources.ResourceLocation.parse("exquisito:ether_bulb_milkshake")),"other Exquisito drinks retain normal rules");
    }
    private static int parfait(int bite,List<CuisineFoodPlacement.Slot> ordinary,List<CuisineFoodPlacement.Slot> temporary,int capacity,int tempCapacity) {
        return com.chi__you.cuisinejournal.food.ExquisitoParfaits.placement(bite,ordinary,temporary,1,capacity,tempCapacity,0,false);
    }
    public static void main(String[] args)throws Exception {
        parfaitPlacementTests();
        var nutrition=own("client/tooltip/NutritionTooltip");
        check(calls(nutrition,"net/minecraft/world/item/ItemStack","getFoodProperties"),"nutrition fallback reads stack-aware food values");
        check(calls(own("forge/ForgeClientBridge"),"com/chi__you/cuisinejournal/client/tooltip/NutritionTooltip","append"),"nutrition icon marker is added for both tooltip modes");
        check(calls(own("mixin/client/ObscureFoodRecognitionMixin"),"com/chi__you/cuisinejournal/food/ExquisitoParfaits","matches"),"Obscure display recognition only extends to exact parfait IDs");
        ClassNode adapter=own("food/AbnormalsPlacedFoods");
        check(calls(adapter,"com/chi__you/cuisinejournal/compat/HostReflection","field"),"cake food values come from host, not vanilla cake");
        var parfait=own("mixin/food/ExquisitoParfaitMixin");
        check(calls(parfait,"com/chi__you/cuisinejournal/food/CuisineFoodClientState","canConsume")
                &&calls(parfait,"com/chi__you/cuisinejournal/food/CuisineFoodService","canConsume"),"parfait custom use preflights both sides before animation");
        check(calls(own("food/CuisineFoodHud"),"com/chi__you/cuisinejournal/food/ExquisitoParfaits","placement"),"HUD uses same parfait placement policy as admission");
        check(calls(parfait,"net/minecraft/world/food/FoodData","eat"),"last parfait bite still applies stack-aware nutrition");
        check(!parfait.methods.stream().flatMap(method->Arrays.stream(method.instructions.toArray())).anyMatch(insn->insn instanceof MethodInsnNode call
                &&call.owner.equals("net/minecraft/world/food/FoodData")&&call.name.equals("eat")&&call.desc.contains("ItemStack")),
                "parfait nutrition bypasses item isEdible=false guard");
        check(calls(parfait,"com/chi__you/cuisinejournal/food/CuisineVanillaBonuses","itemProperties")
                &&calls(parfait,"com/chi__you/cuisinejournal/food/CuisineVanillaBonuses","itemApplication"),"quality nutrition applies once through numeric delegate");
        var stage=own("food/CuisineEffectAdapters");
        for(String name:List.of("limitNativeEffect","blocksDigestedNative"))
            check(stage.methods.stream().filter(method->method.name.equals(name)).anyMatch(method->calls(method,"com/chi__you/cuisinejournal/food/CuisineEffectAdapters","preservesParfaitStage")),
                    "parfait native stage bypass wired to "+name);
        check(stage.methods.stream().noneMatch(method->method.name.equals("captureTemporaryEffect")||method.name.equals("blocksManagedOneShot")),
                "native food effects are no longer captured or suppressed for inventory replay");
        check(calls(own("food/CuisineFoodEffectSource"),"com/chi__you/cuisinejournal/food/CuisineFlavorEffects","forStack"),
                "managed food effect source delegates exclusively to flavors");
        check(calls(own("recipe/PlacedServingRelations"),"com/chi__you/cuisinejournal/food/AbnormalsPlacedFoods","serving"),
                "turkey gets a real whole-to-piece relation");
        check(adapter.fields.stream().anyMatch(field->field.name.equals("TURKEY")&&"com.teamabnormals.autumnity.common.block.TurkeyBlock".equals(field.value)),
                "turkey optional superclass is recognized");
        check(calls(adapter,"com/chi__you/cuisinejournal/compat/HostReflection","invokeDeclared"),
                "protected turkey getLeg getter resolves cooked and raw native serving");
        check(calls(own("food/PlacedFoodConsumption"),"com/chi__you/cuisinejournal/food/AbnormalsPlacedFoods","turkeyCutting"),
                "knife cutting bypasses eating admission without becoming an eating reward");
        var nativeWindow=own("food/CuisineEffectAdapters");
        var limiter=nativeWindow.methods.stream().filter(method->method.name.equals("limitNativeEffect")).findFirst().orElseThrow();
        check(!calls(limiter,"com/chi__you/cuisinejournal/food/CuisineFlavorEffects","resolveStack")
                &&!calls(limiter,"com/chi__you/cuisinejournal/skills/CookingSkills","value"),
                "native effect levels and durations are not modified by flavors or food-duration skills");
        check(own("food/CuisineFoodEffects").methods.stream().noneMatch(method->method.name.equals("acceptNative")),
                "no native-to-managed-reserve entry point remains");
        check(calls(own("food/CuisineFoodEffects"),"com/chi__you/cuisinejournal/food/CuisineEffectAdapters","nativeFoodUse"),
                "adding native same-ID effect releases flavor ownership even without stronger level");
        var bridge=own("mixin/food/AquaLivingEntityMixin");
        check(!calls(bridge,"com/chi__you/cuisinejournal/food/CuisineEffectAdapters","captureTemporaryEffect")
                &&!calls(bridge,"com/chi__you/cuisinejournal/food/CuisineEffectAdapters","blocksManagedOneShot"),
                "native addEffect executes instead of being replayed as a food effect");
        var source=own("food/CuisineFoodEffectSource");
        check(source.methods.stream().noneMatch(method->method.name.equals("baseEffects")),
                "legacy per-food configured effects no longer enter flavor-only reserves");
        var nativeTooltip=own("client/tooltip/NativeFoodEffectTooltip");
        check(!calls(nativeTooltip,"com/chi__you/cuisinejournal/food/CuisineFoodTooltip","info"),
                "native tooltip does not add flavor levels to host effects");
        check(calls(own("food/CuisineFoodService"),"com/chi__you/cuisinejournal/food/ExquisitoParfaits","placement")
                &&calls(own("food/CuisineFoodClientState"),"com/chi__you/cuisinejournal/food/ExquisitoParfaits","placement"),"server/client share parfait placement");
        var curing=own("mixin/food/NeapolitanMilkCureMixin");
        check(calls(curing,"com/chi__you/cuisinejournal/food/CuisineFoodEffects","beginCure")&&calls(curing,"com/chi__you/cuisinejournal/food/CuisineFoodEffects","endCure"),"random milk removal uses curative reserve policy");
        var provenance=own("tracking/MilkshakeCauldronProduction");
        check(provenance.methods.stream().anyMatch(m->m.name.equals("load"))&&provenance.methods.stream().anyMatch(m->m.name.equals("save")),"milk and milkshake records persist across restart");
        check(provenance.methods.stream().anyMatch(method->method.name.equals("append"))&&provenance.methods.stream().anyMatch(method->method.name.equals("remaining")),
                "milkshake storage and production are retained as independently consumed portions");
        var a=new UUID(0,1);var b=new UUID(0,2);var ledger=new ProductionLedger();
        ledger.syncInput(0,"milk",2,a);ledger.syncInput(1,"ice_cream",1,b);
        var batch=ledger.produce(Map.of(0,2,1,1),"shake",2,"neapolitan:milkshake_cauldron");
        check(batch.contributors().equals(Set.of(a,b)),"mixing retains milk and ice cream contributors");
        check(ProductionLedger.readBatch(ProductionLedger.writeBatch(batch)).equals(batch),"mixed batch survives persistence");
        String directory=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(directory==null){System.out.println("SKIPPED: B002 installed-host checks; "+checks+" local checks passed (CUISINEJOURNAL_COMPAT_MODS unset)");return;}
        List<JarFile> jars=new ArrayList<>();
        try{
            try(var files=Files.list(Path.of(directory))){for(var path:files.filter(p->p.toString().endsWith(".jar")).toList())jars.add(new JarFile(path.toFile()));}
            String[][] contracts={
                {"com/teamabnormals/neapolitan/common/block/FlavoredCakeBlock","eatSlice","net/minecraft/world/food/FoodData","m_38707_"},
                {"com/teamabnormals/neapolitan/common/block/FlavoredCandleCakeBlock","getCake",null,null},
                {"com/teamabnormals/neapolitan/common/item/MilkBottleItem","clearRandomEffect","net/minecraft/world/entity/LivingEntity","m_21195_"},
                {"com/teamabnormals/neapolitan/common/item/HealingItem","applyHealing","net/minecraft/world/entity/LivingEntity","m_5634_"},
                {"com/teamabnormals/neapolitan/core/other/NeapolitanCauldronInteractions","addMilkshakeInteractions",null,null},
                {"com/teamabnormals/autumnity/common/block/PancakeBlock","eatCake","net/minecraft/world/food/FoodData","m_38707_"},
                {"com/teamabnormals/autumnity/common/block/TurkeyBlock","restoreHunger","net/minecraft/world/food/FoodData","m_38707_"},
                {"com/teamabnormals/autumnity/common/block/CookedTurkeyBlock","restoreHunger","net/minecraft/world/food/FoodData","m_38707_"},
                {"com/teamabnormals/autumnity/common/block/TurkeyBlock","getLeg",null,null},
                {"com/teamabnormals/autumnity/common/block/CookedTurkeyBlock","getLeg",null,null},
                {"com/teamabnormals/atmospheric/common/block/YuccaGateauBlock","eatCake","net/minecraft/world/food/FoodData","m_38707_"},
                {"com/gumillea/exquisito/common/item/ParfaitItem","giveEffect","net/minecraft/world/food/FoodData","m_38707_"},
                {"com/gumillea/exquisito/common/block/ExquisitoCakeBlock","m_6227_",null,null},
                {"com/teamabnormals/caverns_and_chasms/common/item/GoldenMilkBucketItem","m_5922_","net/minecraft/world/entity/LivingEntity","curePotionEffects"},
                {"net/brdle/collectorsreap/common/block/CRCauldronInteractions","registerCauldronInteractions","com/teamabnormals/neapolitan/core/other/NeapolitanCauldronInteractions","addMilkshakeInteractions"}
            };
            for(var c:contracts){var type=read(jars,c[0]);if(type==null)continue;var method=type.methods.stream().filter(m->m.name.equals(c[1])).findFirst().orElseThrow(()->new AssertionError(Arrays.toString(c)));
                check(true,"verified host method "+c[0]);if(c[2]!=null)check(calls(method,c[2],c[3]),"actual host delegate "+Arrays.toString(c));}
            var cake=read(jars,"com/teamabnormals/neapolitan/common/block/FlavoredCakeBlock");
            var turkey=read(jars,"com/teamabnormals/autumnity/common/block/TurkeyBlock");
            var cookedTurkey=read(jars,"com/teamabnormals/autumnity/common/block/CookedTurkeyBlock");
            if(turkey!=null&&cookedTurkey!=null){
                check(cookedTurkey.superName.equals(turkey.name),"cooked turkey shares the audited five-bite family");
                for(var host:List.of(turkey,cookedTurkey))check(host.methods.stream().anyMatch(method->method.name.equals("getLeg")
                        &&(method.access&org.objectweb.asm.Opcodes.ACC_PROTECTED)!=0),"turkey serving is protected native getter "+host.name);
                var nativeTags=read(jars,"com/teamabnormals/autumnity/core/other/tags/AutumnityItemTags");
                var literals=new HashSet<String>();for(var method:nativeTags.methods)for(var insn:method.instructions)
                    if(insn instanceof LdcInsnNode literal&&literal.cst instanceof String value)literals.add(value);
                check(literals.contains("farmersdelight")&&literals.contains("tools/knives"),"turkey uses actual farmer knife tag, not forge/guessed autumnity tag");
            }
            if(cake!=null)check(cake.fields.stream().anyMatch(f->f.name.equals("food")&&f.desc.equals("Lnet/minecraft/world/food/FoodProperties;")),"native per-bite cake nutrition/effects field exists");
            var config=read(jars,"com/teamabnormals/neapolitan/core/NeapolitanConfig$Common");
            for(String name:List.of("HealingItem","HealingDrinkItem","HealingIceCreamItem","HealingMilkshakeItem")) {
                var host=read(jars,"com/teamabnormals/neapolitan/common/item/"+name);
                if(host!=null)check(host.fields.stream().anyMatch(f->f.name.equals("healAmount")&&f.desc.equals("F")),"direct healing reads actual host field: "+name);
            }
            if(config!=null)check(config.fields.stream().anyMatch(f->f.name.equals("milkCauldron")),"conditional milk conversion config field exists");
            var dessert=read(jars,"com/gumillea/exquisito/common/item/ParfaitItem");
            if(dessert!=null){
                check(dessert.methods.stream().anyMatch(m->m.name.equals("m_7203_")&&m.desc.equals("(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;")),"parfait custom right-click injection target exists");
                var edible=dessert.methods.stream().filter(m->m.name.equals("m_41472_")).findFirst().orElseThrow();
                check(Arrays.stream(edible.instructions.toArray()).anyMatch(i->i.getOpcode()==org.objectweb.asm.Opcodes.ICONST_0),"parfait disables Item.finishUsingItem's nutrition, so direct call must not be removed");
            }
            var candle=read(jars,"com/teamabnormals/caverns_and_chasms/common/block/cupric/CupricCandleCakeBlock");
            if(candle!=null)check(candle.superName.equals("net/minecraft/world/level/block/CandleCakeBlock"),"cupric candle keeps vanilla cake behavior");
            var standalone=new ClassNode();try(var in=B002CompatibilityTest.class.getClassLoader().getResourceAsStream("com/chi__you/foodinteractions/ExquisitoServingRelations.class")){new ClassReader(Objects.requireNonNull(in)).accept(standalone,0);}
            for(var method:standalone.methods)for(var insn:method.instructions)if(insn instanceof MethodInsnNode call)check(!call.owner.startsWith("com/chi__you/cuisinejournal/"),"slice display remains standalone");
            System.out.println("B002CompatibilityTest: "+checks+" checks passed");
        }finally{for(var jar:jars)jar.close();}
    }
    private static ClassNode own(String path)throws Exception{try(var in=B002CompatibilityTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/"+path+".class")){var node=new ClassNode();new ClassReader(Objects.requireNonNull(in)).accept(node,0);return node;}}
    private static ClassNode read(List<JarFile> jars,String name)throws Exception{for(var jar:jars){var entry=jar.getJarEntry(name+".class");if(entry!=null)try(var in=jar.getInputStream(entry)){var node=new ClassNode();new ClassReader(in).accept(node,0);return node;}}return null;}
    private static boolean calls(ClassNode node,String owner,String name){return node.methods.stream().anyMatch(m->calls(m,owner,name));}
    private static boolean calls(MethodNode method,String owner,String name){for(var insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))return true;return false;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
