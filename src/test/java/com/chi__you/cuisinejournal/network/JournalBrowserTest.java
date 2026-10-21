package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.client.journal.GridList;
import com.chi__you.cuisinejournal.client.ClientJournalState;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.stream.IntStream;

/** Packet and grid geometry checks without a running Minecraft client. */
public final class JournalBrowserTest {
    private static int checks;
    public static void main(String[] args) {
        ResourceLocation root = id("drink"), sub = id("drink/tea"), food = id("tea");
        Packets.DishView dish = new Packets.DishView(food, "test", root, List.of(id("crafting")),
                3, 0.5F, sub, new ResourceLocation("minecraft", "the_nether"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            dish.write(buffer);
            check(dish.equals(Packets.DishView.read(buffer)), "dish metadata round trip");
            check(buffer.readableBytes() == 0, "dish consumes all fields");
            Packets.CollectionView collection = new Packets.CollectionView(root, "Tea", true, food, 2, 1, 0,
                    List.of(new Packets.SubcategoryView(sub, "Tea", true, food),
                            new Packets.SubcategoryView(id("drink/_other"), "fallback", false, null)), false);
            collection.write(buffer);
            check(collection.equals(Packets.CollectionView.read(buffer)), "nested category round trip");
            check(buffer.readableBytes() == 0, "category consumes all fields");
            var rules=new Packets.SyncRules(100,63,0,100,List.of(1,5,15,30,60),"LIGHT",false,true,
                    0,0.08,0,1,0,0,false,true,0,0.07,2,1.75);
            rules.write(buffer);
            check(rules.equals(Packets.SyncRules.read(buffer)),"vanilla reward parameters round trip with rules");
            check(buffer.readableBytes()==0,"rules consume all quality reward fields");
        } finally { buffer.release(); }
        Packets.DishView legacy = new Packets.DishView(food, "test", root, List.of(), 0, 0);
        check(legacy.subcategory().equals(root), "legacy implicit subcategory");
        check(legacy.dimension().toString().equals("minecraft:overworld"), "legacy default dimension");
        GridList<Integer> grid = new GridList<>(20, 20, 2, false, (g, item, x, y, w, h, hover, selected) -> { });
        grid.bounds(10, 10, 181, 44);
        grid.items(IntStream.range(0, 1000).boxed().toList());
        check(grid.columns() == 8, "wide food grid has eight columns");
        check(grid.itemAt(11, 11) == 0, "first cell hit");
        check(grid.itemAt(31, 11) == null, "horizontal gap rejected");
        check(grid.itemAt(11, 31) == null, "vertical gap rejected");
        check(grid.itemAt(189, 11) == null, "scrollbar rejected");
        grid.scroll(22);
        check(grid.itemAt(11, 11) == 8, "scroll and hit test agree");
        grid.selected(32);
        grid.bounds(10, 10, 115, 44);
        check(grid.columns() == 5 && grid.selected() == 32, "resize keeps selection");
        grid.moveSelection(5);
        check(grid.selected() == 37 && grid.scroll() > 22, "keyboard selection scrolls into view");
        grid.items(List.of(0));
        check(grid.selected() == null && grid.scroll() == 0, "filter removes stale selection and clamps scroll");
        check(!grid.mouseScrolled(0, 0, 1), "outside scroll ignored");
        grid.bounds(0, 0, 0, 0);
        check(grid.itemAt(0, 0) == null, "empty bounds ignored");
        GridList<Integer> cards = new GridList<>(88, 62, 6, false, (g, item, x, y, w, h, hover, selected) -> { });
        cards.bounds(0, 0, 372, 100);
        cards.items(IntStream.range(0, 20).boxed().toList());
        check(cards.columns() == 4, "372px fits four fixed category cards");
        check(cards.itemAt(366, 10) == 3, "fourth category card remains clickable");
        check(cards.itemAt(371, 10) == null, "category scrollbar gap rejected");
        cards.bounds(0, 0, 212, 100);
        check(cards.columns() == 2, "narrow category view keeps fixed two-column cards");
        checkSubcategoryGrowth();
        checkIngredientViewContract();
        checkDetailOnlyIngredient();
        checkListDragging();
        checkDetailResync();
        checkActivityQuantities();
        System.out.println("JournalBrowserTest: " + checks + " checks passed");
    }

    private static void checkActivityQuantities() {
        var maker = new java.util.UUID(1,2);
        var stamp = new com.chi__you.cuisinejournal.culinary.MealStamp(3,true,5,maker,"Chef");
        var saved = com.chi__you.cuisinejournal.culinary.ActivityEntry.prepared(id("mochi"),8,4,16,stamp);
        check(saved.equals(com.chi__you.cuisinejournal.culinary.ActivityEntry.load(saved.save())),
                "activity NBT retains quantity, perfect quality, cap and actual maker");
        var old = saved.save(); old.remove("amount"); old.remove("perfect"); old.remove("starCap"); old.remove("cookId");
        var restored = com.chi__you.cuisinejournal.culinary.ActivityEntry.load(old);
        check(restored.amount()==1 && !restored.perfect() && restored.starCap()==5,
                "legacy activities keep one occurrence without inventing old batch quantities");
        var first = new Packets.ActivityView("meal_prepared",id("mochi"),8,4,3,"Chef",16,false,5,maker);
        var second = new Packets.ActivityView("meal_prepared",id("mochi"),2,3,3,"Chef",32,false,5,maker);
        var perfect = new Packets.ActivityView("meal_prepared",id("mochi"),9,4,3,"Chef",1,true,5,maker);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            perfect.write(buffer);
            check(perfect.equals(Packets.ActivityView.read(buffer)) && buffer.readableBytes()==0,
                    "perfect quantity/maker packet round trip");
        } finally { buffer.release(); }
        var folded = com.chi__you.cuisinejournal.client.journal.ActivityPresentation.fold(List.of(first,second));
        check(folded.size()==1 && folded.get(0).count()==48,"two take operations count 48 foods, not two clicks");
        check(com.chi__you.cuisinejournal.client.journal.ActivityPresentation.fold(List.of(first,perfect,second)).size()==3,
                "perfect food is a separate activity and never joins ordinary runs");
        check(com.chi__you.cuisinejournal.client.journal.ActivityPresentation.stampFor(perfect).equals(stamp),
                "hover tooltip uses real perfect stamp rather than current player mastery");
        var higher = new Packets.ActivityView("meal_prepared",id("mochi"),2,4,4,"Chef",1,false,5,maker);
        check(!com.chi__you.cuisinejournal.client.journal.ActivityPresentation.sameLine(first,higher),"different quality never folds");
        var anotherMaker = new Packets.ActivityView("meal_prepared",id("mochi"),2,4,3,"Other",1,false,5,new java.util.UUID(3,4));
        check(!com.chi__you.cuisinejournal.client.journal.ActivityPresentation.sameLine(first,anotherMaker),"different makers retain distinct tooltips");
        var batch = com.chi__you.cuisinejournal.client.journal.ActivityPresentation.fold(List.of(second));
        check(batch.get(0).count()==32,"single bulk extraction shows actual batch count");
        var huge = new Packets.ActivityView("meal_prepared",id("mochi"),2,4,3,"Chef",Long.MAX_VALUE,false,5,maker);
        check(com.chi__you.cuisinejournal.client.journal.ActivityPresentation.fold(List.of(huge,first)).get(0).count()==Long.MAX_VALUE,
                "large folded quantity cannot overflow negative");
    }

    private static void checkDetailResync() {
        ClientJournalState.clear();
        ResourceLocation food = id("coffee"), milk = id("milk");
        var detail = new Packets.SyncDishDetail(food,List.of(milk),List.of());
        ClientJournalState.acceptDetail(detail);
        long generation = ClientJournalState.detailGeneration();
        ClientJournalState.acceptProfile(new Packets.SyncProfile(true,0,0,0,0,0,0,0,List.of(),List.of(),List.of()));
        check(ClientJournalState.detailGeneration()==generation && ClientJournalState.detail(food).equals(detail),
                "profile updates retain ingredient detail and outstanding request generation");
        ClientJournalState.acceptCatalogue(new Packets.SyncCatalogue(true,true,List.of()));
        check(ClientJournalState.detailGeneration()>generation && ClientJournalState.detail(food)==null,
                "recipe viewer return catalogue resync invalidates detail request generation");
        generation = ClientJournalState.detailGeneration();
        ClientJournalState.acceptDetail(detail);
        check(ClientJournalState.detailGeneration()==generation && ClientJournalState.detail(food).ingredients().equals(List.of(milk)),
                "detail response restores ingredients without invalidating other outstanding requests");
        ClientJournalState.clear();
        check(ClientJournalState.detailGeneration()>generation && ClientJournalState.detail(food)==null,
                "disconnect discards old ingredient detail generation");
    }

    private static void checkDetailOnlyIngredient() {
        var direct = new Packets.IngredientView(id("salt"), true, 2, 1, 2, 1, 0,
                (byte)2, List.of(), false, null, List.of(), List.of(id("soup")));
        var member = new Packets.IngredientView(id("seed"), true, 7, 1, 2, 0, 0,
                (byte)2, List.of(), false, null, List.of(), List.of(), false, List.of(), List.of(id("seeds")));
        var tag = new Packets.IngredientView(id("seeds"), true, 0, Long.MIN_VALUE, Long.MIN_VALUE,
                1, 0, (byte)3, List.of(), false, null, List.of(), List.of(id("soup")), true,
                List.of(id("seed")), List.of(id("seeds")));
        check(direct.listed() && direct.countedIngredient(), "direct ingredient appears in list and counts");
        check(!member.listed() && !member.countedIngredient(), "tag-only member does not appear or count");
        check(tag.listed() && !tag.countedIngredient(), "tag remains browsable without counting as a physical ingredient");
        ClientJournalState.clear();
        ClientJournalState.acceptExtras(new Packets.SyncJournalExtras(List.of(),List.of(),
                List.of(direct,member,tag),List.of(),List.of(),1));
        check(ClientJournalState.ingredientsDiscovered()==1 && ClientJournalState.totalIngredients()==1,
                "overview count excludes tag-only members and tag entries");
        check(ClientJournalState.ingredients().contains(member), "detail-only member remains available for navigation");
        ClientJournalState.acceptProfile(new Packets.SyncProfile(true,0,0,0,0,30,0,0,List.of(),List.of(),List.of()));
        check(ClientJournalState.ingredientsDiscovered()==1 && ClientJournalState.ingredients().stream()
                .anyMatch(value -> value.item().equals(id("seed")) && value.tagIds().contains(id("seeds"))),
                "profile update retains hidden member detail and filtered discovery count");
        ClientJournalState.clear();
    }

    private static void checkListDragging() {
        var rows = new com.chi__you.cuisinejournal.client.journal.ScrollList<Integer>(29, 3,
                (g, item, x, y, w, h, hover) -> {});
        rows.bounds(10, 20, 176, 64);
        rows.items(IntStream.range(0, 20).boxed().toList());
        check(rows.itemAt(185, 25) == null, "ingredient scrollbar cannot select a row");
        check(rows.mouseClicked(184, 22, 0), "ingredient scrollbar begins mouse drag");
        check(rows.mouseDragged(400, 300, 0), "ingredient scrollbar drag continues outside its track");
        check(Integer.valueOf(18).equals(rows.itemAt(20, 24)), "ingredient scrollbar reaches last viewport");
        check(rows.mouseReleased(0), "ingredient scrollbar releases capture");
        check(!rows.mouseDragged(184, 25, 0), "released ingredient scrollbar does not move");
        rows.scrollToTop();
        rows.ensureVisible(value -> value == 18);
        check(Integer.valueOf(18).equals(rows.itemAt(20, 60)), "detail navigation reveals distant selected row");
        rows.ensureVisible(value -> value == 0);
        check(Integer.valueOf(0).equals(rows.itemAt(20, 24)), "navigation back reveals first selected row");
        rows.items(List.of(0,18,19));
        rows.scrollToTop(); rows.ensureVisible(value -> value == 19);
        check(Integer.valueOf(19).equals(rows.itemAt(20, 60)), "filtered list follows retained selection");
        rows.ensureVisible(value -> value == 100);
        check(Integer.valueOf(19).equals(rows.itemAt(20, 60)), "missing target leaves scroll unchanged");
    }

    private static void checkIngredientViewContract() {
        Packets.IngredientView ingredient = new Packets.IngredientView(
                id("flour"), true, 7L, 12L, 18L, 4, 2, (byte) 0,
                List.of(new ResourceLocation("cuisinejournal", "grain")), true,
                new ResourceLocation("minecraft", "speed"), List.of(id("crafting")),
                List.of(id("bread"), id("pie"), id("soup"), id("stew")));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ingredient.write(buffer);
            check(ingredient.equals(Packets.IngredientView.read(buffer)), "ingredient view contract round trip");
            check(buffer.readableBytes() == 0, "ingredient view consumes all fields");
            Packets.IngredientView unknown = new Packets.IngredientView(id("salt"), false,
                    0, Long.MIN_VALUE, Long.MIN_VALUE, 1, 0, (byte) 2, List.of(),
                    false, null, List.of(), List.of(id("soup")));
            unknown.write(buffer);
            check(unknown.equals(Packets.IngredientView.read(buffer)), "unknown ingredient and null effect round trip");
            var named = new Packets.IngredientView(id("meat_group"), true, 0, Long.MIN_VALUE,
                    Long.MIN_VALUE, 2, 1, (byte) 3, List.of(id("meat")), false, null,
                    List.of(), List.of(id("soup")), true, List.of(id("beef"), id("pork")),
                    List.of(id("raw_meat")), "Custom meat group");
            named.write(buffer);
            check(named.equals(Packets.IngredientView.read(buffer)), "named merged tag packet round trip");
            check(buffer.readableBytes() == 0, "named merged tag consumes all fields");
            check("Custom meat group".equals(named.withDiscovered(2).displayName()), "profile refresh preserves custom name");
            var origins = new Packets.IngredientView(id("egg_group"), true, 0, Long.MIN_VALUE,
                    Long.MIN_VALUE, 1, 0, (byte)3, List.of(), true, id("effect"), List.of(),
                    List.of(id("pie")), true, List.of(id("egg"),id("quail_egg")),
                    List.of(id("eggs"),id("pie_eggs")), null,
                    List.of(new Packets.IngredientTagSource(id("eggs"), List.of(id("egg"),id("quail_egg"))),
                            new Packets.IngredientTagSource(id("pie_eggs"),List.of(id("egg")))),
                    List.of(new Packets.IngredientTagUse(id("pie"),id("pie_recipe"),2,id("pie_eggs"),List.of(id("egg")))));
            origins.write(buffer);
            check(origins.equals(Packets.IngredientView.read(buffer)), "tag group original members and recipe branches round trip");
            check(buffer.readableBytes()==0, "tag provenance packet consumes every field");
            var flavored=new Packets.IngredientView(named.item(),named.known(),named.timesUsed(),named.firstUsedDay(),named.lastUsedDay(),
                    named.mealCount(),named.discoveredCount(),named.kind(),named.types(),true,named.effect(),named.methods(),named.meals(),
                    named.tag(),named.members(),named.tagIds(),named.displayName(),named.tagSources(),named.tagUses(),
                    new Packets.FlavorInfo(true,List.of(id("sweet"),id("rich")),true,false,
                            List.of(new Packets.InheritedFlavor(id("sugar"),List.of(id("sweet")))),List.of("unknown:test:missing")));
            flavored.write(buffer);
            check(flavored.equals(Packets.IngredientView.read(buffer)),"extended multi-flavor ingredient payload round trip");
            check(buffer.readableBytes()==0,"flavor payload consumes every new field");
            check(origins.withDiscovered(1).tagUses().equals(origins.tagUses())
                    && origins.withDiscovered(1).tagSources().equals(origins.tagSources()),
                    "profile refresh preserves tag provenance");
        } finally { buffer.release(); }
        ClientJournalState.clear();
        var linked = new Packets.IngredientView(id("salt"), true, 1, 1, 1, 1, 0,
                (byte) 2, List.of(), false, null, List.of(), List.of(id("soup")));
        ClientJournalState.acceptExtras(new Packets.SyncJournalExtras(List.of(), List.of(),
                List.of(linked), List.of(), List.of(), 1));
        ClientJournalState.acceptProfile(new Packets.SyncProfile(true, 0, 0, 0, 0, 0, 0, 0,
                List.of(new Packets.RecordView(id("soup"), true, 1, 0, 0, false, false, 1, 1)), List.of(), List.of()));
        check(ClientJournalState.ingredients().get(0).discoveredCount() == 1,
                "profile delta refreshes ingredient discovery count");
        ClientJournalState.acceptProfile(new Packets.SyncProfile(true, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of()));
        check(ClientJournalState.ingredients().get(0).discoveredCount() == 0,
                "profile reset refreshes ingredient discovery count");
        ClientJournalState.clear();
    }

    private static void checkSubcategoryGrowth() {
        ResourceLocation root = id("drinks"), tea = id("drinks/tea"), coffee = id("drinks/coffee");
        ResourceLocation green = id("green_tea"), black = id("black_tea"), espresso = id("espresso");
        ClientJournalState.clear();
        ClientJournalState.acceptCatalogue(new Packets.SyncCatalogue(true, true, List.of(
                new Packets.DishView(green, "test", root, List.of(), 2, 0, tea, id("dimension")),
                new Packets.DishView(black, "test", root, List.of(), 2, 0, tea, id("dimension")),
                new Packets.DishView(espresso, "test", root, List.of(), 2, 0, coffee, id("dimension")))));
        ClientJournalState.acceptProfile(new Packets.SyncProfile(true, 0, 0, 0, 0, 0, 0, 0,
                List.of(new Packets.RecordView(green, true, 2, 0, 0, false, false, 1, 1),
                        new Packets.RecordView(black, true, 18, 0, 0, false, false, 1, 1),
                        new Packets.RecordView(espresso, true, 300, 0, 0, false, false, 1, 1)), List.of(), List.of()));
        check(ClientJournalState.categoryMasteryPoints(green) == 20, "tea preparations shared within subcategory");
        check(ClientJournalState.categoryMasteryPoints(espresso) == 300, "coffee preparations independent of tea");
        check(ClientJournalState.rank(green).ordinal() == 2 && ClientJournalState.rank(espresso).ordinal() == 5,
                "different subcategories of one root earn different ranks");
        ClientJournalState.acceptCatalogue(new Packets.SyncCatalogue(true, true, List.of(
                new Packets.DishView(green, "test", root, List.of(), 2, 0, tea, id("dimension")),
                new Packets.DishView(black, "test", root, List.of(), 2, 0, coffee, id("dimension")),
                new Packets.DishView(espresso, "test", root, List.of(), 2, 0, coffee, id("dimension")))));
        check(ClientJournalState.categoryMasteryPoints(green) == 2
                && ClientJournalState.categoryMasteryPoints(espresso) == 318,
                "moving a food recomputes subcategory totals without losing per-food records");
        check(ClientJournalState.record(black).prepared() == 18, "category edit retains food history");
        ClientJournalState.clear();
    }
    private static ResourceLocation id(String path) { return new ResourceLocation("test", path); }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
}
