package com.chi__you.cuisinejournal.recipe;

import net.minecraft.resources.ResourceLocation;

public final class DishExclusionRuleTest {
    public static void main(String[] args) throws Exception {
        var rules = EligibilityRules.builder().denyItemPathSuffix("_crust").build();
        require(rules.evaluateItem(ResourceLocation.parse("farmersdelight:pie_crust"), true, tag -> false)
                == EligibilityRules.Verdict.EXCLUDED, "crust suffix excluded");
        require(rules.evaluateItem(ResourceLocation.parse("silentsdelight:sculk_catalyst_pie_crust"), true, tag -> false)
                == EligibilityRules.Verdict.EXCLUDED, "cross-mod crust suffix excluded");
        require(rules.evaluateItem(ResourceLocation.parse("farmersdelight:apple_pie"), true, tag -> false)
                == EligibilityRules.Verdict.ELIGIBLE, "complete pie retained");
        var item=ResourceLocation.parse("test:duplicate_food");
        var recipe=ResourceLocation.parse("test:duplicate_recipe");
        var type=ResourceLocation.parse("minecraft:crafting");
        var allowed=EligibilityRules.builder().allowItem(item).allowRecipe(recipe).build();
        require(HostItemAvailability.hiddenByUnification(HostItemAvailability.UNITED::equals),
                "Compat united tag is recognized without a hard dependency");
        require(!HostItemAvailability.hiddenByUnification(tag->false),
                "missing or empty unified tag hides nothing");
        require(allowed.evaluate(recipe,type,item,true,tag->false)==EligibilityRules.Verdict.ELIGIBLE,
                "item returns when active united tag no longer contains it");
        var snapshot=HostItemAvailability.class.getDeclaredField("unifiedHidden");
        snapshot.setAccessible(true);
        var previous=snapshot.get(null);
        try {
            snapshot.set(null,java.util.Set.of(item));
            require(allowed.evaluate(recipe,type,item,true,tag->false)==EligibilityRules.Verdict.EXCLUDED,
                    "hidden host item overrides explicit recipe allow");
            require(allowed.evaluateItem(item,true,tag->false)==EligibilityRules.Verdict.EXCLUDED,
                    "configured item cannot restore hidden duplicate");
        } finally { snapshot.set(null,previous); }
        System.out.println("DishExclusionRuleTest: 8 checks passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
