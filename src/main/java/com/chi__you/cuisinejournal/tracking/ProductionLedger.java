package com.chi__you.cuisinejournal.tracking;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.*;

/** Shared contributors stay with an input/output stack until it is cleared or replaced. */
public final class ProductionLedger {
    private record Lot(String identity, int count, Set<UUID> contributors) {
        Lot { contributors = Set.copyOf(contributors); }
    }
    public record Batch(String identity, int count, Set<UUID> contributors, Set<String> ingredients,
                        String method) {
        public Batch { contributors = Set.copyOf(contributors); ingredients = Set.copyOf(ingredients); }
        Batch count(int n) { return new Batch(identity,n,contributors,ingredients,method); }
        public boolean belongsTo(UUID player) { return player != null && contributors.contains(player); }
        public Batch withContributor(UUID player) {
            Set<UUID> owners=new HashSet<>(contributors);owners.add(player);
            return new Batch(identity,count,owners,ingredients,method);
        }
        Batch combine(Batch other) {
            Set<UUID> owners=new HashSet<>(contributors);owners.addAll(other.contributors);
            Set<String> items=new HashSet<>(ingredients);items.addAll(other.ingredients);
            return new Batch(identity,count,owners,items,method);
        }
        Batch stackWith(Batch other) {
            Batch combined=combine(other);
            return new Batch(identity,Math.addExact(count,other.count),combined.contributors,combined.ingredients,method);
        }
    }
    private final Map<Integer, List<Lot>> inputs = new TreeMap<>();
    private final Map<Integer, List<Batch>> outputs = new TreeMap<>();

    /** Merely opening a container cannot contribute; only an actual insertion adds a player. */
    public void syncInput(int slot, String identity, int count, UUID inserter) {
        if(count<=0) {inputs.remove(slot);return;}
        List<Lot> lots=inputs.getOrDefault(slot,List.of());
        Set<UUID> owners=new HashSet<>();int held=0;
        for(Lot lot:lots)if(lot.identity.equals(identity)) {held+=lot.count;owners.addAll(lot.contributors);}
        if(count>held&&inserter!=null)owners.add(inserter);
        inputs.put(slot,new ArrayList<>(List.of(new Lot(identity,count,owners))));
    }
    public Batch produce(Map<Integer,Integer> consumed, String identity, int count, String method) {
        Set<UUID> owners = new HashSet<>(); Set<String> ingredients = new HashSet<>();
        consumed.forEach((slot,amount) -> {
            for (Lot lot : consume(slot,amount)) {
                owners.addAll(lot.contributors);
                ingredients.add(lot.identity);
            }
        });
        return new Batch(identity,count,owners,ingredients,method);
    }
    /** Skillets deliberately attribute the remaining stack to the last actual inserter. */
    public void syncLastInput(int slot,String identity,int count,UUID inserter) {
        int held=inputs.getOrDefault(slot,List.of()).stream().filter(lot->lot.identity.equals(identity)).mapToInt(Lot::count).sum();
        syncInput(slot,identity,count,null);
        if(count>held)adoptInput(slot,identity,count,new Batch(identity,count,inserter==null?Set.of():Set.of(inserter),Set.of(),""));
    }
    public void adoptInput(int slot,String identity,int count,Batch batch) {
        if(count<=0){inputs.remove(slot);return;}
        inputs.put(slot,new ArrayList<>(List.of(new Lot(identity,count,batch==null?Set.of():batch.contributors()))));
    }
    private List<Lot> consume(int slot,int amount) {
        List<Lot> lots = inputs.computeIfAbsent(slot,k -> new ArrayList<>()), removed = new ArrayList<>();
        while (amount > 0 && !lots.isEmpty()) {
            Lot lot = lots.remove(0); int take = Math.min(amount,lot.count); amount -= take;
            removed.add(new Lot(lot.identity,take,lot.contributors));
            if (lot.count > take) lots.add(0,new Lot(lot.identity,lot.count-take,lot.contributors));
        }
        return removed;
    }
    public void append(int slot,Batch batch) {
        if(batch.count<=0)return;
        List<Batch> list=outputs.computeIfAbsent(slot,k -> new ArrayList<>());
        if(!list.isEmpty()&&list.get(0).identity.equals(batch.identity)) {
            Batch combined=batch;
            for(Batch existing:list)combined=existing.stackWith(combined);
            list.clear();list.add(combined);
        } else {list.clear();list.add(batch);}
    }
    public void syncOutput(int slot,String identity,int count,String method) {
        List<Batch> batches = outputs.computeIfAbsent(slot,k -> new ArrayList<>());
        if (batches.stream().anyMatch(b -> !b.identity.equals(identity))) batches.clear();
        int held = batches.stream().mapToInt(Batch::count).sum();
        if (held > count) take(slot,held-count);
        if (count > held) append(slot,new Batch(identity,count-held,Set.of(),Set.of(),method));
        if (count == 0) outputs.remove(slot);
    }
    public Batch peek(int slot) {
        var list = outputs.get(slot); return list == null || list.isEmpty() ? null : list.get(0);
    }
    public List<Batch> take(int slot,int amount) {
        List<Batch> list = outputs.computeIfAbsent(slot,k -> new ArrayList<>()), taken = new ArrayList<>();
        while (amount > 0 && !list.isEmpty()) {
            Batch b = list.remove(0); int n = Math.min(amount,b.count); amount -= n; taken.add(b.count(n));
            if (b.count > n) list.add(0,b.count(b.count-n));
        }
        return List.copyOf(taken);
    }
    public void transfer(int from,int to,int amount) { take(from,amount).forEach(b -> append(to,b)); }
    /** Container provenance belongs only to the servings that actually consume those containers. */
    public void transferWithContainers(int from,int to,int containerSlot,int amount,int containers) {
        for(int i=0;i<amount;i++) {
            List<Batch> serving=take(from,1);if(serving.isEmpty())break;
            Batch b=serving.get(0);
            if(i<containers)b=b.combine(produce(Map.of(containerSlot,1),b.identity,1,b.method));
            append(to,b);
        }
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); ListTag in = new ListTag(), out = new ListTag();
        inputs.forEach((slot,lots) -> lots.forEach(l -> {
            CompoundTag row = new CompoundTag(); row.putInt("slot",slot); row.putString("item",l.identity);
            row.putInt("count",l.count);ListTag owners=new ListTag();
            l.contributors.stream().sorted().forEach(id->{CompoundTag v=new CompoundTag();v.putUUID("id",id);owners.add(v);});
            row.put("owners",owners);in.add(row);
        }));
        outputs.forEach((slot,batches) -> batches.forEach(b -> {
            CompoundTag row = writeBatch(b); row.putInt("slot",slot); out.add(row);
        }));
        tag.put("inputs",in); tag.put("outputs",out); return tag;
    }
    public static ProductionLedger load(CompoundTag tag) {
        ProductionLedger ledger = new ProductionLedger();
        for(Tag value : tag.getList("inputs",Tag.TAG_COMPOUND)) {
            CompoundTag row=(CompoundTag)value; int count=row.getInt("count"); if(count<=0) continue;
            Set<UUID> owners=new HashSet<>();
            if(row.hasUUID("owner"))owners.add(row.getUUID("owner"));
            for(Tag v:row.getList("owners",Tag.TAG_COMPOUND))if(((CompoundTag)v).hasUUID("id"))owners.add(((CompoundTag)v).getUUID("id"));
            int slot=row.getInt("slot");List<Lot> old=ledger.inputs.getOrDefault(slot,List.of());
            for(Lot lot:old)if(lot.identity.equals(row.getString("item"))){owners.addAll(lot.contributors);count+=lot.count;}
            ledger.inputs.put(slot,new ArrayList<>(List.of(new Lot(row.getString("item"),count,owners))));
        }
        for(Tag value : tag.getList("outputs",Tag.TAG_COMPOUND)) {
            CompoundTag row=(CompoundTag)value; ledger.append(row.getInt("slot"),readBatch(row));
        }
        return ledger;
    }
    public static CompoundTag writeBatch(Batch b) {
        CompoundTag row=new CompoundTag(); row.putString("item",b.identity); row.putInt("count",b.count);
        row.putString("method",b.method); ListTag owners=new ListTag(), items=new ListTag();
        b.contributors.stream().sorted().forEach(id -> {CompoundTag v=new CompoundTag();v.putUUID("id",id);owners.add(v);});
        b.ingredients.stream().sorted().forEach(id -> {CompoundTag v=new CompoundTag();v.putString("id",id);items.add(v);});
        row.put("owners",owners);row.put("ingredients",items);return row;
    }
    public static Batch readBatch(CompoundTag row) {
        Set<UUID> owners=new HashSet<>();Set<String> items=new HashSet<>();
        for(Tag v:row.getList("owners",Tag.TAG_COMPOUND)) if(((CompoundTag)v).hasUUID("id")) owners.add(((CompoundTag)v).getUUID("id"));
        for(Tag v:row.getList("ingredients",Tag.TAG_COMPOUND)) items.add(((CompoundTag)v).getString("id"));
        return new Batch(row.getString("item"),Math.max(0,row.getInt("count")),owners,items,row.getString("method"));
    }
}
