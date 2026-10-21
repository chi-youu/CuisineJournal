package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.recipe.CookingMethod;
import net.minecraft.core.BlockPos;

/** Menu/speed context only. Actual production ownership belongs to station ledgers. */
final class CookingContext {
    private int menuId = -1;
    private CookingMethod menuMethod = CookingMethod.UNKNOWN;
    private BlockPos workstationPos;
    private BlockPos windowPos;
    private CookingMethod windowMethod;
    private long windowExpiresAtTick = Long.MIN_VALUE;

    boolean hasMenuSession() { return menuId != -1; }
    int menuId() { return menuId; }
    CookingMethod menuMethod() { return menuMethod; }
    BlockPos workstationPos() { return workstationPos; }
    BlockPos windowPos() { return windowPos; }
    CookingMethod windowMethod() { return windowMethod; }

    void beginMenuSession(int id, CookingMethod method) {
        menuId = id;
        menuMethod = method == null ? CookingMethod.UNKNOWN : method;
    }

    void endMenuSession() {
        menuId = -1;
        menuMethod = CookingMethod.UNKNOWN;
    }

    boolean hasWindow(long currentTick) {
        return windowPos != null && currentTick <= windowExpiresAtTick;
    }

    void beginWindow(BlockPos pos, CookingMethod method, long expiresAtTick) {
        workstationPos = pos;
        windowPos = pos;
        windowMethod = method;
        windowExpiresAtTick = expiresAtTick;
    }

    void endWindow() {
        windowPos = null;
        windowMethod = null;
        windowExpiresAtTick = Long.MIN_VALUE;
    }

    void forgetWorkstation() { workstationPos = null; }
}
