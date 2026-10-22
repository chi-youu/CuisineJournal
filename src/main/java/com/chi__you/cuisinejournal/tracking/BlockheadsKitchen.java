package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.progression.*;
import com.chi__you.cuisinejournal.recipe.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Native kitchen storage transfers preserve input lots; only a real transformation produces food. */
public final class BlockheadsKitchen {
    public static final String OVEN="net.blay09.mods.cookingforblockheads.tile.OvenBlockEntity";
    public static final String TOASTER="net.blay09.mods.cookingforblockheads.tile.ToasterBlockEntity";
    private static final String KEY="cuisinejournal:blockheads_inputs";
    private record Operation(ServerPlayer player,BlockEntity owner,Map<Integer,ItemStack> before) {}
    private record Tick(BlockEntity owner,Map<Integer,ItemStack> before) {}
    private static final ProductionFrames<Deque<Operation>> INPUTS=ProductionFrames.stack();
    private static final ProductionFrames<Deque<Tick>> TICKS=ProductionFrames.stack();
    private static final class Craft {
        final Object owner;Set<String> ingredients;
        Craft(Object owner){this.owner=owner;}
    }
    private static final ProductionFrames<Deque<Craft>> CRAFT_INPUTS=ProductionFrames.stack();
    private BlockheadsKitchen() {}
    public static boolean supports(BlockEntity be){return be!=null&&(be.getClass().getName().equals(OVEN)||be.getClass().getName().equals(TOASTER));}
    private static boolean enabled(BlockEntity be,WorkstationDefinitions.Capability capability){
        var rule=WorkstationDefinitions.get(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()));
        return rule==null||rule.enabled()&&rule.capabilities().contains(capability);
    }
    private static boolean oven(BlockEntity be){return be.getClass().getName().equals(OVEN);}
    public static BlockEntity owner(AbstractContainerMenu menu){
        if(menu!=null&&menu.getClass().getName().equals("net.blay09.mods.cookingforblockheads.menu.OvenMenu")){
            Object raw=HostReflection.invoke(menu,"getOven");if(raw instanceof BlockEntity be)return be;
        }return null;
    }
    private static Container inventory(BlockEntity be){
        Object raw=HostReflection.invoke(be,oven(be)?"getInternalContainer":"getContainer");
        if(!(raw instanceof Container container))throw new IllegalStateException("Kitchen inventory contract changed");return container;
    }
    private static String identity(ItemStack stack){return stack.isEmpty()?"":BuiltInRegistries.ITEM.getKey(stack.getItem())+"|"+Objects.toString(stack.getTag(),"");}
    private static ProductionLedger ledger(BlockEntity be){return ProductionLedger.load(be.getPersistentData().getCompound(KEY));}
    private static void save(BlockEntity be,ProductionLedger ledger){
        var tag=ledger.save();if(!tag.equals(be.getPersistentData().getCompound(KEY))){be.getPersistentData().put(KEY,tag);be.setChanged();}
    }
    private static Map<Integer,ItemStack> snapshot(BlockEntity be){
        var result=new LinkedHashMap<Integer,ItemStack>();var inv=inventory(be);
        for(int i=0;i<(oven(be)?16:2);i++)if(!oven(be)||i<3||i>=7)result.put(i,inv.getItem(i).copy());return result;
    }
    public static void beginInput(ServerPlayer player,BlockEntity be){
        if(!supports(be)||be.getLevel()==null||be.getLevel().isClientSide||!enabled(be,WorkstationDefinitions.Capability.ITEM_INPUT)){INPUTS.get().push(new Operation(player,null,Map.of()));return;}
        var before=snapshot(be);var ledger=ledger(be);before.forEach((slot,stack)->ledger.syncInput(slot,identity(stack),stack.getCount(),null));save(be,ledger);
        INPUTS.get().push(new Operation(player,be,before));
    }
    public static void endInput(ServerPlayer player){
        var frames=INPUTS.get();var frame=ProductionFrames.take(frames,value->value.player==player);if(frames.isEmpty())INPUTS.remove();
        if(frame==null||frame.owner==null)return;var ledger=ledger(frame.owner);
        snapshot(frame.owner).forEach((slot,after)->{var before=frame.before.get(slot);
            int held=ItemStack.isSameItemSameTags(before,after)?before.getCount():0;
            ledger.syncInput(slot,identity(after),held,null);ledger.syncInput(slot,identity(after),after.getCount(),player.getUUID());
        });save(frame.owner,ledger);
    }
    public static void beginTick(BlockEntity be){
        if(be.getLevel()==null||be.getLevel().isClientSide||!enabled(be,WorkstationDefinitions.Capability.ITEM_OUTPUT))return;var before=snapshot(be);var ledger=ledger(be);
        before.forEach((slot,stack)->ledger.syncInput(slot,identity(stack),stack.getCount(),null));save(be,ledger);TICKS.get().push(new Tick(be,before));
    }
    public static void endTick(BlockEntity be){var frames=TICKS.get();ProductionFrames.take(frames,value->value.owner==be);if(frames.isEmpty())TICKS.remove();}
    public static void processingWrite(BlockEntity be,Object container,int slot,ItemStack value){
        if(!(container instanceof Container inv))throw new IllegalStateException("Kitchen processing container changed");
        boolean processing=container==HostReflection.field(be,"processingContainer");
        var tick=TICKS.get().peek();
        if(!processing||tick==null||tick.owner!=be){inv.setItem(slot,value);return;}
        var before=inv.getItem(slot).copy();var ledger=ledger(be);ProductionLedger.Batch produced=null;
        int index=slot+7;
        if(before.isEmpty()&&!value.isEmpty()){
            var full=inventory(be);
            for(int source=0;source<3;source++){var original=tick.before.get(source);
                if(ItemStack.isSameItemSameTags(original,value)&&full.getItem(source).getCount()<original.getCount()){
                    var batch=ledger.produce(Map.of(source,value.getCount()),identity(value),value.getCount(),"cookingforblockheads:oven");
                    ledger.adoptInput(index,identity(value),value.getCount(),batch);break;
                }
            }
        }else if(!before.isEmpty()&&!value.isEmpty()&&!ItemStack.isSameItemSameTags(before,value)){
            produced=ledger.produce(Map.of(index,before.getCount()),identity(value),value.getCount(),"cookingforblockheads:oven");
        }else if(value.isEmpty())ledger.syncInput(index,"",0,null);
        inv.setItem(slot,value);save(be,ledger);
        if(produced!=null)finish(be,value,produced);
    }
    public static ItemStack toast(BlockEntity be,ItemStack value){
        var tick=TICKS.get().peek();if(tick==null||tick.owner!=be||value.isEmpty())return value;
        var inv=inventory(be);var ledger=ledger(be);
        for(var entry:tick.before.entrySet())if(!entry.getValue().isEmpty()&&!inv.getItem(entry.getKey()).isEmpty()){
            var input=entry.getValue();
            // Each native emission clears the matching slot immediately afterwards, preserving native order.
            if(inv.getItem(entry.getKey()).getCount()!=input.getCount())continue;
            var batch=ledger.produce(Map.of(entry.getKey(),input.getCount()),identity(value),value.getCount(),"cookingforblockheads:toaster");
            save(be,ledger);if(!ItemStack.isSameItem(input,value))finish(be,value,batch);return value;
        }return value;
    }
    private static void finish(BlockEntity be,ItemStack value,ProductionLedger.Batch batch){
        var identity=BuiltInRegistries.ITEM.getKey(com.chi__you.cuisinejournal.food.PlacedFoodConsumption.identityForProduction(value).getItem());
        if(!CulinaryRegistries.current().isTracked(identity))return;
        if(batch.contributors().size()==1)CookingRewards.stampProduced(be.getLevel().getServer(),value,batch.contributors().iterator().next(),batch.count());
        DirectDropProgress.finish(be.getLevel(),value,batch);
    }
    public static void beginCraft(Object owner){CRAFT_INPUTS.get().push(new Craft(owner));}
    public static void craftingInputs(Object input){
        var frame=CRAFT_INPUTS.get().peek();if(frame==null||frame.owner!=input||!(input instanceof Container inventory))return;
        var ingredients=new HashSet<String>();for(int i=0;i<inventory.getContainerSize();i++)if(!inventory.getItem(i).isEmpty())ingredients.add(identity(inventory.getItem(i)));
        frame.ingredients=Set.copyOf(ingredients);
    }
    public static void crafted(Object owner,Player user,ItemStack result){
        var frames=CRAFT_INPUTS.get();var frame=ProductionFrames.take(frames,value->value.owner==owner);if(frames.isEmpty())CRAFT_INPUTS.remove();
        if(!(user instanceof ServerPlayer player)||result.isEmpty()||frame==null||frame.ingredients==null)return;
        ProductionTracker.claimBatch(player,result,new ProductionLedger.Batch(identity(result),result.getCount(),Set.of(player.getUUID()),frame.ingredients,"minecraft:crafting"),CulinaryCredit.Source.MENU);
    }
    public static void claim(Player user,ItemStack result){
        if(!(user instanceof ServerPlayer player)||!result.hasTag()||!result.getTag().contains(ProductionTracker.PENDING,10))return;
        var batch=ProductionLedger.readBatch(result.getTag().getCompound(ProductionTracker.PENDING));
        if(!batch.method().startsWith("cookingforblockheads:")||!batch.belongsTo(player.getUUID()))return;
        result.removeTagKey(ProductionTracker.PENDING);ProductionTracker.claimBatch(player,result,batch,CulinaryCredit.Source.MENU);
    }
    public static int countdown(BlockEntity be,int proposed){
        int before=CookingSpeed.number(be,"toastTicks");if(proposed!=before-1||before<=0)return proposed;
        return Math.max(0,before-CookingSpeed.advance(be,"toastTicks",0,1,before));
    }
}
