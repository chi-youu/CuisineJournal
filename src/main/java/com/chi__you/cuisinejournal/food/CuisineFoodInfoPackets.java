package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.CuisineJournal;
import com.chi__you.cuisinejournal.network.CuisineJournalPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class CuisineFoodInfoPackets {
    private CuisineFoodInfoPackets() {
    }
    public record HostRequest(ResourceLocation item,String variant) implements CuisineJournalPacket {
        public static final ResourceLocation ID=ResourceLocation.parse("cuisinejournal:host_food_request");
        public HostRequest{if(variant.length()>16384)throw new IllegalArgumentException("Host variant too large");}
        @Override public ResourceLocation id(){return ID;}
        @Override public void write(FriendlyByteBuf buffer){buffer.writeResourceLocation(item);buffer.writeUtf(variant,16384);}
        public static HostRequest read(FriendlyByteBuf buffer){return new HostRequest(buffer.readResourceLocation(),buffer.readUtf(16384));}
    }
    public record HostResponse(ResourceLocation item,String variant,Response food) implements CuisineJournalPacket {
        public static final ResourceLocation ID=ResourceLocation.parse("cuisinejournal:host_food_response");
        @Override public ResourceLocation id(){return ID;}
        @Override public void write(FriendlyByteBuf buffer){buffer.writeResourceLocation(item);buffer.writeUtf(variant,16384);food.write(buffer);}
        public static HostResponse read(FriendlyByteBuf buffer){return new HostResponse(buffer.readResourceLocation(),buffer.readUtf(16384),Response.read(buffer));}
    }

    public record Effect(ResourceLocation id, int level, int durationTicks) {
        private void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(id);
            buffer.writeVarInt(level);
            buffer.writeVarInt(durationTicks);
        }

        private static Effect read(FriendlyByteBuf buffer) {
            return new Effect(buffer.readResourceLocation(), buffer.readVarInt(), buffer.readVarInt());
        }
    }

    /**
     * @param levelBonuses flavor "effect level +N" totals at this star level (effect id → levels);
     *                     {@code effects} already include them, the tooltip lists them separately
     */
    public record Response(ResourceLocation item, boolean drink, float healthPoints,
                           float regenPerSecond, int durationTicks, int slotCost,
                           boolean repeatable, int eatingTicks, float drinkBonus, List<Effect> effects, int stars,
                           java.util.Map<ResourceLocation, Integer> levelBonuses) {

        public Response(ResourceLocation item, boolean drink, float healthPoints, float regenPerSecond,
                        int durationTicks, int slotCost, boolean repeatable, int eatingTicks,
                        float drinkBonus, List<Effect> effects) {
            this(item,drink,healthPoints,regenPerSecond,durationTicks,slotCost,repeatable,eatingTicks,drinkBonus,effects,0);
        }

        public Response(ResourceLocation item, boolean drink, float healthPoints, float regenPerSecond,
                        int durationTicks, int slotCost, boolean repeatable, int eatingTicks,
                        float drinkBonus, List<Effect> effects, int stars) {
            this(item,drink,healthPoints,regenPerSecond,durationTicks,slotCost,repeatable,eatingTicks,drinkBonus,effects,stars,java.util.Map.of());
        }

        public Response {
            effects = List.copyOf(effects);
            levelBonuses = levelBonuses == null ? java.util.Map.of() : java.util.Map.copyOf(levelBonuses);
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(item);
            buffer.writeBoolean(drink);
            buffer.writeFloat(healthPoints);
            buffer.writeFloat(regenPerSecond);
            buffer.writeVarInt(durationTicks);
            buffer.writeVarInt(slotCost);
            buffer.writeBoolean(repeatable);
            buffer.writeVarInt(eatingTicks);
            buffer.writeFloat(drinkBonus);
            buffer.writeVarInt(effects.size());
            effects.forEach(effect -> effect.write(buffer));
            buffer.writeByte(stars);
            buffer.writeVarInt(levelBonuses.size());
            levelBonuses.forEach((id, levels) -> { buffer.writeResourceLocation(id); buffer.writeVarInt(levels); });
        }

        private static Response read(FriendlyByteBuf buffer) {
            ResourceLocation item = buffer.readResourceLocation();
            boolean drink = buffer.readBoolean();
            float health = buffer.readFloat();
            float regen = buffer.readFloat();
            int duration = buffer.readVarInt();
            int slots = buffer.readVarInt();
            boolean repeatable = buffer.readBoolean();
            int eatingTicks = buffer.readVarInt();
            float drinkBonus = buffer.readFloat();
            int count = buffer.readVarInt();
            if (count < 0 || count > 64) {
                throw new IllegalArgumentException("Invalid food effect count " + count);
            }
            List<Effect> effects = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                effects.add(Effect.read(buffer));
            }
            int stars = buffer.readUnsignedByte();
            int bonusCount = buffer.readVarInt();
            if (bonusCount < 0 || bonusCount > 64) throw new IllegalArgumentException("Invalid level bonus count " + bonusCount);
            java.util.Map<ResourceLocation, Integer> bonuses = new java.util.LinkedHashMap<>();
            for (int i = 0; i < bonusCount; i++) bonuses.put(buffer.readResourceLocation(), buffer.readVarInt());
            return new Response(item, drink, health, regen, duration, slots, repeatable,
                    eatingTicks, drinkBonus, effects, stars, bonuses);
        }
    }

    public record FruitRequest(ResourceLocation item, int stars, List<String> fruits) implements CuisineJournalPacket {
        public static final ResourceLocation ID = ResourceLocation.parse("cuisinejournal:fruit_food_request");
        public FruitRequest { fruits = List.copyOf(fruits); if (fruits.size() > 22) throw new IllegalArgumentException("Too many fruits"); }
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(item); buffer.writeByte(stars); buffer.writeVarInt(fruits.size());
            fruits.forEach(value -> buffer.writeUtf(value, 32));
        }
        public static FruitRequest read(FriendlyByteBuf buffer) {
            ResourceLocation item = buffer.readResourceLocation(); int stars = buffer.readUnsignedByte();
            int count = buffer.readVarInt();
            if (stars > 5 || count < 0 || count > 22) throw new IllegalArgumentException("Invalid fruit request");
            List<String> fruits = new ArrayList<>();
            for (int i = 0; i < count; i++) fruits.add(buffer.readUtf(32));
            return new FruitRequest(item, stars, fruits);
        }
    }

    public record FruitResponse(List<String> fruits, Response food) implements CuisineJournalPacket {
        public static final ResourceLocation ID = ResourceLocation.parse("cuisinejournal:fruit_food_response");
        public FruitResponse { fruits = List.copyOf(fruits); }
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(fruits.size()); fruits.forEach(value -> buffer.writeUtf(value, 32)); food.write(buffer);
        }
        public static FruitResponse read(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > 22) throw new IllegalArgumentException("Invalid fruit response");
            List<String> fruits = new ArrayList<>();
            for (int i = 0; i < count; i++) fruits.add(buffer.readUtf(32));
            return new FruitResponse(fruits, Response.read(buffer));
        }
    }

    public record Batch(boolean first, List<Response> foods) implements CuisineJournalPacket {
        public static final ResourceLocation ID = new ResourceLocation(CuisineJournal.MOD_ID, "food_info_batch");

        public Batch {
            foods = List.copyOf(foods);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(first);
            buffer.writeVarInt(foods.size());
            foods.forEach(food -> food.write(buffer));
        }

        public static Batch read(FriendlyByteBuf buffer) {
            boolean first = buffer.readBoolean();
            int count = buffer.readVarInt();
            if (count < 0 || count > 64) {
                throw new IllegalArgumentException("Invalid food batch size " + count);
            }
            List<Response> foods = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                foods.add(Response.read(buffer));
            }
            return new Batch(first, foods);
        }
    }
}
