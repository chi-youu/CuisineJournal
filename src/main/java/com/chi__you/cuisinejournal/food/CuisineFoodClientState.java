package com.chi__you.cuisinejournal.food;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Last server snapshot; never used to commit food or progression. */
public final class CuisineFoodClientState {
    private static volatile CuisineFoodSnapshot snapshot;
    private static int clientTick;
    private static int receivedTick;

    private CuisineFoodClientState() {
    }

    public static void accept(CuisineFoodSnapshot update) {
        snapshot = update;
        receivedTick = clientTick;
    }

    public static void clear() {
        snapshot = null;
        receivedTick = clientTick;
    }

    public static void tick() {
        // A paused single-player client keeps rendering, but the server does
        // not advance food timers. Freeze prediction with the paused world.
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        clientTick++;
    }

    public static int displayedRemaining(CuisineFoodSnapshot.Slot slot) {
        return Math.max(0, slot.remainingTicks() - Math.max(0, clientTick - receivedTick));
    }

    public static CuisineFoodSnapshot snapshot() {
        return snapshot;
    }

    public static boolean active() {
        return snapshot != null && snapshot.active();
    }

    public static boolean preserveNativeEffects() {
        return snapshot != null && snapshot.preserveNativeEffects();
    }

    /** Client-side prediction used only to prevent starting a rejected use animation. */
    public static boolean canConsume(ItemStack stack) {
        if (snapshot == null || !snapshot.active() || stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(Items.ROTTEN_FLESH)
                || "born_in_chaos_v1:smoked_flesh".equals(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
            return true;
        }
        CuisineFoodInfoPackets.Response info = CuisineFoodTooltip.info(stack);
        if (info == null) {
            return true;
        }
        if (info.drink()) {
            return snapshot.drink() == null ||
                    (CuisineAlcohol.isAlcohol(stack) ||
                            CuisineFoodPlacement.canRefresh(snapshot.drink().remainingTicks(),
                                    snapshot.drink().totalTicks(),
                                    snapshot.eatAgainFraction()));
        }
        var slots = snapshot.foods().stream().filter(slot -> displayedRemaining(slot) > 0).map(slot ->
                new CuisineFoodPlacement.Slot(slot.slotCost(), displayedRemaining(slot),
                        slot.totalTicks(), CuisineFoodService.sameFood(slot.displayStack(), stack))).toList();
        if(ExquisitoParfaits.matches(stack)) {
            var temporary=snapshot.temporaryFoods().stream().filter(slot->displayedRemaining(slot)>0)
                    .map(slot->new CuisineFoodPlacement.Slot(slot.slotCost(),displayedRemaining(slot),slot.totalTicks(),CuisineFoodService.sameFood(slot.displayStack(),stack))).toList();
            return ExquisitoParfaits.placement(stack.getDamageValue(),slots,temporary,info.slotCost(),snapshot.capacity(),
                    snapshot.temporaryCapacity(),snapshot.eatAgainFraction(),info.repeatable())!=CuisineFoodPlacement.REJECT;
        }
        if (CuisineFoodPlacement.find(slots, info.slotCost(), snapshot.capacity(),
                snapshot.eatAgainFraction(),
                info.repeatable()) != CuisineFoodPlacement.REJECT) return true;
        var temporary = snapshot.temporaryFoods().stream().filter(slot -> displayedRemaining(slot) > 0).map(slot ->
                new CuisineFoodPlacement.Slot(slot.slotCost(), displayedRemaining(slot), slot.totalTicks(),
                        CuisineFoodService.sameFood(slot.displayStack(), stack))).toList();
        return CuisineFoodPlacement.acceptsTemporary(temporary, info.slotCost(), snapshot.temporaryCapacity())
                || CuisineEffectAdapters.digesting(com.chi__you.cuisinejournal.client.ClientWorldView.player());
    }

    public static boolean ordinaryAccepts(ItemStack stack) {
        if(!active())return true;
        var info=CuisineFoodTooltip.info(stack);
        if(info==null||info.drink())return true;
        if(ExquisitoParfaits.matches(stack)&&stack.getDamageValue()>0&&snapshot.foods().stream().anyMatch(slot->displayedRemaining(slot)>0
                &&CuisineFoodService.sameFood(slot.displayStack(),stack)))return false;
        var slots=snapshot.foods().stream().map(slot->new CuisineFoodPlacement.Slot(slot.slotCost(),
                displayedRemaining(slot),slot.totalTicks(),CuisineFoodService.sameFood(slot.displayStack(),stack))).toList();
        return CuisineFoodPlacement.find(slots,info.slotCost(),snapshot.capacity(),snapshot.eatAgainFraction(),info.repeatable())
                !=CuisineFoodPlacement.REJECT;
    }

}
