package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.compat.HostReflection;
import com.chi__you.cuisinejournal.food.FoodVariantIdentity;

import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.progression.CookingRewards;
import com.chi__you.cuisinejournal.progression.ProgressionService;
import com.chi__you.cuisinejournal.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.items.IItemHandler;
import java.util.*;

/** Actual input mutations, completed batches and successful extraction share one provenance path. */
public final class ProductionTracker {
    static final String LEDGER = "cuisinejournal:production";
    public static final String PENDING = com.chi__you.cuisinejournal.food.FoodVariantIdentity.PENDING_PRODUCTION;
    private static final Map<Object,ProductionLedger> CRAFTING = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ProductionFrames<Deque<Operation>> OPERATIONS = ProductionFrames.stack();
    private static final ProductionFrames<Deque<Cooking>> COOKING = ProductionFrames.stack();
    private ProductionTracker() {}

    public interface CraftInputs { Container cuisinejournal$inputs(); }
    public record Station(Object owner, int[] inputs, int[] outputs, CookingMethod method) {}
    private record Operation(ServerPlayer player, Station station, Map<Integer,ItemStack> before,BlockEntity fluidOwner) {}
    private record Cooking(Station station, Map<Integer,ItemStack> before, Map<Integer,ItemStack> outputs,
                           Map<Integer,ProductionLedger.Batch> emitted) {}
    private static final ProductionFrames<Extraction> PREPARED = ProductionFrames.value();
    public record Extraction(Station station, Slot slot, ItemStack original, ProductionLedger.Batch batch,
                             ServerPlayer player, int available, Map<Integer,Integer> consumed, ItemStack marked, ItemStack live,
                             boolean deferredQuality, boolean allowPerfect) {}

    public static Station menu(AbstractContainerMenu menu) {
        if (menu == null || ConfigManager.server().compatibility.excludedMenuClasses.contains(menu.getClass().getName())) return null;
        for (Slot slot : menu.slots) {
            if (slot instanceof ResultSlot && slot instanceof CraftInputs access)
                return new Station(access.cuisinejournal$inputs(),range(0,access.cuisinejournal$inputs().getContainerSize()),new int[0],CookingMethod.CRAFTING);
            if (slot.container instanceof BlockEntity be && station(be)!=null)
                return station(be);
            if(slot instanceof net.minecraftforge.items.SlotItemHandler forge){
                Object owner=HostReflection.field(forge.getItemHandler(),"be");
                if(owner instanceof BlockEntity be&&ItemRecipeWorkstations.host(be))return station(be);
            }
        }
        Object be=HostReflection.field(menu,"blockEntity");
        if(be==null)be=HostReflection.field(menu,"be");
        if(be==null)be=HostReflection.invoke(menu,"getBlockEntity");
        if(be instanceof BlockEntity block)return station(block);
        return null;
    }
    public static Station station(BlockEntity be) {
        if(be==null)return null;
        WorkstationDefinitions.Definition definition=WorkstationDefinitions.get(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()));
        if(definition!=null&&!definition.enabled())return null;
        Station detected=detectStation(be);
        if(detected==null)return null;
        if(definition!=null&&definition.adapter()!=WorkstationDefinitions.Adapter.AUTO
                &&definition.adapter()!=adapterFor(be))return null;
        return detected;
    }
    private static boolean allows(Station station,WorkstationDefinitions.Capability capability) {
        if(!(station.owner instanceof BlockEntity be))return true;
        var definition=WorkstationDefinitions.get(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()));
        return definition==null||definition.enabled()&&definition.capabilities().contains(capability);
    }
    private static boolean accepts(Station station,ResourceLocation recipeType) {
        if(!(station.owner instanceof BlockEntity be))return true;
        var definition=WorkstationDefinitions.get(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()));
        return definition==null||definition.acceptsRecipe(recipeType);
    }
    public static WorkstationDefinitions.Adapter adapterFor(BlockEntity be) {
        if (be.getClass().getName().equals("auviotre.enigmatic.delicacy.contents.entities.EtheriumStoveBlockEntity"))
            return WorkstationDefinitions.Adapter.STOVE;
        var verified=VerifiedWorkstations.find(be.getClass());if(verified!=null)return verified.adapter();
        if(be instanceof AbstractFurnaceBlockEntity)return WorkstationDefinitions.Adapter.FURNACE;
        if(be instanceof CampfireBlockEntity)return WorkstationDefinitions.Adapter.CAMPFIRE;
        if(CookingPotCompatibility.methodFor(be.getClass())!=null)return WorkstationDefinitions.Adapter.COOKING_POT;
        for(Class<?> type=be.getClass();type!=null;type=type.getSuperclass()) {
            String name=type.getName();if(!name.startsWith("vectorwing.farmersdelight."))continue;
            if(name.endsWith("CookingPotBlockEntity"))return WorkstationDefinitions.Adapter.COOKING_POT;
            if(name.endsWith("SkilletBlockEntity"))return WorkstationDefinitions.Adapter.SKILLET;
            if(name.endsWith("CuttingBoardBlockEntity"))return WorkstationDefinitions.Adapter.CUTTING_BOARD;
            if(name.endsWith("AbstractStoveBlockEntity"))return WorkstationDefinitions.Adapter.STOVE;
        }
        return WorkstationDefinitions.Adapter.AUTO;
    }
    private static Station detectStation(BlockEntity be) {
        if (be.getClass().getName().equals("auviotre.enigmatic.delicacy.contents.entities.EtheriumStoveBlockEntity"))
            return new Station(be,new int[]{0,2,3,4,5,6,7},new int[]{1},CookingMethod.SMELTING);
        var verified=VerifiedWorkstations.find(be.getClass());
        if(verified!=null)return new Station(be,verified.inputs(),verified.outputs(),verified.method());
        if (be instanceof AbstractFurnaceBlockEntity)
            return new Station(be,new int[]{0},new int[]{2},be instanceof BlastFurnaceBlockEntity?CookingMethod.of("minecraft","blasting"):
                    be instanceof SmokerBlockEntity?CookingMethod.SMOKING:CookingMethod.SMELTING);
        if (be instanceof CampfireBlockEntity) return new Station(be,range(0,4),new int[0],CookingMethod.CAMPFIRE);
        switch(adapterFor(be)) {
            case COOKING_POT:
                CookingMethod potMethod=CookingPotCompatibility.methodFor(be.getClass());
                return new Station(be,new int[]{0,1,2,3,4,5,7},new int[]{6,8},
                        potMethod==null?CookingMethod.COOKING_POT:potMethod);
            case SKILLET: return new Station(be,new int[]{0},new int[0],CookingMethod.CAMPFIRE);
            case CUTTING_BOARD: return new Station(be,new int[]{0},new int[0],CookingMethod.CUTTING_BOARD);
            case STOVE: return new Station(be,range(0,6),new int[0],CookingMethod.CAMPFIRE);
            default: break;
        }
        return null;
    }
    private static int[] range(int start,int end) { return java.util.stream.IntStream.range(start,end).toArray(); }
    private static ProductionLedger ledger(Station s) {
        if(s.owner instanceof BlockEntity be) return ProductionLedger.load(be.getPersistentData().getCompound(LEDGER));
        return CRAFTING.computeIfAbsent(s.owner,k -> new ProductionLedger());
    }
    private static void save(Station s,ProductionLedger ledger) {
        if(s.owner instanceof BlockEntity be) {
            CompoundTag data=ledger.save();
            if(!data.equals(be.getPersistentData().getCompound(LEDGER))) {be.getPersistentData().put(LEDGER,data);be.setChanged();}
        }
    }
    private static ItemStack stack(Station s,int slot) {
        if (s.owner.getClass().getName().equals("auviotre.enigmatic.delicacy.contents.entities.EtheriumStoveBlockEntity")) {
            // Ledger 0/1 are the furnace; 2..7 are the six grilling positions.
            Object inventory = HostReflection.invoke(s.owner, slot < 2 ? "getFurnaceStack" : "getInventory");
            int actualSlot = slot < 2 ? slot : slot - 2;
            return inventory instanceof IItemHandler handler && actualSlot < handler.getSlots()
                    ? handler.getStackInSlot(actualSlot) : ItemStack.EMPTY;
        }
        if(s.owner instanceof BlockEntity be&&VerifiedWorkstations.find(be.getClass())!=null)return VerifiedWorkstations.stack(be,slot);
        if(s.owner instanceof Container c) return slot<c.getContainerSize()?c.getItem(slot):ItemStack.EMPTY;
        if(s.owner instanceof CampfireBlockEntity c) return c.getItems().get(slot);
        try {
            if(s.owner instanceof BlockEntity be&&adapterFor(be)==WorkstationDefinitions.Adapter.STOVE) {
                IItemHandler h=(IItemHandler)s.owner.getClass().getMethod("getItems").invoke(s.owner);
                return slot<h.getSlots()?h.getStackInSlot(slot):ItemStack.EMPTY;
            }
            IItemHandler h=(IItemHandler)s.owner.getClass().getMethod("getInventory").invoke(s.owner);
            return slot<h.getSlots()?h.getStackInSlot(slot):ItemStack.EMPTY;
        } catch(ReflectiveOperationException | RuntimeException failure) { return ItemStack.EMPTY; }
    }
    private static Map<Integer,ItemStack> snapshot(Station s,int[] slots) {
        Map<Integer,ItemStack> result=new TreeMap<>();for(int slot:slots)result.put(slot,stack(s,slot).copy());return result;
    }
    private static String identity(ItemStack stack) {
        // Full item state distinguishes changed variants; provenance never alters ingredient NBT.
        return stack.isEmpty()?"":BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()+"|"+
                (stack.getTag()==null?"":stack.getTag().toString());
    }
    private static String itemId(String identity) { int sep=identity.indexOf('|');return sep<0?identity:identity.substring(0,sep); }
    private static void reconcile(Station s,ProductionLedger ledger) {
        for(int slot:s.inputs){ItemStack v=stack(s,slot);ledger.syncInput(slot,identity(v),v.getCount(),null);}
        for(int slot:s.outputs){ItemStack v=stack(s,slot);ledger.syncOutput(slot,identity(v),v.getCount(),s.method.id().toString());}
    }

    public static void beginMenu(Player player,AbstractContainerMenu menu) {
        if(player instanceof ServerPlayer server)for(Slot slot:menu.slots)if(slot.container instanceof BlockEntity be&&KaleidoscopeProduction.method(be)!=null){KaleidoscopeProduction.begin(be,server);break;}
        Station station=player instanceof ServerPlayer?menu(menu):null;
        begin(player,station,station!=null&&station.owner instanceof BlockEntity be?be:null);
    }
    public static void beginBlock(Player player,Level level,BlockPos pos) {
        BlockEntity be=level.isClientSide?null:level.getBlockEntity(pos);
        be=LetsDoStations.inputOwner(be);
        if(player instanceof ServerPlayer server)KaleidoscopeProduction.begin(be,server);
        begin(player,stationOrNull(be),be);
    }
    private static Station stationOrNull(BlockEntity be) { return be==null?null:station(be); }
    private static void begin(Player player,Station station,BlockEntity be) {
        if(!(player instanceof ServerPlayer server)) return;
        if(station != null) {var l=ledger(station);reconcile(station,l);save(station,l);}
        BlockEntity fluidOwner=FluidProductionTracker.supports(be)?be:null;
        OPERATIONS.get().push(new Operation(server,station,station==null?Map.of():snapshot(station,station.inputs),fluidOwner));
        if(fluidOwner!=null)FluidProductionTracker.begin(fluidOwner,false);
    }
    public static void endOperation(Player player) {
        if(!(player instanceof ServerPlayer))return;
        Deque<Operation> operations=OPERATIONS.get(); if(operations.isEmpty())return;
        Operation op=FrameStacks.finish(operations,frame->frame.player==player);
        if(op==null)return;
        operations.push(op);
        try {
            KaleidoscopeProduction.endOperation(op.player);
            if(op.fluidOwner!=null)FluidProductionTracker.end(op.fluidOwner);
        } finally {
            operations.pop();if(operations.isEmpty())OPERATIONS.remove();
        }
        if(op.station==null)return;
        ProductionLedger l=ledger(op.station);
        for(int slot:op.station.inputs) {
            ItemStack after=stack(op.station,slot),before=op.before.get(slot);
            int existing=ItemStack.isSameItemSameTags(before,after)?before.getCount():0;
            // First reconcile unknown changes/withdrawals, then attribute only a real added quantity.
            if(after.getCount()>existing) {
                l.syncInput(slot,identity(after),existing,null);
                UUID inserter=allows(op.station,WorkstationDefinitions.Capability.ITEM_INPUT)?op.player.getUUID():null;
                if(op.station.owner instanceof BlockEntity be&&adapterFor(be)==WorkstationDefinitions.Adapter.SKILLET)
                    l.syncLastInput(slot,identity(after),after.getCount(),inserter);
                else l.syncInput(slot,identity(after),after.getCount(),inserter);
            } else l.syncInput(slot,identity(after),after.getCount(),null);
        }
        save(op.station,l);
    }
    public static ServerPlayer actor() {return OPERATIONS.get().isEmpty()?null:OPERATIONS.get().peek().player;}
    public static void takeDirect(BlockEntity be,ItemStack output,int slot){
        if(output==null||output.isEmpty()||be.getLevel()==null||be.getLevel().isClientSide)return;
        Station station=station(be);if(station==null)return;var l=ledger(station);var batches=l.take(slot,output.getCount());save(station,l);
        claim(actor(),output,batches,true,CulinaryCredit.Source.INTERACTION);
    }
    public static ProductionLedger.Batch inputPreview(BlockEntity be,int slot,ItemStack input){
        var station=station(be);if(station==null||input.isEmpty())return null;
        return ledger(station).produce(Map.of(slot,input.getCount()),identity(input),input.getCount(),station.method.id().toString());
    }

    public static void beginCooking(BlockEntity be) {
        Station s=station(be);if(s==null || be.getLevel()==null || be.getLevel().isClientSide)return;
        beginCooking(s);
    }
    public static void beginApplePress(BlockEntity be,net.minecraft.world.item.crafting.Recipe<?> recipe){
        if(be.getLevel()==null||be.getLevel().isClientSide)return;
        boolean mashing=net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).getPath().equals("apple_mashing");
        beginCooking(new Station(be,new int[]{mashing?0:1},new int[]{mashing?1:3},CookingMethod.of("vinery",mashing?"apple_mashing":"apple_fermenting")));
    }
    private static void beginCooking(Station s){
        var l=ledger(s);reconcile(s,l);save(s,l);
        COOKING.get().push(new Cooking(s,snapshot(s,s.inputs),snapshot(s,s.outputs),new HashMap<>()));
    }

    public static void beginEtheriumGrilling(BlockEntity be) {
        if (stationOrNull(be) == null || be.getLevel() == null || be.getLevel().isClientSide) return;
        Station grilling = new Station(be,range(2,8),new int[0],CookingMethod.CAMPFIRE);
        var ledger=ledger(grilling); reconcile(grilling,ledger); save(grilling,ledger);
        COOKING.get().push(new Cooking(grilling,snapshot(grilling,grilling.inputs),Map.of(),new HashMap<>()));
    }

    /** Internal two-slot furnace of Etherium Stove: input 0, output 1. */
    public static void beginEtheriumFurnace(BlockEntity be) {
        if (stationOrNull(be) == null || be.getLevel() == null || be.getLevel().isClientSide) return;
        Station station = new Station(be, new int[]{0}, new int[]{1}, CookingMethod.SMELTING);
        var ledger = ledger(station); reconcile(station, ledger); save(station, ledger);
        COOKING.get().push(new Cooking(station, snapshot(station, station.inputs),
                snapshot(station, station.outputs), new HashMap<>()));
    }

    public static void endEtheriumFurnace(BlockEntity be) {
        endCooking(be);
    }

    /** Credits the food produced by Farmer's Delight's hand-held skillet. */
    public static void handheldSkilletOutput(Player player, ItemStack skillet, ItemStack result) {
        if (!(player instanceof ServerPlayer server) || result == null || result.isEmpty()) return;
        if (skillet.isEmpty() || !skillet.hasTag() || !skillet.getTag().contains("Cooking")) return;
        ItemStack input = ItemStack.of(skillet.getTag().getCompound("Cooking"));
        if (input.isEmpty()) return;
        ResourceLocation food = DishServingVariants.target(BuiltInRegistries.ITEM.getKey(result.getItem()));
        if (!CulinaryRegistries.current().isTracked(food)) return;
        Set<ResourceLocation> ingredients = Set.of(BuiltInRegistries.ITEM.getKey(input.getItem()));
        CookingRewards.stampOutput(server, result, true, false);
        ProgressionService.credit(new CulinaryCredit(server, food, result.getCount(),
                CookingMethod.CAMPFIRE, CulinaryCredit.Source.INTERACTION,
                ingredients, false, MealStamp.read(result)));
    }
    public static void endCooking(BlockEntity be) {
        if(be==null || be.getLevel()==null || be.getLevel().isClientSide)return;
        Deque<Cooking> contexts=COOKING.get();if(contexts.isEmpty())return;
        Cooking c=FrameStacks.finish(contexts,frame->frame.station.owner==be);
        if(c==null)return;
        if(contexts.isEmpty())COOKING.remove();
        var l=ledger(c.station);Map<Integer,Integer> losses=new TreeMap<>();
        for(var entry:c.before.entrySet()) {
            int slot=entry.getKey();ItemStack before=entry.getValue(),after=stack(c.station,slot);
            int amount=before.getCount()-(ItemStack.isSameItemSameTags(before,after)?after.getCount():0);
            if(amount>0 && !c.emitted.containsKey(slot))losses.put(slot,amount);
        }
        ProductionLedger.Batch produced=null;
        if(ItemRecipeWorkstations.host(be)&&losses.isEmpty())for(int output:c.station.outputs){
            var result=stack(c.station,output);if(result.isEmpty())continue;
            var before=c.outputs.get(output);int gained=result.getCount()-(ItemStack.isSameItemSameTags(before,result)?before.getCount():0);
            if(gained<=0)continue;
            for(int from:c.station.outputs)if(from!=output){var old=c.outputs.get(from);
                if(!old.isEmpty()&&ItemStack.isSameItemSameTags(old,result)&&stack(c.station,from).getCount()<old.getCount())
                    l.transfer(from,output,Math.min(gained,old.getCount()-stack(c.station,from).getCount()));
            }
        }
        for(int output:c.station.outputs) {
            ItemStack result=stack(c.station,output),before=c.outputs.get(output);
            int gained=result.getCount()-(ItemStack.isSameItemSameTags(before,result)?before.getCount():0);
            if(gained>0 && !losses.isEmpty()) {
                var batch=produced==null?l.produce(losses,identity(result),gained,c.station.method.id().toString())
                        :new ProductionLedger.Batch(identity(result),gained,produced.contributors(),produced.ingredients(),produced.method());
                if(produced==null)produced=batch;
                if(ItemRecipeWorkstations.host(be)){
                    result=result.copy();sourceFromBatch(result,batch);ItemRecipeWorkstations.replaceOutput(be,output,result);
                    batch=new ProductionLedger.Batch(identity(result),gained,batch.contributors(),batch.ingredients(),batch.method());
                }
                if(LetsDoStations.host(be)){
                    result=result.copy();
                    var inputs=losses.keySet().stream().map(c.before::get).filter(Objects::nonNull).toList();
                    if(c.station.method.id().toString().equals("vinery:wine_fermentation"))batch=LetsDoJuice.inherit(be,result,batch);
                    if(c.station.method.id().toString().equals("farm_and_charm:mincer")){
                        var parent=inputs.stream().filter(MealStamp::isStamped).findFirst().orElse(ItemStack.EMPTY);
                        var stamp=MealStamp.read(parent);if(stamp!=null)stamp.write(result);
                    }
                    if(com.chi__you.cuisinejournal.food.LetsDoFood.beer(result)&&batch.contributors().size()==1)
                        CookingRewards.stampProduced(be.getLevel().getServer(),result,batch.contributors().iterator().next(),gained);
                    batch=new ProductionLedger.Batch(identity(result),gained,batch.contributors(),batch.ingredients(),batch.method());
                    if(Set.of("farm_and_charm:crafting_bowl","farm_and_charm:mincer","farm_and_charm:drying").contains(batch.method()))
                        {sourceFromBatch(result,batch);pending(result,batch);
                        batch=new ProductionLedger.Batch(identity(result),gained,batch.contributors(),batch.ingredients(),batch.method());}
                    LetsDoStations.replaceOutput(be,output,result);
                }
                if(!allows(c.station,WorkstationDefinitions.Capability.ITEM_OUTPUT)||!accepts(c.station,c.station.method.id()))
                    batch=new ProductionLedger.Batch(batch.identity(),batch.count(),Set.of(),batch.ingredients(),batch.method());
                l.append(output,batch);
            }
        }
        for(int slot:c.station.inputs){ItemStack v=stack(c.station,slot);l.syncInput(slot,identity(v),v.getCount(),null);}
        save(c.station,l);
    }
    public static ItemStack claimInteractionDrop(ItemStack result) {
        if(OPERATIONS.get().isEmpty())return result;
        Operation operation=OPERATIONS.get().peek();Station station=operation.station;
        if(station==null)return result;
        if(!station.method.id().toString().equals("cataclysm:amethyst_bless"))return result;
        var ledger=ledger(station);var batch=ledger.peek(0);
        if(batch!=null&&batch.identity().equals(identity(result))) {
            var taken=ledger.take(0,result.getCount());save(station,ledger);
            claim(operation.player,result,taken,true,CulinaryCredit.Source.INTERACTION);
        }
        return result;
    }
    public static void emitted(ItemStack output) {
        if(COOKING.get().isEmpty() || output.isEmpty())return;
        Cooking c=COOKING.get().peek();var l=ledger(c.station);
        if(!allows(c.station,WorkstationDefinitions.Capability.DIRECT_DROP))return;
        for(int slot:c.station.inputs) {
            if((c.emitted.containsKey(slot)&&!c.station.method.equals(CookingMethod.CUTTING_BOARD))||c.before.get(slot).isEmpty())continue;
            if(!matches(c.station,c.before.get(slot),output))continue;
            var previous=c.emitted.get(slot);
            var b=previous==null?l.produce(Map.of(slot,1),identity(output),output.getCount(),c.station.method.id().toString())
                    :new ProductionLedger.Batch(identity(output),output.getCount(),previous.contributors(),previous.ingredients(),previous.method());
            c.emitted.put(slot,b);
            if(c.station.owner instanceof BlockEntity be&&LetsDoStations.host(be))
                com.chi__you.cuisinejournal.food.IngredientProvenance.sources(output,List.of(c.before.get(slot)));
            if(IngredientCatalogueConfig.current().processing(c.station.method.id())) {
                MealStamp stamp=MealStamp.read(c.before.get(slot));if(stamp!=null)stamp.write(output);
                save(c.station,l);return;
            }
            if(c.station.owner instanceof BlockEntity be&&immediateDrop(be))DirectDropProgress.finish(be.getLevel(),output,b);
            else output.getOrCreateTag().put(PENDING,ProductionLedger.writeBatch(b));
            save(c.station,l);return;
        }
    }
    public static void beginBlazier(BlockEntity be){
        if(station(be)==null||be.getLevel()==null||be.getLevel().isClientSide)return;
        Object heat=HostReflection.field(be.getBlockState().getBlock(),"HEAT");
        CookingMethod method=CookingMethod.CAMPFIRE;
        if(heat instanceof net.minecraft.world.level.block.state.properties.Property<?> property){
            String value=be.getBlockState().getValue(property).toString().toLowerCase(java.util.Locale.ROOT);
            method=switch(value){case "smelting"->CookingMethod.of("minecraft","blasting");case "baking"->CookingMethod.SMELTING;case "smoking"->CookingMethod.SMOKING;default->CookingMethod.CAMPFIRE;};
        }
        Station s=new Station(be,range(0,4),new int[0],method);var ledger=ledger(s);reconcile(s,ledger);save(s,ledger);
        COOKING.get().push(new Cooking(s,snapshot(s,s.inputs),Map.of(),new HashMap<>()));
    }
    private static boolean matches(Station s,ItemStack input,ItemStack output) {
        if(!(s.owner instanceof BlockEntity be)||be.getLevel()==null)return false;
        for(Recipe<?> recipe:be.getLevel().getRecipeManager().getRecipes()) {
            ResourceLocation recipeType=BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if(recipeType==null||!accepts(s,recipeType)||!CookingMethod.fromRecipeType(recipeType).equals(s.method))continue;
            if(com.chi__you.cuisinejournal.recipe.RecipeClassifier.outputs(recipe,be.getLevel().registryAccess())
                    .stream().anyMatch(result -> ItemStack.isSameItem(result,output))
                    &&recipe.getIngredients().stream().anyMatch(i->i.test(input)))return true;
        }
        return false;
    }
    private static boolean immediateDrop(BlockEntity be) {
        var adapter=adapterFor(be);
        return be instanceof CampfireBlockEntity||adapter==WorkstationDefinitions.Adapter.SKILLET||adapter==WorkstationDefinitions.Adapter.STOVE
                ||be.getClass().getName().equals("net.satisfy.camping.core.world.block.entity.GrillBlockEntity")
                ||be.getClass().getName().equals("com.soytutta.mynethersdelight.common.block.entity.BlazierBlockEntity");
    }
    public static void beginTransfer(BlockEntity be) { beginCooking(be); }
    public static void endTransfer(BlockEntity be) {
        if(be.getLevel()==null||be.getLevel().isClientSide||station(be)==null)return;
        Deque<Cooking> contexts=COOKING.get();if(contexts.isEmpty())return;Cooking c=contexts.pop();
        if(contexts.isEmpty())COOKING.remove();var l=ledger(c.station);
        int from=c.station.outputs[0],to=c.station.outputs[1],container=containerSlot(c.station);
        int moved=c.outputs.get(from).getCount()-stack(c.station,from).getCount();
        int containers=c.before.get(container).getCount()-stack(c.station,container).getCount();
        if(moved>0)l.transferWithContainers(from,to,container,moved,Math.max(0,containers));
        save(c.station,l);
    }
    private static final ProductionFrames<Deque<Integer>> HELD_CONTAINERS=ProductionFrames.stack();
    public static void beginHeldMeal(BlockEntity be,ItemStack container) {
        if(be.getLevel()==null||be.getLevel().isClientSide||station(be)==null)return;
        beginTransfer(be);HELD_CONTAINERS.get().push(container.getCount());
    }
    public static void heldMeal(BlockEntity be,ItemStack container,ItemStack result) {
        if(be.getLevel()==null||be.getLevel().isClientSide||station(be)==null)return;
        var contexts=COOKING.get(); if(contexts.isEmpty())return;
        Cooking c=contexts.pop();if(contexts.isEmpty())COOKING.remove();
        var counts=HELD_CONTAINERS.get();int count=counts.isEmpty()?container.getCount():counts.pop();
        if(counts.isEmpty())HELD_CONTAINERS.remove();
        if(result.isEmpty())return;
        Station s=c.station;var l=ledger(s);
        var batches=l.take(s.outputs[0],result.getCount());
        ServerPlayer player=actor();
        // This method only returns a meal after accepting and consuming the held container.
        if(player!=null&&container.getCount()<count)batches=batches.stream().map(b->b.withContributor(player.getUUID())).toList();
        save(s,l);
        claim(allows(s,WorkstationDefinitions.Capability.RIGHT_CLICK_TAKE)&&allows(s,WorkstationDefinitions.Capability.CONTAINER_FILL)
                ?actor():null,result,batches,true,CulinaryCredit.Source.INTERACTION);
    }

    public static Extraction prepare(Player player,AbstractContainerMenu menu,Slot slot,boolean perfect) {
        return prepare(player,menu,slot,perfect,slot.getItem().getCount());
    }
    public static Extraction prepare(Player player,AbstractContainerMenu menu,Slot slot,boolean perfect,int count) {
        if(!(player instanceof ServerPlayer server)||slot.getItem().isEmpty())return null;
        Station s=menu(menu);if(s==null)return null;
        if(!allows(s,WorkstationDefinitions.Capability.MENU_TAKE))return null;
        boolean craft=slot instanceof ResultSlot;
        if(!isProductionResultSlot(slot)&&!isOutputSlot(s,slot))return null;
        int source=slot.getContainerSlot();
        if(s.owner instanceof BlockEntity be&&ItemRecipeWorkstations.host(be))
            source=Arrays.stream(s.outputs).filter(index->ItemRecipeWorkstations.ownsSlot(be,slot,index)).findFirst().orElse(-1);
        if(!craft){boolean output=false;for(int index:s.outputs)if(index==source)output=true;if(!output)return null;}
        ResourceLocation preparedIdentity=BuiltInRegistries.ITEM.getKey(
                com.chi__you.cuisinejournal.food.PlacedFoodConsumption.identityForProduction(slot.getItem()).getItem());
        if(!CulinaryRegistries.current().isTracked(CulinaryRegistries.current().productionIdentity(preparedIdentity)))return null;
        Extraction prepared=PREPARED.get();if(prepared!=null&&prepared.slot==slot&&prepared.player==server)return prepared;
        ItemStack original=slot.getItem().copy();ProductionLedger l=ledger(s);reconcile(s,l);
        ProductionLedger.Batch batch;Map<Integer,Integer> consumed=new TreeMap<>();
        if(craft||isPreview(s)) {
            for(int i:s.inputs)if(!stack(s,i).isEmpty()&&(!isPreview(s)||VerifiedWorkstations.previewConsumes(s.owner,i,stack(s,i))))consumed.put(i,1);
            // Preview without consuming; commit consumes exactly one recipe on a successful take.
            var preview=ProductionLedger.load(l.save());batch=preview.produce(consumed,identity(original),original.getCount(),s.method.id().toString());
        } else if(s.owner instanceof BlockEntity be&&FluidProductionTracker.supports(be))batch=FluidProductionTracker.extractionPreview(be,source);
        else batch=l.peek(source);
        save(s,l);if(batch==null)return null;
        boolean deferred=count<0;
        ItemStack marked=deferred?original.copy():slot.getItem();
        if(!deferred&&s.owner instanceof BlockEntity be&&LetsDoStations.host(be))marked.removeTagKey(PENDING);
        if(s.owner instanceof BlockEntity be&&LetsDoStations.host(be)&&batch.belongsTo(server.getUUID())&&!deferred)sourceFromBatch(marked,batch);
        if(batch.belongsTo(server.getUUID())&&!deferred)CookingRewards.stampOutput(server,marked,perfect,false,Math.min(original.getCount(),Math.max(1,count)));
        if(!batch.belongsTo(server.getUUID())&&!com.chi__you.cuisinejournal.food.LetsDoFood.mapped(marked)
                &&!(s.owner instanceof BlockEntity be&&LetsDoStations.host(be)&&MealStamp.isStamped(marked)))new MealStamp(0,false,0,null,"").write(marked);
        return new Extraction(s,slot,original,batch,server,original.getCount(),Map.copyOf(consumed),marked.copy(),slot.getItem(),deferred,perfect);
    }
    public static void prepareClick(Player player,AbstractContainerMenu menu,int index,int button,ClickType type) {
        if(!(player instanceof ServerPlayer)||index<0||index>=menu.slots.size()||type==ClickType.QUICK_MOVE)return;
        if(type==ClickType.PICKUP||type==ClickType.THROW||type==ClickType.SWAP) {
            Slot slot=menu.slots.get(index);if(!slot.mayPickup(player))return;
            // tryRemove knows the actual limits; it owns quality for cursor extraction.
            if(type!=ClickType.PICKUP)PREPARED.set(prepare(player,menu,slot,true,-1));
        }
    }
    public static boolean swapOutput(Player player,AbstractContainerMenu menu,int index,int button) {
        if(!(player instanceof ServerPlayer)||index<0||index>=menu.slots.size()||button<0||button>8&&button!=40)return false;
        Slot slot=menu.slots.get(index);
        Station station=menu(menu);
        if(!isProductionResultSlot(slot)&&(station==null||!isOutputSlot(station,slot)))return false;
        if(!player.getInventory().getItem(button).isEmpty()||!slot.mayPickup(player))return true;
        ItemStack taken=slot.safeTake(slot.getItem().getCount(),Integer.MAX_VALUE,player);
        if(!taken.isEmpty())player.getInventory().setItem(button,taken);
        return true;
    }

    public static ItemStack quickMoveOutput(ServerPlayer player,AbstractContainerMenu menu,Slot slot){
        var tx=prepare(player,menu,slot,true,-1);if(tx==null)return null;
        // Reserve room for either whole-batch quality before rolling, so a full inventory cannot reroll a serving.
        ItemStack normal=tx.original.copy();
        if(tx.batch.belongsTo(player.getUUID()))CookingRewards.stampOutput(player,normal,false,false);
        else new MealStamp(0,false,0,null,"").write(normal);
        ItemStack perfect=normal.copy();var stamp=MealStamp.read(normal);
        if(stamp!=null&&tx.batch.belongsTo(player.getUUID()))new MealStamp(stamp.stars(),true,stamp.starCap(),stamp.cookId(),stamp.cookName()).write(perfect);
        int capacity=Math.min(inventoryRoom(player,normal),inventoryRoom(player,perfect));
        if(capacity<=0)return ItemStack.EMPTY;
        PREPARED.set(tx);
        ItemStack taken=slot.safeTake(Math.min(capacity,slot.getItem().getCount()),Integer.MAX_VALUE,player);
        if(taken.isEmpty()){PREPARED.remove();return ItemStack.EMPTY;}
        ItemStack result=taken.copy();
        if(!player.getInventory().add(taken)&&!taken.isEmpty())player.drop(taken,false);
        return result;
    }
    private static int inventoryRoom(ServerPlayer player,ItemStack candidate){
        int room=0,maximum=Math.min(candidate.getMaxStackSize(),player.getInventory().getMaxStackSize());
        for(int i=0;i<36;i++){
            var existing=player.getInventory().getItem(i);
            if(existing.isEmpty())room+=maximum;
            else if(ItemStack.isSameItemSameTags(existing,candidate))room+=Math.max(0,maximum-existing.getCount());
        }
        return room;
    }
    public static void endClick() {
        Extraction tx=PREPARED.get();PREPARED.remove();
        if(tx!=null)finish(tx,0);
    }
    public static boolean isProductionResultSlot(Slot slot) {
        return slot instanceof ResultSlot || slot instanceof FurnaceResultSlot
                || CookingPotCompatibility.isResultSlot(slot.getClass())
                || slot.getClass().getName().endsWith("CookingPotResultSlot");
    }
    private static int containerSlot(Station station) {
        var layout=VerifiedWorkstations.find(station.owner.getClass());return layout==null?7:layout.container();
    }
    private static boolean isPreview(Station station) {
        var layout=VerifiedWorkstations.find(station.owner.getClass());return layout!=null&&layout.preview();
    }
    private static boolean isOutputSlot(Station station,Slot slot) {
        if(station.owner instanceof BlockEntity be&&ItemRecipeWorkstations.host(be))
            return Arrays.stream(station.outputs).anyMatch(index->ItemRecipeWorkstations.ownsSlot(be,slot,index));
        if(station.owner instanceof BlockEntity be&&LetsDoStations.host(be)&&slot.container!=be)return false;
        if(slot.mayPlace(ItemStack.EMPTY)&&!(station.owner instanceof BlockEntity be
                &&(be.getClass().getName().equals("net.ribs.vintagedelight.block.entity.FermentingJarBlockEntity")||LetsDoStations.host(be))))return false;
        return Arrays.stream(station.outputs).anyMatch(index->index==slot.getContainerSlot());
    }
    public static void finish(Extraction tx,int amount) {
        if(tx==null)return;int taken=Math.max(0,Math.min(amount,tx.available));
        if(taken>0&&tx.deferredQuality()&&tx.batch.belongsTo(tx.player.getUUID()))
            {if(tx.station.owner instanceof BlockEntity be&&LetsDoStations.host(be)){tx.marked.removeTagKey(PENDING);sourceFromBatch(tx.marked,tx.batch);}
            CookingRewards.stampOutput(tx.player,tx.marked,tx.allowPerfect(),false,taken);}
        if(PREPARED.get()==tx)PREPARED.remove();
        ProductionLedger l=ledger(tx.station);
        List<ProductionLedger.Batch> batches;
        if(tx.slot instanceof ResultSlot||isPreview(tx.station)) {
            if(taken>0)l.produce(tx.consumed,tx.batch.identity(),taken,tx.batch.method());
            batches=taken>0?List.of(new ProductionLedger.Batch(tx.batch.identity(),taken,tx.batch.contributors(),tx.batch.ingredients(),tx.batch.method())):List.of();
        } else if(tx.station.owner instanceof BlockEntity be&&FluidProductionTracker.supports(be)) {
            batches=taken>0?List.of(new ProductionLedger.Batch(tx.batch.identity(),taken,tx.batch.contributors(),tx.batch.ingredients(),tx.batch.method())):List.of();
            FluidProductionTracker.takeOutput(be,tx.slot.getContainerSlot(),taken);
        } else {
            int source=tx.slot.getContainerSlot();
            if(tx.station.owner instanceof BlockEntity be&&ItemRecipeWorkstations.host(be))
                source=Arrays.stream(tx.station.outputs).filter(index->ItemRecipeWorkstations.ownsSlot(be,tx.slot,index)).findFirst().orElse(-1);
            batches=l.take(source,taken);
        }
        save(tx.station,l);
        ItemStack rest=tx.slot.getItem();
        restoreRemainder(rest,tx.live,tx.original,tx.marked,taken);
        if(tx.batch.belongsTo(tx.player.getUUID()))for(var batch:batches)
            credit(tx.player,tx.marked,batch,CulinaryCredit.Source.MENU);
        if(taken>0 && tx.batch.belongsTo(tx.player.getUUID())) {
            CookingRewards.announcePerfect(tx.player,tx.marked);
        }
    }
    public static void markExtracted(Extraction tx,ItemStack result) {
        if(tx==null||result.isEmpty()||!ItemStack.isSameItem(result,tx.original))return;
        if(tx.station.owner instanceof BlockEntity be&&LetsDoStations.host(be))result.removeTagKey(PENDING);
        var stamp=MealStamp.read(tx.marked);
        if(stamp!=null)stamp.write(result);
        com.chi__you.cuisinejournal.food.FoodVariantIdentity.copyKeys(tx.marked,result,
                com.chi__you.cuisinejournal.food.FoodVariantIdentity.CLAIMED_RESULT);
    }
    public static void restoreRemainder(ItemStack rest,ItemStack live,ItemStack original,ItemStack marked,int taken) {
        if(rest.isEmpty()||rest!=live||taken>=original.getCount()
                ||rest.getCount()!=original.getCount()-taken||!ItemStack.isSameItemSameTags(rest,marked))return;
        rest.setTag(original.getTag()==null?null:original.getTag().copy());
    }
    private static void claim(ServerPlayer player,ItemStack stack,List<ProductionLedger.Batch> batches,
                              boolean perfect,CulinaryCredit.Source source) {
        if(!batches.isEmpty()&&batches.get(0).method().startsWith("brewery:"))sourceFromBatch(stack,batches.get(0));
        if(batches.stream().allMatch(batch->batch.method().equals("cuisinejournal:fluid_transfer")))return;
        ResourceLocation claimedIdentity=BuiltInRegistries.ITEM.getKey(
                com.chi__you.cuisinejournal.food.PlacedFoodConsumption.identityForProduction(stack).getItem());
        if(!CulinaryRegistries.current().isTracked(CulinaryRegistries.current().productionIdentity(claimedIdentity)))return;
        boolean owner=player!=null&&batches.stream().anyMatch(b->b.belongsTo(player.getUUID()));
        if(owner)CookingRewards.stampOutput(player,stack,perfect,true);
        else if(!com.chi__you.cuisinejournal.food.LetsDoFood.mapped(stack)&&!(com.chi__you.cuisinejournal.food.FoodVariantIdentity.hasVariant(stack)&&MealStamp.isStamped(stack)))new MealStamp(0,false,0,null,"").write(stack);
        if(owner)for(var b:batches)credit(player,stack,b,source);
    }
    public static void claimBatch(ServerPlayer player,ItemStack stack,ProductionLedger.Batch batch,CulinaryCredit.Source source) {
        stack.removeTagKey(FoodVariantIdentity.BATCH_COUNT);
        claim(player,stack,List.of(new ProductionLedger.Batch(batch.identity(),stack.getCount(),batch.contributors(),batch.ingredients(),batch.method())),true,source);
    }
    public static void pending(ItemStack stack,ProductionLedger.Batch batch) {
        if(!stack.isEmpty())stack.getOrCreateTag().put(PENDING,ProductionLedger.writeBatch(
                new ProductionLedger.Batch(identity(stack),stack.getCount(),batch.contributors(),batch.ingredients(),batch.method())));
    }
    public static void generated(BlockEntity be,ItemStack result,Map<Integer,Integer> consumed){
        Station station=stationOrNull(be);if(station==null||be.getLevel()==null||be.getLevel().isClientSide||result.isEmpty())return;
        var ledger=ledger(station);var batch=ledger.produce(consumed,identity(result),result.getCount(),station.method.id().toString());
        save(station,ledger);sourceFromBatch(result,batch);pending(result,batch);
    }
    private static void sourceFromBatch(ItemStack output,ProductionLedger.Batch batch){
        if(batch.ingredients().isEmpty())return;List<ItemStack> inputs=new ArrayList<>();
        for(String raw:batch.ingredients().stream().sorted().toList()){var id=net.minecraft.resources.ResourceLocation.tryParse(itemId(raw));
            if(id==null||!BuiltInRegistries.ITEM.containsKey(id))continue;ItemStack input=new ItemStack(BuiltInRegistries.ITEM.get(id));
            int separator=raw.indexOf('|');if(separator>=0&&raw.length()>separator+1)try{input.setTag(net.minecraft.nbt.TagParser.parseTag(raw.substring(separator+1)));}
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException invalid){com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Ignoring malformed ingredient provenance for {}",id);}
            inputs.add(input);
        }
        com.chi__you.cuisinejournal.food.IngredientProvenance.sources(output,inputs);
    }
    private static void credit(ServerPlayer player,ItemStack output,ProductionLedger.Batch batch,CulinaryCredit.Source source) {
        if(batch.method().equals("cuisinejournal:fluid_transfer"))return;
        ResourceLocation food=CulinaryRegistries.current().productionIdentity(
                BuiltInRegistries.ITEM.getKey(com.chi__you.cuisinejournal.food.PlacedFoodConsumption
                        .identityForProduction(output).getItem()));
        if(!CulinaryRegistries.current().isTracked(food)||IngredientCatalogueConfig.current().processing(ResourceLocation.parse(batch.method())))return;
        Set<ResourceLocation> inputs=new HashSet<>();for(String raw:batch.ingredients()) {
            ResourceLocation id=ResourceLocation.tryParse(itemId(raw));if(id!=null)inputs.add(id);
        }
        ProgressionService.credit(new CulinaryCredit(player,food,batch.count(),CookingMethod.of(ResourceLocation.parse(batch.method())),source,inputs,false,MealStamp.read(output)));
    }
    public static void clear() {CRAFTING.clear();ProductionFrames.expireAll();FluidProductionTracker.clear();InPlaceProductionTracker.clear();FluidRecipeBridge.clear();LetsDoSpeed.clear();LetsDoJuice.clear();}
    public static void expireFrames() {
        ProductionFrames.expireAll();
    }

    public static boolean pickup(net.minecraft.world.entity.player.Inventory inventory,ItemStack stack) {
        if(!(inventory.player instanceof ServerPlayer player)||!stack.hasTag()||!stack.getTag().contains(PENDING))return inventory.add(stack);
        var batch=ProductionLedger.readBatch(stack.getTag().getCompound(PENDING));
        if (!batch.belongsTo(player.getUUID())) return inventory.add(stack);
        stack.removeTagKey(PENDING);
        int batchCount=stack.getTag().contains(FoodVariantIdentity.BATCH_COUNT)?stack.getTag().getInt(FoodVariantIdentity.BATCH_COUNT):batch.count();
        stack.removeTagKey(FoodVariantIdentity.BATCH_COUNT);
        CookingRewards.stampOutput(player,stack,true,false,batchCount);
        ItemStack marked=stack.copy();int before=stack.getCount();boolean added=inventory.add(stack);
        int amount=before-stack.getCount();
        if(amount>0)credit(player,marked,new ProductionLedger.Batch(batch.identity(),amount,batch.contributors(),batch.ingredients(),batch.method()),CulinaryCredit.Source.DROP);
        if(!stack.isEmpty()) {
            stack.setTag(marked.getTag()==null?null:marked.getTag().copy());
            stack.getOrCreateTag().put(PENDING,ProductionLedger.writeBatch(new ProductionLedger.Batch(batch.identity(),stack.getCount(),batch.contributors(),batch.ingredients(),batch.method())));
        }
        return added;
    }
    public static void machinePickup(ItemStack stack) {
        HotSpringSource.clean(stack);
        stack.removeTagKey(FoodVariantIdentity.BATCH_COUNT);
        if(stack.hasTag()&&stack.getTag().contains(PENDING)) {
            stack.removeTagKey(PENDING);
            if(!(com.chi__you.cuisinejournal.food.FoodVariantIdentity.hasVariant(stack)&&MealStamp.isStamped(stack)))
                new MealStamp(0,false,0,null,"").write(stack);
        }
    }
}
