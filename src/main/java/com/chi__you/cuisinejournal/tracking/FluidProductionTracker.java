package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import java.util.*;

/** Fluid amounts stay in millibuckets; only filled items earn dish preparation credit. */
public final class FluidProductionTracker {
    private static final String KEY="cuisinejournal:fluid_production";
    private static final int FLUID=10000;
    private static final String TRANSFER="cuisinejournal:fluid_transfer";
    private static final ProductionFrames<Deque<Frame>> FRAMES=ProductionFrames.stack();
    private static final ProductionFrames<Deque<Boolean>> TANK_SCOPES=ProductionFrames.stack();
    private static final Map<Object,java.lang.ref.WeakReference<BlockEntity>> TANKS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ProductionFrames<Deque<Serving>> SERVINGS=ProductionFrames.stack();
    private record Serving(BlockEntity be,FluidStack before,ItemStack container,int count,boolean gui,boolean creative) {}
    private record Frame(BlockEntity owner,Map<Integer,ItemStack> items,List<FluidStack> fluids,boolean production,String method,Map<Integer,Integer> bottled) {}
    private FluidProductionTracker() {}
    public static void expireFrames(){FRAMES.remove();TANK_SCOPES.remove();SERVINGS.remove();}
    private static String method(BlockEntity be) {
        for(Class<?> type=be.getClass();type!=null;type=type.getSuperclass()) {
            switch(type.getName()) {
                case "umpaz.brewinandchewin.common.block.entity.KegBlockEntity":return "brewinandchewin:fermenting";
                case "umpaz.farmersrespite.common.block.entity.KettleBlockEntity":return "farmersrespite:brewing";
                case "dev.xkmc.youkaishomecoming.content.pot.kettle.KettleBlockEntity":return "youkaishomecoming:kettle";
                case "dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationTankBlockEntity":return "youkaishomecoming:fermentation";
                case "dev.xkmc.youkaishomecoming.content.pot.basin.BasinBlockEntity":return "youkaishomecoming:basin";
            }
        }
        return null;
    }
    public static boolean supports(BlockEntity be){return be!=null&&method(be)!=null;}
    public static BlockEntity currentOwner(){return FRAMES.get().isEmpty()?null:FRAMES.get().peek().owner;}
    private static boolean enabled(BlockEntity be,WorkstationDefinitions.Capability capability) {
        var definition=WorkstationDefinitions.get(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()));
        return definition==null||definition.enabled()&&definition.capabilities().contains(capability);
    }
    public static IFluidHandler tank(BlockEntity be) {
        Object tank=HostReflection.invoke(be,"getFluidTank");
        if(tank==null)tank=HostReflection.invoke(be,"getFluidHandler");
        if(tank instanceof IFluidHandler handler){TANKS.put(tank,new java.lang.ref.WeakReference<>(be));return handler;}
        return null;
    }
    private static Map<Integer,ItemStack> items(BlockEntity be) {
        Object inventory=HostReflection.inventory(be);Map<Integer,ItemStack> result=new TreeMap<>();
        int size=inventory instanceof Container c?c.getContainerSize():inventory instanceof IItemHandler h?h.getSlots():0;
        for(int i=0;i<size;i++)result.put(i,VerifiedWorkstations.stack(inventory,i).copy());
        return result;
    }
    private static List<FluidStack> fluids(BlockEntity be) {
        IFluidHandler tank=tank(be);if(tank==null)return List.of();
        List<FluidStack> result=new ArrayList<>();for(int i=0;i<tank.getTanks();i++)result.add(tank.getFluidInTank(i).copy());return result;
    }
    private static String identity(ItemStack stack){return stack.isEmpty()?"":BuiltInRegistries.ITEM.getKey(stack.getItem())+"|"+Objects.toString(stack.getTag(),"");}
    private static String identity(FluidStack stack){return stack.isEmpty()?"":BuiltInRegistries.FLUID.getKey(stack.getFluid())+"|"+Objects.toString(stack.getTag(),"");}
    private static ProductionLedger ledger(BlockEntity be){return ProductionLedger.load(be.getPersistentData().getCompound(KEY));}
    private static void save(BlockEntity be,ProductionLedger ledger){var saved=ledger.save();if(!saved.equals(be.getPersistentData().getCompound(KEY))){be.getPersistentData().put(KEY,saved);be.setChanged();}}
    private static void reconcile(BlockEntity be,ProductionLedger ledger,Map<Integer,ItemStack> items,List<FluidStack> fluids) {
        items.forEach((slot,stack)->{ledger.syncInput(slot,identity(stack),stack.getCount(),null);ledger.syncOutput(slot,identity(stack),stack.getCount(),method(be));});
        for(int i=0;i<fluids.size();i++) {
            FluidStack stack=fluids.get(i);ledger.syncInput(FLUID+i,identity(stack),stack.getAmount(),null);
            ledger.syncOutput(FLUID+i,identity(stack),stack.getAmount(),TRANSFER);
        }
    }
    public static void begin(BlockEntity be,boolean production) {
        if(!supports(be)||be.getLevel()==null||be.getLevel().isClientSide)return;
        var items=items(be);var fluids=fluids(be);var ledger=ledger(be);reconcile(be,ledger,items,fluids);save(be,ledger);
        FRAMES.get().push(new Frame(be,items,fluids,production,method(be),new HashMap<>()));
    }
    public static void end(BlockEntity be) {
        var frames=FRAMES.get();Frame frame=FrameStacks.finish(frames,value->value.owner==be);
        if(frame==null)return;if(frames.isEmpty())FRAMES.remove();
        var afterItems=items(be);var afterFluids=fluids(be);var ledger=ledger(be);
        Map<Integer,Integer> losses=new TreeMap<>();
        frame.items.forEach((slot,before)->{var after=afterItems.getOrDefault(slot,ItemStack.EMPTY);int lost=before.getCount()-(ItemStack.isSameItemSameTags(before,after)?after.getCount():0);if(lost>0)losses.put(slot,lost);});
        for(int i=0;i<Math.max(frame.fluids.size(),afterFluids.size());i++) {
            var before=i<frame.fluids.size()?frame.fluids.get(i):FluidStack.EMPTY;
            var after=i<afterFluids.size()?afterFluids.get(i):FluidStack.EMPTY;
            int lost=before.getAmount()-(before.isFluidEqual(after)?after.getAmount():0);if(lost>0)losses.put(FLUID+i,lost);
        }
        ProductionLedger.Batch consumed=null;
        if(frame.production&&!losses.isEmpty())consumed=ledger.produce(losses,"",1,frame.method);
        for(var entry:afterItems.entrySet()) {
            int slot=entry.getKey();var after=entry.getValue();var before=frame.items.getOrDefault(slot,ItemStack.EMPTY);
            int gain=after.getCount()-(ItemStack.isSameItemSameTags(before,after)?before.getCount():0);
            if(consumed!=null&&gain>0&&enabled(be,WorkstationDefinitions.Capability.ITEM_OUTPUT)) {
                var batch=new ProductionLedger.Batch(identity(after),gain,consumed.contributors(),consumed.ingredients(),frame.method);
                ledger.syncOutput(slot,identity(after),after.getCount()-gain,frame.method);ledger.append(slot,batch);
            }
            if(!frame.production&&gain>0) {
                ledger.syncInput(slot,identity(after),Math.max(0,after.getCount()-gain),null);
                ledger.syncInput(slot,identity(after),after.getCount(),enabled(be,WorkstationDefinitions.Capability.ITEM_INPUT)&&ProductionTracker.actor()!=null?ProductionTracker.actor().getUUID():null);
            } else ledger.syncInput(slot,identity(after),after.getCount(),null);
        }
        for(int i=0;i<Math.max(frame.fluids.size(),afterFluids.size());i++) {
            var before=i<frame.fluids.size()?frame.fluids.get(i):FluidStack.EMPTY;
            var after=i<afterFluids.size()?afterFluids.get(i):FluidStack.EMPTY;int slot=FLUID+i;
            int gain=after.getAmount()-(before.isFluidEqual(after)?before.getAmount():0);
            if(consumed!=null&&gain>0&&enabled(be,WorkstationDefinitions.Capability.FLUID_OUTPUT)) {
                var batch=new ProductionLedger.Batch(identity(after),gain,consumed.contributors(),consumed.ingredients(),frame.method);
                ledger.syncOutput(slot,identity(after),after.getAmount()-gain,frame.method);ledger.append(slot,batch);
                ledger.adoptInput(slot,identity(after),after.getAmount(),ledger.peek(slot));
            } else if(!frame.production&&gain>0) {
                ledger.syncInput(slot,identity(after),after.getAmount()-gain,null);
                ledger.syncInput(slot,identity(after),after.getAmount(),enabled(be,WorkstationDefinitions.Capability.FLUID_INPUT)&&ProductionTracker.actor()!=null?ProductionTracker.actor().getUUID():null);
            } else ledger.syncInput(slot,identity(after),after.getAmount(),null);
            if(!frame.production&&gain<0)ledger.take(slot,unaccountedDrain(-gain,frame.bottled.getOrDefault(slot,0)));
        }
        save(be,ledger);
    }
    public static void beginTank(Object tank) {
        var reference=TANKS.get(tank);BlockEntity be=reference==null?null:reference.get();
        boolean own=be!=null&&FRAMES.get().isEmpty();TANK_SCOPES.get().push(own);
        if(own)begin(be,false);
    }
    public static void endTank(Object tank) {
        var reference=TANKS.get(tank);BlockEntity be=reference==null?null:reference.get();
        var scopes=TANK_SCOPES.get();if(scopes.isEmpty())return;boolean own=scopes.pop();if(scopes.isEmpty())TANK_SCOPES.remove();
        if(own&&be!=null)end(be);
    }
    public static ProductionLedger.Batch bottle(BlockEntity be,FluidStack before,ItemStack result,boolean containerUsed) {
        if(result.isEmpty()||!supports(be))return null;
        var handler=tank(be);var after=handler.getFluidInTank(0);int used=before.getAmount()-(before.isFluidEqual(after)?after.getAmount():0);
        if(used<=0)return null;
        for(var frame:FRAMES.get())if(frame.owner==be)frame.bottled.merge(FLUID,used,Integer::sum);
        var ledger=ledger(be);var batches=ledger.take(FLUID,used);save(be,ledger);
        Set<java.util.UUID> contributors=new HashSet<>();Set<String> ingredients=new HashSet<>();String productionMethod=TRANSFER;
        for(var batch:batches){contributors.addAll(batch.contributors());ingredients.addAll(batch.ingredients());if(!batch.method().equals(TRANSFER))productionMethod=batch.method();}
        if(containerUsed&&ProductionTracker.actor()!=null)contributors.add(ProductionTracker.actor().getUUID());
        return new ProductionLedger.Batch(identity(result),result.getCount(),contributors,ingredients,productionMethod);
    }
    public static ProductionLedger.Batch extractionPreview(BlockEntity be,int slot){return ledger(be).peek(slot);}
    public static int unaccountedDrain(int actual,int alreadyBottled){return Math.max(0,actual-Math.max(0,alreadyBottled));}
    public static void recordBottled(BlockEntity be,int output,ProductionLedger.Batch batch) {
        if(batch==null)return;var ledger=ledger(be);ledger.append(output,batch);save(be,ledger);
    }
    public static ItemStack claimReturned(ItemStack result) {
        if(FRAMES.get().isEmpty())return result;
        Frame frame=FRAMES.get().peek();if(frame.production||frame.fluids.isEmpty())return result;
        ServerPlayer player=ProductionTracker.actor();
        var batch=bottle(frame.owner,frame.fluids.get(0),result,player!=null&&!player.isCreative());
        if(batch!=null)ProductionTracker.claimBatch(enabled(frame.owner,WorkstationDefinitions.Capability.RIGHT_CLICK_TAKE)?player:null,
                result,batch,CulinaryCredit.Source.INTERACTION);
        return result;
    }
    public static ProductionLedger.Batch output(BlockEntity be,int slot){var ledger=ledger(be);reconcile(be,ledger,items(be),fluids(be));save(be,ledger);return ledger.peek(slot);}
    public static void takeOutput(BlockEntity be,int slot,int count){var ledger=ledger(be);ledger.take(slot,count);save(be,ledger);}
    public static void beginKegServing(BlockEntity be,ItemStack container,boolean gui,boolean creative) {
        if(be.getLevel()==null||be.getLevel().isClientSide)return;
        var tank=tank(be);var ledger=ledger(be);reconcile(be,ledger,items(be),fluids(be));save(be,ledger);
        SERVINGS.get().push(new Serving(be,tank.getFluidInTank(0).copy(),container,container.getCount(),gui,creative));
    }
    public static void endKegServing(BlockEntity be,List<ItemStack> results) {
        var servings=SERVINGS.get();if(servings.isEmpty()||servings.peek().be!=be)return;
        var serving=servings.pop();if(servings.isEmpty())SERVINGS.remove();
        var after=tank(be).getFluidInTank(0);int used=serving.before.getAmount()-(serving.before.isFluidEqual(after)?after.getAmount():0);
        if(used<=0||results.isEmpty())return;
        int count=serving.count-serving.container.getCount();
        ItemStack result=results.get(0);if(count<=0||result.isEmpty()||serving.creative)return;
        var batch=bottle(be,serving.before,result,true);
        if(batch==null)return;
        if(serving.gui)recordBottled(be,5,batch);
        else ProductionTracker.claimBatch(ProductionTracker.actor(),result,batch,CulinaryCredit.Source.INTERACTION);
    }
    public static void clear(){FRAMES.remove();TANK_SCOPES.remove();SERVINGS.remove();TANKS.clear();}
}
