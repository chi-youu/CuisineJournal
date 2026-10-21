package com.chi__you.cuisinejournal.food;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

public final class TemporaryFoodTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var policy=CuisineEffectAdapters.APPETIZING;
        var nourishment=ResourceLocation.parse("farmersdelight:nourishment");
        check(CuisineEffectAdapters.descriptionKey(nourishment,false)==null
                &&"cuisinejournal.effect.nourishment.description".equals(CuisineEffectAdapters.descriptionKey(nourishment,true)),
                "nourishment behavior description is Valheim-only");
        check(Math.abs(CuisineEffectAdapters.NOURISHMENT.amount(1.2F,true)-1.5F)<1e-6,
                "nourishment increases out-of-combat healing by twenty five percent");
        check(Math.abs(CuisineEffectAdapters.NOURISHMENT.amount(1.2F,false)-0.6F)<1e-6,
                "combat nourishment uses half of unboosted healing, not half of boosted healing");
        check(CuisineEffectAdapters.NOURISHMENT.amount(0,false)==0,
                "nourishment cannot create healing without an ordinary baseline");
        check(CuisineEffectAdapters.oneShot(ResourceLocation.parse("fruitsdelight:chorus"),false),
                "chorus is a one-shot despite host not declaring instant effect");
        check(CuisineEffectAdapters.oneShot(ResourceLocation.parse("minecraft:instant_health"),true),
                "native instant effects use the shared one-shot path");
        check(!CuisineEffectAdapters.oneShot(ResourceLocation.parse("fruitsdelight:heal_aura"),false),
                "periodic healing aura remains a persistent effect");
        check(policy.capacity()==2&&policy.durationTicks()==200,"appetizing adds two capacity to native temporary slots");
        check(FoodEffectReserve.classify(600,100,false,false,true,true,false,500,0,0)==FoodEffectReserve.Change.CONSUMED,
                "external duration reduction is classified as consumptive use");
        check(FoodEffectReserve.classify(600,100,false,true,false,false,false,0,0,0)==FoodEffectReserve.Change.CURED,
                "curative removal restores remaining reserve");
        check(FoodEffectReserve.classify(600,100,true,false,false,false,false,0,0,0)==FoodEffectReserve.Change.EXHAUSTED,
                "ordinary removal exhausts reserve");
        check(FoodEffectReserve.classify(600,600,false,false,false,false,false,0,0,0)==FoodEffectReserve.Change.NATURAL_END,
                "natural expiration does not mark extra consumption");
        check(FoodEffectReserve.classify(600,100,false,false,true,false,false,700,0,0)==FoodEffectReserve.Change.EXTERNAL,
                "external longer or stronger effect is never claimed as food stock");
        check(FoodEffectReserve.consumption(600,100,300)==200,
                "food-time reduction and effect consumption have exact separate amounts");
        check(FoodEffectReserve.consumption(600,100,500)==0,"natural countdown consumes no extra resource");
        check(FoodEffectReserve.classify(600,100,true,false,true,false,false,300,0,0)==FoodEffectReserve.Change.CONSUMED,
                "remove then re-add shorter effect is charged once without per-mod adapters");
        check(FoodEffectReserve.classify(600,100,true,false,true,false,false,700,0,0)==FoodEffectReserve.Change.EXTERNAL,
                "remove then replace with longer potion relinquishes ownership");
        var reserve=new FoodEffectReserve(2,600);
        reserve.advance(100);reserve.advance(200);
        check(reserve.remaining()==300,"food timeline and external consumption subtract independently");
        var saved=FoodEffectReserve.read(reserve.save());
        check(saved.remaining()==300&&saved.amplifier()==2,"consumed resource persists across save/load");
        saved.exhaust();saved.advance(1);
        check(saved.remaining()==0,"exhausted resource never regrows");
        saved=FoodEffectReserve.read(saved.save());
        check(saved.remaining()==0,"zero reserves persist rather than initializing again");
        check(CuisineEffectAdapters.digestionDuration(32,1)==64&&CuisineEffectAdapters.digestionDuration(32,2)==128
                &&CuisineEffectAdapters.digestionDuration(32,3)==192,"digestion eating duration scales two times actual slot cost");
        check(CuisineEffectAdapters.digestionDuration(Integer.MAX_VALUE,3)==Integer.MAX_VALUE,"digestion duration cannot overflow");
        check(!CuisineDigestionUse.elapsedEnough(62,64)&&CuisineDigestionUse.elapsedEnough(63,64),
                "instant healing requires finishing the extended eating action");
        check(CuisineFoodPlacement.extraPlacement(true,true)==CuisineFoodPlacement.TEMPORARY,
                "appetizing takes priority when digestion can also consume food");
        check(CuisineFoodPlacement.extraPlacement(false,true)==CuisineFoodPlacement.DIGESTED
                &&CuisineFoodPlacement.extraPlacement(false,false)==CuisineFoodPlacement.REJECT,
                "digestion fallback grants instant healing without additional food capacity");
        var one=new CuisineFoodPlacement.Slot(1,100,200,false);
        var longSame=new CuisineFoodPlacement.Slot(1,4000,6000,true);
        check(CuisineFoodPlacement.find(List.of(longSame),1,3,0,false)==CuisineFoodPlacement.REJECT
                &&CuisineFoodPlacement.acceptsTemporary(List.of(),1,3),
                "ordinary duplicate can fall through to an independent temporary slot");
        var readySame=new CuisineFoodPlacement.Slot(1,1199,6000,true);
        check(CuisineFoodPlacement.find(List.of(readySame),1,3,0,false)==0,
                "ordinary duplicate still refreshes when normal eating conditions permit");
        check(CuisineFoodPlacement.acceptsTemporary(List.of(),1,3),
                "ordinary food does not block temporary replenishment");
        check(CuisineFoodPlacement.find(List.of(longSame),1,3,0,true)==0,
                "explicit repeatable food retains ordinary refresh semantics");
        var two=new CuisineFoodPlacement.Slot(2,100,200,false);
        check(CuisineFoodPlacement.acceptsTemporary(List.of(),3,3),"three-slot meal fits empty temporary capacity");
        check(CuisineFoodPlacement.acceptsTemporary(List.of(one),2,3),"one and two slot foods share three capacity");
        check(CuisineFoodPlacement.temporaryPlan(List.of(two),2,3).remove().equals(List.of(0)),"temporary meals replace oldest food while retaining actual cost");
        check(CuisineFoodPlacement.temporaryPlan(List.of(one,two),1,3).remove().equals(List.of(0)),"full temporary capacity evicts only enough oldest food");
        check(CuisineFoodPlacement.temporaryPlan(List.of(new CuisineFoodPlacement.Slot(1,1,200,true)),1,3).remove().equals(List.of(0)),
                "matching temporary food refreshes even before expiry");
        check(!CuisineFoodPlacement.acceptsTemporary(List.of(one),3,1),"one-slot temporary capacity never accepts three-slot meals");
        check(CuisineFoodPlacement.temporaryPlan(List.of(one,one,two),3,4).remove().equals(List.of(0,1,2)),
                "multi-slot incoming food evicts enough old slots");
        check(CuisineFoodPlacement.temporaryPlan(List.of(one,two),1,4).remove().isEmpty(),"spare temporary capacity preserves previous foods");
        check(CuisineFoodCapacity.total(1,0,2)==3&&CuisineFoodCapacity.total(1,2,2)==5,
                "native base growth and appetizing capacity add together");
        for(int capacity=0;capacity<=8;capacity++)for(int cost=1;cost<=3;cost++) {
            var old=List.of(new CuisineFoodPlacement.Slot(1,100,200,false),new CuisineFoodPlacement.Slot(2,100,200,true),
                    new CuisineFoodPlacement.Slot(3,100,200,false));
            var plan=CuisineFoodPlacement.temporaryPlan(old,cost,capacity);
            if(cost>capacity)check(!plan.accepted(),"oversized food is rejected even by full replacement");
            else {
                int remaining=cost;
                for(int i=0;i<old.size();i++)if(!plan.remove().contains(i)) {
                    remaining+=old.get(i).cost();
                    check(!old.get(i).sameFood(),"matching food always replaced so refresh never stacks");
                }
                check(remaining<=capacity,"replacement plan stays within temporary capacity");
                check(plan.remove().stream().distinct().count()==plan.remove().size(),"replacement indices are unique");
            }
        }
        var snapshot=new CuisineFoodSnapshot(true,true,5,0.1,List.of(),null,List.of(),6);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {snapshot.write(buffer);check(CuisineFoodSnapshot.read(buffer).equals(snapshot),"growth capacities survive network sync without food items");}
        finally{buffer.release();}
        check(!CuisineFoodPlacement.acceptsTemporary(List.of(),0,3)&&!CuisineFoodPlacement.acceptsTemporary(List.of(),4,3),
                "invalid slot costs rejected");
        var id=ResourceLocation.parse("fruitsdelight:appetizing");
        check(CuisineEffectAdapters.descriptionKey(id,false)==null,"vanilla description is not overridden");
        var digestion=ResourceLocation.parse("fruitsdelight:digesting");
        check(CuisineEffectAdapters.descriptionKey(digestion,false)==null
                &&CuisineEffectAdapters.descriptionKey(digestion,true).equals("cuisinejournal.effect.digesting.description"),
                "digestion description changes only in Valheim");
        check(CuisineEffectAdapters.descriptionKey(id,true).equals("cuisinejournal.effect.appetizing.description"),
                "Valheim selects adapted description");
        check(CuisineEffectAdapters.translatedDescriptionKey("effect.fruitsdelight.appetizing.description",true)!=null,
                "host description key redirects only in Valheim");
        check(CuisineEffectAdapters.translatedDescriptionKey("effect.fruitsdelight.appetizing",true)==null,
                "effect display name is never changed");
        var old=new net.minecraft.nbt.CompoundTag();old.putInt(CuisineFoodState.VERSION,1);
        check(!CuisineFoodState.read(old).isReadOnly(),"version-one food state remains supported");
        old.putInt(CuisineFoodState.VERSION,99);
        check(CuisineFoodState.read(old).isReadOnly(),"future unknown saves remain read only");
        var stats=compiled("CuisineFoodStats");
        check(!calls(stats,"maxHealth","temporaryFoods"),"temporary food never contributes maximum health");
        check(calls(stats,"temporaryHealPerPulse","temporaryFoods")&&calls(stats,"temporaryHealPerPulse","drinkBonus"),
                "combat healing uses temporary food and drink contribution");
        var service=compiled("CuisineFoodService");
        check(!calls(service,"findPlacement","blocksExtra"),
                "server allows ordinary duplicates to fall through to temporary placement");
        check(!calls(compiled("CuisineFoodClientState"),"canConsume","blocksExtra"),
                "client allows ordinary duplicates to start temporary placement");
        check(calls(service,"consume","removeAll")&&calls(service,"consume","replaceTemporary"),
                "ordinary food placement clears matching temporary records and their effect supply");
        check(calls(service,"consume","completed")&&calls(service,"consume","healthPoints")&&calls(service,"consume","heal"),
                "extra-food healing requires completed eating and uses final food health value");
        check(calls(compiled("CuisineDigestionUse"),"completed","isSameItemSameTags")
                &&calls(compiled("CuisineDigestionUse"),"completed","elapsedEnough"),
                "completion cannot substitute a different food or skip extended eating time");
        check(calls(service,"findPlacement","temporary")&&calls(service,"findPlacement","acceptsTemporary"),
                "server placement uses native growth-aware temporary capacity");
        check(calls(service,"consume","replaceTemporary")&&!calls(service,"consume","acceptNative"),
                "refresh replaces old flavor supply without owning newly consumed native effects");
        check(effectsNode().methods.stream().noneMatch(method->method.name.equals("acceptNative")),
                "native effects never enter the finite flavor reserve");
        check(calls(service,"tick","advanceOrdinary"),"hunger's extra tick does not accelerate temporary food");
        var effects=compiled("CuisineFoodEffects");
        check(calls(effects,"observe","removeDepletedShield"),"shield exhaustion is checked every server observation before effect renewal");
        check(calls(effects,"removeDepletedShield","getAbsorptionAmount")&&calls(effects,"removeDepletedShield","active")
                &&calls(effects,"removeDepletedShield","exhaust")&&calls(effects,"removeDepletedShield","remove"),
                "empty shield removes absorption only in Valheim and consumes existing food reserves");
        check(calls(effects,"removeDepletedShield","markTriggered")&&calls(effects,"collect","wasTriggered"),
                "legacy uninitialized food cannot refill a broken absorption shield");
        check(calls(effects,"collect","markTriggered")&&calls(effects,"collect","applyOneShot"),
                "one-shot food effect is claimed before dispatch and not renewed");
        var adapters=compiled("CuisineEffectAdapters");
        check(calls(adapters,"applyOneShot","applyInstantenousEffect")&&calls(adapters,"applyOneShot","applyEffectTick"),
                "native instant and explicit end-trigger adapters call actual host execution methods");
        check(!calls(adapters,"applyOneShot","addEffect"),"one-shot execution cannot be merged into a longer active instance");
        check(calls(effects,"reconcile","allFoods")&&calls(effects,"collect","activeTicks"),
                "temporary effects use ten-second source lifetime and normal ownership");
        var events=compiled("CuisineFoodEvents");
        check(calls(events,"tick","recovery")&&calls(events,"tick","amount"),
                "server recovery pulses consult the active adapted potion effect");
        check(calls(events,"tick","temporaryHealPerPulse"),"temporary regeneration is part of server tick");
        var language=compiled("mixin/client/EffectDescriptionLanguageMixin");
        check(calls(language,"cuisinejournal$adaptDescription","active"),"translated descriptions use server-synced mode");
        check(calls(compiled("mixin/client/JeedEffectDescriptionMixin"),"cuisinejournal$description","active"),
                "cached JEED page descriptions reevaluate mode when displayed");
        stateTests();
        System.out.println("TemporaryFoodTest: "+checks+" checks passed");
    }
    private static void stateTests() {
        try {
            var state=compiled("CuisineFoodState");
            check(calls(state,"advance","advanceOrdinary")&&calls(state,"advance","removeIf"),
                    "ordinary and temporary timers advance together and expire");
            check(!calls(state,"skipNight","tick")&&calls(state,"skipNight","advance"),
                    "sleep advances temporary food directly without ordinary minimum");
            check(calls(state,"read","sameFood")&&calls(state,"read","totalTicks"),
                    "saved duplicate temporary foods and excessive duration are checked");
            boolean save=false,read=false;
            for(var method:state.methods)for(var instruction:method.instructions)
                if(instruction instanceof LdcInsnNode value&&"temporary_count".equals(value.cst)) {
                    if(method.name.equals("save"))save=true;
                    if(method.name.equals("read"))read=true;
                }
            check(save&&read,"temporary food state is serialized separately from normal slots");
            int remaining=200;
            for(int i=0;i<199;i++)remaining=CuisineFoodMath.remainingAfterAdvance(remaining,1);
            check(remaining==1&&CuisineFoodMath.remainingAfterAdvance(remaining,1)==0,"ten-second timer expires exactly at two hundred ticks");
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private static ClassNode compiled(String name)throws Exception {
        var node=new ClassNode();
        try(var input=TemporaryFoodTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/"+(name.startsWith("mixin/")?name:"food/"+name)+".class")) {
            new ClassReader(java.util.Objects.requireNonNull(input)).accept(node,0);
        }
        return node;
    }
    private static ClassNode effectsNode()throws Exception{return compiled("CuisineFoodEffects");}
    private static boolean calls(ClassNode node,String method,String target) {
        for(var value:node.methods)if(value.name.equals(method)||value.name.startsWith("lambda$"+method+"$"))for(var instruction:value.instructions)
            if(instruction instanceof MethodInsnNode call&&call.name.equals(target))return true;
        return false;
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}
