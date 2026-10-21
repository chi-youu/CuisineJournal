package com.chi__you.cuisinejournal.tracking;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.*;

public final class MilkshakePortionsTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();
        var bootstrap=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");bootstrap.setAccessible(true);bootstrap.setBoolean(null,true);
        BuiltInRegistries.bootStrap();
        var first=new UUID(0,1);var second=new UUID(0,2);
        var batch=new ProductionLedger.Batch("test:milkshake",1,Set.of(first),Set.of("test:milk","test:ice_cream"),"neapolitan:milkshake_cauldron");
        var made=new MilkshakeCauldronProduction.Portion(batch,ItemStack.EMPTY);
        var storedStack=new ItemStack(Items.APPLE);storedStack.getOrCreateTag().putString("cook","stored");
        var stored=new MilkshakeCauldronProduction.Portion(null,storedStack);
        var mixed=MilkshakeCauldronProduction.append(List.of(made,made),2,3,stored);
        check(mixed.size()==3&&mixed.get(0).batch()==batch&&mixed.get(1).batch()==batch,"pourback preserves two pending cooked portions");
        check(mixed.get(2).batch()==null&&mixed.get(2).original()==storedStack,"pourback is storage with original quality, not a new recipe");
        var two=MilkshakeCauldronProduction.remaining(mixed,2);
        check(two.get(0).batch()==batch&&two.get(1).batch()==null,"taking one removes only the first produced serving");
        var one=MilkshakeCauldronProduction.remaining(two,1);
        check(one.get(0).original()==storedStack&&one.get(0).batch()==null,"final stored serving is not credited as unfinished production");
        check(MilkshakeCauldronProduction.remaining(one,0).isEmpty(),"empty cauldron leaves no old provenance");
        var unknown=MilkshakeCauldronProduction.append(List.of(),2,3,stored);
        check(unknown.get(0).batch()==null&&unknown.get(0).original().isEmpty()&&unknown.get(2)==stored,
                "legacy unknown liquid is not falsely assigned the poured bottle's quality or cook");
        var milkA=new MilkshakeCauldronProduction.Portion(new ProductionLedger.Batch("milk",1,Set.of(first),Set.of("milk"),"transfer"),ItemStack.EMPTY);
        var milkB=new MilkshakeCauldronProduction.Portion(new ProductionLedger.Batch("milk",1,Set.of(second),Set.of("milk"),"transfer"),ItemStack.EMPTY);
        var reduced=MilkshakeCauldronProduction.remaining(List.of(milkA,milkB),1);
        var refilled=MilkshakeCauldronProduction.append(reduced,1,2,milkB);
        check(refilled.stream().allMatch(p->p.batch().contributors().equals(Set.of(second))),"removed milk owner cannot return after refill");
        persistence(batch,storedStack);
        System.out.println("MilkshakePortionsTest: "+checks+" checks passed");
    }
    private static void persistence(ProductionLedger.Batch batch,ItemStack stored)throws Exception{
        var load=MilkshakeCauldronProduction.class.getDeclaredMethod("load",CompoundTag.class);load.setAccessible(true);
        var root=new CompoundTag();var rows=new ListTag();var row=new CompoundTag();row.putLong("pos",42);row.putString("block","test:cauldron");
        var portions=new ListTag();var made=new CompoundTag();made.put("batch",ProductionLedger.writeBatch(batch));portions.add(made);
        var bottle=new CompoundTag();bottle.put("original",stored.save(new CompoundTag()));portions.add(bottle);row.put("contents",portions);rows.add(row);root.put("cauldrons",rows);
        var restored=(MilkshakeCauldronProduction)load.invoke(null,root);var saved=restored.save(new CompoundTag());
        var result=saved.getList("cauldrons",10).getCompound(0).getList("contents",10);
        check(result.size()==2&&result.getCompound(0).contains("batch")&&!result.getCompound(1).contains("batch"),"mixed production and storage persist as distinct portions");
        check(ItemStack.isSameItemSameTags(ItemStack.of(result.getCompound(1).getCompound("original")),stored),"stored bottle quality survives restart");
        row.remove("contents");row.putInt("portions",3);row.put("batch",ProductionLedger.writeBatch(batch.count(3)));row.put("original",new CompoundTag());
        var legacy=(MilkshakeCauldronProduction)load.invoke(null,root);
        var migrated=legacy.save(new CompoundTag()).getList("cauldrons",10).getCompound(0).getList("contents",10);
        check(migrated.size()==3,"old aggregate records migrate to actual liquid portions");
        for(Tag raw:migrated)check(ProductionLedger.readBatch(((CompoundTag)raw).getCompound("batch")).count()==1,"migrated portions claim one bottle, not the old whole batch");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
