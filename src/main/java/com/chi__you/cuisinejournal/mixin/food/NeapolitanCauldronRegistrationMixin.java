package com.chi__you.cuisinejournal.mixin.food;

import com.chi__you.cuisinejournal.compat.HostReflection;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import java.util.Map;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.teamabnormals.neapolitan.core.other.NeapolitanCauldronInteractions",remap=false)
public abstract class NeapolitanCauldronRegistrationMixin {
    @Inject(method="addMilkshakeInteractions",at=@At("HEAD"),remap=false)
    private static void cuisinejournal$relation(Item bottle,Block cauldron,Item iceCream,Map<?,?> map,CallbackInfo ci){
        // Conversion is only installed when milk cauldrons are enabled, checked by the host below.
        try {
            Object common=Class.forName("com.teamabnormals.neapolitan.core.NeapolitanConfig").getField("COMMON").get(null);
            Object config=common.getClass().getField("milkCauldron").get(common);
            if(Boolean.TRUE.equals(HostReflection.invoke(config,"get")))
                com.chi__you.cuisinejournal.recipe.NeapolitanCauldrons.register(bottle,cauldron,iceCream);
        }catch(ReflectiveOperationException | LinkageError failure){
            com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Skipping unsupported Neapolitan milk-cauldron config",failure);
        }
    }
}
