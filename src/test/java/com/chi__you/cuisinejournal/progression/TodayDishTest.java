package com.chi__you.cuisinejournal.progression;

import com.chi__you.cuisinejournal.config.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

public final class TodayDishTest {
    private static int checks;
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    public static void main(String[] args){
        var config=new ServerConfig();config.validate();
        check(config.todayDish.groups.size()==1&&config.todayDish.groups.get(0).mods.equals(List.of("minecraft","farmersdelight",
                "delightful","brewinandchewin","youkaishomecoming")),"default whitelist contains only requested mods");
        var serialized=Toml.write(config,List.of());
        var round=Toml.read(serialized,ServerConfig.class);round.validate();
        check(round.todayDish.groups.get(0).mods.equals(config.todayDish.groups.get(0).mods)
                &&round.todayDish.groups.get(0).weight==1,"group config round trips as TOML array tables");
        check(!Toml.read("[challenges]\nrecipeOfTheDayEnabled = false\n",ServerConfig.class).todayDish.enabled,
                "legacy enabled preference migrates to independent feature");
        var pool=config.todayDish;pool.groups=new ArrayList<>(List.of(new ServerConfig.TodayDishGroup(1,List.of("test"),1),
                new ServerConfig.TodayDishGroup(10,List.of("late"),3)));
        ResourceLocation overworld=id("minecraft:overworld"),nether=id("minecraft:the_nether");
        ResourceLocation low=id("test:soup"),high=id("late:boss"),foreign=id("other:food");
        var candidates=List.of(new TodayDishSelection.Candidate(low,overworld),new TodayDishSelection.Candidate(high,nether),
                new TodayDishSelection.Candidate(foreign,overworld));
        check(TodayDishSelection.choose(8,44,pool,candidates,Set.of(overworld,nether)).equals(low),"one-based unlock day is enforced");
        check(TodayDishSelection.choose(9,44,pool,candidates,Set.of(overworld)).equals(low),"unvisited dimension still locks late foods");
        check(TodayDishSelection.choose(9,44,pool,candidates,Set.of())==null,"no eligible food returns no daily choice");
        pool.requireVisitedDimension=false;
        int late=0;
        for(int i=0;i<10000;i++){
            var choice=TodayDishSelection.choose(10,i*7919L,pool,candidates,Set.of());
            if(choice.equals(foreign))throw new AssertionError("nonwhitelisted mod entered pool");if(choice.equals(high))late++;
        }
        check(late>7000&&late<8000,"three-to-one group weight is reflected in choices");
        var first=TodayDishSelection.choose(10,59,pool,candidates,Set.of());
        check(first.equals(TodayDishSelection.choose(10,59,pool,List.of(candidates.get(2),candidates.get(1),candidates.get(0)),Set.of())),
                "candidate ordering cannot reroll the day's choice");
        pool.groups.clear();check(TodayDishSelection.choose(10,59,pool,candidates,Set.of())==null,"empty whitelist never falls back to all foods");
        check(Toml.read(Toml.write(config,List.of()),ServerConfig.class).todayDish.groups.isEmpty(),
                "empty whitelist survives save and reload instead of restoring defaults");
        var state=new TodayDishState();state.visit(nether);state.select(4,high);
        var restored=TodayDishState.load(state.save(new CompoundTag()));
        check(restored.day()==4&&restored.dish().equals(high)&&restored.visited().contains(nether),"selection and visits survive restart");
        check(ProductionQuality.stars(4,true)==5&&ProductionQuality.stars(5,true)==5&&ProductionQuality.stars(0,true)==1,
                "today adds a star and respects five-star ceiling");
        close(.28,ProductionQuality.chance(.08,true,1));close(.25,ProductionQuality.chance(.08,true,4));
        close(.13,ProductionQuality.chance(.08,true,16));close(.01,ProductionQuality.chance(.08,true,32));
        close(.05,ProductionQuality.chance(.08,false,4));close(.01,ProductionQuality.chance(.08,false,16));
        close(1,ProductionQuality.chance(.99,true,1));close(0,ProductionQuality.chance(.99,true,0));
        close(.01,ProductionQuality.chance(0,false,1));close(.01,ProductionQuality.chance(0,false,64));
        close(.20,ProductionQuality.chance(0,true,1));
        close(.04,ProductionQuality.chance(.08,false,64,.04));
        close(0,ProductionQuality.chance(.08,false,64,0));close(0,ProductionQuality.chance(.08,true,0,.04));
        close(1,ProductionQuality.chance(0,false,1,2));
        close(.08,ProductionQuality.chance(.08,false,1,Double.NaN));
        check(round.mastery.bonuses.minimumPerfectChance==.01,"missing setting defaults to one percent");
        config.mastery.bonuses.minimumPerfectChance=.0375;
        var configured=Toml.read(Toml.write(config,List.of()),ServerConfig.class);configured.validate();
        close(.0375,configured.mastery.bonuses.minimumPerfectChance);
        configured.mastery.bonuses.minimumPerfectChance=-1;configured.validate();close(0,configured.mastery.bonuses.minimumPerfectChance);
        configured.mastery.bonuses.minimumPerfectChance=2;configured.validate();close(1,configured.mastery.bonuses.minimumPerfectChance);
        var rules=new com.chi__you.cuisinejournal.network.Packets.SyncRules(1,2,3,100,List.of(1,2,3,4,5),"CUSTOM",
                false,true,0,.08,0,1,0,0,true,false,0,.05,1,1.5,true,true,true,false,.0375);
        var rulesBuffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{rules.write(rulesBuffer);
            check(com.chi__you.cuisinejournal.network.Packets.SyncRules.read(rulesBuffer).equals(rules)&&rulesBuffer.readableBytes()==0,
                    "configured minimum probability survives server-to-client sync");
        }finally{rulesBuffer.release();}
        var packet=new com.chi__you.cuisinejournal.network.Packets.SyncTodayDish(10,high);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{packet.write(buffer);check(com.chi__you.cuisinejournal.network.Packets.SyncTodayDish.read(buffer).equals(packet)
                &&buffer.readableBytes()==0,"today dish sync carries stable identity and day");}finally{buffer.release();}
        try{contracts();}catch(Exception failure){throw new AssertionError(failure);}
        System.out.println("TodayDishTest: "+checks+" checks passed");
    }
    private static void contracts()throws Exception{
        var tracker=compiled("tracking/ProductionTracker");
        check(calls(tracker,"finish","stampOutput"),"returned extraction count is supplied to deferred quality before credit");
        check(!calls(tracker,"markExtracted","stampOutput")&&calls(tracker,"markExtracted","write"),
                "extracted food copies the decided quality instead of rerolling");
        check(calls(tracker,"quickMoveOutput","safeTake")&&calls(tracker,"quickMoveOutput","inventoryRoom"),
                "shift extraction reserves inventory capacity before one serving roll");
        var spring=compiled("mixin/production/HotSpringProductionMixin");
        check(calls(spring,"cuisinejournal$result","output")&&calls(spring,"cuisinejournal$result","copyWithCount"),
                "hot spring determines quality before splitting item entities");
        check(calls(compiled("event/CulinaryHooks"),"onPlayerJoin","visit")
                &&calls(compiled("forge/ForgeEventBridge"),"onDimensionChanged","visit"),"dimension unlock visits are recorded at join and travel");
        check(calls(compiled("progression/CookingRewards"),"stampOutput","isToday")
                &&calls(compiled("progression/CookingRewards"),"stampOutput","chance"),"daily bonuses use shared quality math");
        check(calls(compiled("tracking/HotSpringProduction"),"output","stampProduced")
                &&calls(compiled("progression/CookingRewards"),"stampProduced","peekOffline"),
                "single-owner spring batch fixes quality while owner is offline, without offline progression credit");
    }
    private static org.objectweb.asm.tree.ClassNode compiled(String path)throws Exception{
        var node=new org.objectweb.asm.tree.ClassNode();
        try(var input=TodayDishTest.class.getClassLoader().getResourceAsStream("com/chi__you/cuisinejournal/"+path+".class")){
            new org.objectweb.asm.ClassReader(Objects.requireNonNull(input)).accept(node,0);
        }return node;
    }
    private static boolean calls(org.objectweb.asm.tree.ClassNode node,String method,String target){
        for(var value:node.methods)if(value.name.equals(method))for(var instruction:value.instructions)
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.name.equals(target))return true;
        return false;
    }
    private static void close(double expected,double actual){check(Math.abs(expected-actual)<1e-9,"batch percentage-point quality formula");}
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);checks++;}
}
