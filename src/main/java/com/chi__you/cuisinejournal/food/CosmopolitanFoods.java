package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.compat.HostClasses;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Cosmopolitan pie inheritance includes roll cakes with a doubled serving state. */
public final class CosmopolitanFoods {
    private static final String PIE="com.gumillea.cosmopolitan.common.block.FDPieBlock";
    private static final String ROLL="com.gumillea.cosmopolitan.common.block.RollCakeBlock";
    private static final String CUBE="com.gumillea.cosmopolitan.common.block.CubecakeBlock";
    private CosmopolitanFoods() {}

    public static boolean known(Block block) { return HostClasses.matches(block,PIE); }
    public static int servings(BlockState state,int count) {
        if(HostClasses.matches(state.getBlock(),ROLL)){
            var doubled=state.getBlock().getStateDefinition().getProperty("double");
            if(doubled instanceof net.minecraft.world.level.block.state.properties.BooleanProperty value&&state.getValue(value))count*=2;
        }
        return count;
    }
    public static boolean refilling(Block block,ItemStack held,ItemStack serving) {
        return HostClasses.matches(block,CUBE)&&held.is(serving.getItem());
    }
}
