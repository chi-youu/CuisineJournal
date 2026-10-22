package com.chi__you.cuisinejournal.mixin.food;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="club.someoneice.jellyfishingdelight.block.ColorfulKrabbyPattys",remap=false)
public abstract class JellyfishPlatterBoundsMixin {
    // The native loop is inclusive; COLOR is the count, not the final list index.
    @Redirect(method="m_49635_",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;m_61143_(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;"),remap=false)
    private Comparable<?> cuisinejournal$lastIndex(BlockState state,Property<?> property){
        if(property instanceof IntegerProperty number&&property.getName().equals("color"))return state.getValue(number)-1;
        return state.getValue(property);
    }
}
