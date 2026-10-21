package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.CuisineJournal;
import com.chi__you.cuisinejournal.network.CuisineJournalPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Server-to-client food state projection; stacks are display-only copies. */
public record CuisineFoodSnapshot(boolean active, boolean preserveNativeEffects,
                                  int capacity, double eatAgainFraction, List<Slot> foods, Slot drink, List<Slot> temporaryFoods,
                                  int temporaryCapacity)
        implements CuisineJournalPacket {
    public static final ResourceLocation ID = new ResourceLocation(CuisineJournal.MOD_ID, "food_state");

    public CuisineFoodSnapshot {
        foods = List.copyOf(foods);
        temporaryFoods = List.copyOf(temporaryFoods);
    }
    public CuisineFoodSnapshot(boolean active,boolean preserveNativeEffects,int capacity,double eatAgainFraction,
                              List<Slot> foods,Slot drink,List<Slot> temporaryFoods) {
        this(active,preserveNativeEffects,capacity,eatAgainFraction,foods,drink,temporaryFoods,1);
    }

    public record Slot(ItemStack displayStack, int remainingTicks, int totalTicks, int slotCost) {
        private void write(FriendlyByteBuf buffer) {
            buffer.writeItem(displayStack);
            buffer.writeVarInt(remainingTicks);
            buffer.writeVarInt(totalTicks);
            buffer.writeVarInt(slotCost);
        }

        private static Slot read(FriendlyByteBuf buffer) {
            return new Slot(buffer.readItem(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt());
        }
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeBoolean(preserveNativeEffects);
        buffer.writeVarInt(capacity);
        buffer.writeDouble(eatAgainFraction);
        buffer.writeVarInt(foods.size());
        foods.forEach(slot -> slot.write(buffer));
        buffer.writeBoolean(drink != null);
        if (drink != null) {
            drink.write(buffer);
        }
        buffer.writeVarInt(temporaryFoods.size());
        temporaryFoods.forEach(slot -> slot.write(buffer));
        buffer.writeVarInt(temporaryCapacity);
    }

    public static CuisineFoodSnapshot read(FriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        boolean preserveNativeEffects = buffer.readBoolean();
        int capacity = buffer.readVarInt();
        double eatAgainFraction = buffer.readDouble();
        int count = buffer.readVarInt();
        if (count < 0 || count > 64) {
            throw new IllegalArgumentException("Invalid food slot count " + count);
        }
        var foods = new java.util.ArrayList<Slot>(count);
        for (int i = 0; i < count; i++) {
            foods.add(Slot.read(buffer));
        }
        Slot drink = buffer.readBoolean() ? Slot.read(buffer) : null;
        int temporaryCount = buffer.readVarInt();
        if (temporaryCount < 0 || temporaryCount > CuisineFoodCapacity.MAXIMUM) throw new IllegalArgumentException("Invalid temporary slot count");
        var temporaryFoods = new java.util.ArrayList<Slot>(temporaryCount);
        for (int i = 0; i < temporaryCount; i++) temporaryFoods.add(Slot.read(buffer));
        int temporaryCapacity=buffer.readVarInt();
        if(temporaryCapacity<0||temporaryCapacity>CuisineFoodCapacity.MAXIMUM)throw new IllegalArgumentException("Invalid temporary capacity");
        return new CuisineFoodSnapshot(active, preserveNativeEffects, capacity,
                eatAgainFraction, foods, drink, temporaryFoods,temporaryCapacity);
    }
}
