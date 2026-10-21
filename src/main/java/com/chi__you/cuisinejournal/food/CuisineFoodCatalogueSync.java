package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.network.Network;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Sends display-only food definitions in bounded batches at login and reload. */
public final class CuisineFoodCatalogueSync {
    private static final int PAGE_SIZE = 32;

    private CuisineFoodCatalogueSync() {
    }
    private static final java.util.Map<ServerPlayer,Long> HOST_REQUESTS=new java.util.WeakHashMap<>();
    public static void sendHost(ServerPlayer player,CuisineFoodInfoPackets.HostRequest request){
        long now=player.server.overworld().getGameTime();Long before=HOST_REQUESTS.get(player);
        if(before!=null&&now-before<2)return;HOST_REQUESTS.put(player,now);
        // Only inventory/server-owned stacks can be queried; clients cannot supply arbitrary effect NBT.
        List<ItemStack> candidates=new ArrayList<>();
        for(int i=0;i<player.getInventory().getContainerSize();i++)candidates.add(player.getInventory().getItem(i));
        candidates.add(player.containerMenu.getCarried());
        for(var slot:player.containerMenu.slots)candidates.add(slot.getItem());
        for(var entry:CuisineFoodService.state(player).allFoods())candidates.add(entry.stack());
        if(CuisineFoodService.state(player).drink()!=null)candidates.add(CuisineFoodService.state(player).drink().stack());
        for(ItemStack stack:candidates)if(!stack.isEmpty()&&BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(request.item())
                &&FoodVariantIdentity.hasVariant(stack)&&FoodVariantIdentity.variant(stack).equals(request.variant())){
            var definition=CuisineFoodService.definition(stack);if(definition==null)return;
            java.util.Map<net.minecraft.resources.ResourceLocation,CuisineFoodInfoPackets.Effect> effects=new java.util.LinkedHashMap<>();
            for(var setting:CuisineFoodEffectSource.forStack(stack))effects.put(setting.id(),new CuisineFoodInfoPackets.Effect(setting.id(),setting.level(),setting.activeTicks(definition.durationTicks())));
            var food=new CuisineFoodInfoPackets.Response(request.item(),definition.drink(),definition.healthPoints(),CuisineFoodStats.displayedRegen(definition),
                    definition.durationTicks(),definition.slotCost(),definition.repeatable(),stack.getUseDuration(),definition.drink()?CuisineFoodStats.drinkBonus(stack,null):0,
                    List.copyOf(effects.values()),CuisineFoodQuality.stars(stack),CuisineFlavorEffects.resolveStack(stack,definition.drink()).effectLevelBonuses());
            Network.toPlayer(player,new CuisineFoodInfoPackets.HostResponse(request.item(),request.variant(),food));return;
        }
    }

    public static void sendFruit(ServerPlayer player, CuisineFoodInfoPackets.FruitRequest request) {
        if (!CuisineFoodRuntime.active() || request.stars() < 0 || request.stars() > 5) return;
        Item item = BuiltInRegistries.ITEM.getOptional(request.item()).orElse(null);
        if (item == null) return;
        ItemStack stack = new ItemStack(item);
        var list = new net.minecraft.nbt.ListTag();
        request.fruits().forEach(value -> list.add(net.minecraft.nbt.StringTag.valueOf(value)));
        stack.getOrCreateTag().put(FruitJellyFlavorBridge.KEY, list);
        List<String> fruits = FruitJellyFlavorBridge.fruits(stack);
        if (fruits.isEmpty() || FruitJellyFlavorBridge.jellies(fruits).size() != fruits.size()) return;
        CuisineFoodQuality.markQualitySample(stack,request.stars(),false,null,"");
        CuisineFoodDefinition definition = CuisineFoodService.definition(stack);
        if (definition == null) return;
        List<CuisineFoodInfoPackets.Effect> effects = CuisineFoodEffectSource.forStack(stack).stream()
                .map(effect -> new CuisineFoodInfoPackets.Effect(effect.id(), effect.level(), effect.activeTicks(definition.durationTicks()))).toList();
        Network.toPlayer(player, new CuisineFoodInfoPackets.FruitResponse(fruits,
                new CuisineFoodInfoPackets.Response(request.item(), definition.drink(), definition.healthPoints(),
                        CuisineFoodStats.displayedRegen(definition), definition.durationTicks(), definition.slotCost(), definition.repeatable(),
                        item.getUseDuration(stack), definition.drink() ? CuisineFoodStats.drinkBonus(stack, null) : 0, effects, request.stars(),
                        CuisineFlavorEffects.resolveStack(stack, definition.drink()).effectLevelBonuses())));
    }

    public static void send(ServerPlayer player) {
        if (!CuisineFoodRuntime.active()) {
            return;
        }
        List<CuisineFoodInfoPackets.Response> page = new ArrayList<>(PAGE_SIZE);
        boolean first = true;
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            CuisineFoodDefinition definition = CuisineFoodService.definition(stack);
            if (definition == null) {
                continue;
            }
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (stack.is(net.minecraft.world.item.Items.ROTTEN_FLESH)
                    || id.toString().equals("born_in_chaos_v1:smoked_flesh")
                    || id.toString().equals("youkaishomecoming:sake_bottle")) {
                continue;
            }
            for (int stars = 0; stars <= 5; stars++) {
            CuisineFoodQuality.markQualitySample(stack,stars,false,null,"");
            definition = CuisineFoodService.definition(stack);
            final int duration = definition.durationTicks();
            List<CuisineFoodInfoPackets.Effect> effects = CuisineFoodEffectSource.forStack(stack).stream()
                    .map(effect -> new CuisineFoodInfoPackets.Effect(
                            effect.id(), effect.level(), effect.activeTicks(duration))).toList();
            page.add(new CuisineFoodInfoPackets.Response(id, definition.drink(),
                    definition.healthPoints(), CuisineFoodStats.displayedRegen(definition), definition.durationTicks(),
                    definition.slotCost(), definition.repeatable(), stack.getItem().getUseDuration(stack),
                    definition.drink() ? CuisineFoodStats.drinkBonus(stack, null) : 0.0F, effects, stars,
                    CuisineFlavorEffects.resolveStack(stack, definition.drink()).effectLevelBonuses()));
            if (page.size() == PAGE_SIZE) {
                Network.toPlayer(player, new CuisineFoodInfoPackets.Batch(first, page));
                first = false;
                page = new ArrayList<>(PAGE_SIZE);
            }
            }
        }
        if (first || !page.isEmpty()) {
            Network.toPlayer(player, new CuisineFoodInfoPackets.Batch(first, page));
        }
    }
}
