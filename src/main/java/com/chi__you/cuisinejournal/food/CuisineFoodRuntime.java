package com.chi__you.cuisinejournal.food;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Central food-mode guard, including the explicit SOL runtime conflict. */
public final class CuisineFoodRuntime {
    private CuisineFoodRuntime() {
    }

    public static boolean active() {
        return CuisineFoodConfig.cuisineFoodSelected()
                && !ModList.get().isLoaded("sol_valheim");
    }

    public static boolean activeForCurrentThread() {
        if (isClientThread()) {
            return CuisineFoodClientState.active();
        }
        return active();
    }

    public static boolean isClientThread() {
        return FMLEnvironment.dist == Dist.CLIENT
                && DistExecutor.unsafeCallWhenOn(Dist.CLIENT,
                () -> () -> net.minecraft.client.Minecraft.getInstance().isSameThread());
    }
}
