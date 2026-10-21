package com.chi__you.cuisinejournal.tracking;
import java.util.*;
public final class ProductionLedgerTest {
    private static int checks;
    private static final UUID A=new UUID(0,1),B=new UUID(0,2),C=new UUID(0,3);
    public static void main(String[] args){
        iceBucketPersistence();
        ProductionLedger l=new ProductionLedger();
        l.syncInput(0,"meat",2,A);l.syncInput(1,"carrot",2,B);
        var batch=l.produce(Map.of(0,1,1,1),"soup",4,"cooking");
        check(batch.belongsTo(A)&&batch.belongsTo(B)&&!batch.belongsTo(C),"only actual contributors qualify");
        check(batch.ingredients().equals(Set.of("meat","carrot")),"real consumed ingredients retained");
        l.append(6,batch);l.transfer(6,8,2);
        check(l.peek(6).count()==2&&l.peek(8).count()==2,"container transfer preserves batch quantities");
        check(l.peek(8).contributors().equals(Set.of(A,B)),"container provider cannot change contributor set");
        var restored=ProductionLedger.load(l.save());
        check(restored.save().equals(l.save()),"ledger persistence round trip");
        check(restored.take(8,1).get(0).belongsTo(B)&&restored.peek(8).count()==1,"partial take preserves remaining provenance");
        l=new ProductionLedger();l.syncInput(0,"meat",1,null);l.syncInput(0,"meat",2,A);
        check(l.produce(Map.of(0,1),"food",1,"cooking").belongsTo(A),"real insertion contributes to shared input stack");
        check(l.produce(Map.of(0,1),"food",1,"cooking").belongsTo(A),"shared contribution lasts until input stack is exhausted");
        l=new ProductionLedger();l.syncInput(0,"meat",1,A);l.syncInput(0,"meat",0,null);l.syncInput(0,"meat",1,B);
        check(l.produce(Map.of(0,1),"food",1,"cooking").contributors().equals(Set.of(B)),"withdrawn ingredients no longer contribute");
        l=new ProductionLedger();l.syncInput(0,"meat",1,A);l.syncInput(0,"fish",1,null);
        check(l.produce(Map.of(0,1),"food",1,"cooking").contributors().isEmpty(),"replaced input invalidates provenance");
        l=new ProductionLedger();l.syncInput(0,"milk",1,A);l.syncInput(1,"bowl",1,B);
        var food=l.produce(Map.of(0,1,1,1),"food",1,"crafting");
        check(food.contributors().equals(Set.of(A,B)),"consumed container qualifies as contribution");
        l.append(2,food);l.append(2,new ProductionLedger.Batch("food",2,Set.of(B),Set.of("fish"),"cooking"));
        check(l.peek(2).count()==3,"same output stack merges all quantities");
        check(l.take(2,1).get(0).belongsTo(A)&&l.peek(2).belongsTo(A)&&l.peek(2).belongsTo(B),"partial extraction keeps shared contributors on rest");
        l.syncOutput(2,"food",1,"cooking");check(l.peek(2).count()==1,"machine extraction reconciles quantity");
        l.syncOutput(2,"food",3,"cooking");l.take(2,1);
        check(l.peek(2).belongsTo(A)&&l.peek(2).belongsTo(B),"output additions preserve shared stack contributors");
        l.syncOutput(2,"other",1,"cooking");check(l.peek(2).identity().equals("other")&&l.peek(2).contributors().isEmpty(),"replaced output cannot inherit old maker");
        check(ProductionLedger.readBatch(ProductionLedger.writeBatch(batch)).equals(batch),"drop provenance serialization");
        check(l.take(2,0).isEmpty()&&l.peek(2).count()==1,"failed extraction commits no batch");
        l=new ProductionLedger();l.syncInput(0,"meat",1,A);l.syncInput(7,"custom_container",1,B);
        l.append(6,l.produce(Map.of(0,1),"soup",3,"cooking"));
        l.transferWithContainers(6,8,7,1,1);
        check(l.peek(8).contributors().equals(Set.of(A,B)),"actual consumed custom container joins serving contributors");
        check(l.peek(6).contributors().equals(Set.of(A)),"container contribution never leaks to unserved portions");
        l.transferWithContainers(6,8,7,1,1);l.take(8,1);
        check(l.peek(8).belongsTo(B),"stacked servings share contributor union");
        l=new ProductionLedger();l.syncInput(0,"meat",1,A);l.syncInput(4,"unrelated",1,C);
        check(!l.produce(Map.of(0,1),"soup",1,"cooking").belongsTo(C),"unconsumed unrelated input never grants contribution");
        checkTwentyStack();
        checkFluidAndCopper();
        checkLastInserter();
        checkEtheriumInventories();
        check(HotSpringSource.contributors(List.of(A,A)).equals(Set.of(A)),"single thrower across multiple inputs stays uniquely attributed");
        check(HotSpringSource.contributors(List.of(A,B)).equals(Set.of(A,B)),"shared hot spring inputs stay multiplayer claimable");
        check(HotSpringSource.contributors(Arrays.asList(A,null)).isEmpty(),"one untraceable consumed input makes hot spring output unowned");
        check(HotSpringSource.contributors(Collections.singletonList(null)).isEmpty(),"ambiguous merged input never guesses a thrower");
        System.out.println("ProductionLedgerTest: "+checks+" checks passed");
    }
    private static void checkEtheriumInventories() {
        ProductionLedger ledger=new ProductionLedger();
        ledger.syncInput(0,"meat",2,A);
        ledger.syncInput(2,"meat",1,B);
        ledger.syncInput(7,"fish",1,C);
        var grilled=ledger.produce(Map.of(2,1),"cooked_meat",1,"minecraft:campfire_cooking");
        check(grilled.contributors().equals(Set.of(B)),"grilling never inherits same-item furnace contributor");
        ledger.append(1,ledger.produce(Map.of(0,1),"cooked_meat",1,"minecraft:smelting"));
        var restored=ProductionLedger.load(ledger.save());
        check(restored.peek(1).contributors().equals(Set.of(A)),"furnace output and grilling provenance remain separate after reload");
        check(restored.produce(Map.of(7,1),"cooked_fish",1,"minecraft:campfire_cooking").contributors().equals(Set.of(C)),
                "sixth grilling position has independent contribution");
        check(restored.take(1,1).get(0).belongsTo(A)&&restored.peek(1)==null,
                "internal furnace extraction consumes only its output batch");
        check(restored.produce(Map.of(0,1),"cooked_meat",1,"minecraft:smelting").contributors().equals(Set.of(A)),
                "grilling completion cannot clear remaining furnace input provenance");
    }

    private static void checkFluidAndCopper() {
        ProductionLedger ledger=new ProductionLedger();
        ledger.syncInput(10000,"water",1000,A);ledger.syncInput(0,"tea",1,B);
        var tea=ledger.produce(Map.of(10000,1000,0,1),"tea_fluid",1000,"youkaishomecoming:kettle");
        ledger.append(10000,tea);ledger.adoptInput(10000,"tea_fluid",1000,tea);
        check(tea.contributors().equals(Set.of(A,B)),"fluid conversion includes water and actual solid contributors");
        var changed=ledger.produce(Map.of(10000,250),"fermented",250,"youkaishomecoming:fermentation");
        check(changed.contributors().equals(Set.of(A,B)),"converted liquid remains an attributed recipe input");
        var first=ledger.take(10000,250).get(0);
        check(first.count()==250&&ledger.peek(10000).count()==750,"bottling removes actual millibuckets only");
        ledger.syncInput(10000,"tea_fluid",750,null);
        check(ProductionLedger.load(ledger.save()).peek(10000).contributors().equals(Set.of(A,B)),"fluid batch persists across reload");
        ledger.syncInput(10000,"",0,null);ledger.syncInput(10000,"water",1000,C);
        check(ledger.produce(Map.of(10000,1000),"new",1000,"cooking").contributors().equals(Set.of(C)),"empty and refill clears prior fluid contributors");
        ledger=new ProductionLedger();ledger.syncInput(0,"ingredients",1,A);ledger.syncInput(5,"copper_cup",2,B);
        ledger.append(4,ledger.produce(Map.of(0,1),"soup_cup",2,"farmersdelight:cooking"));
        ledger.transferWithContainers(4,6,5,2,2);
        check(ledger.peek(6).count()==2&&ledger.peek(6).contributors().equals(Set.of(A,B)),"copper pot uses its own 4/5/6 layout and actual two cup output");
        check(ledger.take(6,2).get(0).count()==2&&ledger.peek(4)==null,"whole copper-cup output commits once");
    }
    private static void checkTwentyStack() {
        ProductionLedger l=new ProductionLedger();
        l.syncInput(0,"meat",10,A);l.syncInput(0,"meat",20,B);
        for(int i=0;i<20;i++) {
            var b=l.produce(Map.of(0,1),"food",1,"cooking");
            check(b.belongsTo(A)&&b.belongsTo(B)&&!b.belongsTo(C),"every shared-stack production allows both contributors");
            l.append(2,b);
        }
        check(l.peek(2).count()==20,"twenty outputs are one claimable stack");
        check(l.peek(2).belongsTo(A)&&l.peek(2).belongsTo(B)&&!l.peek(2).belongsTo(C),"whole-stack qualification uses contributor set");
        var restored=ProductionLedger.load(l.save());var taken=restored.take(2,20);
        check(taken.size()==1&&taken.get(0).count()==20&&restored.peek(2)==null,"normal whole-stack take is not split into ten and ten");
        check(l.take(2,10).get(0).count()==10&&l.peek(2).count()==10&&l.peek(2).belongsTo(B),"half-stack take leaves same contributors");
        l.syncInput(0,"meat",1,C);
        check(l.produce(Map.of(0,1),"food",1,"cooking").contributors().equals(Set.of(C)),"new input after exhaustion does not inherit A or B");
        l.syncOutput(2,"food",0,"cooking");l.syncOutput(2,"food",1,"cooking");
        check(l.peek(2).contributors().isEmpty(),"cleared output cannot give new stack stale contributors");
        var old=new net.minecraft.nbt.CompoundTag();var rows=new net.minecraft.nbt.ListTag();
        for(UUID owner:List.of(A,B)) {
            var row=new net.minecraft.nbt.CompoundTag();row.putInt("slot",0);row.putString("item","meat");
            row.putInt("count",10);row.putUUID("owner",owner);rows.add(row);
        }
        old.put("inputs",rows);var migrated=ProductionLedger.load(old);
        check(migrated.produce(Map.of(0,1),"food",1,"cooking").contributors().equals(Set.of(A,B)),"old quantity-lot records migrate to shared contributor set");
        l=new ProductionLedger();l.syncInput(0,"meat",20,A);l.syncInput(0,"meat",20,B);
        check(!l.produce(Map.of(0,1),"food",1,"cooking").belongsTo(B),"opening or unchanged inventory never adds contributor");
    }
    private static void checkLastInserter() {
        ProductionLedger ledger=new ProductionLedger();
        ledger.syncLastInput(0,"meat",32,A);ledger.syncLastInput(0,"meat",64,B);
        check(ledger.produce(Map.of(0,1),"cooked",1,"campfire").contributors().equals(Set.of(B)),"last actual skillet insertion owns remaining shared input");
        ledger.syncLastInput(0,"meat",63,C);
        check(ledger.produce(Map.of(0,1),"cooked",1,"campfire").belongsTo(B),"opening unchanged skillet does not replace last inserter");
        ledger.syncLastInput(0,"meat",20,C);
        check(ledger.produce(Map.of(0,1),"cooked",1,"campfire").belongsTo(B),"withdrawal cannot steal skillet ownership");
        ledger.syncLastInput(0,"meat",0,null);ledger.syncLastInput(0,"meat",1,A);
        check(ProductionLedger.load(ledger.save()).produce(Map.of(0,1),"cooked",1,"campfire").contributors().equals(Set.of(A)),"empty refill and save reset last inserter");
        ledger.syncLastInput(0,"meat",1,null);ledger.syncLastInput(0,"meat",2,null);
        check(ledger.produce(Map.of(0,1),"cooked",1,"campfire").contributors().isEmpty(),"unknown automation addition never claims a player");
    }
    private static void iceBucketPersistence() {
        var root = new net.minecraft.nbt.CompoundTag();
        var entries = new net.minecraft.nbt.ListTag();
        var entry = new net.minecraft.nbt.CompoundTag();
        entry.putLong("pos",123);
        entry.putUUID("player",A);
        entry.putString("name","Cook");
        entry.putString("mix","trailandtales_delight:rice_ice_cream_mix");
        entry.putString("frozen","trailandtales_delight:bucket_rice_ice_cream");
        entry.putInt("remaining",3);
        entries.add(entry); root.put("pours",entries);
        var result=IceBucketPours.load(root).save(new net.minecraft.nbt.CompoundTag());
        check(result.equals(root),"frozen serving batch retains pourer, source, block and remaining count across save");
        entry.remove("frozen"); entry.remove("remaining");
        var old=IceBucketPours.load(root).save(new net.minecraft.nbt.CompoundTag()).getList("pours",10).getCompound(0);
        check(old.getString("frozen").isEmpty()&&old.getInt("remaining")==0,"legacy unfinished pour cannot fabricate frozen servings");
        check(old.getUUID("player").equals(A)&&old.getString("mix").endsWith("rice_ice_cream_mix"),"legacy pour keeps its input provenance");
    }
    private static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);checks++;}
}
