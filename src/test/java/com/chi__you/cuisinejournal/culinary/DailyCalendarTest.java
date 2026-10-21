package com.chi__you.cuisinejournal.culinary;

import net.minecraft.nbt.CompoundTag;
import java.util.*;

public final class DailyCalendarTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        var switchWindow=new DailyCalendar();switchWindow.observe(21000,false,0);
        check(switchWindow.observe(22500,false,0).day()==1,"four AM counted before switching date provider");
        check(switchWindow.observe(22500,true,0).day()==1
                &&switchWindow.observe(24000,true,0).day()==1,"installing BetterTime inside four-to-six window does not count the same day twice");
        check(switchWindow.observe(48000,true,0).day()==2,"next actual date advances after guarded source switch");
        check(DailyCalendar.date(21999,false)==0&&DailyCalendar.date(22000,false)==1,
                "vanilla refresh boundary is 4 AM, not midnight or dawn");
        check(DailyCalendar.date(23999,false)==1&&DailyCalendar.date(24000,false)==1,
                "sleep to dawn after 4 AM cannot rotate twice");
        check(DailyCalendar.date(23999,true)==0&&DailyCalendar.date(24000,true)==1,
                "BetterTime follows its actual date rollover at tick zero");
        check(DailyCalendar.remaining(21999,false)==1&&DailyCalendar.remaining(22000,false)==24000
                &&DailyCalendar.remaining(24000,false)==22000,"vanilla remaining time uses shifted daylight boundary");
        check(DailyCalendar.remaining(23999,true)==1&&DailyCalendar.remaining(24000,true)==24000,
                "BetterTime remaining time follows its calendar date");
        var calendar=new DailyCalendar();calendar.observe(13000,false,8);
        check(calendar.observe(21999,false,99).day()==8,"upgrade preserves existing daily period even if legacy game time differs");
        check(calendar.observe(24000,false,99).day()==9,"sleep skipping 4 AM advances daily period once");
        check(calendar.observe(24100,false,99).day()==9,"same morning does not refresh twice");
        var reloaded=DailyCalendar.load(calendar.save(new CompoundTag()));
        check(reloaded.observe(24200,false,99).day()==9,"restart keeps calendar period and high-water mark");
        check(reloaded.observe(13000,false,99).day()==9&&reloaded.observe(24000,false,99).day()==9,
                "backward time edits cannot recreate already completed day rewards");
        check(reloaded.observe(4*24000,false,99).day()==12,"forward calendar jumps skip to current day instead of awarding skipped tasks");
        check(reloaded.observe(4*24000,true,99).day()==12,"installing BetterTime rebases source without an extra daily reroll");
        check(reloaded.observe(5*24000,true,99).day()==13,"BetterTime dates continue advancing after source switch");
        var betterSleep=new DailyCalendar();betterSleep.observe(14000,true,0);
        check(betterSleep.observe(24000,true,0).day()==1&&betterSleep.observe(24001,true,0).day()==1,
                "BetterTime sleeping to next date triggers one rotation");
        var stopped=new DailyCalendar();stopped.observe(14000,false,0);
        check(stopped.observe(14000,false,50).day()==0,"daylight cycle disabled means no daily reset from runtime alone");
        var state=new com.chi__you.cuisinejournal.challenge.ChallengeState(1);
        state.preparePeriod(9,100,Long.MAX_VALUE,true);state.advance(0,1);
        check(!state.preparePeriod(9,200,Long.MAX_VALUE,true)&&state.deadline()==Long.MAX_VALUE&&state.progress(0)==1,
                "daily progress has no running countdown and stays stable within its calendar period");
        state.complete(9);state.preparePeriod(9,200,Long.MAX_VALUE,true);
        check(state.isCompleted(),"calendar projection does not undo completed reward state");
        budgetTests();
        automaticChallengeTests();
        String jar=System.getenv("CUISINEJOURNAL_BETTERTIME_JAR");
        if(jar!=null)try(var archive=new java.util.jar.JarFile(jar);
                var input=archive.getInputStream(archive.getJarEntry("com/kljiana/bettertime/BetterTime.class"))) {
            var host=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(input).accept(host,0);
            var days=host.methods.stream().filter(m->m.name.equals("getDays")&&m.desc.equals("(Lnet/minecraft/world/level/Level;)J"))
                    .findFirst().orElseThrow();
            check(java.util.stream.StreamSupport.stream(days.instructions.spliterator(),false).anyMatch(i->
                    i instanceof org.objectweb.asm.tree.MethodInsnNode call&&call.name.equals("m_46468_")),
                    "installed BetterTime date reads dayTime rather than gameTime");
            check(java.util.stream.StreamSupport.stream(days.instructions.spliterator(),false).anyMatch(i->
                    i instanceof org.objectweb.asm.tree.LdcInsnNode value&&Long.valueOf(24000).equals(value.cst)),
                    "installed BetterTime date uses 24000 daylight ticks");
        }
        System.out.println("DailyCalendarTest: "+checks+" checks passed");
    }
    private static void budgetTests(){
        var calendar=new DailyCalendar();var config=new com.chi__you.cuisinejournal.config.ServerConfig();
        var dish=net.minecraft.resources.ResourceLocation.parse("test:meal");
        var profile=new CulinaryProfile(new UUID(3,4));
        long before=calendar.observe(21000,false,4).day();
        profile.meal(dish).recordPreparation(before,20,com.chi__you.cuisinejournal.recipe.CookingMethod.CRAFTING);
        profile.recordExperimentationXp(before,60);
        for(int i=0;i<5;i++)profile.claimSocialReward(before,dish,config);
        check(profile.claimSocialReward(before,dish,config).multiplier()==.5,"social count reaches diminishing tier before dawn");
        check(calendar.observe(21000,false,900).day()==before&&profile.experimentationXpOn(before)==60
                &&profile.meal(dish).preparedOnDay(before)==20,"frozen daylight cannot reset budgets despite elapsed runtime");
        long after=calendar.observe(24000,false,900).day();
        check(after==before+1&&profile.experimentationXpOn(after)==0&&profile.meal(dish).preparedOnDay(after)==0
                &&profile.claimSocialReward(after,dish,config).multiplier()==1,"sleep resets all three budgets on one shared day");
        var saved=CulinaryProfile.load(profile.playerId(),profile.save());
        var restored=DailyCalendar.load(calendar.save(new CompoundTag()));
        check(restored.observe(24001,false,1200).day()==after&&saved.claimSocialReward(after,dish,config).multiplier()==1,
                "restart preserves shared calendar and social usage");
        var slow=new DailyCalendar();slow.observe(1000,false,0);
        for(int i=0;i<100000;i++)if(slow.observe(1000+i/10,false,1000).day()!=0)throw new AssertionError("slow day advanced too soon");
        check(slow.observe(22000,false,1000).day()==1,"tenfold slower daylight refreshes at calendar boundary, not runtime boundary");
        var fast=new DailyCalendar();fast.observe(1000,false,0);
        check(fast.observe(72000,false,0).day()==3,"accelerated calendar advances all skipped dates consistently");
        var migrated=com.chi__you.cuisinejournal.config.Toml.read("[streaks]\nclock = \"REAL_DAYS\"\n",
                com.chi__you.cuisinejournal.config.ServerConfig.class);
        check(migrated.streaks.clock==com.chi__you.cuisinejournal.config.ConfigEnums.StreakClock.MINECRAFT_DAYS,
                "retired real-day selector migrates to shared calendar");
    }
    private static void automaticChallengeTests(){
        var attempt=new com.chi__you.cuisinejournal.challenge.ChallengeState(1);
        check(attempt.prepareAutomatic(4,100,Long.MAX_VALUE,0,true)&&attempt.started(),"single-day challenge automatically starts");
        attempt.advanceUnique(0,net.minecraft.resources.ResourceLocation.parse("test:a"));
        check(!attempt.prepareAutomatic(4,200,Long.MAX_VALUE,0,true)&&attempt.progress(0)==1&&attempt.deadline()==Long.MAX_VALUE,
                "automatic challenge tracks the calendar without a countdown");
        var reloaded=com.chi__you.cuisinejournal.challenge.ChallengeState.load(attempt.save(),1);
        check(reloaded.prepareAutomatic(5,201,Long.MAX_VALUE,0,true)&&reloaded.progress(0)==0,
                "unfinished single-day challenge automatically retries on next calendar day");
        reloaded.complete(5);check(!reloaded.prepareAutomatic(6,300,Long.MAX_VALUE,0,true)&&reloaded.isCompleted(),
                "completed one-off challenge never resets when day changes");
        var lifetime=new com.chi__you.cuisinejournal.challenge.ChallengeState(1);
        lifetime.prepareAutomatic(4,100,Long.MAX_VALUE,0,false);lifetime.advance(0,2);
        check(!lifetime.prepareAutomatic(5,1000,Long.MAX_VALUE,0,false)&&lifetime.progress(0)==2,
                "unlimited automatic challenge retains progress across days");
        var legacy=com.chi__you.cuisinejournal.challenge.ChallengeDefinition.fromJson(
                net.minecraft.resources.ResourceLocation.parse("test:old"),com.google.gson.JsonParser.parseString(
                        "{\"manual\":true,\"objectives\":[{\"type\":\"COOK_RECIPE\",\"amount\":2}]}").getAsJsonObject());
        check(!legacy.manual(),"legacy manual settings cannot re-enable acceptance requirement");
        var slowDay=new com.chi__you.cuisinejournal.challenge.ChallengeState(1);
        slowDay.prepareAutomatic(4,10,Long.MAX_VALUE,0,true);slowDay.advance(0,2);
        check(!slowDay.prepareAutomatic(4,1000,Long.MAX_VALUE,0,true)&&slowDay.progress(0)==2&&!slowDay.failed(),
                "slow or frozen world at calendar boundary cannot fail an attempt by runtime projection");
        var expired=new com.chi__you.cuisinejournal.challenge.ChallengeState(1);expired.start(100,1);expired.advance(0,3);expired.expire(102);
        expired.prepareAutomatic(4,200,Long.MAX_VALUE,0,false);
        check(!expired.failed()&&expired.deadline()==Long.MAX_VALUE&&expired.progress(0)==3,
                "legacy timed-out challenge migrates without countdown or erasing progress");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
