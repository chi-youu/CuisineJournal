package com.chi__you.cuisinejournal.mixin.production;
import com.chi__you.cuisinejournal.tracking.ProductionTracker;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
@Mixin(ResultSlot.class)
public abstract class ProductionCraftInputsMixin implements ProductionTracker.CraftInputs {
    @Shadow @Final private CraftingContainer craftSlots;
    public Container cuisinejournal$inputs(){return craftSlots;}
}
