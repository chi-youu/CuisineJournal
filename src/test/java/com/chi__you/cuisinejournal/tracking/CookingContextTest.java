package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.config.ServerConfig;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import net.minecraft.core.BlockPos;

/** Context transitions must not infer production from unrelated inventory changes. */
public final class CookingContextTest {
    private static int checks;

    public static void main(String[] args) {
        var context = new CookingContext();
        check(!context.hasMenuSession() && !context.hasWindow(0), "new context is idle");
        var position = new BlockPos(1, 2, 3);
        context.beginWindow(position, CookingMethod.SMOKING, 20);
        check(context.hasWindow(19) && context.hasWindow(20) && !context.hasWindow(21), "interaction window expires at the original boundary");
        check(context.windowPos().equals(position) && context.workstationPos().equals(position), "station position retained for menu speed");
        check(context.windowMethod().equals(CookingMethod.SMOKING), "interaction method retained");
        context.beginMenuSession(7, CookingMethod.CRAFTING);
        check(context.hasMenuSession() && context.menuId() == 7, "menu session records actual container identity");
        check(context.menuMethod().equals(CookingMethod.CRAFTING), "menu method is independent of interaction method");
        context.endWindow();
        check(!context.hasWindow(0) && context.windowPos() == null && context.windowMethod() == null, "ending interaction clears only the window");
        check(context.hasMenuSession() && context.workstationPos().equals(position), "open menu retains its station after window ends");
        context.endMenuSession();
        check(!context.hasMenuSession() && context.menuMethod().equals(CookingMethod.UNKNOWN), "closing menu clears menu identity");
        context.forgetWorkstation();
        check(context.workstationPos() == null, "expired context can forget station");
        context.beginMenuSession(8, null);
        check(context.menuMethod().equals(CookingMethod.UNKNOWN), "missing method is explicitly unknown");
        var config = new ServerConfig();
        check(config.compatibility.workstationContextTicks == 20, "renamed context setting retains default lifetime");
        config.compatibility.workstationContextTicks = Integer.MAX_VALUE;
        config.validate();
        check(config.compatibility.workstationContextTicks == 200, "context setting keeps bounded range");
        System.out.println("CookingContextTest: " + checks + " checks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
