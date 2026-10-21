package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.network.Network;
import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.progression.CookingSpeed;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Menu context and speed only; real production/extraction is handled by ProductionTracker. */
public final class CookingContextTracker {
    private static final Map<UUID, CookingContext> CONTEXTS = new ConcurrentHashMap<>();

    private CookingContextTracker() {}

    public static void forget(ServerPlayer player) { CONTEXTS.remove(player.getUUID()); }

    public static void reset() {
        CONTEXTS.clear();
        CookingSpeed.clear();
    }

    public static String describe(ServerPlayer player) {
        CookingContext context = CONTEXTS.get(player.getUUID());
        if (context == null) return "no cooking context";
        return (context.hasMenuSession() ? "menu context " + context.menuMethod() : "no menu context")
                + "; exact production adapters";
    }

    public static void onServerTick(MinecraftServer server) {
        long tick = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CookingContext context = CONTEXTS.computeIfAbsent(player.getUUID(), key -> new CookingContext());
            AbstractContainerMenu menu = player.containerMenu;
            CookingMethod method = method(menu, context, tick);
            if (context.hasMenuSession() && (menu == null || menu.containerId != context.menuId() || method == null)) {
                context.endMenuSession();
                Network.toPlayer(player, new Packets.MenuContext(false, null));
            }
            if (!context.hasMenuSession() && method != null) {
                context.beginMenuSession(menu.containerId, method);
                if (menu != player.inventoryMenu) Network.toPlayer(player, new Packets.MenuContext(true, method.id()));
            }
            if (context.hasMenuSession()) {
                CookingSpeed.tick(player, context.workstationPos(), method, menu);
            } else if (context.hasWindow(tick)) {
                CookingSpeed.tick(player, context.windowPos(), context.windowMethod(), null);
            } else if (context.windowPos() != null) {
                context.endWindow();
                context.forgetWorkstation();
            }
        }
    }

    private static CookingMethod method(AbstractContainerMenu menu, CookingContext context, long tick) {
        if (menu == null || ConfigManager.server().compatibility.excludedMenuClasses.contains(menu.getClass().getName())) return null;
        if (menu instanceof CraftingMenu) return CookingMethod.CRAFTING;
        if (menu instanceof SmokerMenu) return CookingMethod.SMOKING;
        if (menu instanceof BlastFurnaceMenu) return CookingMethod.of("minecraft", "blasting");
        if (menu instanceof AbstractFurnaceMenu) return CookingMethod.SMELTING;
        if (menu instanceof InventoryMenu) {
            for (int slot = 1; slot <= 4; slot++) if (menu.getSlot(slot).hasItem()) return CookingMethod.CRAFTING;
            return null;
        }
        ProductionTracker.Station station = ProductionTracker.menu(menu);
        if (station != null) return station.method();
        if (context.hasMenuSession() && context.menuId() == menu.containerId) return context.menuMethod();
        return context.hasWindow(tick) ? context.windowMethod() : null;
    }

    public static void onWorkstationUsed(ServerPlayer player, BlockPos pos, BlockState state) {
        CookingMethod method = WorkstationRegistry.methodFor(state);
        if (method == null) {
            var blockEntity = player.level().getBlockEntity(pos);
            if (blockEntity != null) {
                var definition = WorkstationDefinitions.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
                if (definition != null && !definition.enabled()) return;
                method = CookingSpeed.timedMethod(blockEntity);
            }
        }
        if (method == null) return;
        CookingContext context = CONTEXTS.computeIfAbsent(player.getUUID(), key -> new CookingContext());
        context.beginWindow(pos.immutable(), method,
                player.server.getTickCount() + ConfigManager.server().compatibility.workstationContextTicks);
    }
}
