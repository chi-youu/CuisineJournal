package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import com.chi__you.cuisinejournal.event.CulinaryHooks;
import com.chi__you.cuisinejournal.tracking.CulinaryCredit;
import com.chi__you.cuisinejournal.tracking.IceBucketPours;
import com.chi__you.cuisinejournal.tracking.PlacedMealProduction;
import com.chi__you.cuisinejournal.tracking.ProductionLedger;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import com.chi__you.cuisinejournal.tracking.VerifiedWorkstations;
import com.chi__you.cuisinejournal.culinary.MealStamp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Set;
import java.util.function.Supplier;

/** Verified direct-eating families carry identity through host calls; arbitrary hunger changes are ignored. */
public final class PlacedFoodConsumption {
    private static final String[] MELONS={"net.brnbrd.delightful.common.block.SlicedMelonBlock",
            "net.brnbrd.delightful.common.block.SlicedMiniMelonBlock","net.brnbrd.delightful.common.block.SlicedGourdBlock"};
    private static final String[] PIES={"net.donne431.ice_and_fire_delight.block.DragonSpecialPieBlock",
            "net.donne431.ice_and_fire_delight.block.ElectricPieBlock","net.donne431.ice_and_fire_delight.block.FieryHotPieBlock",
            "net.donne431.ice_and_fire_delight.block.FrostPieBlock"};
    private static final String NETHER_PIZZA="net.jadenxgamer.netherexp.registry.block.custom.NetherPizzaBlock";
    /** Trail & Tales ice cream: mix poured into a bucket, frozen, then served from the bucket. */
    private static final String ICE_BUCKET="show.tatd.mod.block.ice_cream.IceBucketBlock";
    private static final String ICE_CREAM_BUCKET="show.tatd.mod.block.ice_cream.IceCreamBucketBlock";
    private static final String ICE_CREAM_METHOD="trailandtales_delight:ice_cream";
    private static final String ICE_CREAM_TYPE="ice_cream_type";
    private static final ConsumptionScope<Frame> FRAMES=new ConsumptionScope<>();
    private static final ThreadLocal<Interaction> INTERACTION=new ThreadLocal<>();
    private record Interaction(Level level,BlockPos pos,BlockState state) {}
    private static final class IceServing {
        final ServerLevel level;
        final BlockPos pos;
        final BlockState state;
        final IceBucketPours.Pour pour;
        final Player player;
        final ItemStack held;
        final int heldCount;
        final java.util.Set<ItemStack> delivered = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        IceServing(ServerLevel level, BlockPos pos, BlockState state, Player player, InteractionHand hand) {
            this.level=level; this.pos=pos; this.state=state; this.player=player;
            this.pour=IceBucketPours.peek(level,pos);
            this.held=player.getItemInHand(hand); this.heldCount=held.getCount();
        }
    }
    private static final ConsumptionScope<IceServing> ICE_SERVINGS = new ConsumptionScope<>();
    private static final class Frame {
        final Player player; final ItemStack food;
        boolean observed;
        Frame(Player player,ItemStack food) {
            this.player=player;this.food=food.isEmpty()?ItemStack.EMPTY:food.copyWithCount(1);
        }
    }
    private PlacedFoodConsumption() {}

    public static InteractionResult use(Block block,BlockState state,Level level,BlockPos pos,
                                         Player player,InteractionHand hand,BlockHitResult hit) {
        if(com.chi__you.cuisinejournal.tracking.LetsDoWorldWork.supports(state))return com.chi__you.cuisinejournal.tracking.LetsDoWorldWork.use(
                level,pos,state,player,player.getItemInHand(hand),()->block.use(state,level,pos,player,hand,hit));
        if(KaleidoscopePlacedFoods.known(block)){
            var origin=KaleidoscopePlacedFoods.origin(pos,state);
            if(!origin.equals(pos)&&level.getBlockState(origin).getBlock()==block)
                return use(block,level.getBlockState(origin),level,origin,player,hand,hit);
        }
        if(com.chi__you.cuisinejournal.tracking.KaleidoscopeServings.supports(state))
            return com.chi__you.cuisinejournal.tracking.KaleidoscopeServings.use(level,pos,state,player.getItemInHand(hand),()->block.use(state,level,pos,player,hand,hit));
        if(AbnormalsPlacedFoods.is(block,AbnormalsPlacedFoods.CANDLE,AbnormalsPlacedFoods.CUPRIC,"club.someoneice.pineapple.common.BlockPineappleCandleCake")
                &&player.getItemInHand(hand).isEmpty()&&hit.getLocation().y-pos.getY()>.5){
            var lit=block.getStateDefinition().getProperty("lit");
            if(lit instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag&&state.getValue(flag))
                return block.use(state,level,pos,player,hand,hit);
        }
        if(com.chi__you.cuisinejournal.tracking.MilkshakeCauldronProduction.supports(state))
            return com.chi__you.cuisinejournal.tracking.MilkshakeCauldronProduction.use(level,pos,state,player,hand,()->block.use(state,level,pos,player,hand,hit));
        var creamPot=com.chi__you.cuisinejournal.compat.UnificationDairy.legacyCreamPot(state,level,pos,player,hand,hit);
        if(creamPot!=null)return creamPot;
        if(com.chi__you.cuisinejournal.tracking.BrazilPotProduction.supports(state))
            return com.chi__you.cuisinejournal.tracking.BrazilPotProduction.use(level,pos,state,player,hand,()->block.use(state,level,pos,player,hand,hit));
        if(is(block,com.chi__you.cuisinejournal.tracking.CheeseMoldProduction.HOST))
            return com.chi__you.cuisinejournal.tracking.CheeseMoldProduction.use(level,pos,state,player,player.getItemInHand(hand),()->block.use(state,level,pos,player,hand,hit));
        if(is(block,DelightPlacedFoods.HOGLIN)&&state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)==net.minecraft.world.level.block.state.properties.BedPart.FOOT){
            BlockPos head=pos.relative(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).getOpposite());
            var headState=level.getBlockState(head);
            if(headState.getBlock()==block)return use(block,headState,level,head,player,hand,hit);
        }
        if(ICE_BUCKET.equals(block.getClass().getName()))
            return iceBucketUse(block,state,level,pos,player,hand,hit);
        if(ICE_CREAM_BUCKET.equals(block.getClass().getName()) && level instanceof ServerLevel server)
            return ICE_SERVINGS.run(new IceServing(server,pos,state,player,hand),
                    ignored -> block.use(state,level,pos,player,hand,hit));
        ItemStack food=ItemStack.EMPTY;
        boolean active=level.isClientSide?CuisineFoodClientState.active():CuisineFoodRuntime.active();
        if(!active&&!known(block))return block.use(state,level,pos,player,hand,hit);
        try {
            if(is(block,DelightPlacedFoods.UNIVERSAL_LEAF)&&player.isShiftKeyDown()&&player.getItemInHand(hand).isEmpty()){
                var be=level.getBlockEntity(pos);
                for(int slot=5;slot>=0;slot--){var candidate=be==null?ItemStack.EMPTY:VerifiedWorkstations.stack(be,slot);if(!candidate.isEmpty()){food=candidate.copyWithCount(1);break;}}
            }else if(CustomPlacedFoods.table(block))food=CustomPlacedFoods.table(level,pos,player.getItemInHand(hand),player.isShiftKeyDown());
            else if(DelightPlacedFoods.is(block,LetsDoPlacedFoods.TABLE))food=LetsDoPlacedFoods.table(level,pos,state,player.getItemInHand(hand),player.isShiftKeyDown());
            else food=resolve(block,state,player,player.getItemInHand(hand),player.isShiftKeyDown());
        }
        catch(RuntimeException|LinkageError failure) {
            com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Cannot resolve placed food {}",BuiltInRegistries.BLOCK.getKey(block),failure);
            // A confirmed direct-eating family must not silently bypass food-slot limits.
            return InteractionResult.FAIL;
        }
        if(food.isEmpty()&&FRAMES.current()==null) {
            if(!known(block))return block.use(state,level,pos,player,hand,hit);
            INTERACTION.set(new Interaction(level,pos,state));
            try { return block.use(state,level,pos,player,hand,hit); }
            finally { PlacedMealProduction.transition(level,pos,state); INTERACTION.remove(); }
        }
        // Empty cleanup, slicing, filling and decorative interactions do not enter an eating context.
        if (!level.isClientSide && !food.isEmpty()) PlacedMealProduction.stampOutput(level,pos,state,food);
        if(LetsDoPlacedFoods.known(block)&&!DelightPlacedFoods.is(block,LetsDoPlacedFoods.TABLE)&&!food.isEmpty())
            food=com.chi__you.cuisinejournal.tracking.PlacedFoodVariants.readServing(level,pos,food);
        else if(KaleidoscopePlacedFoods.known(block)&&!food.isEmpty())
            food=com.chi__you.cuisinejournal.tracking.PlacedFoodVariants.read(level,pos,food);
        if(LetsDoPlacedFoods.known(block)&&!food.isEmpty()&&level.getBlockEntity(pos)!=null){
            Object nativeEffects=HostReflection.invoke(level.getBlockEntity(pos),"getEffects");
            if(nativeEffects instanceof java.util.List<?> list){var rows=new net.minecraft.nbt.ListTag();
                for(var raw:list)if(raw instanceof com.mojang.datafixers.util.Pair<?,?> pair&&pair.getFirst() instanceof net.minecraft.world.effect.MobEffectInstance effect){
                    var row=new net.minecraft.nbt.CompoundTag();row.putShort("id",(short)net.minecraft.world.effect.MobEffect.getId(effect.getEffect()));
                    row.putInt("duration",effect.getDuration());row.putInt("amplifier",effect.getAmplifier());row.putFloat("chance",1);rows.add(row);
                }
                food.getOrCreateTag().put("StoredEffects",rows);
            }
        }
        Frame frame=new Frame(player,food);
        if(active&&!food.isEmpty()) {
            // Direct block eating has no use timer and cannot pay the digestion healing cost.
            if(CuisineEffectAdapters.digesting(player) && (level.isClientSide
                    ? !CuisineFoodClientState.ordinaryAccepts(food)
                    : player instanceof ServerPlayer server && (CuisineFoodService.isDigestedUse(server,food)
                            || CuisineFoodService.isTemporaryUse(server,food))))return InteractionResult.FAIL;
            boolean allowed=level.isClientSide?CuisineFoodClientState.canConsume(food)
                    :player instanceof ServerPlayer server&&CuisineFoodService.canConsume(server,food);
            if(!allowed)return InteractionResult.FAIL;
        }
        InteractionResult result;
if(KaleidoscopePlacedFoods.known(block)||LetsDoPlacedFoods.known(block)||CustomPlacedFoods.known(block)||CakeFoodCompat.cake(block)){
            FoodUseContext.begin(frame.food);
            CuisineEffectAdapters.beginNativeFood(frame.food,player);
            try{result=FRAMES.run(frame,ignored->block.use(state,level,pos,player,hand,hit));}
            finally{try{GrillingFoodCompat.finishHot(frame.food,player);CuisineEffectAdapters.endNativeFood();}finally{FoodUseContext.end();}}
        }else result=FRAMES.run(frame,ignored->block.use(state,level,pos,player,hand,hit));
        // Our effect reconciliation is outside the host window so it is never suppressed as a native effect.
        if(!food.isEmpty()&&frame.observed&&result.consumesAction()&&!level.isClientSide
                &&(!level.getBlockState(pos).equals(state)||CustomPlacedFoods.table(block)
                    &&!ItemStack.isSameItem(VerifiedWorkstations.stack(level.getBlockEntity(pos),0),frame.food)||DelightPlacedFoods.is(block,LetsDoPlacedFoods.TABLE)
                    &&VerifiedWorkstations.stack(level.getBlockEntity(pos),0).isEmpty())&&player instanceof ServerPlayer server
                &&(!active||CuisineFoodService.consume(server,frame.food))) {
            PlacedMealProduction.stampOutput(level,pos,state,frame.food);
            PlacedMealProduction.claim(level,pos,state,server,frame.food,false);
            CulinaryHooks.onItemEaten(server,frame.food);
            if (active) {
                CuisineFoodEffects.onConsumed(server);
                CuisineFoodSync.send(server);
            } else CuisineFlavorEffects.applyVanilla(server,frame.food);
        }
        PlacedMealProduction.transition(level,pos,state);
        return result;
    }

    public static void itemEntitySpawned(ItemEntity entity) {
        com.chi__you.cuisinejournal.tracking.LetsDoWorldWork.output(entity.getItem());
        com.chi__you.cuisinejournal.tracking.KaleidoscopeServings.output(entity.getItem());
        com.chi__you.cuisinejournal.tracking.BrazilPotProduction.output(entity);
        com.chi__you.cuisinejournal.tracking.CheeseMoldProduction.output(entity);
        IceServing ice=ICE_SERVINGS.current();
        if (ice!=null && !entity.level().isClientSide) iceCreamOutput(ice.player,entity.getItem());
        Interaction interaction=INTERACTION.get();
        if(interaction==null||entity.level().isClientSide)return;
        if(LetsDoPlacedFoods.known(interaction.state.getBlock())&&LetsDoPlacedFoods.output(interaction.state.getBlock(),interaction.state,entity.getItem())){var item=entity.getItem();
            var inherited=com.chi__you.cuisinejournal.tracking.PlacedFoodVariants.readServing(interaction.level,interaction.pos,interaction.state,item);
            item.setTag(inherited.getTag());
        }
        PlacedMealProduction.transferOutput(interaction.level,interaction.pos,interaction.state,entity.getItem());
    }

    public static void inventoryOutput(ItemStack stack) {
        com.chi__you.cuisinejournal.tracking.LetsDoWorldWork.output(stack);
        com.chi__you.cuisinejournal.tracking.KaleidoscopeServings.output(stack);
        IceServing ice=ICE_SERVINGS.current();
        if (ice!=null) iceCreamOutput(ice.player,stack);
        Interaction interaction=INTERACTION.get();
        if(interaction==null||stack.isEmpty())return;
        if(LetsDoPlacedFoods.known(interaction.state.getBlock())&&LetsDoPlacedFoods.output(interaction.state.getBlock(),interaction.state,stack)){
            var inherited=com.chi__you.cuisinejournal.tracking.PlacedFoodVariants.readServing(interaction.level,interaction.pos,interaction.state,stack);stack.setTag(inherited.getTag());
        }
        PlacedMealProduction.stampOutput(interaction.level,interaction.pos,interaction.state,stack);
    }

    public static void inventoryOutputDone(ItemStack stack,int amount) {
        Interaction interaction=INTERACTION.get();
        if(interaction==null||stack.isEmpty()||amount<=0)return;
        PlacedMealProduction.transferOutput(interaction.level,interaction.pos,interaction.state,stack,amount);
    }

    public static void placed(Level level,BlockPos pos,BlockState state,ItemStack stack) {
        if(!level.isClientSide&&LetsDoPlacedFoods.known(state.getBlock()))
            com.chi__you.cuisinejournal.tracking.PlacedFoodVariants.store(level,pos,stack);
        if(!level.isClientSide&&FarmersSpellFoods.multiblock(state.getBlock())){
            for(var part:FarmersSpellFoods.positions(pos,state)){
                var actual=level.getBlockState(part);
                if(actual.getBlock()==state.getBlock())PlacedMealProduction.storeStamped(level,part,actual,stack,FarmersSpellFoods.remaining(actual));
            }
            return;
        }
        if(!level.isClientSide&&com.chi__you.cuisinejournal.tracking.KaleidoscopeServings.plate(state))
            com.chi__you.cuisinejournal.tracking.PlacedFoodVariants.store(level,pos,stack);
        if(!level.isClientSide&&com.chi__you.cuisinejournal.compat.HostClasses.matches(stack.getItem(),"com.github.ysbbbbbb.kaleidoscopecookery.item.TeacupItem"))
            com.chi__you.cuisinejournal.tracking.PlacedFoodVariants.addCup(level,pos,stack,0);
        if(!level.isClientSide&&KaleidoscopePlacedFoods.known(state.getBlock()))
            com.chi__you.cuisinejournal.tracking.PlacedFoodVariants.store(level,pos,stack);
        if(level.isClientSide||stack.isEmpty()||MealStamp.read(stack)==null||!known(state.getBlock()))return;
        PlacedMealProduction.storeStamped(level,pos,state,stack,remaining(state));
    }

    private static int remaining(BlockState state) {
        if(CustomPlacedFoods.known(state.getBlock()))return CustomPlacedFoods.remaining(state);
        if(LetsDoPlacedFoods.known(state.getBlock()))return LetsDoPlacedFoods.remaining(state);
        if(AbnormalsPlacedFoods.known(state.getBlock()))return AbnormalsPlacedFoods.remaining(state);
        if(DelightPlacedFoods.known(state.getBlock()))return DelightPlacedFoods.remaining(state);
        if(CakeFoodCompat.cake(state.getBlock()))return CakeFoodCompat.remaining(state);
        for(var property:state.getProperties()) if(property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty integer
                &&(property.getName().equals("bites")||property.getName().equals("omnomnom"))) {
            int max=integer.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
            return Math.max(1,max-state.getValue(integer));
        }
        return 1;
    }

    private static boolean known(Block block) {
        if(CustomPlacedFoods.known(block))return true;
        return CakeFoodCompat.cake(block)||LetsDoPlacedFoods.known(block)||KaleidoscopePlacedFoods.known(block)||AbnormalsPlacedFoods.known(block)||DelightPlacedFoods.known(block)||is(block,MELONS)||is(block,PIES)||is(block,
                "io.github.rcneg.legendarydelicacies.blocks.CommonCakeBlock","com.starmeow.smc.blocks.CommonCakeBlock",
                "com.aizistral.enigmaticlegacy.blocks.BlockCosmicCake","net.minecraft.world.level.block.CakeBlock",
                NETHER_PIZZA,
                "com.renyigesai.immortalers_delight.block.food.ZeaPancakeBLock",
                "com.renyigesai.immortalers_delight.block.food.SuperKwatBurgerBlock",
                "com.renyigesai.immortalers_delight.block.food.ScarletDevilsCakeBlock",
                "com.renyigesai.immortalers_delight.block.food.BraisedSpiderEyesBlock",
                "com.renyigesai.immortalers_delight.block.food.TartareChickenBigMealBlock",
                "com.renyigesai.immortalers_delight.block.StackedFoodBlock",
                "dev.xkmc.youkaishomecoming.content.block.food.BowlBlock",
                "net.yirmiri.dungeonsdelight.common.block.MonsterCakeBlock",
                "net.yirmiri.dungeonsdelight.common.block.SpiderDonutBlock",
                "twilightforest.block.Experiment115Block",
                ICE_CREAM_BUCKET,
                "dev.xkmc.fruitsdelight.content.block.MangosteenCakeBlock");
    }

    /** Maps a placed-food block item to the serving item used by the journal. */
    public static ItemStack identityForProduction(ItemStack source) {
        if(source==null||source.isEmpty())return ItemStack.EMPTY;
        if(!(source.getItem() instanceof BlockItem blockItem))return source.copyWithCount(1);
        if(LetsDoPlacedFoods.known(blockItem.getBlock())){var serving=LetsDoPlacedFoods.serving(blockItem.getBlock(),blockItem.getBlock().defaultBlockState());return serving.isEmpty()?source.copyWithCount(1):serving;}
        if(AbnormalsPlacedFoods.known(blockItem.getBlock()))return AbnormalsPlacedFoods.serving(blockItem.getBlock());
        if(DelightPlacedFoods.known(blockItem.getBlock())){
            ItemStack portion=DelightPlacedFoods.serving(blockItem.getBlock(),blockItem.getBlock().defaultBlockState());
            if(!portion.isEmpty())return portion.copyWithCount(1);
        }
        if(blockItem.getBlock().getClass().getName().equals(NETHER_PIZZA))
            return item("netherexp:nether_pizza_slice");
        try {
            ItemStack serving=resolve(blockItem.getBlock(),blockItem.getBlock().defaultBlockState(),null,
                    ItemStack.EMPTY,false);
            return serving.isEmpty()?source.copyWithCount(1):serving.copyWithCount(1);
        } catch(RuntimeException|LinkageError ignored) {
            return source.copyWithCount(1);
        }
    }

    /** Mark actual nutrition application before the valheim FoodData mixin suppresses it. */
    public static void foodApplied(FoodData data) {
        Frame frame=current();
        if(frame!=null&&!frame.food.isEmpty()&&frame.player.getFoodData()==data)frame.observed=true;
    }

    /** The item-bearing FoodData overload is used by several placed-food hosts. */
    public static void foodApplied(FoodData data, ItemStack food, Item item) {
        Frame frame=current();
        if(frame!=null&&!frame.food.isEmpty()&&frame.player.getFoodData()==data
                &&((food!=null&&!food.isEmpty()&&ItemStack.isSameItem(frame.food,food))
                ||(item!=null&&frame.food.is(item))))frame.observed=true;
    }
    /** Hosts may emit Forge Finish themselves; the enclosing transaction owns this one serving. */
    public static boolean finishEvent(Player player,ItemStack food) {
        Frame frame=current();
        if(frame==null||frame.player!=player||frame.food.isEmpty()
                ||!ItemStack.isSameItem(frame.food,food))return false;
        frame.observed=true;return true;
    }
    public static boolean blocksNativeEffect(LivingEntity entity) {
        Frame frame=current();
        return frame!=null&&frame.observed&&frame.player==entity&&!frame.food.isEmpty()
                &&!CuisineGameplayRules.nativeEffectsEnabled();
    }
    public static boolean handles(Player player) {
        Frame frame=current();return frame!=null&&frame.player==player&&!frame.food.isEmpty();
    }
    public static boolean procedureFood(FoodData data){
        Frame frame=current();if(frame==null||frame.player.getFoodData()!=data||frame.food.isEmpty())return false;
        String id=BuiltInRegistries.ITEM.getKey(frame.food.getItem()).toString();
        return id.equals("oceanic_delight:sponge_cake_slice")||id.equals("unusual_delight:glow_gummy");
    }
    private static Frame current(){return FRAMES.current();}
    public static ItemStack vanillaServing(FoodData data) {
        Frame frame=current();
        return frame!=null&&frame.player.getFoodData()==data?frame.food:ItemStack.EMPTY;
    }
    public static ItemStack qualityStack(ItemStack stack) {
        Frame frame=current();
        return frame!=null&&!frame.food.isEmpty()&&ItemStack.isSameItem(frame.food,stack)?frame.food:stack;
    }
    public static boolean eatingContext() { return current()!=null; }

    private static ItemStack resolve(Block block,BlockState state,Player player,ItemStack held,boolean sneaking) {
        if(CustomPlacedFoods.known(block))return CustomPlacedFoods.eating(block,state,held);
        if(LetsDoPlacedFoods.known(block))return LetsDoPlacedFoods.eating(block,state,held,sneaking);
        if(KaleidoscopePlacedFoods.known(block))return KaleidoscopePlacedFoods.serving(block,state);
        if(AbnormalsPlacedFoods.known(block)){
            if(is(block,AbnormalsPlacedFoods.TURKEY))return AbnormalsPlacedFoods.turkeyCutting(held)
                    ?ItemStack.EMPTY:AbnormalsPlacedFoods.serving(block);
            if(is(block,AbnormalsPlacedFoods.PANCAKE))return sneaking||held.is(block.asItem())?ItemStack.EMPTY:AbnormalsPlacedFoods.serving(block);
            if(held.is(Items.FLINT_AND_STEEL)||held.is(Items.FIRE_CHARGE)||held.is(ItemTags.CANDLES))return ItemStack.EMPTY;
            if(is(block,AbnormalsPlacedFoods.EXQUISITO)&&knife(held))return ItemStack.EMPTY;
            return AbnormalsPlacedFoods.serving(block);
        }
        if(DelightPlacedFoods.known(block)) {
            if(FarmersSpellFoods.multiblock(block))return ItemStack.EMPTY;
            var unusual=UnusualServingRelations.get(block);if(unusual!=null)return unusual.direct()?UnusualServingRelations.item(unusual.output()):ItemStack.EMPTY;
            if(is(block,DelightPlacedFoods.PANCAKE,DelightPlacedFoods.EGGPLANT,DelightPlacedFoods.DRINK,DelightPlacedFoods.HOGLIN,DelightPlacedFoods.UNIVERSAL_LEAF,DelightPlacedFoods.CAN,DelightPlacedFoods.TOAST,DelightPlacedFoods.POPCORN,DelightPlacedFoods.FD_FEAST))return ItemStack.EMPTY;
            if(is(block,DelightPlacedFoods.EGG))return value(state,"bites")>0?DelightPlacedFoods.serving(block,state):ItemStack.EMPTY;
            if(is(block,DelightPlacedFoods.RICE))return sneaking&&value(state,"slice")>1?DelightPlacedFoods.serving(block,state):ItemStack.EMPTY;
            if(is(block,DelightPlacedFoods.BRAZIL_FEAST)&&Boolean.TRUE.equals(HostReflection.field(block,"requireServing")))return ItemStack.EMPTY;
            if(is(block,DelightPlacedFoods.LEAF,DelightPlacedFoods.LARGE_LEAF))return sneaking&&held.isEmpty()?DelightPlacedFoods.serving(block,state):ItemStack.EMPTY;
            if(knife(held)||held.is(Items.FLINT_AND_STEEL)||held.is(Items.FIRE_CHARGE))return ItemStack.EMPTY;
            if(is(block,DelightPlacedFoods.MAGMA)&&held.is(block.asItem()))return ItemStack.EMPTY;
            if(value(state,"bites")==0&&held.is(ItemTags.CANDLES))return ItemStack.EMPTY;
            return DelightPlacedFoods.serving(block,state);
        }
        if(is(block,MELONS)) {
            if(knife(held))return ItemStack.EMPTY;
            // The host falls back to eating when optional melon juicing is disabled.
            if(!block.getClass().getName().endsWith("SlicedGourdBlock")&&held.is(Items.GLASS_BOTTLE)
                    &&melonJuicing())return ItemStack.EMPTY;
            return methodStack(block,"getSliceItem");
        }
        if(is(block,PIES)||is(block,"vectorwing.farmersdelight.common.block.PieBlock",
                "com.scouter.silentsdelight.blocks.PieBaseBlockEntity","com.starmeow.smc.blocks.BasePieBlock",
                "womp.tinfoilknight.aquamirae_delight.blocks.BreadFoodBlock"))
            return knife(held)?ItemStack.EMPTY:methodStack(block,"getPieSliceItem");
        if(is(block,"io.github.rcneg.legendarydelicacies.blocks.CommonCakeBlock","com.starmeow.smc.blocks.CommonCakeBlock")) {
            if(knife(held)||value(state,"bites")==0&&held.is(ItemTags.CANDLES))return ItemStack.EMPTY;
            return supplierStack(block,"sliceItem");
        }
        if(is(block,"com.aizistral.enigmaticlegacy.blocks.BlockCosmicCake")) {
            if(value(state,"bites")>=6)return ItemStack.EMPTY;
            return item("enigmaticdelicacy:cosmic_cake_slice");
        }
        if(is(block,"com.renyigesai.immortalers_delight.block.food.ZeaPancakeBLock")) {
            if(knife(held)||held.is(ItemTags.create(ResourceLocation.parse("immortalers_delight:tools/immortal_knives"))))return ItemStack.EMPTY;
            return value(state,"bites")>=4?ItemStack.EMPTY:item("immortalers_delight:zea_pancake_slice");
        }
        if(is(block,"com.renyigesai.immortalers_delight.block.food.SuperKwatBurgerBlock"))
            return value(state,"bites")>=4?ItemStack.EMPTY:item("immortalers_delight:super_kwat_wheat_hamburger");
        if(is(block,"com.renyigesai.immortalers_delight.block.food.ScarletDevilsCakeBlock")) {
            if(knife(held)||held.is(ItemTags.create(ResourceLocation.parse("immortalers_delight:tools/immortal_knives"))))return ItemStack.EMPTY;
            return value(state,"bites")>=8?ItemStack.EMPTY:item("immortalers_delight:scarlet_devils_cake_slice");
        }
        if(is(block,"com.renyigesai.immortalers_delight.block.food.BraisedSpiderEyesBlock"))
            return held.is(Items.BOWL)||value(state,"bites")>=4?ItemStack.EMPTY:item("immortalers_delight:braised_spider_eyes_in_gravy");
        if(is(block,"com.renyigesai.immortalers_delight.block.food.TartareChickenBigMealBlock"))
            return held.is(Items.BOWL)?ItemStack.EMPTY:item("immortalers_delight:tartare_chicken");
        if(is(block,"com.renyigesai.immortalers_delight.block.StackedFoodBlock")) {
            if(!Boolean.TRUE.equals(HostReflection.invoke(block,"isEdible")))return ItemStack.EMPTY;
            ItemStack pile=supplierStack(block,"pileItem");
            if(held.is(pile.getItem())||Boolean.TRUE.equals(HostReflection.invoke(block,"isCuttable",held)))return ItemStack.EMPTY;
            Object maximum=HostReflection.invoke(block,"getMaxBites");
            if(!(maximum instanceof Integer count))throw new IllegalStateException("Missing stacked food bite limit");
            return value(state,"bites")>=count?ItemStack.EMPTY:supplierStack(block,"servingItem");
        }
        if(is(block,"dev.xkmc.youkaishomecoming.content.block.food.BowlBlock")) {
            if(!held.isEmpty()&&!"youkaishomecoming:big_spoon".equals(
                    BuiltInRegistries.ITEM.getKey(held.getItem()).toString()))return ItemStack.EMPTY;
            ItemStack serving=fieldItemStack(block,"food");
            return serving.isEmpty()||!CuisineEdibility.isFood(serving.getFoodProperties(player))
                    ?ItemStack.EMPTY:serving;
        }
        if(is(block,"net.yirmiri.dungeonsdelight.common.block.MonsterCakeBlock")) {
            if(knife(held)||value(state,"bites")==0&&held.is(item("dungeonsdelight:living_candle").getItem()))return ItemStack.EMPTY;
            return item("dungeonsdelight:monster_cake_slice");
        }
        if(is(block,"net.yirmiri.dungeonsdelight.common.block.SpiderDonutBlock"))
            return held.is(block.asItem())?ItemStack.EMPTY:new ItemStack(block.asItem());
        if(is(block,"twilightforest.block.Experiment115Block")) {
            int bites=value(state,"omnomnom");
            var property=state.getBlock().getStateDefinition().getProperty("regenerate");
            boolean regenerate=property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag&&state.getValue(flag);
            if(sneaking&&!regenerate||!sneaking&&bites>0&&held.is(item("twilightforest:experiment_115").getItem())
                    ||!sneaking&&bites==0&&!regenerate&&held.is(Items.REDSTONE))return ItemStack.EMPTY;
            return item("twilightforest:experiment_115");
        }
        if(is(block,"dev.xkmc.fruitsdelight.content.block.MangosteenCakeBlock"))
            return value(state,"bites")<=0?ItemStack.EMPTY:new ItemStack(block.asItem());
        if(CakeFoodCompat.cake(block))return CakeFoodCompat.eating(block,state,held);
        return ItemStack.EMPTY;
    }
    private static int value(BlockState state,String name) {
        var property=state.getBlock().getStateDefinition().getProperty(name);
        return property instanceof net.minecraft.world.level.block.state.properties.IntegerProperty integer?state.getValue(integer):0;
    }
    /** Wraps the pour so the mix leaves a cook record; taking the mix back clears it. */
    private static InteractionResult iceBucketUse(Block block,BlockState state,Level level,BlockPos pos,
                                                  Player player,InteractionHand hand,BlockHitResult hit) {
        ItemStack held=player.getItemInHand(hand).copyWithCount(1);
        int before=value(state,ICE_CREAM_TYPE);
        InteractionResult result=block.use(state,level,pos,player,hand,hit);
        if(!level.isClientSide&&result.consumesAction()&&level instanceof ServerLevel server) {
            BlockState after=level.getBlockState(pos);
            if(after.getBlock()==block) {
                int type=value(after,ICE_CREAM_TYPE);
                var rule = com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.get(
                        ResourceLocation.parse("trailandtales_delight:ice_bucket"));
                if(before==0&&type>0) {
                    IceBucketPours.forget(server,pos);
                    if (rule == null || rule.enabled() && rule.capabilities().contains(
                            com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.Capability.ITEM_INPUT))
                        IceBucketPours.record(server,pos,player.getUUID(),
                                player.getGameProfile().getName(),BuiltInRegistries.ITEM.getKey(held.getItem()));
                }
                else if(before>0&&type==0) IceBucketPours.forget(server,pos);
            }
        }
        return result;
    }
    /** Called after FeastBlock consumes a serving, before insertion or fallback drop. */
    public static void iceCreamOutput(Player player, ItemStack output) {
        IceServing frame = ICE_SERVINGS.current();
        if (frame == null || !(player instanceof ServerPlayer server) || output.isEmpty()) return;
        if (frame.delivered.contains(output)) return;
        var pour = frame.pour;
        Object actual = HostReflection.invoke(frame.state.getBlock(),"getServingItem",frame.state);
        if (!(actual instanceof ItemStack serving) || !ItemStack.isSameItem(serving,output)) return;
        frame.delivered.add(output);
        var owners = new java.util.HashSet<java.util.UUID>();
        if (pour != null && pour.remaining() > 0
                && pour.frozen().equals(BuiltInRegistries.BLOCK.getKey(frame.state.getBlock()).toString()))
            owners.add(pour.player());
        // An accepted, consumed serving container contributes, like the other stations.
        if (!owners.isEmpty() && frame.held.getCount()<frame.heldCount && !player.isCreative()) owners.add(player.getUUID());
        var rule = com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.get(ResourceLocation.parse("trailandtales_delight:ice_bucket"));
        if (rule != null && (!rule.enabled() || !rule.capabilities().contains(
                com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.Capability.RIGHT_CLICK_TAKE))) owners.clear();
        ProductionTracker.claimBatch(server, output, new ProductionLedger.Batch(
                BuiltInRegistries.ITEM.getKey(output.getItem()).toString(),output.getCount(),owners,
                pour == null || pour.mix() == null ? Set.of() : Set.of(pour.mix().toString()),ICE_CREAM_METHOD),
                CulinaryCredit.Source.INTERACTION);
        if (pour != null) IceBucketPours.claimed(frame.level,frame.pos,pour,output.getCount());
    }
    public static void iceCreamSpoonOutput(ItemStack food) {
        IceServing frame=ICE_SERVINGS.current();
        if (frame!=null) iceCreamOutput(frame.player,food);
    }
    private static boolean melonJuicing() {
        try {
            Object config=Class.forName("net.brnbrd.delightful.common.DelightfulConfig").getField("MELON_JUICING").get(null);
            return Boolean.TRUE.equals(HostReflection.invoke(config,"get"));
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Missing melon juicing config",failure);}
    }
    private static boolean knife(ItemStack stack){return stack.is(ItemTags.create(ResourceLocation.parse("farmersdelight:tools/knives")))
            ||stack.is(ItemTags.create(ResourceLocation.parse("forge:tools/knives")));}
    private static boolean is(Block block,String... names) {
        Set<String> accepted=Set.of(names);
        for(Class<?> type=block.getClass();type!=null;type=type.getSuperclass())if(accepted.contains(type.getName()))return true;
        return false;
    }
    private static ItemStack methodStack(Block block,String method) {
        Object result=HostReflection.invoke(block,method);
        if(result instanceof ItemStack stack)return stack.copyWithCount(1);
        throw new IllegalStateException("Missing serving method "+method);
    }
    private static ItemStack supplierStack(Block block,String field) {
        Object result=HostReflection.field(block,field);
        if(result instanceof Supplier<?> supplier&&supplier.get() instanceof Item item)return new ItemStack(item);
        throw new IllegalStateException("Missing serving supplier "+field);
    }
    private static ItemStack fieldItemStack(Block block,String field) {
        Object result=HostReflection.field(block,field);
        if(result instanceof net.minecraft.world.level.ItemLike like)return new ItemStack(like);
        throw new IllegalStateException("Missing serving item field "+field);
    }
    private static ItemStack item(String id) {
        Item item=BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(null);
        if(item==null||item==Items.AIR)throw new IllegalStateException("Missing serving "+id);
        return new ItemStack(item);
    }
}
