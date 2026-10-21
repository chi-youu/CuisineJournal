package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.recipe.CookingMethod;
import java.util.Map;
import java.util.Set;

/** Only verified pot implementations may reuse the pot production and serving hooks. */
public final class CookingPotCompatibility {
    private static final Map<String, CookingMethod> METHODS = Map.of(
            "net.yirmiri.dungeonsdelight.common.block.entity.MonsterPotBlockEntity",
            CookingMethod.of("dungeonsdelight", "monster_cooking"),
            "io.github.rcneg.bossesdelight.blocks.entity.SoulCookingPotBlockEntity",
            CookingMethod.of("bosses_delight", "soul_cooking"),
            "com.chenjdy.farmers_spell.block.entity.AlchemistPotBlockEntity",
            CookingMethod.of("farmers_spell", "alchemist_cooking"));
    private static final Set<String> RESULT_SLOTS = Set.of(
            "vectorwing.farmersdelight.common.block.entity.container.CookingPotResultSlot",
            "net.yirmiri.dungeonsdelight.common.block.entity.container.MonsterPotResultSlot",
            "io.github.rcneg.bossesdelight.blocks.entity.container.SoulCookingPotResultSlot",
            "com.chenjdy.farmers_spell.block.entity.container.AlchemistPotResultSlot",
            "dev.xkmc.youkaishomecoming.content.pot.base.BasePotResultSlot",
            "com.sammy.minersdelight.content.block.copper_pot.CopperPotResultSlot",
            "com.freefish.torchesbecomesunlight.client.render.gui.screen.oven.OvenResultSlot",
            "com.freefish.torchesbecomesunlight.client.render.gui.screen.cuttingborad.CuttingBoardResultSlot");

    private CookingPotCompatibility() {}
    static CookingMethod methodFor(String name) { return METHODS.get(name); }
    public static CookingMethod methodFor(Class<?> type) {
        for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
            CookingMethod method = methodFor(parent.getName());
            if (method != null) return method;
        }
        return null;
    }
    static boolean isResultSlot(String name) { return RESULT_SLOTS.contains(name); }
    public static boolean isResultSlot(Class<?> type) {
        for (Class<?> parent = type; parent != null; parent = parent.getSuperclass()) {
            if (isResultSlot(parent.getName())) return true;
        }
        return false;
    }
}
