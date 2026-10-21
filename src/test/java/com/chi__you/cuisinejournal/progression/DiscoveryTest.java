package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.config.*;
import com.chi__you.cuisinejournal.culinary.*;
import com.chi__you.cuisinejournal.recipe.*;
import com.chi__you.cuisinejournal.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public final class DiscoveryTest {
    private static int checks;
    private static ResourceLocation id(String path){return ResourceLocation.parse("test:"+path);}
    public static void main(String[] args) throws Exception {
        var config=new ServerConfig.Discovery();
        check(DiscoveryService.accepts(config,DiscoveryService.Trigger.PREPARED)
                &&DiscoveryService.accepts(config,DiscoveryService.Trigger.OBTAINED),"default discovery accepts cooking and obtaining");
        check(!DiscoveryService.accepts(config,DiscoveryService.Trigger.EATEN),"default does not discover merely by eating");
        config.discoverOnObtain=false;
        for(var mode:ConfigEnums.DiscoveryMode.values()) {
            config.mode=mode;
            for(var trigger:DiscoveryService.Trigger.values()) {
                boolean expected=mode==ConfigEnums.DiscoveryMode.ALWAYS_VISIBLE||mode.name().equals(switch(trigger){
                    case PREPARED->"COOK_RECIPE";case EATEN->"EAT_OUTPUT";case OBTAINED->"OBTAIN_OUTPUT";});
                check(DiscoveryService.accepts(config,trigger)==expected,"discovery trigger follows selected mode");
            }
        }
        var migrated=Toml.read("[discovery]\nmode = \"VIEW_RECIPE\"\nshared = \"TEAM\"\ndiscoverOnObtain = false\n",ServerConfig.class);
        migrated.validate();
        check(migrated.discovery.mode==ConfigEnums.DiscoveryMode.COOK_RECIPE
                &&migrated.discovery.shared==ConfigEnums.SharedDiscovery.TEAM&&!migrated.discovery.discoverOnObtain,
                "retired view mode migrates to cooking without changing other discovery preferences");
        check(!Toml.write(migrated,List.of()).contains("VIEW_RECIPE"),"removed mode is not written back");
        var fileOnly=Toml.read("[discovery]\nmode = \"ALWAYS_VISIBLE\"\n",ServerConfig.class);fileOnly.validate();
        check(fileOnly.discovery.mode==ConfigEnums.DiscoveryMode.ALWAYS_VISIBLE,"explicit file-only automatic discovery remains supported");
        for(String allowed:List.of("COOK_RECIPE","EAT_OUTPUT","OBTAIN_OUTPUT"))
            check(ServerSettingsHandler.editableDiscoveryMode(allowed).name().equals(allowed),"normal discovery modes remain editable");
        for(String rejected:List.of("VIEW_RECIPE","ALWAYS_VISIBLE")) {
            boolean denied=false;try{ServerSettingsHandler.editableDiscoveryMode(rejected);}catch(IllegalArgumentException expected){denied=true;}
            check(denied,"settings network cannot enable removed/file-only mode "+rejected);
        }
        check(DiscoveryService.group(ConfigEnums.SharedDiscovery.PERSONAL,"a")==null
                &&DiscoveryService.group(ConfigEnums.SharedDiscovery.TEAM,null)==null,"sharing off and unteamed players remain personal");
        check(!DiscoveryService.group(ConfigEnums.SharedDiscovery.TEAM,"a").equals(
                DiscoveryService.group(ConfigEnums.SharedDiscovery.TEAM,"b")),"different teams never share a ledger");
        var ledger=new SharedDiscoveries();
        check(ledger.add("team:a",id("soup"))&&!ledger.add("team:a",id("soup")),"shared ledger deduplicates discoveries");
        ledger.add("server",id("bread"));
        var restored=SharedDiscoveries.load(ledger.save(new CompoundTag()));
        check(restored.entries("team:a").equals(Set.of(id("soup")))&&restored.entries("team:b").isEmpty()
                &&restored.entries("server").equals(Set.of(id("bread"))),"world save preserves separate team and server discoveries");
        var current=CulinaryRegistries.class.getDeclaredField("current");current.setAccessible(true);Object previous=current.get(null);
        try {
            var dish=id("soup");var slice=id("slice");
            var meal=new MealEntry(dish,null,"test",List.of(),Set.of(),Set.of(),FoodCategory.OTHER_FOOD,6,.6F);
            var portion=new MealEntry(slice,null,"test",List.of(),Set.of(),Set.of(),FoodCategory.OTHER_FOOD,3,.6F);
            var constructor=CulinaryRegistry.class.getDeclaredConstructor(Map.class,List.class,CulinaryAudit.class,List.class,Map.class,Map.class);
            constructor.setAccessible(true);
            var registry=(CulinaryRegistry)constructor.newInstance(Map.of(dish,meal,slice,portion),List.of(meal,portion),CulinaryAudit.EMPTY,List.of(),Map.of(),Map.of());
            var origins=CulinaryRegistry.class.getDeclaredField("portionOrigins");origins.setAccessible(true);origins.set(registry,Map.of(slice,dish));current.set(null,registry);
            var profile=new CulinaryProfile(new UUID(1,2));
            check(DiscoveryService.mark(profile,slice,3)&&profile.isDiscovered(dish),"obtained portion discovers preparation identity");
            check(!DiscoveryService.mark(profile,slice,4)&&profile.activity().size()==1,"repeated discovery has no duplicate activity");
            check(profile.peekMeal(dish).prepared()==0&&profile.peekMeal(dish).masteryPoints()==0
                    &&profile.cookingXp()==0&&profile.stats().mealsPrepared()==0,"discovery cannot fabricate preparation or growth");
            check(!DiscoveryService.mark(profile,id("missing"),4)&&profile.peekMeal(id("missing"))==null,"untracked items cannot be discovered");
            var saved=CulinaryProfile.load(profile.playerId(),profile.save());
            check(saved.isDiscovered(slice)&&saved.isDiscovered(dish)&&saved.cookingXp()==0,"discovery survives profile storage without rewards");
        } finally {current.set(null,previous);}
        var configRules=ConfigManager.server();boolean mastery=configRules.mastery.enabled;
        try {
            configRules.mastery.enabled=false;
            var historical=new CulinaryProfile(new UUID(8,9));
            historical.meal(id("old")).addMasteryPoints(100);
            check(historical.rankOf(id("old"),configRules.mastery.toCurve()).ordinal()==2
                    &&historical.peekMeal(id("old")).masteryPoints()==100,"star switch uses neutral rank without deleting historical points");
        } finally {configRules.mastery.enabled=mastery;}
        var rules=new Packets.SyncRules(1,2,3,100,List.of(1,2,3,4,5),"CUSTOM",false,true,0,0,0,1,0,0,
                true,false,0,.05,1,1.5,false,false);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {rules.write(buffer);check(Packets.SyncRules.read(buffer).equals(rules)&&buffer.readableBytes()==0,
                "star and effect switches round-trip with server rules");} finally{buffer.release();}
        contracts();
        System.out.println("DiscoveryTest: "+checks+" checks passed");
    }
    private static void contracts() throws Exception {
        check(calls("event/CulinaryHooks","onServerTick","tick")&&calls("event/CulinaryHooks","onServerTick","flushDiscoveryUpdates"),
                "inventory discovery and batched sync are wired into server ticks");
        check(!calls("network/ServerPacketHandler","sendDishDetail","discover")
                &&!calls("command/CuisineJournalCommands","recipe","discover"),"detail requests and recipe command cannot discover dishes");
        check(!calls("food/CuisineFoodEffects","collect","effectsEnabled")
                &&!calls("food/CuisineFoodEffects","collect","nativeEffectsEnabled"),"flavor reserves remain independent of native effect toggle and native supply");
        check(calls("mixin/food/ItemFoodPropertiesMixin","cuisinejournal$filterNativeEffects","nativeEffectsEnabled")
                &&calls("mixin/food/VanillaFoodPropertiesMixin","getFoodProperties","nativeEffectsEnabled"),"native item and stack properties share one effect policy");
        check(calls("client/tooltip/NativeFoodEffectTooltip","append","nativeEffectsEnabled")
                &&calls("food/CuisineFoodSync","send","nativeEffectsEnabled"),"native tooltip and food snapshot share runtime policy");
        check(!calls("food/CuisineFlavorEffects","forStack","effectsEnabled")
                &&!calls("food/CuisineFlavorEffects","resolveSources","effectsEnabled")
                &&!calls("food/CuisineFoodEffectSource","forStack","effectsEnabled"),"native toggle cannot suppress flavor effects or levels");
        check(calls("mixin/food/FoodInstantEffectToggleMixin","cuisinejournal$foodOnly","blocksFoodEffects"),
                "direct native instant actions are gated inside food context only");
        check(calls("food/CuisineFoodQuality","stars","starsEnabled"),"quality baseline follows synced star switch");
    }
    private static boolean calls(String path,String name,String target) throws Exception {
        var node=new org.objectweb.asm.tree.ClassNode();
        try(var input=DiscoveryTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/"+path+".class")) {
            new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(node,0);
        }
        for(var method:node.methods)if(method.name.equals(name)||method.name.startsWith("lambda$"+name+"$"))for(var instruction:method.instructions)
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.name.equals(target))return true;
        return false;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
