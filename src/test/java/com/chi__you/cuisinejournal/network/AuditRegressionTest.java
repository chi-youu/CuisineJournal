package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.config.*;
import com.chi__you.cuisinejournal.culinary.*;
import com.chi__you.cuisinejournal.challenge.*;
import com.chi__you.cuisinejournal.recipe.*;
import com.chi__you.cuisinejournal.data.ProfileStorage;
import com.google.gson.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.nio.file.*;
import java.util.*;

public final class AuditRegressionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        directions(); counts(); budgets(); userFiles(); challengeGrowth(); profileWrites(); remainingRegressions();
        var config=new ServerConfig();
        config.progression=null;config.mastery=null;config.antiFarming=null;config.challenges=null;
        config.validate();check(config.progression!=null&&config.mastery!=null,"legacy null sections recover");
        var profile=new CulinaryProfile(UUID.randomUUID());profile.addCookingXp(Long.MAX_VALUE,LevelCurve.DEFAULT);
        profile.addCookingXp(1,LevelCurve.DEFAULT);
        check(profile.cookingXp()==Long.MAX_VALUE,"XP addition saturates instead of wrapping negative");
        check(DishServingVariants.target(null,Set.of())==null,"cup overload accepts null consistently");
        check(com.chi__you.cuisinejournal.food.CuisineFoodMath.remainingAfterSleep(600,12_000)==0,"sleep never extends sub-minute food to sixty seconds");
        check(com.chi__you.cuisinejournal.food.CuisineFoodMath.remainingAfterSleep(600,-20)==600
                &&com.chi__you.cuisinejournal.food.CuisineFoodMath.remainingAfterSleep(30_000,12_000)==18_000,"sleep clamps negative skips and debits exact elapsed time");
        check(ChallengeReward.parseItem("minecraft:bread 2147483647")==null,"absurd item reward quantity rejected");
        var removal=net.minecraft.tags.TagFile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                JsonParser.parseString("{values:[],remove:['minecraft:stone']}")).result().orElseThrow();
        check(removal.remove().size()==1&&removal.remove().get(0).getId().equals(ResourceLocation.parse("minecraft:stone")),
                "Forge 1.20.1 really decodes tag remove field; audit U2 is not a defect");
        check(com.chi__you.cuisinejournal.tracking.FluidProductionTracker.unaccountedDrain(250,250)==0
                &&com.chi__you.cuisinejournal.tracking.FluidProductionTracker.unaccountedDrain(500,250)==250,"bottled fluid debit not deducted again at frame end");
        check(com.chi__you.cuisinejournal.compat.UnificationPacks.replace(List.of("vanilla","mod_resources","file/user"),List.of("cuisinejournal:unification"))
                .equals(List.of("vanilla","mod_resources","cuisinejournal:unification","file/user")),"missing managed anchor preserves user-pack precedence");
        System.out.println("AuditRegressionTest: "+checks+" checks passed");
    }
    private static void directions() {
        for(var id:Network.packetIds()) {
            check(PacketSafety.allowed(id,true)!=PacketSafety.allowed(id,false),"exactly one allowed packet direction: "+id);
        }
        check(PacketSafety.allowed(Packets.RequestProfile.ID,true),"profile request permitted");
        check(!PacketSafety.allowed(Packets.SyncCatalogue.ID,true),"forged catalogue rejected before decoder");
        check(!PacketSafety.allowed(ResourceLocation.parse("test:unknown"),false)
                &&!PacketSafety.allowed(ResourceLocation.parse("test:unknown"),true),"unknown envelopes rejected");
    }
    private static void counts() {
        for(int size:new int[]{-1,Integer.MAX_VALUE,65_537}) {
            FriendlyByteBuf b=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                b.writeVarInt(size); boolean failed=false;
                try{Packets.SyncCatalogue.read(b);}catch(RuntimeException expected){failed=true;}
                check(failed,"catalogue oversized/truncated rejected without allocation "+size);
            }finally{b.release();}
            b=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                b.writeBoolean(false);b.writeBoolean(false);b.writeVarInt(size);
                check(Network.decode(Packets.SyncCatalogue.ID,b)==null,"malicious seven-byte catalogue decode stays bounded");
            }finally{b.release();}
        }
        FriendlyByteBuf b=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{b.writeVarInt(1000);boolean failed=false;try{PacketSafety.count(b,4096);}catch(RuntimeException expected){failed=true;}
            check(failed,"truncated count fails before allocating declared collection");}finally{b.release();}
    }
    private static void budgets() {
        var budget=new RequestBudget<String>();
        check(budget.allow("a","profile",0,2000),"first request accepted");
        check(!budget.allow("a","profile",1,2000),"profile macro blocked");
        check(budget.allow("b","profile",1,2000),"another player independent");
        check(budget.allow("a","profile",2000,2000),"cooldown recovers");
        int accepted=0;for(int i=0;i<1000;i++)if(budget.allow("flood","detail",0,0))accepted++;
        check(accepted==24,"shared bucket bounds a same-tick flood");
        check(budget.allow("flood","detail",1000,0),"tokens recover");
        budget.remove("a");check(budget.allow("a","profile",2001,2000),"disconnect forgets budget");
    }
    private static ResourceManager emptyResources() {
        return (ResourceManager)java.lang.reflect.Proxy.newProxyInstance(AuditRegressionTest.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class},(proxy,method,arguments)->method.getName().equals("listResources")?Map.of():null);
    }
    private static void userFiles() throws Exception {
        Path dir=Files.createTempDirectory("cj-audit-files");
        GlobalCategoryStore.setPackOverlays(List.of());GlobalCategoryStore.load(dir);GlobalCategoryStore.apply(List.of());
        check(GlobalCategoryStore.createRoot("test:kept","Kept",null),"category fixture saved");
        String before=Files.readString(dir.resolve("cuisinejournal-categories.json"));
        check(!GlobalCategoryStore.replaceLocal(JsonParser.parseString("{roots:17}").getAsJsonObject()),"malformed layer rejected");
        check(!GlobalCategoryStore.replaceLocal(JsonParser.parseString("{categories:17}").getAsJsonObject()),"wrong nested schema cannot clear local categories");
        check(Files.readString(dir.resolve("cuisinejournal-categories.json")).equals(before),"rejected layer never writes");
        Path categories=dir.resolve("cuisinejournal-categories.json");Files.writeString(categories,"{broken");
        GlobalCategoryStore.load(dir);
        check(!GlobalCategoryStore.replaceLocal(new JsonObject()),"unreadable categories stay read-only");
        check(Files.readString(categories).equals("{broken")&&GlobalCategoryStore.hasCustomRoot("test:kept"),"invalid category file and last good runtime retained");
        Files.writeString(categories,before);GlobalCategoryStore.load(dir);
        check(GlobalCategoryStore.replaceLocal(GlobalCategoryStore.localJson()),"repair and reload unlocks editing");
        IngredientCatalogueConfig.load(dir);Path ingredients=dir.resolve("cuisinejournal-ingredients.json");
        Files.writeString(ingredients,"{broken");IngredientCatalogueConfig.load(dir);
        check(!IngredientCatalogueConfig.replaceLocalFlavors(Map.of(),Map.of())&&Files.readString(ingredients).equals("{broken"),"broken ingredient binding file not overwritten");
        var resource=emptyResources();FlavorPresets.reload(resource,dir);
        var preset=new FlavorPresets.Preset(ResourceLocation.parse("test:kept"),"Kept","",true,List.of());
        check(FlavorPresets.putLocal(preset),"flavor fixture saved");
        Path flavors=dir.resolve("cuisinejournal-flavors.json");Files.writeString(flavors,"{broken");
        FlavorPresets.reload(resource,dir);
        check(FlavorPresets.get(preset.id())!=null&&!FlavorPresets.clearLocal(),"broken flavors retain runtime and reject edits");
        try(var files=Files.list(dir)){check(files.filter(p->p.getFileName().toString().contains(".unreadable-")).count()==3,"each unreadable file has unique recovery copy");}
    }
    private static void challengeGrowth() {
        var profile=new CulinaryProfile(UUID.randomUUID());var id=ResourceLocation.parse("test:grow");
        var state=profile.challenge(id,1);state.advance(0,3);
        var same=profile.challenge(id,3);check(same==state&&same.progress(0)==3,"objective expansion keeps old progress");
        check(same.advance(2,2),"new objective counter writable");
        check(same.satisfies(List.of(new ChallengeObjective(ChallengeObjective.Type.EAT_FOOD,"",3,false),
                new ChallengeObjective(ChallengeObjective.Type.EAT_FOOD,"",1,false),
                new ChallengeObjective(ChallengeObjective.Type.EAT_FOOD,"",2,false)))==false,"new unmet objective cannot complete early");
    }
    private static void profileWrites() throws Exception {
        Path dir=Files.createTempDirectory("cj-audit-profile");var storage=new ProfileStorage(dir);
        var profile=new CulinaryProfile(UUID.randomUUID());profile.addCookingXp(30,LevelCurve.DEFAULT);
        Files.createDirectories(storage.root());Path temp=storage.root().resolve(profile.playerId()+".dat.tmp");
        Files.createDirectory(temp);
        check(!storage.save(profile)&&profile.isDirty(),"failed profile save reports failure and keeps dirty");
        Files.delete(temp);check(storage.save(profile)&&!profile.isDirty(),"profile write can retry successfully");
        var futureTag=profile.save();futureTag.putInt("schemaVersion",Integer.MAX_VALUE);
        var future=CulinaryProfile.load(profile.playerId(),futureTag);
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                Files.readAllBytes(storage.root().resolve(profile.playerId()+".dat"))));
        check(!storage.save(future),"newer schema cannot be downgraded by saving");
        check(hash.equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                Files.readAllBytes(storage.root().resolve(profile.playerId()+".dat"))))),"future profile refusal preserves disk bytes");
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private static void remainingRegressions() throws Exception {
        var oldConfig=new ServerConfig();oldConfig.flavors.enabled=false;oldConfig.nutritionDisplay.enabled=true;
        FoodModeDefaults.initializeExisting(oldConfig,"valheim");
        check(!FoodModeDefaults.apply(oldConfig,"valheim")&&!oldConfig.flavors.enabled&&oldConfig.nutritionDisplay.enabled,
                "upgrading an existing config preserves manually selected mode toggles");
        check(com.chi__you.cuisinejournal.food.CuisineFoodStats.displayedRegen(2F,10)==1F
                &&com.chi__you.cuisinejournal.food.CuisineFoodStats.displayedRegen(2F,5)==2F,
                "tooltip rate follows actual regeneration interval without changing gameplay");
        boolean oversized=false;try{PacketSafety.size(4097,4096);}catch(IllegalArgumentException expected){oversized=true;}
        check(oversized&&PacketSafety.size(4096,4096)==4096,
                "outgoing editor collections enforce the same limit as their decoder");
        var frames=new ArrayDeque<String>();frames.push("outer");frames.push("abandoned");
        check(com.chi__you.cuisinejournal.tracking.FrameStacks.finish(frames,"other"::equals)==null&&frames.size()==2,
                "unmatched station cannot steal another station frame");
        check(com.chi__you.cuisinejournal.tracking.FrameStacks.finish(frames,"outer"::equals).equals("outer")&&frames.isEmpty(),
                "matching outer frame unwinds an abandoned inner call");
        var savedBatch=new net.minecraft.nbt.CompoundTag();
        savedBatch.put("output100",com.chi__you.cuisinejournal.tracking.ProductionLedger.writeBatch(
                new com.chi__you.cuisinejournal.tracking.ProductionLedger.Batch("test:food",4,Set.of(),Set.of(),"test:cooking")));
        for(int i=0;i<5;i++)com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction.outputBatch(savedBatch,1,false);
        check(com.chi__you.cuisinejournal.tracking.ProductionLedger.readBatch(savedBatch.getCompound("output100")).count()==4,
                "output preview and simulated extraction never debit a production batch");
        check(com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction.outputBatch(savedBatch,3,true).count()==3
                &&com.chi__you.cuisinejournal.tracking.ProductionLedger.readBatch(savedBatch.getCompound("output100")).count()==1,
                "actual bulk extraction consumes only the delivered portion");
        com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction.outputBatch(savedBatch,1,true);
        check(!savedBatch.contains("output100")
                &&com.chi__you.cuisinejournal.tracking.KaleidoscopeProduction.outputBatch(savedBatch,1,true)==null,
                "exhausted batch cannot attribute another generated output");
        Map<Long,String> placed=new HashMap<>();String old=new String("same");placed.put(1L,old);
        var data=new net.minecraft.world.level.saveddata.SavedData(){public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag){return tag;}};
        com.chi__you.cuisinejournal.tracking.PlacedDataCleanup.defer(placed,1L,data);
        placed.put(1L,new String("same"));com.chi__you.cuisinejournal.tracking.PlacedDataCleanup.flush();
        check(placed.containsKey(1L),"same-tick equal new food is not mistaken for the destroyed record");
        com.chi__you.cuisinejournal.tracking.PlacedDataCleanup.defer(placed,1L,data);
        com.chi__you.cuisinejournal.tracking.PlacedDataCleanup.flush();
        check(placed.isEmpty()&&data.isDirty(),"destroyed record removed after serving settlement");
        var profile=new CulinaryProfile(UUID.randomUUID());var id=ResourceLocation.parse("test:reward");
        int[] attempts={0};var action=new net.minecraft.nbt.CompoundTag();action.putString("kind","item");
        check(!RewardDelivery.attempt(action,()->false,()->attempts[0]++,failure->{} )
                &&attempts[0]==0&&action.getInt("state")==0,
                "no item delivery before a successful durable checkpoint");
        var checkpoints=new ArrayList<Integer>();
        RewardDelivery.attempt(action,()->{checkpoints.add(action.getInt("state"));return true;},
                ()->{attempts[0]++;throw new IllegalStateException("partial external delivery");},failure->{});
        RewardDelivery.attempt(action,()->true,()->attempts[0]++,failure->{});
        check(attempts[0]==1&&action.getInt("state")==2&&checkpoints.equals(List.of(2,2)),
                "uncertain partial item delivery is preserved and never blindly duplicated");
        var next=new net.minecraft.nbt.CompoundTag();next.putString("kind","badge");
        check(RewardDelivery.attempt(next,()->true,()->attempts[0]++,failure->{})
                &&next.getInt("state")==1&&attempts[0]==2,
                "failure of one reward component does not block other components");
        RewardDelivery.enqueue(profile,id,10,new ChallengeReward(30,0,2,null,null,List.of("minecraft:bread 2"),List.of()));
        var delivery=profile.rewardDeliveries().getCompound(0);
        check(!RewardDelivery.settled(delivery),"frozen reward begins pending");
        var actions=delivery.getList("actions",10);actions.getCompound(0).putInt("state",1);
        actions.getCompound(1).putInt("state",2);profile.clearChallenge(id);
        var restored=CulinaryProfile.load(profile.playerId(),profile.save());
        check(restored.rewardDeliveries().getCompound(0).getList("actions",10).getCompound(1).getInt("state")==2,
                "uncertain external delivery survives save/load and challenge rotation");
        check(restored.rewardDeliveries().getCompound(0).getList("actions",10).getCompound(0).getInt("state")==1,
                "already delivered XP never queued again");
        profile.reset(CulinaryProfile.ResetScope.EVERYTHING);check(profile.rewardDeliveries().isEmpty(),"explicit full reset clears reward outbox");
        var recipes=FruitCauldronBridge.recipes(List.of(
                new FruitCauldronBridge.Step(ResourceLocation.parse("test:pot"),id,1,null,ResourceLocation.parse("test:good"),1),
                new FruitCauldronBridge.Step(ResourceLocation.parse("test:pot"),id,999,null,ResourceLocation.parse("test:bad"),1)));
        check(recipes.size()==1&&recipes.get(0).masteryTarget().equals(ResourceLocation.parse("test:good")),
                "one illegal cauldron step cannot remove unrelated valid recipes");
        var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{new SyncFailure(JournalSyncPackets.Directory.ID).write(buffer);
            check(SyncFailure.read(buffer).failedPacket().equals(JournalSyncPackets.Directory.ID),"oversized sync failure packet roundtrips");
        }finally{buffer.release();}
        Path dir=Files.createTempDirectory("cj-edit-transaction");Path file=dir.resolve("cuisinejournal-flavors.json");
        Files.writeString(file,"original");var snapshot=new com.chi__you.cuisinejournal.data.LocalEditSnapshot(dir);
        Files.writeString(file,"changed");Files.writeString(dir.resolve("cuisinejournal-categories.json"),"created");snapshot.restore();
        check(Files.readString(file).equals("original")&&!Files.exists(dir.resolve("cuisinejournal-categories.json")),
                "failed edit restores exact file bytes and removes newly created document");
        var zh=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/cuisinejournal/lang/zh_cn.json"))).getAsJsonObject();
        var en=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/cuisinejournal/lang/en_us.json"))).getAsJsonObject();
        check(en.keySet().containsAll(zh.keySet()),"English resources cover every Chinese language key");
        var workstationTag=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/cuisinejournal/tags/blocks/workstations.json"))).getAsJsonObject().getAsJsonArray("values");
        var blocks=new HashSet<String>();workstationTag.forEach(value->{var entry=value.getAsJsonObject();
            check(!entry.get("required").getAsBoolean(),"optional workstation tag member cannot break missing-mod loads");blocks.add(entry.get("id").getAsString());});
        try(var files=Files.list(Path.of("src/main/resources/data/cuisinejournal/cuisinejournal/compat/workstations"))){
            for(var path:files.toList())for(var station:JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("workstations"))
                check(blocks.contains(station.getAsJsonObject().get("block").getAsString()),"public workstation tag covers bundled station definitions");
        }
        var categoryDirectory=Path.of("src/main/resources/data/cuisinejournal/cuisinejournal/categories");
        if(Files.isDirectory(categoryDirectory))try(var files=Files.list(categoryDirectory)){
            for(var path:files.toList())for(var sub:JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("subcategories")){
                var row=sub.getAsJsonObject();String key=row.get("translation_key").getAsString();
                check(!row.has("name")&&zh.has(key)&&en.has(key),"bundled category title resolves in both languages: "+key);
            }
        }
    }
}
