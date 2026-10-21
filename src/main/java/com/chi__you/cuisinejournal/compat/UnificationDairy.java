package com.chi__you.cuisinejournal.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import static com.chi__you.cuisinejournal.compat.UnificationContent.id;

/** Host cookware still accepts the representative cream; the temporary legacy input never escapes. */
public final class UnificationDairy {
    private static final ThreadLocal<Boolean> LEGACY_INPUT=ThreadLocal.withInitial(()->false);
    private UnificationDairy() {}
    public static ItemStack handOutput(Player player,ItemStack output){
        return LEGACY_INPUT.get()?output:UnificationRuntime.interactionOutput(output);
    }
    public static InteractionResult legacyCreamPot(BlockState state,Level level,BlockPos pos,Player player,
                                                   InteractionHand hand,BlockHitResult hit){
        if(LEGACY_INPUT.get()||!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals("farmersdelight:cooking_pot"))return null;
        var plan=UnificationRuntime.plan(level.getServer());var winner=plan.winner("cuisinejournal:cream_bucket");
        var legacy=id("braziliandelight:heavy_cream_bucket");var held=player.getItemInHand(hand);
        if(winner==null||winner.equals(legacy)||!BuiltInRegistries.ITEM.containsKey(legacy)
                ||!BuiltInRegistries.ITEM.getKey(held.getItem()).equals(winner))return null;
        var proxy=new ItemStack(BuiltInRegistries.ITEM.get(legacy),held.getCount());
        if(held.hasTag())proxy.setTag(held.getTag().copy());
        LEGACY_INPUT.set(true);
        try{
            player.setItemInHand(hand,proxy);
            var result=com.chi__you.cuisinejournal.tracking.BrazilPotProduction.use(level,pos,state,player,hand,
                    ()->state.getBlock().use(state,level,pos,player,hand,hit));
            if(player.getItemInHand(hand)==proxy)player.setItemInHand(hand,held);
            return result;
        }finally{
            if(player.getItemInHand(hand)==proxy)player.setItemInHand(hand,held);
            LEGACY_INPUT.remove();
        }
    }
    public static boolean creamBucket(ItemStack stack,UnificationContent.Plan plan){
        var item=BuiltInRegistries.ITEM.getKey(stack.getItem());
        var legacy=id("braziliandelight:heavy_cream_bucket");
        return item.equals(legacy)||item.equals(plan.replace(legacy));
    }
}
