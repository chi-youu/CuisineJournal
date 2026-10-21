package com.chi__you.cuisinejournal.food;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.IdentityHashMap;
import java.util.Map;

/** Single server-side entry point for the first Cuisine Journal food model. */
public final class CuisineFoodService {
    public static final int MIN_DURATION_TICKS = 20;
    public static final int EARLY_REFRESH_WINDOW_TICKS = 1200;
    private static final Map<ServerPlayer, CuisineFoodState> STATES = new IdentityHashMap<>();

    private CuisineFoodService() {
    }

    public static CuisineFoodState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player, current -> {
            CompoundTag root = current.getPersistentData();
            if (root.contains(CuisineFoodState.ROOT, 10)) {
                return CuisineFoodState.read(root.getCompound(CuisineFoodState.ROOT));
            }
            if (root.contains(CuisineFoodState.ROOT)) {
                return CuisineFoodState.unsupported();
            }
            CompoundTag legacy = CuisineSolMigration.pendingLegacy(root);
            CuisineFoodState migrated = legacy != null && !legacy.isEmpty()
                    ? CuisineSolMigration.importLegacy(legacy) : new CuisineFoodState();
            root.put(CuisineFoodState.ROOT, migrated.save());
            if (legacy != null) {
                root.putBoolean(CuisineSolMigration.IMPORTED, true);
            }
            return migrated;
        });
    }

    public static void save(ServerPlayer player, CuisineFoodState state) {
        if (!state.isReadOnly()) {
            player.getPersistentData().put(CuisineFoodState.ROOT, state.save());
        }
    }

    public static CuisineFoodDefinition definition(ItemStack stack) {
        return CuisineFoodDefinition.from(stack);
    }

    public static boolean canConsume(ServerPlayer player, ItemStack stack) {
        CuisineFoodState state = state(player);
        if (clearsFood(stack)) {
            return !state.isReadOnly();
        }
        CuisineFoodDefinition definition = definition(stack);
        if (definition == null) {
            return false;
        }
        return !state.isReadOnly()
                && findPlacement(player, state, stack, definition) != CuisineFoodPlacement.REJECT;
    }

    public static boolean isTemporaryUse(ServerPlayer player, ItemStack stack) {
        if (!CuisineFoodRuntime.active() || clearsFood(stack) || state(player).isReadOnly()) return false;
        CuisineFoodDefinition definition = definition(stack);
        return definition != null && findPlacement(player, state(player), stack, definition) == CuisineFoodPlacement.TEMPORARY;
    }

    public static boolean isDigestedUse(ServerPlayer player, ItemStack stack) {
        if(!CuisineFoodRuntime.active()||clearsFood(stack)||state(player).isReadOnly())return false;
        var definition=definition(stack);
        return definition!=null&&findPlacement(player,state(player),stack,definition)==CuisineFoodPlacement.DIGESTED;
    }

    private static int findPlacement(ServerPlayer player, CuisineFoodState state, ItemStack stack,
                                           CuisineFoodDefinition definition) {
        if (definition.drink()) {
            if (CuisineAlcohol.isAlcohol(stack)) {
                return state.drink() == null ? CuisineFoodPlacement.ADD : 0;
            }
            return state.drink() == null ? CuisineFoodPlacement.ADD
                    : canRefresh(state.drink()) ? 0 : CuisineFoodPlacement.REJECT;
        }
        var slots = state.foods().stream().map(entry -> new CuisineFoodPlacement.Slot(
                entry.slotCost(), entry.remainingTicks(), entry.totalTicks(),
                sameFood(entry.stack(), stack))).toList();
        if (ExquisitoParfaits.matches(stack)) {
            var temporary=state.temporaryFoods().stream().filter(entry->entry.remainingTicks()>0)
                    .map(entry->new CuisineFoodPlacement.Slot(entry.slotCost(),entry.remainingTicks(),entry.totalTicks(),sameFood(entry.stack(),stack))).toList();
            return ExquisitoParfaits.placement(stack.getDamageValue(),slots,temporary,definition.slotCost(),
                    CuisineFoodCapacity.ordinary(player),CuisineFoodCapacity.temporary(player),CuisineFoodConfig.EAT_AGAIN_PERCENTAGE.get(),definition.repeatable());
        }
        int placement = CuisineFoodPlacement.find(slots, definition.slotCost(), CuisineFoodCapacity.ordinary(player),
                CuisineFoodConfig.EAT_AGAIN_PERCENTAGE.get(), definition.repeatable());
        if (placement != CuisineFoodPlacement.REJECT) return placement;
        var temporary = state.temporaryFoods().stream().map(entry -> new CuisineFoodPlacement.Slot(
                entry.slotCost(), entry.remainingTicks(), entry.totalTicks(), sameFood(entry.stack(), stack))).toList();
        return CuisineFoodPlacement.extraPlacement(
                CuisineFoodPlacement.acceptsTemporary(temporary, definition.slotCost(), CuisineFoodCapacity.temporary(player)),
                CuisineEffectAdapters.digesting(player));
    }

    public static boolean consume(ServerPlayer player, ItemStack stack) {
        CuisineFoodState state = state(player);
        if(CuisineFoodRuntime.active())CuisineFoodEffects.observe(player,state);
        if (clearsFood(stack)) {
            if (state.isReadOnly()) {
                return false;
            }
            state.clear();
            save(player, state);
            return true;
        }
        CuisineFoodDefinition definition = definition(stack);
        if (definition == null) {
            return false;
        }
        if (state.isReadOnly()) {
            return false;
        }
        int placement = findPlacement(player, state, stack, definition);
        if (placement == CuisineFoodPlacement.REJECT) {
            return false;
        }
        boolean instantHealing=placement==CuisineFoodPlacement.DIGESTED||placement==CuisineFoodPlacement.TEMPORARY
                &&CuisineEffectAdapters.digesting(player);
        if(instantHealing&&!CuisineDigestionUse.completed(player,stack))return false;
        if(placement==CuisineFoodPlacement.DIGESTED) {
            player.heal(definition.healthPoints());
            return true;
        }
        if (placement == CuisineFoodPlacement.TEMPORARY) {
            var slots=state.temporaryFoods().stream().map(entry->new CuisineFoodPlacement.Slot(entry.slotCost(),entry.remainingTicks(),
                    entry.totalTicks(),sameFood(entry.stack(),stack))).toList();
            var plan=CuisineFoodPlacement.temporaryPlan(slots,definition.slotCost(),CuisineFoodCapacity.temporary(player));
            var replacement=new CuisineFoodEntry(stack,com.chi__you.cuisinejournal.skills.CookingSkills.temporaryTicks(player),definition.slotCost());
            var removed=new java.util.ArrayList<CuisineFoodEntry>();
            plan.remove().stream().sorted(java.util.Comparator.reverseOrder()).forEach(index->removed.add(state.temporaryFoods().remove((int)index)));
            state.temporaryFoods().add(replacement);
            CuisineFoodEffects.replaceTemporary(player,state,removed);
            save(player, state);
            if(instantHealing)player.heal(definition.healthPoints());
            return true;
        }

        if (definition.drink()) {
            if(state.drink()!=null)
                CuisineFoodEffects.replaceTemporary(player,state,java.util.List.of(state.drink()));
            if (state.drink() == null) {
                state.setDrink(new CuisineFoodEntry(stack, definition.durationTicks(), definition.slotCost()));
            } else {
                state.drink().refresh(stack, definition.durationTicks(), definition.slotCost());
            }
            save(player, state);
            return true;
        }

        var duplicateTemporary = state.temporaryFoods().stream().filter(entry -> sameFood(entry.stack(), stack)).toList();
        state.temporaryFoods().removeAll(duplicateTemporary);
        CuisineFoodEffects.replaceTemporary(player, state, duplicateTemporary);
        boolean fillCapacity = CuisineFoodRuntime.active() && CuisineStarBonuses.fillsCapacity(stack);
        float oldMaximum = 0;
        if (fillCapacity) {
            // Bring the old state current before measuring, so stale attribute ticks cannot pay twice.
            CuisineFoodEvents.synchronizeHealth(player, state);
            oldMaximum = player.getMaxHealth();
        }
        if (placement == CuisineFoodPlacement.ADD) {
            state.foods().add(new CuisineFoodEntry(stack, com.chi__you.cuisinejournal.skills.CookingSkills.ordinaryTicks(player,definition.durationTicks()), definition.slotCost()));
        } else {
            CuisineFoodEntry previous = state.foods().get(placement);
            CuisineFoodEntry replacement = new CuisineFoodEntry(
                    stack, com.chi__you.cuisinejournal.skills.CookingSkills.ordinaryTicks(player,definition.durationTicks()), definition.slotCost());
            replacement.adoptOwnedEffectsFrom(previous);
            state.foods().set(placement, replacement);
        }
        save(player, state);
        if (fillCapacity) {
            CuisineFoodEvents.synchronizeHealth(player, state);
            CuisineFoodEvents.fillNewCapacity(player, oldMaximum);
        }
        return true;
    }

    public static void tick(ServerPlayer player) {
        CuisineFoodState state = state(player);
        if (state.isReadOnly() || state.allFoods().isEmpty() && state.drink() == null) {
            return;
        }
        state.tick();
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.HUNGER)
                || hasGrowingHunger(player)) {
            state.advanceOrdinary(1);
        }
        save(player, state);
    }

    public static void skipNight(ServerPlayer player, long passedTicks) {
        CuisineFoodState state = state(player);
        if (state.isReadOnly() || passedTicks <= 0) {
            return;
        }
        state.skipNight(passedTicks);
        save(player, state);
        CuisineFoodSync.send(player);
    }

    private static boolean hasGrowingHunger(ServerPlayer player) {
        var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(
                new net.minecraft.resources.ResourceLocation("enigmaticlegacy", "growing_hunger")).orElse(null);
        return effect != null && player.hasEffect(effect);
    }

    public static void clear(ServerPlayer player) {
        CuisineFoodState state = state(player);
        if (state.isReadOnly()) {
            return;
        }
        CuisineFoodEffects.clear(player);
        state.clear();
        save(player, state);
    }

    public static void forget(ServerPlayer player) {
        CuisineDigestionUse.cancel(player);
        STATES.remove(player);
    }

    public static void forgetAll() {
        CuisineDigestionUse.clear();
        STATES.clear();
    }

    private static boolean canRefresh(CuisineFoodEntry entry) {
        return CuisineFoodPlacement.canRefresh(entry.remainingTicks(), entry.totalTicks(),
                CuisineFoodConfig.EAT_AGAIN_PERCENTAGE.get());
    }

    public static boolean sameFood(ItemStack left, ItemStack right) {
        // Food-slot identity is the item type; names, quality and addon NBT cannot bypass it.
        return ItemStack.isSameItem(left, right);
    }

    private static boolean clearsFood(ItemStack stack) {
        if (stack.is(Items.ROTTEN_FLESH)) {
            return true;
        }
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.toString().equals("born_in_chaos_v1:smoked_flesh");
    }

}
