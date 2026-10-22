package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostClasses;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Audited direct-FoodData dishes and their independent block stages. */
public final class ProcedurePlacedFoods {
    public record Serving(ResourceLocation whole, ResourceLocation portion, int remaining, boolean direct, int nutrition, float saturation) {}
    private ProcedurePlacedFoods() {}
    public static Serving rule(ResourceLocation id) {
        if(id.toString().equals("slime_delight:slime_pocky_box"))return new Serving(id,
                ResourceLocation.parse("slime_delight:slime_pocky"),8,false,0,0);
        if(com.chi__you.foodinteractions.CoconutStewServings.known(id))return new Serving(id,
                com.chi__you.foodinteractions.CoconutStewServings.portion(id),id.getPath().equals("coconut_crab_stew")?5:4,false,0,0);
        int festive=com.chi__you.foodinteractions.FestiveServings.remaining(id);
        if(festive>0)return new Serving(com.chi__you.foodinteractions.FestiveServings.whole(),
                com.chi__you.foodinteractions.FestiveServings.portion(),festive,false,0,0);
        int stew=com.chi__you.foodinteractions.SlimeFoodServings.stewRemaining(id);
        if(stew>0)return new Serving(ResourceLocation.parse("slime_delight:nether_stew_1"),
                ResourceLocation.parse("slime_delight:bowl_of_nether_stew"),stew,false,0,0);
        int slime=com.chi__you.foodinteractions.SlimeFoodServings.remaining(id);
        if(slime>0)return new Serving(com.chi__you.foodinteractions.SlimeFoodServings.whole(),
                com.chi__you.foodinteractions.SlimeFoodServings.slice(),slime,true,3,1F/6F);
        if (id.getNamespace().equals("dumplings_delight") && id.getPath().equals("dumpling_medley"))
            return new Serving(id, id, 7, true, 5, .7F);
        var relation=com.chi__you.foodinteractions.UndergardenServings.relation(id);if(relation==null)return null;
        int nutrition = relation.whole().getPath().equals("gloomgourd_pie") ? 2 : 3;
        return new Serving(relation.whole(),relation.portion(),relation.remaining(),relation.pie(),nutrition,1F/(2*nutrition));
    }
    public static boolean known(Block block) {
        return rule(BuiltInRegistries.BLOCK.getKey(block)) != null && (HostClasses.matches(block,
                "com.tyxcnjiu.main.dumplingsdelight.block.DumplingMedleyBlock")
                || block.getClass().getName().startsWith("net.mcreator.undergardendelight.block.")
                || block.getClass().getName().startsWith("net.mcreator.slimedelight.block.GrassBlockCake")
                || block.getClass().getName().startsWith("net.mcreator.slimedelight.block.NetherStew")
                || block.getClass().getName().equals("net.mcreator.slimedelight.block.SlimePockyBoxBlock")
                || block.getClass().getName().startsWith("net.toopa.festivedelight.block.FestiveChickenStage")
                || block.getClass().getName().startsWith("net.budoumiao.crabbersdelightexpand.block."));
    }
    public static boolean procedure(ItemStack food) {
        return BuiltInRegistries.ITEM.getKey(food.getItem()).toString().equals("dumplings_delight:dumpling_medley")
                || food.hasTag() && food.getTag().getBoolean("cuisinejournal:procedure_serving");
    }
    public static int remaining(BlockState state) {
        var rule = rule(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if(rule!=null&&rule.whole().getNamespace().equals("crabbers_delight_expand"))
            return Math.max(0,rule.remaining()-DelightPlacedFoods.value(state,"blockstate"));
        return rule == null ? 0 : rule.whole().getNamespace().equals("dumplings_delight")
                ? DelightPlacedFoods.value(state, "servings") : rule.remaining();
    }
    public static ItemStack serving(BlockState state, Player player) {
        var rule = rule(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (rule == null || remaining(state) <= 0) return ItemStack.EMPTY;
        if(rule.whole().toString().equals("crabbers_delight_expand:coconut_crab_stew")&&DelightPlacedFoods.value(state,"blockstate")==0)
            return BuiltInRegistries.ITEM.get(com.chi__you.foodinteractions.CoconutStewServings.claws()).getDefaultInstance();
        ItemStack stack = BuiltInRegistries.ITEM.get(rule.portion()).getDefaultInstance();
        if (!rule.direct() || stack.isEmpty()) return stack;
        int nutrition = rule.nutrition();float saturation = rule.saturation();
        if (rule.whole().getNamespace().equals("dumplings_delight")) {
            boolean garlic=player != null && player.getActiveEffects().stream().anyMatch(effect ->
                    ResourceLocation.parse("dumplings_delight:garlic").equals(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect())));
            var food=dumplingNutrition(remaining(state),garlic);nutrition=food.getNutrition();saturation=food.getSaturationModifier();
        }
        var values = new net.minecraft.nbt.CompoundTag();values.putInt("nutrition", nutrition);values.putFloat("saturation", saturation);
        stack.getOrCreateTag().put(CakeFoodCompat.SERVING, values);
        stack.getOrCreateTag().putBoolean("cuisinejournal:procedure_serving", true);
        return stack;
    }
    public static FoodProperties dumplingNutrition(int left,boolean garlic){
        int nutrition=left>=6?5:left>=3?6:8;float gain=left>=6?7F:left>=3?8.4F:14.4F;
        if(garlic)nutrition++;
        return new FoodProperties.Builder().nutrition(nutrition).saturationMod(gain/(2*nutrition)).build();
    }
    public static int nutritionIncrement(int current,int cap,int proposed){
        int observed=current<cap?Math.min(19,current):Math.max(20,current);
        return Math.max(0,proposed-observed);
    }
    public static ItemStack eating(BlockState state, Player player, ItemStack held, boolean sneaking) {
        var rule = rule(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (rule == null || !rule.direct()) return ItemStack.EMPTY;
        if(player!=null&&!rule.whole().getNamespace().equals("dumplings_delight"))held=player.getMainHandItem();
        if (rule.whole().getNamespace().equals("slime_delight"))return sneaking
                || held.is(ItemTags.create(ResourceLocation.parse("forge:tools/knives")))?ItemStack.EMPTY:serving(state,player);
        if (rule.whole().getNamespace().equals("dumplings_delight") && !held.isEmpty()) return ItemStack.EMPTY;
        if (held.is(ItemTags.create(ResourceLocation.parse("farmersdelight:tools/knives")))) return ItemStack.EMPTY;
        return serving(state, player);
    }
    public static boolean sameMeal(BlockState before, BlockState after) {
        var first = rule(BuiltInRegistries.BLOCK.getKey(before.getBlock()));
        var next = rule(BuiltInRegistries.BLOCK.getKey(after.getBlock()));
        return first != null && next != null && first.whole().equals(next.whole()) && remaining(after) < remaining(before);
    }
    public static boolean returnedStage(BlockState state, ItemStack output) {
        var source = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return (com.chi__you.foodinteractions.SlimeFoodServings.remaining(source) > 0
                ||com.chi__you.foodinteractions.SlimeFoodServings.stewRemaining(source)>0)
                && source.equals(BuiltInRegistries.ITEM.getKey(output.getItem()));
    }
    public static FoodProperties properties(ItemStack stack, FoodProperties original) {
        if (stack.hasTag() && stack.getTag().contains(CakeFoodCompat.SERVING, 10) && procedure(stack)) {
            var data = stack.getTag().getCompound(CakeFoodCompat.SERVING);
            var builder = new FoodProperties.Builder().nutrition(data.getInt("nutrition")).saturationMod(data.getFloat("saturation"));
            if (original != null) for (var effect : original.getEffects()) builder.effect(effect::getFirst, effect.getSecond());
            return builder.build();
        }
        if (original == null && stack.getItem() instanceof BlockItem placed && known(placed.getBlock())) {
            var rule = rule(BuiltInRegistries.BLOCK.getKey(placed.getBlock()));
            if (rule.direct()) return new FoodProperties.Builder().nutrition(rule.nutrition()).saturationMod(rule.saturation()).build();
        }
        return original;
    }
}
