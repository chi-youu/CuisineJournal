package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.food.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Supplier;

/** World preparation has no result slot: keep real inputs through dough stages and fondue refills. */
public final class LetsDoWorldWork {
    private static final String WORK="cuisinejournal:lets_do_work";
    private record Frame(Level level,BlockPos pos,BlockState before,ServerPlayer player,ItemStack held,ItemStack stored,Set<ItemStack> delivered){}
    private static final ProductionFrames<Deque<Frame>> FRAMES=ProductionFrames.stack();
    private LetsDoWorldWork(){}
    public static boolean supports(BlockState state){return DelightPlacedFoods.is(state.getBlock(),
            "net.satisfy.bakery.core.block.BakerStationBlock","net.satisfy.bakery.core.block.cake.BlankCakeBlock","net.satisfy.meadow.core.block.FondueBlock");}
    public static InteractionResult use(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.player.Player player,ItemStack held,Supplier<InteractionResult> host){
        if(level.isClientSide||!(player instanceof ServerPlayer server))return host.get();
        boolean station=DelightPlacedFoods.is(state.getBlock(),"net.satisfy.bakery.core.block.BakerStationBlock");
        BlockPos target=station?pos.above():pos;BlockState before=level.getBlockState(target);
        ItemStack stored=PlacedFoodVariants.stored(level,target);
        if(stored.isEmpty()&&DelightPlacedFoods.is(state.getBlock(),"net.satisfy.bakery.core.block.cake.BlankCakeBlock"))
            stored=BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse("bakery:cake_dough")).map(ItemStack::new).orElse(ItemStack.EMPTY);
        var frame=new Frame(level,target,before,server,held.copyWithCount(1),stored,Collections.newSetFromMap(new IdentityHashMap<>()));
        FRAMES.get().push(frame);InteractionResult result;
        try{result=host.get();}finally{FRAMES.get().pop();if(FRAMES.get().isEmpty())FRAMES.remove();}
        if(!result.consumesAction())return result;
        BlockState after=level.getBlockState(target);
        if(station&&!after.equals(before)&&DelightPlacedFoods.is(after.getBlock(),"net.satisfy.bakery.core.block.cake.BlankCakeBlock")){
            var dough=frame.held.copy();writeWork(dough,Set.of(server.getUUID()),List.of(frame.held));PlacedFoodVariants.store(level,target,dough);
        }else if(DelightPlacedFoods.is(state.getBlock(),"net.satisfy.bakery.core.block.cake.BlankCakeBlock")&&!after.equals(before)){
            if(after.getBlock()==before.getBlock()){PlacedFoodVariants.store(level,target,stored);return result;}
            ItemStack product=new ItemStack(after.getBlock().asItem());
            if(product.isEmpty())product=LetsDoPlacedFoods.serving(after.getBlock(),after).copyWithCount(4);
        if(product.isEmpty())return result;
            var owners=owners(stored);owners.add(server.getUUID());
            var sources=new ArrayList<ItemStack>();sources.add(stored);sources.add(frame.held);
            IngredientProvenance.sources(product,sources);produce(frame,product,owners,"bakery:baking_station",sources);
            PlacedFoodVariants.store(level,target,product);
            PlacedMealProduction.storeStamped(level,target,after,product,LetsDoPlacedFoods.remaining(after));
        }else if(DelightPlacedFoods.is(state.getBlock(),"net.satisfy.meadow.core.block.FondueBlock")){
            int old=DelightPlacedFoods.value(before,"fill_amount"),now=DelightPlacedFoods.value(after,"fill_amount");
            if(now>old){var sources=new ArrayList<ItemStack>();if(old>0)sources.add(stored);sources.add(frame.held);
                var owners=old>0?owners(stored):new HashSet<UUID>();owners.add(server.getUUID());
                ItemStack container=new ItemStack(Items.PAPER);writeWork(container,owners,sources);PlacedFoodVariants.store(level,target,container);
            }else if(now==0)PlacedFoodVariants.clear(level,target);
        }
        return result;
    }
    private static Set<UUID> owners(ItemStack stack){Set<UUID> result=new HashSet<>();if(stack.hasTag())for(var raw:stack.getTag().getList(WORK,10))
        if(((CompoundTag)raw).hasUUID("id"))result.add(((CompoundTag)raw).getUUID("id"));return result;}
    private static void writeWork(ItemStack stack,Set<UUID> owners,List<ItemStack> inputs){
        var list=new ListTag();owners.stream().sorted().forEach(id->{var row=new CompoundTag();row.putUUID("id",id);list.add(row);});
        stack.getOrCreateTag().put(WORK,list);IngredientProvenance.sources(stack,inputs.stream().filter(input->!input.is(Items.PAPER)).toList());
        for(var input:inputs)if(input.is(Items.PAPER)&&IngredientProvenance.sources(input)!=null){
            var sources=stack.getTag().getList(IngredientProvenance.KEY,8);
            for(String source:IngredientProvenance.sources(input)){var value=StringTag.valueOf(source);if(!sources.contains(value)&&sources.size()<128)sources.add(value);}
        }
    }
    private static void produce(Frame frame,ItemStack result,Set<UUID> owners,String method,List<ItemStack> inputs){
        Set<String> ingredients=new HashSet<>();for(var input:inputs)if(!input.isEmpty()&&!input.is(Items.PAPER))ingredients.add(BuiltInRegistries.ITEM.getKey(input.getItem()).toString());
        var batch=new ProductionLedger.Batch(BuiltInRegistries.ITEM.getKey(result.getItem()).toString(),result.getCount(),owners,ingredients,method);
        if(owners.size()==1)com.chi__you.cuisinejournal.progression.CookingRewards.stampProduced(frame.player.server,result,owners.iterator().next(),result.getCount());
        ProductionTracker.claimBatch(frame.player,result,batch,CulinaryCredit.Source.INTERACTION);
    }
    public static void output(ItemStack output){
        var frames=FRAMES.get();if(frames.isEmpty()||output.isEmpty())return;var frame=frames.peek();
        if(!DelightPlacedFoods.is(frame.before.getBlock(),"net.satisfy.meadow.core.block.FondueBlock")
                ||!BuiltInRegistries.ITEM.getKey(output.getItem()).toString().equals("meadow:cheese_stick")||!frame.delivered.add(output))return;
        var sources=List.of(frame.stored,frame.held);IngredientProvenance.sources(output,sources.stream().filter(input->!input.is(Items.PAPER)).toList());
        var storedSources=IngredientProvenance.sources(frame.stored);if(storedSources!=null){var rows=output.getTag().getList(IngredientProvenance.KEY,8);
            for(String source:storedSources){var value=StringTag.valueOf(source);if(!rows.contains(value)&&rows.size()<128)rows.add(value);}}
        var owners=owners(frame.stored);owners.add(frame.player.getUUID());produce(frame,output,owners,"meadow:fondue",sources);
    }
    public static void clear(){FRAMES.remove();}
}
