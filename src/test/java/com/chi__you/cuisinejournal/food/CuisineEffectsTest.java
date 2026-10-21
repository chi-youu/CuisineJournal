package com.chi__you.cuisinejournal.food;

import net.minecraft.resources.ResourceLocation;
import com.chi__you.cuisinejournal.recipe.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.List;

public final class CuisineEffectsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(CuisineEffects.FROZEN_TICKS == 140, "freezing target matches vanilla full-freeze threshold");
        combatTimingTests();
        check(CuisineEffects.matureReduction(4,0,600)==0, "small hits round down before duration cost");
        check(CuisineEffects.matureReduction(10,0,600)==2, "level one reduces twenty percent");
        check(CuisineEffects.matureReduction(10,1,600)==4, "level two scales reduction");
        check(CuisineEffects.matureReduction(10,1,299)==2, "nourishment limits reduction to whole five-second units");
        check(CuisineEffects.matureReduction(10,0,99)==0, "less than five seconds cannot absorb one point");
        check(CuisineEffects.matureReduction(10,0,-1)==0, "infinite nourishment matches host integer division");
        for (boolean loaded : new boolean[]{false,true}) {
            var expected=loaded?CuisineEffectIds.YOUKAI_MATURE:CuisineEffectIds.MATURE;
            check(CuisineEffectIds.resolve(CuisineEffectIds.MATURE,loaded).equals(expected), "local reference follows provider");
            check(CuisineEffectIds.resolve(CuisineEffectIds.YOUKAI_MATURE,loaded).equals(expected), "host reference follows provider");
            check(CuisineEffectIds.resolve(CuisineEffectIds.ICE,loaded).equals(CuisineEffectIds.ICE), "ice is unaffected by provider");
            CuisineEffectIds.configure(loaded);
            var preset = new FlavorPresets.Preset(ResourceLocation.parse("test:mature"),"Mature","",true,
                    List.of(new FlavorStage(0,List.of(new FlavorStage.Effect(CuisineEffectIds.MATURE,1,30,1)))));
            check(FlavorAvailability.hasContent(preset,expected::equals), "saved flavor remains available across providers");
            check(preset.toJson().toString().contains("cuisinejournal:mature"), "availability does not rewrite saved identifiers");
        }
        CuisineEffectIds.configure(false);
        var ice=compiled("CuisineEffects$IceEffect");
        check(ice.superName.endsWith("InstantenousMobEffect"), "ice uses genuine instant effect path");
        check(calls(ice,"freeze","setTicksFrozen"), "ice sets frozen ticks directly");
        check(calls(ice,"applyInstantenousEffect","freeze")&&calls(ice,"applyEffectTick","freeze"),
                "food instant path and command tick path both freeze");
        var effects=compiled("CuisineEffects");
        check(calls(effects,"register","isLoaded"), "provider registration is gated by loaded mods");
        check(calls(effects,"onDamage","observe")&&calls(effects,"onDamage","cuisinejournal$duration"),
                "nourishment debits are observed by finite food reserves");
        for(String icon:new String[]{"ice_ice_ice","mature"}) {
            try(var input=CuisineEffectsTest.class.getClassLoader().getResourceAsStream(
                    "assets/cuisinejournal/textures/mob_effect/"+icon+".png")) {
                var bitmap=javax.imageio.ImageIO.read(java.util.Objects.requireNonNull(input));
                check(bitmap.getWidth()==18&&bitmap.getHeight()==18, "effect atlas icon has native dimensions");
                check((bitmap.getRGB(8,8)>>>24)>0, "effect icon is not blank");
            }
        }
        nativeFoodClassificationTests();
        System.out.println("CuisineEffectsTest: "+checks+" checks passed");
    }
    private static void nativeFoodClassificationTests() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrap=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true);bootstrap.setBoolean(null,true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        for(var item:List.of(net.minecraft.world.item.Items.POTION,net.minecraft.world.item.Items.SPLASH_POTION,
                net.minecraft.world.item.Items.LINGERING_POTION,net.minecraft.world.item.Items.MILK_BUCKET))
            check(!CuisineEffectAdapters.isNativeFood(new net.minecraft.world.item.ItemStack(item),null),"non-food use stays outside food effect suppression: "+item);
        for(var item:List.of(net.minecraft.world.item.Items.GOLDEN_APPLE,net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE,
                net.minecraft.world.item.Items.HONEY_BOTTLE,net.minecraft.world.item.Items.MUSHROOM_STEW))
            check(CuisineEffectAdapters.isNativeFood(new net.minecraft.world.item.ItemStack(item),null),"edible food/drink retains food effect suppression: "+item);
        check(!CuisineEffectAdapters.isNativeFood(net.minecraft.world.item.ItemStack.EMPTY,null),"empty item is not a native food");
        flavorReservePersistenceTests();
    }
    private static void flavorReservePersistenceTests() {
        var nativeId=ResourceLocation.parse("minecraft:speed");
        var flavorId=ResourceLocation.parse("minecraft:regeneration");
        var serving=new CuisineFoodEntry(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE),600,1);
        serving.effectReserves().put(nativeId,new FoodEffectReserve(1,400));
        serving.effectReserves().put(flavorId,new FoodEffectReserve(0,300));
        serving.markOwned(nativeId);serving.markTriggered(flavorId);serving.initializeEffects();
        var saved=serving.save();
        check(!saved.contains("effect_scope"),"all reserves are flavor-only without a legacy source-mode marker");
        var current=CuisineFoodEntry.read(saved);
        check(current.effectReserves().size()==2&&current.effectsInitialized(),"flavor reserves persist without reinitializing");
        check(current.ownsEffect(nativeId)&&current.wasTriggered(flavorId),"effect ownership and one-shot history persist");
        check(current.remainingTicks()==600&&current.slotCost()==1,"persistence retains lifetime and capacity");
        check(current.save().equals(saved),"flavor reserve serialization is stable on repeated round trips");
    }
    private static void combatTimingTests() throws Exception {
        var timing = new FoodCombatTiming();
        check(timing.ready(0,120,false), "no previous combat allows recovery");
        timing.enter(1000);
        check(!timing.ready(1119,120,false)&&timing.ready(1120,120,false), "ordinary delay remains six seconds");
        check(!timing.ready(1059,120,true)&&timing.ready(1060,120,true), "warm heart halves delay to three seconds");
        check(!timing.ready(1060,120,false), "removing warm heart restores original delay");
        check(!timing.ready(1015,31,true)&&timing.ready(1016,31,true), "odd delays round up to nearest tick");
        timing.saturation(1020);
        check(timing.ready(1020,120,false), "saturation immediately clears existing combat");
        timing.enter(1021);
        timing.enter(1139);
        check(timing.ready(1139,120,false), "attacks and incoming hits are ignored throughout grace");
        check(timing.ready(1140,120,false), "ignored hits cannot start a delayed combat timer after grace");
        timing.enter(1140);
        check(!timing.ready(1140,120,false)&&timing.ready(1260,120,false), "a hit at grace deadline enters combat normally");
        timing.saturation(1200);
        timing.saturation(1250);
        timing.enter(1320);
        check(timing.ready(1320,120,false), "new saturation renews six-second grace from latest trigger");
        timing.enter(1370);
        check(!timing.ready(1370,120,false), "renewed grace ends at latest deadline");
        timing.saturation(1370);
        check(timing.ready(1370,120,true), "saturation and warm heart compose without delaying exit");
        var adapters=compiled("CuisineEffectAdapters");
        check(calls(adapters,"applySaturation","activeForCurrentThread")&&calls(adapters,"applySaturation","saturation"),
                "saturation is guarded by Valheim mode and triggers combat grace");
        var events=compiled("CuisineFoodEvents");
        check(calls(events,"enteredCombat","enter")&&calls(events,"tick","ready")&&calls(events,"tick","warmHeart"),
                "combat entry and regeneration share the grace timer and warm heart check");
        var saturation=compiled("mixin/food/SaturationCombatMixin");
        check(calls(saturation,"cuisinejournal$saturation","applySaturation")&&calls(saturation,"cuisinejournal$saturation","cancel"),
                "saturation replaces native hunger restoration only when adapted");
        var warmth=compiled("mixin/food/BrewinWarmHeartMixin");
        check(calls(warmth,"cuisinejournal$warmHeart","activeForCurrentThread")&&calls(warmth,"cuisinejournal$warmHeart","cancel"),
                "warm heart suppresses native saturation healing in Valheim only");
        var saturationId=ResourceLocation.parse("minecraft:saturation");
        var warmId=ResourceLocation.parse("brewinandchewin:sweet_heart");
        check(CuisineEffectAdapters.descriptionKey(saturationId,false)==null
                &&CuisineEffectAdapters.descriptionKey(warmId,false)==null, "vanilla descriptions remain native");
        check("cuisinejournal.effect.saturation.description".equals(CuisineEffectAdapters.translatedDescriptionKey(
                "effect.minecraft.saturation.description",true)), "saturation description changes with mode");
        check("cuisinejournal.effect.sweet_heart.description".equals(CuisineEffectAdapters.translatedDescriptionKey(
                "effect.brewinandchewin.sweet_heart.description",true)), "warm heart description changes with mode");
        String host=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(host!=null)try(var paths=java.nio.file.Files.list(java.nio.file.Path.of(host))) {
            var jar=paths.filter(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains("brewinandchewin")
                    &&p.toString().endsWith(".jar")).findFirst().orElse(null);
            if(jar!=null)try(var archive=new java.util.jar.JarFile(jar.toFile());
                    var input=archive.getInputStream(archive.getJarEntry("umpaz/brewinandchewin/common/effect/SweetHeartEffect.class"))) {
                var target=new ClassNode();new ClassReader(input).accept(target,0);
                check(target.methods.stream().anyMatch(m->m.name.equals("m_6742_")&&m.desc.equals("(Lnet/minecraft/world/entity/LivingEntity;I)V")),
                        "optional warm heart mixin matches installed host method descriptor");
            }
        }
    }
    private static ClassNode compiled(String name) throws Exception {
        ClassNode node=new ClassNode();
        try(var input=CuisineEffectsTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/"+(name.startsWith("mixin/")?name:"food/"+name)+".class")) {
            new ClassReader(java.util.Objects.requireNonNull(input)).accept(node,0);
        }
        return node;
    }
    private static boolean calls(ClassNode node,String method,String target) {
        for(var m:node.methods)if(m.name.equals(method))for(var insn:m.instructions)
            if(insn instanceof MethodInsnNode call&&call.name.equals(target))return true;
        return false;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
