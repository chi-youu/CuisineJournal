package com.chi__you.cuisinejournal.tracking;
import com.chi__you.cuisinejournal.food.FoodVariantIdentity;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.food.IngredientProvenance;

import com.chi__you.cuisinejournal.food.KaleidoscopeFood;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;

/** Host containers retain actual contributors and source stacks across asynchronous cooking. */
public final class KaleidoscopeProduction {
    private static final String KEY="cuisinejournal:kaleidoscope_production";
    private static final String INPUT_OWNERS=com.chi__you.cuisinejournal.food.FoodVariantIdentity.INPUT_OWNERS;
    private record Frame(BlockEntity be,ServerPlayer actor,Map<Integer,ItemStack> before){}
    private static final ProductionFrames<Deque<Frame>> FRAMES=ProductionFrames.stack();
    private KaleidoscopeProduction(){}
    public static void expireFrames(){FRAMES.remove();}
    public static String method(BlockEntity be) {
        return be==null?null:method(be.getClass());
    }
    public static String method(Class<?> kind) {
        for(Class<?> type=kind;type!=null;type=type.getSuperclass()){
            String name=type.getSimpleName();String host=type.getName();
            if(host.startsWith("com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.")){
                String method=switch(name){
                case "PotBlockEntity"->"kaleidoscope_cookery:pot";case "StockpotBlockEntity"->"kaleidoscope_cookery:stockpot";
                case "TeapotBlockEntity"->"kaleidoscope_cookery:teapot";case "SteamerBlockEntity"->"kaleidoscope_cookery:steamer";
                case "BambooTrayBlockEntity"->"kaleidoscope_cookery:bamboo_tray";case "MillstoneBlockEntity"->"kaleidoscope_cookery:millstone";
                case "ChoppingBoardBlockEntity"->"kaleidoscope_cookery:chopping_board";case "ShawarmaSpitBlockEntity"->"minecraft:campfire_cooking";default->null;};
                if(method!=null)return method;
            }
            if(host.equals("com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.BarrelBlockEntity"))return "kaleidoscope_tavern:barrel";
            if(host.equals("com.github.ysbbbbbb.kaleidoscopetavern.blockentity.brew.PressingTubBlockEntity"))return "kaleidoscope_tavern:pressing_tub";
            if(host.equals("com.bmt.kaleidoscope_chinesefood.block.entity.PickleJarBlockEntity"))return "kaleidoscope_chinesefood:pickle_jar";
            if(host.equals("com.bmt.kaleidoscope_chinesefood.block.entity.FreezerBlockEntity"))return "kaleidoscope_chinesefood:freezing";
            if(host.equals("com.bmt.kaleidoscope_world_liquor.block.entity.FreezerBlockEntity"))return "kaleidoscope_world_liquor:freezer";
        }
        return null;
    }
    private static String identity(ItemStack stack){
        if(stack.isEmpty())return "";var tag=stack.hasTag()?stack.getTag().copy():new CompoundTag();tag.remove(INPUT_OWNERS);
        return BuiltInRegistries.ITEM.getKey(stack.getItem())+"|"+tag;
    }
    private static Set<UUID> inputOwners(ItemStack stack){
        Set<UUID> owners=new HashSet<>();if(stack.hasTag())for(var value:stack.getTag().getList(INPUT_OWNERS,10))
            if(((CompoundTag)value).hasUUID("id"))owners.add(((CompoundTag)value).getUUID("id"));return owners;
    }
    private static boolean sameProduct(ItemStack a,ItemStack b){
        return com.chi__you.cuisinejournal.food.FoodVariantIdentity.sameProduct(a,b,
                com.chi__you.cuisinejournal.food.FoodVariantIdentity.PRODUCTION_BOOKKEEPING);
    }
    private static void rememberOwner(ItemStack stack,UUID actor){
        var owners=inputOwners(stack);owners.add(actor);var rows=new net.minecraft.nbt.ListTag();
        owners.stream().sorted().forEach(id->{var row=new CompoundTag();row.putUUID("id",id);rows.add(row);});stack.getOrCreateTag().put(INPUT_OWNERS,rows);
    }
    public static ItemStack insertedVariant(ItemStack stack){
        var actor=ProductionTracker.actor();if(actor==null||stack.isEmpty())return stack;
        var copy=stack.copy();rememberOwner(copy,actor.getUUID());return copy;
    }
    private static void save(BlockEntity be,CompoundTag saved){
        if(!saved.equals(be.getPersistentData().getCompound(KEY))){be.getPersistentData().put(KEY,saved);be.setChanged();}
    }
    private static void add(Map<Integer,ItemStack> result,Object value,int start) {
        if(value instanceof ItemStack stack)result.put(start,stack);
        else if(value instanceof List<?> list){for(int i=0;i<list.size();i++)if(list.get(i) instanceof ItemStack stack)result.put(start+i,stack);}
        else if(value instanceof net.minecraftforge.items.IItemHandler handler)for(int i=0;i<handler.getSlots();i++)result.put(start+i,handler.getStackInSlot(i));
    }
    private static Map<Integer,ItemStack> stacks(BlockEntity be) {
        Map<Integer,ItemStack> result=new TreeMap<>();
        String method=method(be);if(method==null)return result;
        if(method.endsWith(":pot")||method.endsWith(":stockpot")){
            add(result,HostReflection.field(be,"inputs"),0);add(result,HostReflection.field(be,"result"),100);
        }else if(method.endsWith(":teapot")){
            add(result,HostReflection.field(be,"input"),0);add(result,HostReflection.field(be,"result"),100);
        }else if(method.endsWith(":millstone")){
            add(result,HostReflection.field(be,"input"),0);add(result,HostReflection.field(be,"outputs"),100);
        }else if(method.endsWith(":barrel")){
            add(result,HostReflection.field(be,"ingredient"),0);add(result,HostReflection.field(be,"output"),100);
        }else if(method.equals("kaleidoscope_world_liquor:freezer")){
            // Its output handler manufactures preview stacks; inspecting it would recurse into output().
            add(result,HostReflection.field(be,"inputInventory"),0);
        }else if(method.endsWith(":chopping_board")){
            add(result,HostReflection.field(be,"currentCutStack"),0);add(result,HostReflection.field(be,"results"),100);
        }else if(method.equals("minecraft:campfire_cooking")){
            add(result,HostReflection.field(be,"cookingItem"),0);add(result,HostReflection.field(be,"cookedItem"),100);
        }else if(be instanceof net.minecraft.world.Container container){for(int i=0;i<container.getContainerSize();i++)result.put(i,container.getItem(i));}
        else {
            add(result,HostReflection.field(be,"items"),0);add(result,HostReflection.field(be,"inventory"),0);
        }
        return result;
    }
    public static void begin(BlockEntity be,ServerPlayer actor) {
        if(be==null||be.getLevel()==null||be.getLevel().isClientSide||method(be)==null)return;
        Map<Integer,ItemStack> before=new TreeMap<>();stacks(be).forEach((slot,item)->before.put(slot,item.copy()));
        if(actor==null){
            var saved=be.getPersistentData().getCompound(KEY).copy();var ledger=ProductionLedger.load(saved.getCompound("ledger"));
            Frame operation=FRAMES.get().stream().filter(frame->frame.be==be&&frame.actor!=null).findFirst().orElse(null);
            before.forEach((slot,item)->{if(slot<100){
                if(operation!=null){var old=operation.before.getOrDefault(slot,ItemStack.EMPTY);
                    ledger.syncInput(slot,identity(item),identity(old).equals(identity(item))?Math.min(old.getCount(),item.getCount()):0,null);
                }
                ledger.syncInput(slot,identity(item),item.getCount(),operation==null?null:operation.actor.getUUID());
            }});
            saved.put("ledger",ledger.save());save(be,saved);
        }
        FRAMES.get().push(new Frame(be,actor,before));
    }
    public static void end(BlockEntity be) {end(be,ItemStack.EMPTY);}
    public static void endVirtual(BlockEntity be,boolean success){
        Object raw=HostReflection.field(be,"recipe");
        ItemStack result=success&&raw instanceof net.minecraft.world.item.crafting.Recipe<?> recipe
                ?recipe.getResultItem(be.getLevel().registryAccess()).copy():ItemStack.EMPTY;end(be,result);
    }
    private static void end(BlockEntity be,ItemStack virtual) {
        var frames=FRAMES.get();Frame frame=FrameStacks.finish(frames,value->value.be==be);
        if(frame==null)return;if(frames.isEmpty())FRAMES.remove();
        Map<Integer,ItemStack> after=stacks(be);
        if(method(be).endsWith(":chopping_board")){
            Object raw=HostReflection.field(be,"results");
            if(raw instanceof List<?> results){
                var copies=new ArrayList<ItemStack>();for(Object value:results)copies.add(((ItemStack)value).copy());
                field(be,"results",copies);after=stacks(be);
            }
        }
        if(!virtual.isEmpty())after.put(100,virtual);
        CompoundTag saved=be.getPersistentData().getCompound(KEY).copy();
        ProductionLedger ledger=ProductionLedger.load(saved.getCompound("ledger"));
        if(frame.actor!=null)for(var row:after.entrySet())if(row.getKey()<100&&!row.getValue().isEmpty()){
            ItemStack old=frame.before.getOrDefault(row.getKey(),ItemStack.EMPTY),item=row.getValue();
            if(!identity(old).equals(identity(item))||item.getCount()>old.getCount()){
                if(method(be).equals("kaleidoscope_cookery:steamer")||method(be).equals("kaleidoscope_cookery:teapot"))rememberOwner(item,frame.actor.getUUID());
                ledger.syncInput(row.getKey(),identity(item),identity(old).equals(identity(item))?old.getCount():0,null);
                ledger.syncInput(row.getKey(),identity(item),item.getCount(),frame.actor.getUUID());
            }
        }
        boolean transformed=after.entrySet().stream().anyMatch(row->row.getKey()>=100&&!row.getValue().isEmpty()
                &&!sameProduct(row.getValue(),frame.before.getOrDefault(row.getKey(),ItemStack.EMPTY)));
        if(frame.actor!=null&&!transformed){
            for(var row:after.entrySet())if(row.getKey()<100){
                ItemStack old=frame.before.getOrDefault(row.getKey(),ItemStack.EMPTY),item=row.getValue();
                int held=identity(old).equals(identity(item))?Math.min(old.getCount(),item.getCount()):0;
                ledger.syncInput(row.getKey(),identity(item),held,null);
                ledger.syncInput(row.getKey(),identity(item),item.getCount(),frame.actor.getUUID());
            }
            saved.put("ledger",ledger.save());save(be,saved);return;
        }
        List<ItemStack> inputs=new ArrayList<>();Map<Integer,Integer> consumed=new TreeMap<>();
        Map<Integer,ItemStack> actualInputs=new TreeMap<>(frame.before);
        after.forEach((slot,item)->{if(slot<100&&!item.isEmpty()&&frame.before.getOrDefault(slot,ItemStack.EMPTY).isEmpty())actualInputs.put(slot,item);});
        actualInputs.forEach((slot,item)->{if(slot<100&&!item.isEmpty()){
            inputs.add(item);ledger.syncInput(slot,identity(item),item.getCount(),null);consumed.put(slot,item.getCount());
            var portable=inputOwners(item);
            if(!portable.isEmpty()){
                var previous=ledger.produce(Map.of(slot,item.getCount()),identity(item),item.getCount(),method(be));
                portable.addAll(previous.contributors());
                ledger.adoptInput(slot,identity(item),item.getCount(),new ProductionLedger.Batch(identity(item),item.getCount(),portable,Set.of(),method(be)));
            }
        }});
        Set<UUID> fluidOwners=new HashSet<>();for(var value:saved.getList("fluidOwners",10))if(((CompoundTag)value).hasUUID("id"))fluidOwners.add(((CompoundTag)value).getUUID("id"));
        ProductionLedger.Batch shared=null;
        for(var row:after.entrySet()){
            int slot=row.getKey();ItemStack item=row.getValue(),old=frame.before.getOrDefault(slot,ItemStack.EMPTY);
            boolean inPlace=slot<100&&!old.isEmpty()&&!ItemStack.isSameItem(old,item)
                    &&matches(be,old,item);
            if(item.isEmpty()||item.hasTag()&&item.getTag().contains(ProductionTracker.PENDING))continue;
            boolean newResult=slot>=100&&!item.isEmpty()&&!sameProduct(old,item)
                    &&!(ItemStack.isSameItem(old,item)&&com.chi__you.cuisinejournal.culinary.MealStamp.isStamped(item));
            String outputKey="output"+slot;
            if(!(inPlace||newResult)||inputs.isEmpty()&&fluidOwners.isEmpty())continue;
            boolean wholeContainer=method(be).equals("kaleidoscope_chinesefood:pickle_jar");
            Map<Integer,Integer> used=inPlace&&!wholeContainer?Map.of(slot,old.getCount()):consumed;
            ProductionLedger.Batch batch=inPlace&&!wholeContainer?ledger.produce(used,identity(item),item.getCount(),method(be))
                    :shared==null?ledger.produce(used,identity(item),item.getCount(),method(be)):shared;
            if(!inPlace||wholeContainer){shared=batch;Set<UUID> owners=new HashSet<>(batch.contributors());owners.addAll(fluidOwners);
                batch=new ProductionLedger.Batch(identity(item),item.getCount(),owners,batch.ingredients(),method(be));}
            IngredientProvenance.sources(item,inPlace&&!wholeContainer?List.of(old):inputs);
            if(com.chi__you.cuisinejournal.recipe.IngredientCatalogueConfig.current().processing(net.minecraft.resources.ResourceLocation.parse(method(be)))){
                ItemStack parent=inputs.stream().filter(input->com.chi__you.cuisinejournal.culinary.MealStamp.isStamped(input)).findFirst().orElse(ItemStack.EMPTY);
                var stamp=com.chi__you.cuisinejournal.culinary.MealStamp.read(parent);
                if(stamp!=null)stamp.write(item);
                else if(!inputs.isEmpty())new com.chi__you.cuisinejournal.culinary.MealStamp(
                        com.chi__you.cuisinejournal.food.CuisineFoodQuality.stars(inputs.get(0)),false,5,null,"").write(item);
                if(KaleidoscopeFood.quality(parent))item.getOrCreateTag().putInt(KaleidoscopeFood.QUALITY,parent.getTag().getInt(KaleidoscopeFood.QUALITY));
                continue;
            }
            if(saved.contains("fluidSources",9)){
                var sources=item.getTag().getList(IngredientProvenance.KEY,8);
                for(var source:saved.getList("fluidSources",8))if(!sources.contains(source))sources.add(source.copy());
            }
            if(!inPlace)saved.put("sources",item.getTag().get(IngredientProvenance.KEY).copy());
            ProductionTracker.pending(item,batch);
            saved.put(outputKey,ProductionLedger.writeBatch(batch));
            saved.putString("last"+slot,identity(item));
        }
        saved.put("ledger",ledger.save());save(be,saved);
    }
    public static void endOperation(ServerPlayer player) {
        var frames=FRAMES.get();if(!frames.isEmpty()&&frames.peek().actor==player)end(frames.peek().be);
    }
    public static void output(BlockEntity be,ItemStack item) {
        output(be,item,false);
    }
    public static ProductionLedger.Batch outputBatch(CompoundTag saved,int count,boolean consume){
        if(!saved.contains("output100",10)||count<=0)return null;
        var batch=ProductionLedger.readBatch(saved.getCompound("output100"));
        int taken=Math.min(count,batch.count());if(taken<=0)return null;
        if(consume){
            if(batch.count()<=taken)saved.remove("output100");
            else saved.put("output100",ProductionLedger.writeBatch(new ProductionLedger.Batch(
                    batch.identity(),batch.count()-taken,batch.contributors(),batch.ingredients(),batch.method())));
        }
        return new ProductionLedger.Batch(batch.identity(),taken,batch.contributors(),batch.ingredients(),batch.method());
    }
    public static void output(BlockEntity be,ItemStack item,boolean consume) {
        if(item.isEmpty()||be.getLevel()==null||be.getLevel().isClientSide)return;
        var saved=be.getPersistentData().getCompound(KEY).copy();
        if(saved.contains("output100",10)){
            var batch=ProductionLedger.readBatch(saved.getCompound("output100"));
            var portion=outputBatch(saved,item.getCount(),consume);
            if(portion==null)return;
            item.getOrCreateTag().put(ProductionTracker.PENDING,ProductionLedger.writeBatch(portion));
            item.getOrCreateTag().putInt(FoodVariantIdentity.BATCH_COUNT,batch.count());
            if(method(be).equals("kaleidoscope_tavern:barrel")&&batch.contributors().size()==1
                    &&be.getLevel() instanceof net.minecraft.server.level.ServerLevel server)
                com.chi__you.cuisinejournal.progression.CookingRewards.stampProduced(server.getServer(),item,batch.contributors().iterator().next(),batch.count());
            if(consume)save(be,saved);
        }
        Object raw=HostReflection.field(be,"output");
        if(raw instanceof net.minecraftforge.items.IItemHandler handler&&!handler.getStackInSlot(0).isEmpty()){
            ItemStack template=handler.getStackInSlot(0);
            if(template.hasTag()&&template.getTag().contains(IngredientProvenance.KEY))
                item.getOrCreateTag().put(IngredientProvenance.KEY,template.getTag().get(IngredientProvenance.KEY).copy());
        }
        if(saved.contains("sources",9))item.getOrCreateTag().put(IngredientProvenance.KEY,saved.getList("sources",8).copy());
    }
    public static void claim(net.minecraft.world.entity.LivingEntity entity,ItemStack item) {
        var frames=FRAMES.get();
        if(!frames.isEmpty()&&"kaleidoscope_tavern:pressing_tub".equals(method(frames.peek().be)))
            net.minecraftforge.fluids.FluidUtil.getFluidHandler(item).ifPresent(handler->{
                if(handler.getTanks()>0&&!handler.getFluidInTank(0).isEmpty())fluidOutput(frames.peek().be,item);
            });
        prepareDelivery(entity,item);
        item.removeTagKey(INPUT_OWNERS);
    }
    public static void prepareResult(BlockEntity be){
        int status=com.chi__you.cuisinejournal.progression.CookingSpeed.number(be,"status");
        if(method(be).endsWith(":pot")&&status!=2||method(be).endsWith(":stockpot")&&status!=3)return;
        if(method(be).endsWith(":teapot")&&status!=2)return;
        Object raw=HostReflection.field(be,"result");
        if(!(raw instanceof ItemStack result)||!result.hasTag()||!result.getTag().contains(ProductionTracker.PENDING))return;
        var batch=ProductionLedger.readBatch(result.getTag().getCompound(ProductionTracker.PENDING));
        if(batch.contributors().size()==1&&be.getLevel() instanceof net.minecraft.server.level.ServerLevel server)
            com.chi__you.cuisinejournal.progression.CookingRewards.stampProduced(server.getServer(),result,batch.contributors().iterator().next(),batch.count());
    }
    public static void prepareDelivery(net.minecraft.world.entity.LivingEntity entity,ItemStack item) {
        if(!(entity instanceof ServerPlayer player)||!item.hasTag()||!item.getTag().contains(ProductionTracker.PENDING))return;
        var batch=ProductionLedger.readBatch(item.getTag().getCompound(ProductionTracker.PENDING));
        if(batch.method().equals("kaleidoscope_grilling:grilling"))return;
        if(!batch.method().startsWith("kaleidoscope_")&&!(batch.method().equals("minecraft:campfire_cooking")&&IngredientProvenance.sources(item)!=null))return;
        item.removeTagKey(ProductionTracker.PENDING);
        if(batch.belongsTo(player.getUUID()))com.chi__you.cuisinejournal.progression.CookingRewards.stampOutput(player,item,true,false,batch.count());
        ProductionTracker.claimBatch(player,item,batch,CulinaryCredit.Source.INTERACTION);
    }
    public record InventoryDelivery(ProductionLedger.Batch batch,ItemStack marked,int count){}
    public static InventoryDelivery inventoryBegin(net.minecraft.world.entity.player.Player entity,ItemStack item){
        if(!(entity instanceof ServerPlayer player)||!item.hasTag()||!item.getTag().contains(ProductionTracker.PENDING))return null;
        var batch=ProductionLedger.readBatch(item.getTag().getCompound(ProductionTracker.PENDING));
        if(!batch.method().startsWith("kaleidoscope_")&&!(batch.method().equals("minecraft:campfire_cooking")&&IngredientProvenance.sources(item)!=null))return null;
        item.removeTagKey(ProductionTracker.PENDING);
        if(batch.belongsTo(player.getUUID()))com.chi__you.cuisinejournal.progression.CookingRewards.stampOutput(player,item,true,false,batch.count());
        return new InventoryDelivery(batch,item.copy(),item.getCount());
    }
    public static void inventoryEnd(net.minecraft.world.entity.player.Player entity,ItemStack remainder,InventoryDelivery delivery){
        if(delivery==null||!(entity instanceof ServerPlayer player))return;
        int accepted=Math.max(0,delivery.count-remainder.getCount());
        if(accepted>0)ProductionTracker.claimBatch(player,delivery.marked.copyWithCount(accepted),delivery.batch,CulinaryCredit.Source.INTERACTION);
        if(!remainder.isEmpty())ProductionTracker.pending(remainder,delivery.batch);
    }
    private static void field(Object target,String name,Object value){
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{
            var field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);return;
        }catch(NoSuchFieldException ignored){}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    public static void fluidSource(BlockEntity be,ItemStack input) {
        if(be==null||input.isEmpty()||be.getLevel()==null||be.getLevel().isClientSide)return;
        var saved=be.getPersistentData().getCompound(KEY).copy();var list=new net.minecraft.nbt.ListTag();
        list.add(net.minecraft.nbt.StringTag.valueOf(BuiltInRegistries.ITEM.getKey(input.getItem()).toString()));
        var inherited=IngredientProvenance.sources(input);if(inherited!=null)for(String id:inherited){var value=net.minecraft.nbt.StringTag.valueOf(id);if(!list.contains(value))list.add(value);}
        saved.put("fluidSources",list);
        var owners=new net.minecraft.nbt.ListTag();ServerPlayer actor=ProductionTracker.actor();
        if(actor!=null){var owner=new CompoundTag();owner.putUUID("id",actor.getUUID());owners.add(owner);}
        saved.put("fluidOwners",owners);be.getPersistentData().put(KEY,saved);be.setChanged();
    }
    public static void markCopy(BlockEntity be,ItemStack copy){
        if(copy.isEmpty())return;
        Object raw=HostReflection.field(be,"result");
        if(raw instanceof ItemStack template)com.chi__you.cuisinejournal.food.FoodVariantIdentity.copyKeys(template,copy,
                com.chi__you.cuisinejournal.food.FoodVariantIdentity.PENDING_OUTPUT);
    }
    public static void fluidOutput(BlockEntity be,ItemStack copy){
        if(copy.isEmpty())return;var saved=be.getPersistentData().getCompound(KEY);
        if(saved.contains("fluidSources",9))copy.getOrCreateTag().put(IngredientProvenance.KEY,saved.getList("fluidSources",8).copy());
    }
    public static void clearFluid(BlockEntity be){
        var saved=be.getPersistentData().getCompound(KEY).copy();saved.remove("fluidSources");saved.remove("fluidOwners");save(be,saved);
    }
    public static void prepareTeapotItem(ItemStack pot,ItemStack result){
        var actor=ProductionTracker.actor();
        if(actor==null||result.isEmpty()||!result.hasTag()||!result.getTag().contains(ProductionTracker.PENDING))return;
        var batch=ProductionLedger.readBatch(result.getTag().getCompound(ProductionTracker.PENDING));
        if(batch.contributors().size()!=1)return;
        com.chi__you.cuisinejournal.progression.CookingRewards.stampProduced(actor.server,result,batch.contributors().iterator().next(),batch.count());
        var block=net.minecraft.world.item.BlockItem.getBlockEntityData(pot);
        if(block!=null&&com.chi__you.cuisinejournal.culinary.MealStamp.isStamped(result))block.put("Result",result.save(new CompoundTag()));
    }
    private static boolean matches(BlockEntity be,ItemStack input,ItemStack output){
        for(var recipe:be.getLevel().getRecipeManager().getRecipes()){
            var type=BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());if(type==null)continue;
            boolean typeMatches=type.toString().equals(method(be))||method(be).equals("kaleidoscope_chinesefood:freezing")&&type.toString().equals("kaleidoscope_chinesefood:refrigerating");
            if(!typeMatches)continue;
            if(!com.chi__you.cuisinejournal.recipe.FluidRecipeBridge.inputs(recipe).stream().anyMatch(ingredient->ingredient.test(input)))continue;
            if(com.chi__you.cuisinejournal.recipe.RecipeClassifier.outputs(recipe,be.getLevel().registryAccess()).stream().anyMatch(item->ItemStack.isSameItem(item,output)))return true;
        }return false;
    }
}
