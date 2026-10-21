package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Only the two direct-output exceptions settle at production; multiplayer batches still settle on pickup. */
public final class DirectDropProgress {
    private DirectDropProgress() {}
    public static void finish(Level level,ItemStack output,ProductionLedger.Batch batch) {
        if(level==null||level.isClientSide||output.isEmpty())return;
        output.removeTagKey(ProductionTracker.PENDING);
        if(!CulinaryRegistries.current().isTracked(BuiltInRegistries.ITEM.getKey(output.getItem())))return;
        if(batch.contributors().size()==1) {
            var server=level.getServer();var player=server==null?null:server.getPlayerList().getPlayer(batch.contributors().iterator().next());
            if(player!=null) {
                ProductionTracker.claimBatch(player,output,batch,CulinaryCredit.Source.DROP);
                return;
            }
            // Offline contributors keep their identity for settlement when they can receive progression.
            ProductionTracker.pending(output,batch);
        } else if(batch.contributors().isEmpty())new MealStamp(0,false,0,null,"").write(output);
        else ProductionTracker.pending(output,batch);
    }
}
