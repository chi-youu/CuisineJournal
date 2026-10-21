package com.chi__you.cuisinejournal.client.journal.page;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.EditClientState;
import com.chi__you.cuisinejournal.client.journal.JournalText;
import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.client.journal.GridList;
import com.chi__you.cuisinejournal.client.journal.FoodDetailPanel;
import com.chi__you.cuisinejournal.client.journal.JournalDropdown;
import com.chi__you.cuisinejournal.client.journal.JournalSearchField;
import com.chi__you.cuisinejournal.client.journal.CreativeFoodOrder;
import com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat;
import com.chi__you.cuisinejournal.client.journal.edit.SchemeModal;
import com.chi__you.cuisinejournal.client.journal.edit.*;
import com.chi__you.cuisinejournal.network.Network;
import net.minecraft.client.gui.screens.Screen;
import com.chi__you.cuisinejournal.mastery.CategoryMastery;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import net.minecraft.network.chat.MutableComponent;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.Locale;

/**
 * The food-category entry page.
 *
 * <p>Three browsing levels share the journal frame. Each group caches its
 * scroll and selected food; progress reflects the server's active per-dish or
 * subcategory mastery scope.</p>
 */
public final class CollectionsPage extends BasePage {
    private static final int SEARCH_WIDTH = 92;
    private int gridSearchX, gridSearchY;
    private enum Level { ROOT, SUBCATEGORY, GRID }
    private enum Status { ALL, DISCOVERED, UNDISCOVERED }
    private enum Sort { CREATIVE, PREPARED, NUTRITION_ASC, NUTRITION_DESC, CATEGORY }
    private record Key(ResourceLocation category, ResourceLocation subcategory) { }
    private record Group(Packets.SubcategoryView view, List<ResourceLocation> foods,
                         long prepared, int discovered) {
        int cap() { return CategoryMastery.rankCap(discovered, foods.size()); }
        int rank() { return CategoryMastery.rank(prepared, discovered, foods.size(),
                ClientJournalState.masteryCurve()).ordinal(); }
        /** Stars this group contributes: its own rank, or (per-dish scope) the sum of its dishes' ranks. */
        int stars() {
            if (!ClientJournalState.perDishMastery()) return rank();
            int sum = 0;
            for (ResourceLocation food : foods) sum += ClientJournalState.rank(food).ordinal();
            return sum;
        }
        /** Maximum of {@link #stars()}: five, or five per dish in per-dish scope. */
        int maxStars() {
            return ClientJournalState.perDishMastery() ? foods.size() * MasteryRank.highestIndex()
                    : MasteryRank.highestIndex();
        }
        /** Whether more stars can still be earned right now. */
        boolean canRankUp() {
            if (!ClientJournalState.perDishMastery()) return cap() > rank();
            for (ResourceLocation food : foods) {
                if (ClientJournalState.isDiscovered(food)
                        && ClientJournalState.rank(food).ordinal() < MasteryRank.highestIndex()) return true;
            }
            return false;
        }
    }
    private final GridList<ResourceLocation> roots = new GridList<>(150, 56, 6, true, this::rootCard);
    private final Map<ResourceLocation, GridList<ResourceLocation>> subgrids = new HashMap<>();
    private final Map<Key, Session> sessions = new HashMap<>();
    private final Map<ResourceLocation, Packets.CollectionView> categories = new LinkedHashMap<>();
    private final Map<Key, Group> groups = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<ResourceLocation>> children = new LinkedHashMap<>();
    private final Map<ResourceLocation, Component> names = new HashMap<>();
    private final CreativeFoodOrder creativeOrder = new CreativeFoodOrder();
    private Level level = Level.ROOT;
    private ResourceLocation category;
    private Key key;
    private long revision = -1;
    private long searchRevision = -1, flavorRevision = -1;
    private final Map<ResourceLocation,List<String>> searchExtras = new HashMap<>();
    private String language = "";
    private EditBox search;
    private JournalDropdown<ResourceLocation> dimensions;
    private JournalDropdown<Status> statuses;
    private JournalDropdown<Sort> sorts;
    private int toolY, toolHeight, bodyY, detailX, detailWidth, bodyHeight;
    private boolean wide, detailPopup;
    private boolean editing;
    private final CategoryEditState editState = new CategoryEditState();
    private static final ResourceLocation NEW_CARD = ResourceLocation.fromNamespaceAndPath("cuisinejournal", "__editor_new__");
    private JournalMenu editMenu;
    private JournalModal modal;
    private int mouseX, mouseY;
    private ResourceLocation flashId, pendingFlash;
    private int flashTicks;
    private Packets.SyncCategoryEditor hiddenSnapshot;
    private boolean refreshHiddenAfterSuccess;
    /**
     * "全部食物" view: the home page shows every food in one grid (same filter bar and detail
     * panel as a subcategory) instead of category cards. Remembered in the client config.
     */
    private boolean allView = com.chi__you.cuisinejournal.config.ConfigManager.client().journal.allFoodsView;
    private static final ResourceLocation ALL_ID = ResourceLocation.fromNamespaceAndPath("cuisinejournal", "__all_foods__");
    private Group allGroup;
    private Session allSession;
    /** Position of each subcategory in category order, for the "按分类" sort. */
    private final Map<Key, Integer> categoryIndex = new HashMap<>();
    private boolean allGrid() { return level == Level.ROOT && allView; }
    /** Whether the food grid (subcategory grid or the all-foods grid) is showing. */
    private boolean grid() { return level == Level.GRID || allGrid(); }
    private Group currentGroup() { return allGrid() ? allGroup : groups.get(key); }
    private Session allSession() { if (allSession == null) allSession = new Session(); return allSession; }
    private static final ResourceLocation ICON_FONT =
            ResourceLocation.fromNamespaceAndPath("cuisinejournal", "tooltip");

    private final class Session {
        final GridList<ResourceLocation> grid = new GridList<>(20, 20, 2, false, CollectionsPage.this::foodCell);
        String query = "";
        ResourceLocation dimension;
        Status status = Status.ALL;
        Sort sort = Sort.PREPARED;
        int detailScroll, detailMaxScroll;

        /** Clears only the selected food; the rest of this subcategory's browse state remains. */
        void clearSelection() {
            grid.selected(null);
        }
    }

    @Override
    public String id() {
        return "collections";
    }

    @Override
    public Component title() {
        return Component.translatable("cuisinejournal.page.collections");
    }

    /** Heading / first breadcrumb of this page ("料理图鉴"); the sidebar tab keeps {@link #title()}. */
    private static Component journalLabel() {
        return Component.translatable("cuisinejournal.category.page_title");
    }

    /** Same as the ingredient page heading: bold, 10/9 scale, 2px below the content top. */
    private static final float HEADING_SCALE = 10.0F / 9.0F;
    private static final int HEADING_Y = 2;
    /** The larger root heading pushes the overview block (and the card grid) down by this much. */
    private static final int ROOT_SHIFT = 2;

    /**
     * Shows one dish in its subcategory grid with the detail panel open — used when another
     * page links here (an ingredient's dish list). Filters that would hide it are reset.
     *
     * @return false when the dish is not in any category on this client
     */
    public boolean openFood(ResourceLocation food) {
        refresh();
        Packets.DishView dish = ClientJournalState.dish(food);
        if (dish == null) return false;
        Key target = new Key(dish.category(), dish.subcategory());
        if (!groups.containsKey(target)) return false;
        if (editing) Network.toServer(new Packets.CategoryEditModeRequest(false));
        browseQuery = ""; if (browse != null) browse.setValue("");
        category = target.category(); key = target; level = Level.GRID;
        Session s = session();
        s.query = ""; s.dimension = null; s.status = Status.ALL;
        detailPopup = false;
        layoutView();
        s.grid.selected(food);
        s.detailScroll = 0;
        // Scroll the grid so the selected cell is visible.
        int[] cell = s.grid.itemBounds(food);
        if (cell != null) {
            int top = cell[1] + (int) s.grid.scroll();
            s.grid.scroll(Math.max(0, top - bodyY - Math.max(0, bodyHeight / 2 - cell[3] / 2)));
        }
        detailPopup = !wide;
        return true;
    }

    @Override
    protected void onLayout() {
        // Resize and recipe-viewer return preserve the subcategory sessions.
        refresh();
        layoutView();
    }

    private void refresh() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        boolean persistedEditing = ClientJournalState.categoryEditing() && ClientJournalState.canEditCategories();
        // Entering/leaving edit mode does not bump the journal revision, so the
        // card lists (new-category cards) must be rebuilt here explicitly.
        if (editing != persistedEditing) applyEditing(persistedEditing);
        if (revision == ClientJournalState.revision() && language.equals(lang)
                && searchRevision == com.chi__you.cuisinejournal.client.DishSearchState.revision()
                && flavorRevision == com.chi__you.cuisinejournal.client.FlavorClientState.revision()) return;
        creativeOrder.refresh();
        // Returning from JEI resyncs the catalogue and discards ingredient details.
        // Other profile updates do not invalidate outstanding detail requests.
        if (detailGeneration != ClientJournalState.detailGeneration()) {
            requestedDetails.clear();
            detailGeneration = ClientJournalState.detailGeneration();
        }
        revision = ClientJournalState.revision(); language = lang;
        searchRevision = com.chi__you.cuisinejournal.client.DishSearchState.revision();
        flavorRevision = com.chi__you.cuisinejournal.client.FlavorClientState.revision();
        searchExtras.clear();
        categories.clear(); groups.clear(); children.clear(); names.clear(); categoryIndex.clear();
        Map<Key, List<ResourceLocation>> members = new HashMap<>();
        for (Packets.DishView dish : ClientJournalState.dishes()) {
            names.put(dish.target(), JournalText.nameOf(dish.target()));
            var metadata = com.chi__you.cuisinejournal.client.DishSearchState.get(dish.target());
            List<String> extras = new ArrayList<>();
            if (metadata != null) {
                for (var id : metadata.flavors()) {
                    if (!com.chi__you.cuisinejournal.client.journal.FlavorUi.visible(id)) continue;
                    extras.add(com.chi__you.cuisinejournal.client.journal.FlavorUi.name(id).getString());
                    extras.add(id.toString());
                }
                for (var id : metadata.effects()) {
                    var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(id).orElse(null);
                    if (effect != null) { extras.add(effect.getDisplayName().getString()); extras.add(id.toString()); }
                }
            }
            searchExtras.put(dish.target(), List.copyOf(extras));
            members.computeIfAbsent(new Key(dish.category(), dish.subcategory()), unused -> new ArrayList<>())
                    .add(dish.target());
        }
        for (Packets.CollectionView root : ClientJournalState.collections()) {
            categories.put(root.id(), root);
            List<ResourceLocation> ids = new ArrayList<>();
            for (Packets.SubcategoryView sub : root.subcategories()) {
                Key k = new Key(root.id(), sub.id());
                List<ResourceLocation> foods = members.getOrDefault(k, List.of());
                long prepared = 0; int found = 0;
                java.util.Set<ResourceLocation> countedPreparations=new java.util.HashSet<>();
                for (ResourceLocation food : foods) {
                    if (ClientJournalState.dish(food) == null || names.get(food) == null) continue;
                    Packets.RecordView record = ClientJournalState.record(food);
                    if (record != null) {
                        if(countedPreparations.add(ClientJournalState.preparationTarget(food)))prepared += record.prepared()+record.bonusMasteryPoints();
                        if (record.discovered()) found++;
                    }
                }
                List<ResourceLocation> validFoods = foods.stream()
                        .filter(food -> ClientJournalState.dish(food) != null && names.get(food) != null)
                        .toList();
                groups.put(k, new Group(sub, validFoods, prepared, found)); ids.add(sub.id());
                categoryIndex.put(k, categoryIndex.size());
            }
            children.put(root.id(), List.copyOf(ids));
        }
        updateCards();
        subgrids.keySet().retainAll(categories.keySet()); sessions.keySet().retainAll(groups.keySet());
        if (category != null && !categories.containsKey(category)) {
            level = Level.ROOT; category = null; key = null;
        } else if (key != null && !groups.containsKey(key)) { level = Level.SUBCATEGORY; key = null; }
        sessions.forEach((k, s) -> rebuildFoods(groups.get(k), s));
        java.util.LinkedHashSet<ResourceLocation> every = new java.util.LinkedHashSet<>();
        long allPrepared = 0; int allFound = 0;
        for (Group group : groups.values()) {
            every.addAll(group.foods()); allPrepared += group.prepared(); allFound += group.discovered();
        }
        allGroup = new Group(new Packets.SubcategoryView(ALL_ID, "cuisinejournal.category.grand_food", false, null),
                List.copyOf(every), allPrepared, allFound);
        if (allSession != null) rebuildFoods(allGroup, allSession);
        layoutView();
    }
    private GridList<ResourceLocation> subgrid(ResourceLocation root) {
        return subgrids.computeIfAbsent(root, unused -> new GridList<>(88, 62, 6, false, this::subCard));
    }
    private Session session() { return allGrid() ? allSession() : sessions.computeIfAbsent(key, unused -> new Session()); }

    private void updateCards() {
        rebuildSearchHits();
        List<ResourceLocation> ids = new ArrayList<>(categories.keySet().stream().filter(this::rootVisible).toList());
        // "New" cards would only clutter search results.
        if (editing && !browsing()) ids.add(NEW_CARD);
        roots.items(ids);
        children.forEach((root, subs) -> {
            List<ResourceLocation> values = new ArrayList<>(subs.stream()
                    .filter(sub -> groupVisible(new Key(root, sub))).toList());
            // Don't add NEW_CARD to mod categories
            Packets.CollectionView rootCategory = categories.get(root);
            if (editing && !browsing() && (rootCategory == null || !rootCategory.isModCategory())) {
                values.add(NEW_CARD);
            }
            subgrid(root).items(values);
        });
    }

    // ------------------------------------------------------------ category search

    /** Query typed in the browse box shown on the root and subcategory levels. */
    private String browseQuery = "";
    private EditBox browse;
    /** Matching food count per subcategory for the current query. */
    private final Map<Key, Integer> searchHits = new HashMap<>();

    private boolean browsing() { return !browseQuery.isBlank(); }
    private String needle() { return browseQuery.strip().toLowerCase(Locale.ROOT); }
    private boolean nameMatches(Component name) {
        var parsed = com.chi__you.cuisinejournal.client.journal.IngredientSearch.parse(browseQuery);
        return parsed.mod() == null && name != null
                && com.chi__you.cuisinejournal.recipe.PinyinMatch.contains(name.getString(), parsed.text());
    }
    private boolean foodMatches(ResourceLocation food) {
        return foodMatches(food,browseQuery);
    }
    private boolean foodMatches(ResourceLocation food, String query) {
        var dish = ClientJournalState.dish(food);
        if (dish == null || names.get(food) == null) return false;
        String mod = dish.sourceModId();
        var root = categories.get(dish.category());
        var group = groups.get(new Key(dish.category(),dish.subcategory()));
        List<String> categoryNames = new ArrayList<>();
        if (root != null) categoryNames.add(rootName(root).getString());
        if (group != null) categoryNames.add(groupName(group.view()).getString());
        return com.chi__you.cuisinejournal.client.journal.DishSearch.matches(query,food.toString(),names.get(food).getString(),mod,
                List.of(JournalText.modName(mod).getString(),com.chi__you.cuisinejournal.platform.Services.PLATFORM.modDisplayName(mod)),
                categoryNames,searchExtras.getOrDefault(food,List.of()));
    }
    private void rebuildSearchHits() {
        searchHits.clear();
        if (!browsing()) return;
        groups.forEach((k, group) -> {
            int hits = (int) group.foods().stream().filter(this::foodMatches).count();
            if (hits > 0) searchHits.put(k, hits);
        });
    }
    private boolean rootNameMatches(ResourceLocation id) {
        Packets.CollectionView root = categories.get(id);
        return root != null && nameMatches(rootName(root));
    }
    /** A subcategory stays when its root or its own name matches, or it holds a matching food. */
    private boolean groupVisible(Key k) {
        if (!browsing()) return true;
        Group group = groups.get(k);
        return rootNameMatches(k.category()) || group != null && nameMatches(groupName(group.view()))
                || searchHits.containsKey(k);
    }
    private boolean rootVisible(ResourceLocation id) {
        if (!browsing() || rootNameMatches(id)) return true;
        return children.getOrDefault(id, List.of()).stream().anyMatch(sub -> groupVisible(new Key(id, sub)));
    }
    private int rootHits(ResourceLocation id) {
        return children.getOrDefault(id, List.of()).stream()
                .mapToInt(sub -> searchHits.getOrDefault(new Key(id, sub), 0)).sum();
    }

    /** Box geometry {x, y, width, height}: on the breadcrumb row, left of the edit pencil when it is shown. */
    private int[] browseBounds() {
        int rightEdge = right();
        if (ClientJournalState.canEditCategories()) {
            rightEdge = pencilBounds()[0] - 4;
            if (editing) rightEdge -= font().width(tr("cuisinejournal.edit.mode")) + 6 + exportButtonWidth() + 6;
        }
        int w = Math.max(60, Math.min(110, rightEdge - x - 110));
        // On the home page the view toggle takes the right end of the box; its left edge stays put.
        if (level == Level.ROOT) return new int[]{rightEdge - w, y - 1, Math.max(36, w - VIEW_TOGGLE_W - 4), 12};
        return new int[]{rightEdge - w, y - 1, w, 12};
    }

    private static final int VIEW_TOGGLE_W = 24;
    /** x of the first (cards) toggle button; the grid button sits 13px to its right. */
    private int viewToggleX() {
        int rightEdge = right();
        if (ClientJournalState.canEditCategories()) {
            rightEdge = pencilBounds()[0] - 4;
            if (editing) rightEdge -= font().width(tr("cuisinejournal.edit.mode")) + 6 + exportButtonWidth() + 6;
        }
        return rightEdge - VIEW_TOGGLE_W;
    }
    private void renderViewToggle(GuiGraphics g, int mx, int my) {
        int bx = viewToggleX();
        for (int i = 0; i < 2; i++) {
            boolean gridButton = i == 1, active = gridButton == allView;
            int px = bx + i * 13;
            boolean hover = inside(mx, my, px, y, 11, 11);
            squareButton(g, px, y, 11, hover && !active, active);
            int color = active ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM;
            if (gridButton) {
                // 4x4 dots: every food at once.
                for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++)
                    g.fill(px + 2 + c * 2, y + 2 + r * 2, px + 3 + c * 2, y + 3 + r * 2, color);
            } else {
                // 2x2 blocks: category cards.
                for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++)
                    g.fill(px + 2 + c * 4, y + 2 + r * 4, px + 5 + c * 4, y + 5 + r * 4, color);
            }
            if (hover) g.renderTooltip(font(), tr(gridButton ? "cuisinejournal.category.view.grid"
                    : "cuisinejournal.category.view.cards"), mx, my);
        }
    }
    private boolean viewToggleClick(double mx, double my, int button) {
        if (level != Level.ROOT || button != 0) return false;
        int bx = viewToggleX();
        for (int i = 0; i < 2; i++) {
            if (!inside(mx, my, bx + i * 13, y, 11, 11)) continue;
            boolean wantGrid = i == 1;
            if (wantGrid != allView) {
                CuisineUiSoundCompat.click();
                allView = wantGrid;
                com.chi__you.cuisinejournal.config.ConfigManager.client().journal.allFoodsView = allView;
                com.chi__you.cuisinejournal.config.ConfigManager.saveClient(com.chi__you.cuisinejournal.config.ConfigManager.client());
                // Both views share the home search box.
                allSession().query = browseQuery.strip();
                detailPopup = false;
                layoutView();
            }
            return true;
        }
        return false;
    }
    private void browseChanged(String value) {
        browseQuery = value == null ? "" : value;
        if (allGrid()) { allSession().query = browseQuery.strip(); filterChanged(); }
        updateCards();
        roots.scrollToTop();
        if (category != null) subgrid(category).scrollToTop();
    }
    private void renderBrowse(GuiGraphics g, int mx, int my, float partialTick) {
        if (browse == null) return;
        int[] b = browseBounds();
        // The clear × is drawn inside the box by JournalSearchField, like the other search fields.
        JournalSearchField.render(g, font(), browse, b[0], b[1], b[2], b[3],
                tr("cuisinejournal.category.search_hint"), mx, my, partialTick);
    }
    private boolean browseClick(double mx, double my, int button) {
        if (browse == null || level == Level.GRID || button != 0) return false;
        int[] b = browseBounds();
        boolean hit = inside(mx, my, b[0], b[1], b[2], b[3]);
        JournalSearchField.click(browse, mx, my, button, b[0], b[1], b[2], b[3]);
        return hit;
    }
    private void applyEditing(boolean value) {
        editing = value;
        editState.reset(); editMenu = null; modal = null; closeMenus();
        detailPopup = false;
        updateCards();
        if (screen != null) screen.toastBatchOffset(0);
        layoutView();
    }
    private List<ResourceLocation> foodSource(Group group) {
        return editing && editState.addingFoods ? new ArrayList<>(names.keySet()) : group.foods();
    }
    private int batchHeight() { return editing && !editState.selected.isEmpty() ? 22 : 0; }

    private void layoutView() {
        if (screen == null) return;
        boolean focused = search != null && search.isFocused();
        int cursor = search == null ? 0 : search.getCursorPosition();
        if (search != null) search.setFocused(false);
        search = null; closeMenus();
        boolean browseFocused = browse != null && browse.isFocused();
        int browseCursor = browse == null ? 0 : browse.getCursorPosition();
        if (browse != null) browse.setFocused(false);
        browse = null;
        if (level != Level.GRID) {
            int[] b = browseBounds();
            browse = new EditBox(font(), b[0], b[1], Math.max(1, b[2] - 12), b[3], tr("cuisinejournal.category.search_hint"));
            browse.setBordered(false);
            browse.setMaxLength(64);
            browse.setHint(Component.empty());
            JournalSearchField.bounds(browse, b[0], b[1], b[2], b[3]);
            browse.setValue(browseQuery);
            browse.setCursorPosition(Math.min(browseCursor, browseQuery.length()));
            browse.setHighlightPos(browse.getCursorPosition());
            browse.setFocused(browseFocused);
            browse.setResponder(this::browseChanged);
        }
        if (level == Level.ROOT && !allView) roots.bounds(x, y + 46 + ROOT_SHIFT, width, Math.max(0, height - 46 - ROOT_SHIFT));
        else if (level == Level.SUBCATEGORY) subgrid(category).bounds(x, y + 45, width, Math.max(0, height - 45));
        else {
            Session s = session(); Group group = currentGroup(); if (group == null) return;
            // The all-foods grid sits under the home overview and has no search box of its own.
            toolY = allGrid() ? y + 46 + ROOT_SHIFT : y + 43;
            toolHeight = width < 340 && !allGrid() ? 34 : 18; bodyY = toolY + toolHeight + 6;
            bodyHeight = Math.max(0, bottom() - bodyY); wide = width >= 340;
            detailWidth = wide ? 185 : Math.min(230, width);
            int gridWidth = wide ? width - detailWidth - 6 : Math.min(width, width < 260 ? 115 : 181);
            detailX = wide ? x + gridWidth + 6 : x + (width - detailWidth) / 2;
            s.grid.selectionModel(editing ? editState.selected::contains : null);
            s.grid.bounds(x, bodyY, gridWidth, Math.max(0, bodyHeight - batchHeight()));
            dimensions = new JournalDropdown<>(tr("cuisinejournal.filter.dimension"), s.dimension,
                    value -> { s.dimension = value; filterChanged(); });
            List<JournalDropdown.Option<ResourceLocation>> options = new ArrayList<>();
            options.add(new JournalDropdown.Option<>(null, tr("cuisinejournal.filter.all")));
            foodSource(group).stream().map(ClientJournalState::dish).filter(java.util.Objects::nonNull)
                    .map(Packets.DishView::dimension).filter(java.util.Objects::nonNull).distinct()
                    .sorted().forEach(id -> options.add(new JournalDropdown.Option<>(id, dimensionName(id))));
            dimensions.options(options); s.dimension = dimensions.value();
            statuses = new JournalDropdown<>(tr("cuisinejournal.filter.status"), s.status,
                    value -> { s.status = value; filterChanged(); });
            statuses.options(List.of(new JournalDropdown.Option<>(Status.ALL, tr("cuisinejournal.filter.all")),
                    new JournalDropdown.Option<>(Status.DISCOVERED, tr("cuisinejournal.category.discovered")),
                    new JournalDropdown.Option<>(Status.UNDISCOVERED, tr("cuisinejournal.category.undiscovered"))));
            sorts = new JournalDropdown<>(tr("cuisinejournal.filter.sort"), s.sort,
                    value -> { s.sort = value; filterChanged(); });
            List<JournalDropdown.Option<Sort>> sortOptions = new ArrayList<>(List.of(
                    new JournalDropdown.Option<>(Sort.PREPARED, tr("cuisinejournal.sort.most_cooked")),
                    new JournalDropdown.Option<>(Sort.CREATIVE, tr("cuisinejournal.sort.creative")),
                    new JournalDropdown.Option<>(Sort.NUTRITION_ASC, tr("cuisinejournal.sort.nutrition_asc")),
                    new JournalDropdown.Option<>(Sort.NUTRITION_DESC, tr("cuisinejournal.sort.nutrition_desc"))));
            if (allGrid()) sortOptions.add(new JournalDropdown.Option<>(Sort.CATEGORY, tr("cuisinejournal.sort.category")));
            sorts.options(sortOptions);
            if (allGrid()) { layoutTools(); rebuildFoods(group, s); return; }
            search = new EditBox(font(), right() - SEARCH_WIDTH - 3, toolY + (toolHeight == 18 ? 3 : 18), SEARCH_WIDTH, 12,
                    tr("cuisinejournal.journal.search"));
            search.setBordered(false);
            search.setMaxLength(128);
            search.setHint(Component.empty());
            JournalSearchField.bounds(search, right() - SEARCH_WIDTH - 3,
                    toolY + (toolHeight == 18 ? 3 : 18), SEARCH_WIDTH, 12);
            search.setValue(s.query);
            search.setCursorPosition(cursor);
            search.setHighlightPos(search.getCursorPosition());
            search.setFocused(focused);
            search.setResponder(query -> { s.query = query; filterChanged(); });
            layoutTools();
            rebuildFoods(group, s);
        }
    }
    private void filterChanged() {
        if (!grid()) return;
        Session s = session(); rebuildFoods(currentGroup(), s); s.grid.scrollToTop(); s.detailScroll = 0;
        layoutTools();
    }
    private void layoutTools() {
        int dw = dimensions.preferredWidth(font()), sw = statuses.preferredWidth(font()), ow = sorts.preferredWidth(font());
        int addWidth = editing && !inModCategory() && !allGrid() ? 16 : 0;
        int searchSpace = allGrid() ? 0 : SEARCH_WIDTH + 10;
        toolHeight = allGrid() ? 18 : width < 340 || dw + sw + ow + addWidth + 8 > width - searchSpace ? 34 : 18;
        int budget = width - (toolHeight == 18 ? Math.max(6, searchSpace) : 6) - 8 - addWidth;
        // Preserve content widths whenever possible, shrink only overflowing labels.
        int[] widths = { dw, sw, ow };
        while (widths[0] + widths[1] + widths[2] > budget) {
            int largest = widths[1] > widths[0] ? 1 : 0;
            if (widths[2] > widths[largest]) largest = 2;
            if (widths[largest] <= 24) break;
            widths[largest]--;
        }
        dimensions.bounds(x + 3 + addWidth, toolY + 3, widths[0], right(), bottom());
        statuses.bounds(x + 7 + addWidth + widths[0], toolY + 3, widths[1], right(), bottom());
        sorts.bounds(x + 11 + addWidth + widths[0] + widths[1], toolY + 3, widths[2], right(), bottom());
        gridSearchX = right() - SEARCH_WIDTH - 3;
        gridSearchY = toolY + (toolHeight == 18 ? 3 : 18);
        if (search != null) JournalSearchField.bounds(search, gridSearchX, gridSearchY, SEARCH_WIDTH, 12);
        bodyY = toolY + toolHeight + 6;
        bodyHeight = Math.max(0, bottom() - bodyY);
        int gridWidth = wide ? width - detailWidth - 6 : Math.min(width, width < 260 ? 115 : 181);
        session().grid.bounds(x, bodyY, gridWidth, Math.max(0, bodyHeight - batchHeight()));
    }
    private void rebuildFoods(Group group, Session s) {
        if (group == null) return;
        List<ResourceLocation> visible = new ArrayList<>(); String query = s.query.strip().toLowerCase(Locale.ROOT);
        for (ResourceLocation food : foodSource(group)) {
            Packets.DishView dish = ClientJournalState.dish(food); boolean found = ClientJournalState.isDiscovered(food);
            if (dish == null || names.get(food) == null) continue;
            if (s.dimension != null && !s.dimension.equals(dish.dimension())) continue;
            if (s.status == Status.DISCOVERED && !found || s.status == Status.UNDISCOVERED && found) continue;
            if (!query.isEmpty() && !foodMatches(food,s.query)) continue;
            visible.add(food);
        }
        Comparator<ResourceLocation> within = Comparator.comparing(food -> names.get(food).getString(),
                String.CASE_INSENSITIVE_ORDER);
        if (s.sort == Sort.CREATIVE) within = creativeOrder.comparator();
        if (s.sort == Sort.CATEGORY) within = Comparator.<ResourceLocation>comparingInt(food -> {
            Packets.DishView d = ClientJournalState.dish(food);
            return d == null ? Integer.MAX_VALUE : categoryIndex.getOrDefault(new Key(d.category(), d.subcategory()), Integer.MAX_VALUE);
        }).thenComparing(creativeOrder.comparator());
        if (s.sort == Sort.PREPARED) within = CreativeFoodOrder.preparedComparator(food -> {
            Packets.RecordView r = ClientJournalState.record(food); return r == null ? 0 : r.prepared();
        }, creativeOrder.comparator());
        boolean nutritionSort = s.sort == Sort.NUTRITION_ASC || s.sort == Sort.NUTRITION_DESC;
        if (nutritionSort) {
            within = Comparator.comparingInt(food -> ClientJournalState.dish(food).nutrition());
            within = within.thenComparingDouble(food -> ClientJournalState.dish(food).saturation());
            if (s.sort == Sort.NUTRITION_DESC) within = within.reversed();
            within = within.thenComparing(creativeOrder.comparator());
        }
        visible.sort(Comparator.<ResourceLocation>comparingInt(food -> editState.addingFoods && !belongsHere(food) ? 1 : 0)
                .thenComparingInt(food -> s.sort == Sort.CREATIVE || s.sort == Sort.CATEGORY || nutritionSort
                        || ClientJournalState.isDiscovered(food) ? 0 : 1)
                .thenComparing(within).thenComparing(ResourceLocation::toString));
        s.grid.items(visible);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX; this.mouseY = mouseY;
        int actualX = mouseX, actualY = mouseY;
        if (blocksInput()) { mouseX = -10000; mouseY = -10000; }
        refresh(); breadcrumbs(graphics, mouseX, mouseY);
        if (level != Level.GRID) renderBrowse(graphics, mouseX, mouseY, partialTick);
        if (screen != null) screen.toastBatchOffset(grid() && batchHeight() > 0 ? 22 : 0);
        if (allGrid()) {
            grandOverview(graphics);
            renderFoodGrid(graphics, mouseX, mouseY, partialTick);
        } else if (level == Level.ROOT) {
            grandOverview(graphics);
            if (roots.items().isEmpty()) renderEmpty(graphics); else {
                roots.render(graphics, mouseX, mouseY);
                ResourceLocation hovered = roots.itemAt(mouseX, mouseY);
                if (hovered != null && !NEW_CARD.equals(hovered)) {
                    Packets.CollectionView root = categories.get(hovered);
                    if (root != null) renderNameTooltip(graphics, rootName(root),
                            roots.itemBounds(hovered)[0] + 25, roots.itemBounds(hovered)[1] + 7,
                            Math.max(1, roots.itemBounds(hovered)[2] - 30), mouseX, mouseY);
                    int[] card = roots.itemBounds(hovered);
                    if (editing && card != null) sourceTooltip(graphics, hovered, card, mouseX, mouseY);
                }
            }
        } else if (level == Level.SUBCATEGORY) {
            summary(graphics, false, mouseX, mouseY); subgrid(category).render(graphics, mouseX, mouseY);
            if (browsing() && subgrid(category).items().isEmpty()) renderEmpty(graphics);
            ResourceLocation hovered = subgrid(category).itemAt(mouseX, mouseY);
            if (hovered != null && !NEW_CARD.equals(hovered)) {
                int[] card = subgrid(category).itemBounds(hovered);
                Group hoveredGroup = groups.get(new Key(category, hovered));
                if (card != null && hoveredGroup != null) {
                    renderNameTooltip(graphics, groupName(hoveredGroup.view()),
                            card[0] + 25, card[1] + 9, 46, mouseX, mouseY);
                }
                if (editing && card != null) sourceTooltip(graphics, hovered, card, mouseX, mouseY);
            }
        } else {
            summary(graphics, true, mouseX, mouseY);
            renderFoodGrid(graphics, mouseX, mouseY, partialTick);
        }
        renderEditOverlays(graphics, actualX, actualY, partialTick);
    }

    /** Filter bar, food grid, batch bar and detail panel — shared by a subcategory and the all-foods view. */
    private void renderFoodGrid(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (dimensions == null || currentGroup() == null) return;
        {
            tools(graphics, mouseX, mouseY, partialTick);
            Session s = session(); s.grid.render(graphics, mouseX, mouseY);
            if (batchHeight() > 0) renderBatch(graphics, mouseX, mouseY);
            if (s.grid.items().isEmpty()) renderCenteredEmpty(graphics,
                    Math.max(1, wide ? detailX - x - 10 : width - 8));
            if (s.grid.selected() != null && (wide || detailPopup)) {
                detail(graphics, s, mouseX, mouseY);
                if (!blocksInput() && !menusOpen()) renderDetailIngredientTooltips(graphics, s, mouseX, mouseY);
            } else FoodDetailPanel.clearHotspots();
            ResourceLocation hovered = s.grid.itemAt(mouseX, mouseY);
            if (!detailPopup && hovered != null && !menusOpen()) foodTooltip(graphics, hovered, mouseX, mouseY);
            dimensions.renderPopup(graphics, font(), mouseX, mouseY);
            statuses.renderPopup(graphics, font(), mouseX, mouseY);
            sorts.renderPopup(graphics, font(), mouseX, mouseY);
        }
    }

    private void rootCard(GuiGraphics g, ResourceLocation id, int cx, int cy, int cw, int ch, boolean hover, boolean selected) {
        if (NEW_CARD.equals(id)) { newCard(g, cx, cy, cw, ch, hover, true); return; }
        Packets.CollectionView root = categories.get(id); if (root == null) return;
        card(g, cx, cy, cw, ch, hover);
        if (root.icon() != null) g.renderFakeItem(JournalText.stackOf(root.icon()), cx + 5, cy + 8);
        List<ResourceLocation> subIds = children.getOrDefault(id, List.of());
        // Don't show preview items for mod categories
        int previewCount = editing || root.isModCategory() ? 0 : Math.min(3, subIds.size());
        int previewWidth = previewCount * 10;
        int found = found(id), total = total(id);
        int nameWidth = Math.max(1, editing ? cw - 44 : cw - 30 - previewWidth);
        drawTruncated(g, rootName(root), cx + 25, cy + 7, nameWidth, JournalTheme.TEXT);
        // While searching, say why the card is here: how many of its foods match.
        int hits = browsing() ? rootHits(id) : 0;
        clipped(g, hits > 0 ? tr("cuisinejournal.category.search_hits", hits)
                        : tr("cuisinejournal.category.subcategories", subIds.size()),
                cx + 25, cy + 18, nameWidth, hits > 0 ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_FAINT);
        // Mod category indicator - small "MOD" label in top-right
        if (root.isModCategory()) {
            Component modLabel = Component.literal("MOD");
            int labelWidth = font().width(modLabel);
            g.drawString(font(), modLabel, cx + cw - labelWidth - 5, cy + 5, 0xFF8B7355, false);
        }
        if (editing && !root.isModCategory()) editCardDecorations(g, id, cx, cy, cw, ch);
        for (int i = previewCount - 1; i >= 0; i--) {
            Group group = groups.get(new Key(id, subIds.get(i)));
            if (group != null && group.view().icon() != null) {
                smallItem(g, group.view().icon(), cx + cw - 14 - (previewCount - 1 - i) * 10,
                        cy + 4, false);
            }
        }
        List<Group> rootGroups = subIds.stream().map(sub -> groups.get(new Key(id, sub)))
                .filter(java.util.Objects::nonNull).toList();
        starTotal(g, rootGroups.stream().mapToInt(Group::stars).sum(),
                rootGroups.stream().mapToInt(Group::maxStars).sum(), cx + 5, cy + 33);
        progress(g, found, total, cx + cw - 5, cy + 33, cw / 2);
        discovery(g, cx + 5, cy + 45, cw - 10, found, total, found == total && total > 0);
    }

    private boolean truncated(Component text, int width) {
        return font().width(text) > width;
    }

    private void drawTruncated(GuiGraphics g, Component text, int tx, int ty, int width, int color) {
        if (!truncated(text, width)) {
            g.drawString(font(), text, tx, ty, color, false);
            return;
        }
        String ellipsis = "...";
        String value = font().plainSubstrByWidth(text.getString(), Math.max(0, width - font().width(ellipsis)));
        g.drawString(font(), value + ellipsis, tx, ty, color, false);
    }

    private void renderNameTooltip(GuiGraphics g, Component text, int tx, int ty, int width,
                                   int mouseX, int mouseY) {
        if (truncated(text, width) && inside(mouseX, mouseY, tx, ty, width, 10)) {
            g.renderTooltip(font(), text, mouseX, mouseY);
        }
    }
    private int found(ResourceLocation id) { return children.getOrDefault(id, List.of()).stream()
            .mapToInt(sub -> groups.get(new Key(id, sub)).discovered()).sum(); }
    private int total(ResourceLocation id) { return children.getOrDefault(id, List.of()).stream()
            .mapToInt(sub -> groups.get(new Key(id, sub)).foods().size()).sum(); }
    private void subCard(GuiGraphics g, ResourceLocation id, int cx, int cy, int cw, int ch, boolean hover, boolean selected) {
        if (NEW_CARD.equals(id)) { newCard(g, cx, cy, cw, ch, hover, false); return; }
        Group group = groups.get(new Key(category, id)); if (group == null) return;
        Packets.CollectionView root = categories.get(category);
        boolean isModCategory = root != null && root.isModCategory();
        card(g, cx, cy, cw, ch, hover);
        if (group.view().icon() != null) g.renderFakeItem(JournalText.stackOf(group.view().icon()), cx + 5, cy + 4);
        if (!editing && !isModCategory && !group.foods().isEmpty()) {
            smallItem(g, group.foods().get(0), cx + cw - 14, cy + 4,
                    !ClientJournalState.isDiscovered(group.foods().get(0)));
        }
        // Mod category indicator
        if (isModCategory) {
            Component modLabel = Component.literal("MOD");
            int labelWidth = font().width(modLabel);
            g.drawString(font(), modLabel, cx + cw - labelWidth - 5, cy + 5, 0xFF8B7355, false);
        }
        if (editing && !isModCategory) editCardDecorations(g, id, cx, cy, cw, ch);
        Component groupName = groupName(group.view());
        drawTruncated(g, groupName, cx + 25, cy + 9, editing && !isModCategory ? cw - 44 : 46, JournalTheme.TEXT);
        // Per-dish scope: the card sums its dishes' stars instead of drawing one 5-star row.
        if (ClientJournalState.perDishMastery()) starTotal(g, group.stars(), group.maxStars(), cx + 5, cy + 28);
        else stars(g, group, cx + 5, cy + 28);
        int hits = browsing() ? searchHits.getOrDefault(new Key(category, id), 0) : 0;
        clipped(g, hits > 0 ? tr("cuisinejournal.category.search_hits", hits)
                        : tr("cuisinejournal.category.progress", group.discovered(), group.foods().size()),
                cx + 5, cy + 42, cw - 10, hits > 0 ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_DIM);
        discovery(g, cx + 5, cy + 53, cw - 10, group.discovered(), group.foods().size(),
                group.discovered() == group.foods().size() && !group.foods().isEmpty());
    }
    private void foodCell(GuiGraphics g, ResourceLocation food, int cx, int cy, int cw, int ch, boolean hover, boolean selected) {
        g.fill(cx, cy, cx + cw, cy + ch, selected ? 0xFF1E1610 : 0xFF140F0A);
        g.renderFakeItem(JournalText.stackOf(food), cx + 2, cy + 2);
        if (!ClientJournalState.isDiscovered(food)) {
            g.pose().pushPose(); g.pose().translate(0, 0, 200);
            // Undiscovered: a heavy veil of the cell colour, so only a faint outline of the icon shows.
            g.fill(cx + 2, cy + 2, cx + 18, cy + 18, 0xC4140F0A); g.pose().popPose();
        }
        if (selected) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 220);
            g.fill(cx - 1, cy - 1, cx + cw, cy, 0x59E8A93C);
            g.fill(cx - 1, cy + ch, cx + cw, cy + ch + 1, 0x59E8A93C);
            g.fill(cx - 1, cy, cx, cy + ch, 0x59E8A93C);
            g.fill(cx + cw, cy, cx + cw + 1, cy + ch, 0x59E8A93C);
            g.fill(cx + 1, cy + 1, cx + cw - 1, cy + 2, 0x55FFE9C7);
            g.fill(cx + 1, cy + 1, cx + 2, cy + ch - 1, 0x55FFE9C7);
            g.fill(cx + 1, cy + ch - 2, cx + cw - 1, cy + ch - 1, 0x66000000);
            g.fill(cx + cw - 2, cy + 1, cx + cw - 1, cy + ch - 1, 0x66000000);
            g.pose().popPose();
        }
        JournalTheme.border(g, cx, cy, cw, ch, selected ? JournalTheme.TEXT_ACCENT
                : hover ? JournalTheme.TEXT_DIM : 0xFF3A2C1F);
        // Per-dish scope: a row of five 1px-tall pips along the cell bottom shows the dish's stars.
        if (ClientJournalState.perDishMastery() && ClientJournalState.isDiscovered(food)) {
            int rank = ClientJournalState.rank(food).ordinal();
            g.pose().pushPose(); g.pose().translate(0, 0, 240);
            for (int i = 0; i < MasteryRank.highestIndex(); i++) {
                int px = cx + 2 + i * 3;
                g.fill(px, cy + ch - 3, px + 2, cy + ch - 2, i < rank ? JournalTheme.TEXT_ACCENT : 0x663E332A);
            }
            g.pose().popPose();
        }
        if (editing) {
            g.pose().pushPose(); g.pose().translate(0, 0, 230);
            boolean manual = ClientJournalState.categoryEditor().manualItems().contains(food);
            if (editState.addingFoods && !belongsHere(food)) {
                g.fill(cx + 2, cy + 2, cx + 18, cy + 18, hover ? 0x33000000 : 0x66000000);
                // Grey "other subcategory" dot in top-right corner
                int dotX = cx + cw - 4;
                int dotY = cy + 1;
                g.fill(dotX, dotY, dotX + 3, dotY + 3, JournalTheme.TEXT_FAINT);
            }
            if (manual) {
                // Blue manual assignment dot in top-right corner, slightly offset if grey dot is present
                int dotX = editState.addingFoods && !belongsHere(food) ? cx + cw - 4 : cx + cw - 4;
                int dotY = editState.addingFoods && !belongsHere(food) ? cy + 5 : cy + 1;
                g.fill(dotX, dotY, dotX + 3, dotY + 3, JournalTheme.ADJUSTED);
            }
            if (selected) {
                g.fill(cx + cw - 6, cy + 1, cx + cw - 1, cy + 6, JournalTheme.TEXT_ACCENT);
                g.fill(cx + cw - 5, cy + 3, cx + cw - 4, cy + 4, 0xFF000000);
                g.fill(cx + cw - 4, cy + 4, cx + cw - 3, cy + 5, 0xFF000000);
                g.fill(cx + cw - 3, cy + 2, cx + cw - 2, cy + 4, 0xFF000000);
                if (food.equals(session().grid.selected())) JournalTheme.border(g, cx + 1, cy + 1, cw - 2, ch - 2, JournalTheme.TEXT_ACCENT);
            }
            g.pose().popPose();
        }
    }
    private void breadcrumbs(GuiGraphics g, int mx, int my) {
        int cursor = x;
        if (level != Level.ROOT) {
            boolean arrowHover = inside(mx, my, x, y, 11, 11);
            squareButton(g, x, y, 11, arrowHover, false);
            // Pixel arrow (7x5) centred exactly in the 11x11 box; the font glyph sat off-centre.
            PixelIcons.drawCentered(g, PixelIcons.Icon.ARROW_BACK, x, y, 11, 11,
                    arrowHover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM); cursor += 15;
        }
        // One rule for every level: the label of the level you are on is bright; ancestors are
        // dim and, on hover, turn bright with an underline (they are the clickable ones).
        Component journal = journalLabel();
        Component root = level == Level.ROOT ? null : rootName(categories.get(category));
        Component groupLabel = null;
        if (level == Level.GRID) {
            Component label = groupName(groups.get(key).view());
            // Implicit subcategories share the root's name; the root label then stands for the current level.
            if (!label.getString().equals(root.getString())) groupLabel = label;
        }
        boolean journalCurrent = level == Level.ROOT;
        boolean rootCurrent = level == Level.SUBCATEGORY || (level == Level.GRID && groupLabel == null);
        if (journalCurrent) {
            // Top level: the page heading, styled exactly like the ingredient page's "食材图鉴".
            g.pose().pushPose();
            g.pose().translate(cursor, y + HEADING_Y, 0);
            g.pose().scale(HEADING_SCALE, HEADING_SCALE, 1.0F);
            g.drawString(font(), journal.copy().withStyle(style -> style.withBold(true)), 0, 0,
                    JournalTheme.TEXT_ACCENT, false);
            g.pose().popPose();
        } else cursor = crumb(g, journal, cursor, mx, my, false, true);
        if (root != null) {
            g.drawString(font(), " \u203a ", cursor, y + 1, JournalTheme.TEXT_FAINT, false);
            cursor += font().width(" \u203a ");
            cursor = crumb(g, root, cursor, mx, my, rootCurrent, level == Level.GRID && !directModGrid());
        }
        if (groupLabel != null) {
            g.drawString(font(), " \u203a ", cursor, y + 1, JournalTheme.TEXT_FAINT, false);
            cursor += font().width(" \u203a ");
            cursor = crumb(g, groupLabel, cursor, mx, my, true, false);
        }
        if (level == Level.ROOT) renderViewToggle(g, mx, my);
        if (!ClientJournalState.canEditCategories()) { if (editing) applyEditing(false); return; }
        int[] pencil = pencilBounds();
        boolean hover = inside(mx, my, pencil[0], pencil[1], pencil[2], pencil[3]);
        squareButton(g, pencil[0], pencil[1], pencil[2], hover, editing);
        drawPencil(g, pencil[0] + 2, pencil[1] + 2, editing ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM);
        if (editing) {
            Component mode = tr("cuisinejournal.edit.mode");
            int modeX = pencil[0] - 4 - font().width(mode);
            g.drawString(font(), mode, modeX, y + 1, JournalTheme.TEXT_ACCENT, false);
            // Export the in-game edits (categories, and flavors if any) into a data pack.
            int[] export = exportBounds();
            boolean exportHover = inside(mx, my, export[0], export[1], export[2], export[3]);
            g.fill(export[0], export[1], export[0] + export[2], export[1] + export[3], exportHover ? 0xFF2A2018 : 0xFF1B140E);
            JournalTheme.border(g, export[0], export[1], export[2], export[3], exportHover ? JournalTheme.TEXT_DIM : JournalTheme.PANEL_BORDER);
            g.drawString(font(), tr("cuisinejournal.scheme.button"), export[0] + 3, export[1] + 2,
                    exportHover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, false);
            if (exportHover) g.renderTooltip(font(), tr("cuisinejournal.scheme.tooltip.category"), mx, my);
        }
        if (hover) g.renderTooltip(font(), tr(editing ? "cuisinejournal.edit.exit" : "cuisinejournal.edit.enter"), mx, my);
    }

    /** Draws one breadcrumb label and returns the x after it. */
    private int crumb(GuiGraphics g, Component label, int cx, int mx, int my, boolean current, boolean clickable) {
        int w = font().width(label);
        // Crumbs stop short of the search box (root/subcategory levels) instead of running under it.
        int limit = level != Level.GRID ? browseBounds()[0] - 6 : right();
        boolean hover = clickable && inside(mx, my, cx, y, w, 11);
        clipped(g, label, cx, y + 1, limit - cx, current || hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM);
        if (hover) JournalTheme.divider(g, cx, y + 10, Math.max(0, Math.min(w, limit - cx)));
        return cx + w;
    }

    private int[] pencilBounds() { return new int[]{right() - 11, y, 11, 11}; }
    private int exportButtonWidth() { return font().width(tr("cuisinejournal.scheme.button")) + 6; }
    private int[] exportBounds() {
        int modeX = pencilBounds()[0] - 4 - font().width(tr("cuisinejournal.edit.mode"));
        int w = exportButtonWidth();
        return new int[]{modeX - 6 - w, y, w, 11};
    }
    private int exportRequest;
    /** 7x7 pencil: eraser cap top-right, a faint ferrule line, and a dim graphite tip bottom-left. */
    private void drawPencil(GuiGraphics g, int px, int py, int color) {
        String[] shape = {".....##", "....#.#", "...##..", "..###..", ".###...", ".##....", "#......"};
        for (int row = 0; row < shape.length; row++) for (int col = 0; col < shape[row].length(); col++)
            if (shape[row].charAt(col) == '#') g.fill(px + col, py + row, px + col + 1, py + row + 1,
                    row == 6 ? JournalTheme.TEXT_FAINT : color);
    }
    /**
     * Small square icon button shared by the back arrow, the edit toggle and the add-food toggle:
     * always has a dark fill and a border so it reads as a button, not a stray glyph.
     */
    private void squareButton(GuiGraphics g, int bx, int by, int size, boolean hover, boolean active) {
        g.fill(bx, by, bx + size, by + size, active ? 0x40E8A93C : hover ? 0xFF2A2018 : 0xFF1B140E);
        JournalTheme.border(g, bx, by, size, size,
                active ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT_DIM : JournalTheme.PANEL_BORDER);
    }
    private void drawDots(GuiGraphics g, int px, int py, int color) {
        for (int col = 0; col < 3; col++) g.fill(px + col * 3, py, px + col * 3 + 2, py + 2, color);
    }

    private Packets.CategoryMetadata metadata(ResourceLocation id) {
        return ClientJournalState.categoryEditor().categories().stream()
                .filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }
    /**
     * Root/sub-aware lookup. A legacy root's implicit subcategory shares the root id
     * and has no metadata of its own, so a sub lookup must not return the root entry.
     */
    private Packets.CategoryMetadata metadata(ResourceLocation id, boolean root) {
        return ClientJournalState.categoryEditor().categories().stream()
                .filter(value -> value.id().equals(id) && (value.parent() == null) == root)
                .findFirst().orElse(null);
    }
    private Component sourceText(Packets.CategoryMetadata value) {
        return tr("cuisinejournal.edit.source." + (value == null || value.source() == 0 ? "datapack"
                : value.source() == 1 ? "ingame" : "adjusted"));
    }
    private int hiddenCount() {
        return (int) ClientJournalState.categoryEditor().categories().stream().filter(Packets.CategoryMetadata::hidden).count();
    }
    /** The reserved "<root>/_other" fallback only collects unmatched foods and cannot be an assignment target. */
    private boolean inModCategory() {
        Packets.CollectionView root = category == null ? null : categories.get(category);
        return root != null && root.isModCategory();
    }
    private boolean canAddHere() {
        return key != null && key.subcategory() != null && !key.subcategory().getPath().endsWith("/_other");
    }
    private boolean belongsHere(ResourceLocation food) {
        Packets.DishView dish = ClientJournalState.dish(food);
        return dish != null && key != null && dish.category().equals(key.category()) && dish.subcategory().equals(key.subcategory());
    }
    private Component foodPath(ResourceLocation food) {
        var dish = ClientJournalState.dish(food);
        if (dish == null) return Component.empty();
        var root = categories.get(dish.category()); var group = groups.get(new Key(dish.category(), dish.subcategory()));
        return (root == null ? Component.literal(dish.category().toString()) : rootName(root)).copy()
                .append(" › ").append(group == null ? Component.literal(dish.subcategory().toString()) : groupName(group.view()));
    }
    private void foodTooltip(GuiGraphics g, ResourceLocation food, int mx, int my) {
        List<Component> extra = new ArrayList<>();
        if (editing && editState.addingFoods && !belongsHere(food)) extra.add(tr("cuisinejournal.edit.current_path", foodPath(food)));
        if (editing && ClientJournalState.categoryEditor().manualItems().contains(food))
            extra.add(tr("cuisinejournal.edit.source.manual_food").withStyle(s -> s.withColor(JournalTheme.ADJUSTED)));
        JournalText.renderItemTooltip(g, font(), food, extra, mx, my);
    }

    /** Full item tooltip for the dish header or an ingredient icon in the detail panel. */
    private void renderDetailIngredientTooltips(GuiGraphics g, Session s, int mx, int my) {
        FoodDetailPanel.renderTooltip(g, font(), mx, my);
    }
    private void newCard(GuiGraphics g, int cx, int cy, int cw, int ch, boolean hover, boolean root) {
        int color = hover ? JournalTheme.TEXT_DIM : JournalTheme.TEXT_FAINT;
        g.fill(cx, cy, cx + cw, cy + ch, hover ? 0x22FFE9C7 : 0x10000000);
        for (int dx = 0; dx < cw; dx += 4) {
            g.fill(cx + dx, cy, cx + Math.min(cw, dx + 2), cy + 1, color);
            g.fill(cx + dx, cy + ch - 1, cx + Math.min(cw, dx + 2), cy + ch, color);
        }
        for (int dy = 0; dy < ch; dy += 4) {
            g.fill(cx, cy + dy, cx + 1, cy + Math.min(ch, dy + 2), color);
            g.fill(cx + cw - 1, cy + dy, cx + cw, cy + Math.min(ch, dy + 2), color);
        }
        plus(g, cx + cw / 2 - 3, cy + ch / 2 - 9, color);
        Component text = tr(root ? "cuisinejournal.edit.new_root" : "cuisinejournal.edit.new_sub");
        g.drawString(font(), text, cx + (cw - font().width(text)) / 2, cy + ch / 2 + 3, color, false);
    }
    private void plus(GuiGraphics g, int px, int py, int color) {
        g.fill(px + 3, py, px + 4, py + 7, color); g.fill(px, py + 3, px + 7, py + 4, color);
    }
    private void link(GuiGraphics g, Component text, int right, int ty, int mx, int my) {
        int w = Math.min(font().width(text), width / 2);
        boolean hover = inside(mx, my, right - w, ty, w, 11);
        clipped(g, text, right - w, ty, w, hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM);
        if (hover) g.fill(right - w, ty + 10, right, ty + 11, JournalTheme.TEXT_DIM);
    }
    private void editCardDecorations(GuiGraphics g, ResourceLocation id, int cx, int cy, int cw, int ch) {
        boolean hover = !blocksInput() && inside(mouseX, mouseY, cx + cw - 16, cy + 3, 12, 10);
        if (hover) g.fill(cx + cw - 16, cy + 3, cx + cw - 4, cy + 13, 0x1AFFE9C7);
        drawDots(g, cx + cw - 14, cy + 7, hover ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT);
        Packets.CategoryMetadata value = metadata(id, level == Level.ROOT);
        boolean created = value != null && value.source() == 1;
        int color = created ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_FAINT;
        g.pose().pushPose(); g.pose().translate(0, 0, 220);
        PixelIcons.drawSourceBadge(g, created ? PixelIcons.Icon.CREATED : PixelIcons.Icon.PACKET,
                cx + 14, cy + 15, color, value != null && value.source() == 2);
        g.pose().popPose();
        if (id.equals(flashId) && flashTicks > 0) {
            g.fill(cx + 1, cy + 1, cx + cw - 1, cy + ch - 1, 0x33E8A93C);
        }
    }
    /** Hover tooltip for the 9×9 source badge drawn by {@link #editCardDecorations}. */
    private void sourceTooltip(GuiGraphics g, ResourceLocation id, int[] card, int mx, int my) {
        if (menusOpen() || !inside(mx, my, card[0] + 14, card[1] + 15, 9, 9)) return;
        Packets.CategoryMetadata value = metadata(id, level == Level.ROOT);
        List<Component> lines = new ArrayList<>();
        if (value == null) {
            lines.add(tr("cuisinejournal.edit.source.auto_sub"));
        } else if (value.source() == 1) {
            lines.add(tr("cuisinejournal.edit.source.ingame"));
            lines.add(tr("cuisinejournal.edit.source.custom_tip").withStyle(s -> s.withColor(JournalTheme.TEXT_DIM)));
        } else if (value.source() == 2) {
            lines.add(tr("cuisinejournal.edit.source.adjusted"));
            lines.add(tr("cuisinejournal.edit.source.adjusted_tip").withStyle(s -> s.withColor(JournalTheme.TEXT_DIM)));
        } else {
            lines.add(tr("cuisinejournal.edit.source.datapack"));
            lines.add(tr("cuisinejournal.edit.source.namespace", id.getNamespace())
                    .withStyle(s -> s.withColor(JournalTheme.TEXT_DIM)));
        }
        g.renderComponentTooltip(font(), lines, mx, my);
    }

    private int gridWidth() { return wide ? width - detailWidth - 6 : Math.min(width, width < 260 ? 115 : 181); }

    /** Batch bar geometry shared by drawing and clicks: {actionX, actionWidth, returnX (or -1), clearX}. */
    private int[] batchLayout() {
        int gw = gridWidth();
        int actionWidth = Math.min(gw / 2, font().width(tr(editState.addingFoods ? "cuisinejournal.edit.move_into" : "cuisinejournal.edit.assign_short")) + 12);
        int ax = x + gw - actionWidth;
        int returnX = canReturnSelection() ? ax - 17 : -1;
        int clearX = (returnX >= 0 ? returnX : ax) - 17;
        return new int[]{ax, actionWidth, returnX, clearX};
    }

    /** Selected foods currently outside a mod category; hidden while picking foods to add. */
    private List<ResourceLocation> returnableSelection() {
        if (editState.addingFoods || inModCategory()) return List.of();
        return editState.selected.stream().filter(food -> {
            Packets.DishView dish = ClientJournalState.dish(food);
            if (dish == null) return false;
            Packets.CollectionView root = categories.get(dish.category());
            return root == null || !root.isModCategory();
        }).toList();
    }
    private boolean canReturnSelection() { return !returnableSelection().isEmpty(); }

    private void renderBatch(GuiGraphics g, int mx, int my) {
        int gw = gridWidth(), by = bottom() - 20;
        g.fill(x, by, x + gw, bottom(), 0xFF140F0A);
        g.fill(x, by, x + gw, by + 1, JournalTheme.TEXT_ACCENT);
        int[] layout = batchLayout();
        int actionWidth = layout[1], returnX = layout[2], clearX = layout[3];
        Component count = gw < 180 ? Component.literal(Integer.toString(editState.selected.size()))
                : tr("cuisinejournal.edit.selected", editState.selected.size());
        int hidden = (int) editState.selected.stream().filter(food -> !session().grid.items().contains(food)).count();
        PixelIcons.draw(g, PixelIcons.Icon.CHECK, x + 4, by + 7, JournalTheme.TEXT_ACCENT);
        int countX = x + 14, countMax = Math.max(1, clearX - countX - 4);
        Component countLine = hidden > 0 && gw >= 180
                ? count.copy().append(tr("cuisinejournal.edit.hidden_count", hidden)
                        .withStyle(s -> s.withColor(JournalTheme.TEXT_FAINT))) : count;
        clipped(g, countLine, countX, by + 6, countMax, JournalTheme.TEXT);
        // Both buttons are 13px tall at by+4, centred in the 19px strip under the accent line.
        boolean clearHover = inside(mx, my, clearX, by + 4, 13, 13);
        squareButton(g, clearX, by + 4, 13, clearHover, false);
        PixelIcons.drawCentered(g, PixelIcons.Icon.CLOSE, clearX, by + 4, 13, 13,
                clearHover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM);
        boolean returnHover = returnX >= 0 && !editState.waiting() && inside(mx, my, returnX, by + 4, 13, 13);
        if (returnX >= 0) {
            squareButton(g, returnX, by + 4, 13, returnHover, false);
            PixelIcons.drawCentered(g, PixelIcons.Icon.RETURN, returnX, by + 4, 13, 13,
                    editState.waiting() ? JournalTheme.TEXT_FAINT : returnHover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM);
        }
        int ax = layout[0];
        boolean actionHover = !editState.waiting() && inside(mx, my, ax, by + 4, actionWidth, 13);
        g.fill(ax, by + 4, ax + actionWidth, by + 17, editState.waiting() ? 0xFF1B140E
                : actionHover ? 0xFF6B4A1C : 0xFF4A3418);
        JournalTheme.border(g, ax, by + 4, actionWidth, 13, editState.waiting() ? JournalTheme.TEXT_FAINT : JournalTheme.TEXT_ACCENT);
        Component action = tr(editState.addingFoods ? "cuisinejournal.edit.move_into" : "cuisinejournal.edit.assign_short");
        int actionTextWidth = Math.min(font().width(action), actionWidth - 8);
        clipped(g, action, ax + (actionWidth - actionTextWidth) / 2, by + 7, actionWidth - 8,
                editState.waiting() ? JournalTheme.TEXT_FAINT : actionHover ? 0xFFFFF4DE : JournalTheme.TEXT);
        if (clearHover) g.renderTooltip(font(), tr("cuisinejournal.edit.clear_selection"), mx, my);
        else if (returnHover) {
            List<ResourceLocation> back = returnableSelection();
            g.renderComponentTooltip(font(), List.of(tr("cuisinejournal.edit.return_mod", back.size()),
                    tr("cuisinejournal.edit.return_mod_tip").withStyle(s -> s.withColor(JournalTheme.TEXT_DIM))), mx, my);
        }
        else if (editState.waiting() && inside(mx, my, ax, by + 4, actionWidth, 13))
            g.renderTooltip(font(), tr("cuisinejournal.edit.disabled.waiting"), mx, my);
        else if (inside(mx, my, x, by, Math.max(1, clearX - x - 2), 20)) {
            List<Component> lines = new ArrayList<>(); lines.add(tr("cuisinejournal.edit.selected", editState.selected.size()));
            if (hidden > 0) lines.add(tr("cuisinejournal.edit.hidden_count", hidden));
            if (editState.addingFoods) {
                long already = editState.selected.stream().filter(this::belongsHere).count();
                if (already > 0) lines.add(tr("cuisinejournal.edit.already_here", already));
            }
            g.renderComponentTooltip(font(), lines, mx, my);
        }
    }
    /** Exact hit rect of a right-aligned header link, matching {@link #link}. */
    private boolean onLink(double mx, double my, Component text, int ty) {
        int w = Math.min(font().width(text), width / 2);
        return inside(mx, my, right() - w, ty, w, 11);
    }
    private boolean editHeaderClick(double mx, double my, int button) {
        if (button != 0) return false;
        if (level == Level.ROOT && onLink(mx, my, tr("cuisinejournal.edit.blocked.count", hiddenCount()), y + ROOT_SHIFT + 16)) {
            CuisineUiSoundCompat.click(); openHidden(); return true;
        }
        if (level == Level.SUBCATEGORY && onLink(mx, my, tr("cuisinejournal.edit.edit_root"), y + 26)) {
            Packets.CollectionView current = categories.get(category);
            if (current != null && current.isModCategory()) return false;
            Packets.CategoryMetadata value = metadata(category, true);
            if (value != null) { CuisineUiSoundCompat.click(); openForm(true, value, null); }
            return true;
        }
        if (!grid()) return false;
        if (!allGrid() && !inModCategory() && inside(mx, my, x + 3, toolY + 3, 12, 12)) {
            if (!editState.addingFoods && !canAddHere()) return true;
            editState.addingFoods = !editState.addingFoods;
            CuisineUiSoundCompat.click();
            editState.clearSelection();
            layoutView();
            return true;
        }
        if (batchHeight() == 0 || !inside(mx, my, x, bottom() - 20, gridWidth(), 20)) return false;
        int[] layout = batchLayout();
        int ax = layout[0], actionWidth = layout[1], returnX = layout[2], clearX = layout[3];
        if (inside(mx, my, ax, bottom() - 16, actionWidth, 13)) {
            if (!editState.waiting()) {
                CuisineUiSoundCompat.click();
                List<ResourceLocation> items = new ArrayList<>(editState.selected);
                if (editState.addingFoods) confirmAssign(items.stream().filter(food -> !belongsHere(food)).toList(), key.subcategory());
                else openAssign(items);
            }
        } else if (returnX >= 0 && inside(mx, my, returnX, bottom() - 16, 13, 13)) {
            if (!editState.waiting()) { CuisineUiSoundCompat.click(); confirmReturn(returnableSelection()); }
        } else if (inside(mx, my, clearX, bottom() - 16, 13, 13)) {
            CuisineUiSoundCompat.click();
            editState.clearSelection(); layoutTools();
        }
        return true;
    }
    private boolean detailChangeClick(double mx, double my, int button) {
        if (button != 0 || !editing || session().grid.selected() == null || !(wide || detailPopup)) return false;
        var dish = ClientJournalState.dish(session().grid.selected()); if (dish == null) return false;
        int top = wide ? bodyY : y + 30;
        int categoryY = top + FoodDetailPanel.categoryY(font(), dish, detailWidth) - session().detailScroll + 25;

        // Check "Change..." button click
        int changeW = font().width(tr("cuisinejournal.edit.change"));
        if (categoryY >= top && categoryY + 11 <= bottom() && inside(mx, my, detailX + detailWidth - 6 - changeW, categoryY, changeW, 11)) {
            CuisineUiSoundCompat.click(); openAssign(List.of(dish.target())); return true;
        }
        return false;
    }

    private void renderEditOverlays(GuiGraphics g, int mx, int my, float partial) {
        if (modal != null && modal.isOpen()) modal.render(g, mx, my, partial);
        if (editMenu != null && editMenu.isOpen()) editMenu.render(g, font(), mx, my);
    }

    private void pollEditResult() {
        Packets.CategoryEditResult result = editState.poll();
        if (result == null) return;
        Component message = tr(result.message(), result.args().toArray());
        if (screen != null) screen.editToast(result.success() ? message
                : tr("cuisinejournal.edit.toast.failed", message), result.success(), false);
        if (!result.success()) {
            refreshHiddenAfterSuccess = false;
            // Keep the dialog open so the player can fix the input or retry.
            if (modal != null && modal.isOpen()) {
                if (modal instanceof CategoryFormModal form) form.acceptResult(result);
                else if (modal instanceof HiddenCategoriesModal hidden) hidden.acceptResult(result);
                else modal.waiting(false);
            }
            pendingFlash = null;
            return;
        }
        editMenu = null;
        if (modal instanceof HiddenCategoriesModal hidden && hidden.isOpen()) {
            // The hidden list stays open so several entries can be restored in a row.
            hidden.acceptResult(result);
            refreshHiddenAfterSuccess = true;
            if (ClientJournalState.categoryEditor() != hiddenSnapshot) {
                hiddenSnapshot = ClientJournalState.categoryEditor();
                hidden.refreshEntries(hiddenSnapshot.categories());
                refreshHiddenAfterSuccess = false;
            }
        } else if (modal != null) {
            modal.close(); modal = null;
        }
        flashId = pendingFlash; pendingFlash = null; flashTicks = 20;
        refresh();
        if (grid() && dimensions != null) layoutTools();
    }

    private void submit(Packets.CategoryEditRequest request, Runnable success) {
        if (editState.waiting()) { if (modal != null) modal.waiting(false); return; }
        editState.submit(request, success);
        if (!editState.waiting()) {
            // Refused locally (no permission / edit mode already off): release the dialog.
            if (modal != null) modal.waiting(false);
            return;
        }
        if (modal != null) modal.waiting(true);
        if (modal instanceof HiddenCategoriesModal) hiddenSnapshot = ClientJournalState.categoryEditor();
        pendingFlash = request.targetId();
        if (screen != null) screen.editToast(tr("cuisinejournal.edit.toast.saving"), true, true);
    }

    private void openInContent(JournalModal value) {
        value.openCentered(x, y, width, height);
    }

    private void openForm(boolean root, Packets.CategoryMetadata existing, ResourceLocation parent) {
        openForm(root, existing, parent, Map.copyOf(EditClientState.snapshot()));
    }

    private void openForm(boolean root, Packets.CategoryMetadata existing, ResourceLocation parent,
                          Map<String, Long> expectedVersions) {
        if (!ClientJournalState.canEditCategories()) return;
        Packets.CategoryMetadata original = existing;
        CategoryFormModal.Mode mode = existing == null
                ? (root ? CategoryFormModal.Mode.CREATE_ROOT : CategoryFormModal.Mode.CREATE_SUB)
                : (root ? CategoryFormModal.Mode.EDIT_ROOT : CategoryFormModal.Mode.EDIT_SUB);
        List<ResourceLocation> recommended = existing != null ? recommendedIcons(existing.id(), root)
                : root ? List.of() : recommendedIcons(parent, true);
        Runnable danger = existing == null ? null : () -> {
            Map<String, Long> versions = ((CategoryFormModal) modal).expectedVersions();
            modal = null; confirmDestroy(original.id(), root, versions);
        };
        modal = new CategoryFormModal(mode, existing, parent, recommended,
                request -> submit(request, null), () -> modal = null, danger, expectedVersions);
        openInContent(modal);
    }

    /** Foods of the category (discovered first) offered as recommended icons. */
    private List<ResourceLocation> recommendedIcons(ResourceLocation id, boolean root) {
        if (id == null) return List.of();
        List<ResourceLocation> foods = new ArrayList<>();
        if (root) {
            for (ResourceLocation sub : children.getOrDefault(id, List.of())) {
                Group group = groups.get(new Key(id, sub));
                if (group != null) foods.addAll(group.foods());
            }
        } else {
            groups.forEach((k, group) -> { if (k.subcategory().equals(id)) foods.addAll(group.foods()); });
        }
        return foods.stream().distinct()
                .sorted(Comparator.<ResourceLocation>comparingInt(food -> ClientJournalState.isDiscovered(food) ? 0 : 1))
                .limit(16).toList();
    }

    private void openHidden() {
        hiddenSnapshot = ClientJournalState.categoryEditor();
        refreshHiddenAfterSuccess = false;
        modal = new HiddenCategoriesModal(request -> submit(request, null), () -> modal = null);
        openInContent(modal);
    }

    private void openAssign(List<ResourceLocation> items) {
        if (items.isEmpty()) return;
        Map<String, Long> versions = editState.selected.isEmpty()
                ? Map.copyOf(EditClientState.snapshot()) : editState.selectionVersions();
        // Pre-select the subcategory only when every item already shares it.
        ResourceLocation current = null;
        for (int i = 0; i < items.size(); i++) {
            Packets.DishView dish = ClientJournalState.dish(items.get(i));
            ResourceLocation sub = dish == null ? null : dish.subcategory();
            if (i == 0) current = sub;
            else if (!java.util.Objects.equals(current, sub)) { current = null; break; }
        }
        modal = new TargetPickerModal(false, current, items,
                target -> confirmAssign(items, target, versions), () -> modal = null);
        openInContent(modal);
    }

    /** Large batches (20+) need an explicit confirmation before they are sent. */
    private void confirmAssign(List<ResourceLocation> items, ResourceLocation target) {
        confirmAssign(items, target, editState.selectionVersions());
    }

    private void confirmAssign(List<ResourceLocation> items, ResourceLocation target, Map<String, Long> versions) {
        if (items.isEmpty() || target == null) return;
        if (items.size() < 20) { assignTo(items, target, versions); return; }
        ResourceLocation here = key == null ? null : key.subcategory();
        long other = items.stream().map(ClientJournalState::dish).filter(java.util.Objects::nonNull)
                .filter(dish -> !dish.subcategory().equals(target) && !dish.subcategory().equals(here)).count();
        List<Component> body = new ArrayList<>();
        if (other > 0) body.add(tr("cuisinejournal.edit.confirm.other", other));
        body.add(tr("cuisinejournal.edit.confirm.records"));
        modal = new ConfirmModal(tr("cuisinejournal.edit.confirm.assign", items.size(), categoryName(target)), body,
                tr("cuisinejournal.edit.assign_short"), () -> assignTo(items, target, versions), () -> modal = null);
        openInContent(modal);
    }

    private void assignTo(List<ResourceLocation> items, ResourceLocation target, Map<String, Long> versions) {
        if (items.isEmpty()) return;
        submit(new Packets.CategoryEditRequest(0, Packets.CategoryEditRequest.Action.ASSIGN_FOODS,
                target, null, null, null, items, versions), editState::clearSelection);
    }

    private void confirmReturn(List<ResourceLocation> items) {
        if (items.isEmpty()) return;
        Map<String, Long> versions = editState.selectionVersions();
        modal = new ConfirmModal(tr("cuisinejournal.edit.confirm.return_title", items.size()),
                List.of(tr("cuisinejournal.edit.confirm.return_body"), tr("cuisinejournal.edit.confirm.records")),
                tr("cuisinejournal.edit.return_short"),
                () -> submit(new Packets.CategoryEditRequest(0, Packets.CategoryEditRequest.Action.UNASSIGN_FOOD,
                        null, null, null, null, items, versions), editState::clearSelection),
                () -> modal = null);
        openInContent(modal);
    }

    private void openFoodMenu(ResourceLocation food, int mx, int my) {
        editMenu = new JournalMenu(List.of(new JournalMenu.Item(tr("cuisinejournal.edit.assign"),
                PixelIcons.Icon.ARROW_MOVE, false, true, null, () -> openAssign(List.of(food)))));
        editMenu.open(mx, my, x, y, width, height, font());
    }

    private void openCategoryMenu(ResourceLocation id, boolean root, int mx, int my) {
        Map<String, Long> versions = Map.copyOf(EditClientState.snapshot());
        Packets.CollectionView modRoot = categories.get(root ? id : category);
        if (modRoot != null && modRoot.isModCategory()) {
            // Mod fallback categories are generated from item ids and cannot be edited or hidden.
            Component locked = tr("cuisinejournal.edit.disabled.mod");
            editMenu = new JournalMenu(List.of(
                    new JournalMenu.Item(tr("cuisinejournal.edit.menu.edit"), PixelIcons.Icon.PENCIL, false, false, locked, null),
                    JournalMenu.Item.separator(),
                    new JournalMenu.Item(tr("cuisinejournal.edit.menu.block"), PixelIcons.Icon.SLASH, true, false, locked, null)));
            editMenu.open(mx, my, x, y, width, height, font());
            return;
        }
        Packets.CategoryMetadata value = metadata(id, root);
        // A root without configured children shows one implicit subcategory sharing the root id.
        // It has no metadata of its own, so editing/blocking it acts on the root itself.
        if (!root && value == null && metadata(id, true) != null) {
            openCategoryMenu(id, true, mx, my);
            return;
        }
        Component name = categoryName(id);
        ResourceLocation parent = value == null ? category : value.parent();
        List<JournalMenu.Item> items = new ArrayList<>();
        items.add(new JournalMenu.Item(tr("cuisinejournal.edit.menu.edit"), PixelIcons.Icon.PENCIL,
                false, value != null, tr("cuisinejournal.edit.disabled.default_sub"),
                () -> openForm(root, value, root ? null : parent, versions)));
        // Removed "新建小类" from root category menu - use the button inside the category instead
        if (!root) {
            boolean fixed = value == null || value.protectedEntry();
            boolean noOther = categories.size() <= 1;
            items.add(new JournalMenu.Item(tr("cuisinejournal.edit.menu.move"), PixelIcons.Icon.ARROW_MOVE,
                    false, !fixed && !noOther,
                    tr(fixed ? "cuisinejournal.edit.disabled.default_sub" : "cuisinejournal.edit.disabled.no_other"),
                    () -> {
                        modal = new TargetPickerModal(true, id, name, parent, List.of(),
                                target -> submit(new Packets.CategoryEditRequest(0,
                                        Packets.CategoryEditRequest.Action.MOVE_SUB, id, target,
                                        requestName(name), null, List.of(), versions), null),
                                () -> modal = null);
                        openInContent(modal);
                    }));
        }
        items.add(JournalMenu.Item.separator());
        boolean datapack = value == null || value.source() != 1;
        boolean locked = value == null || value.protectedEntry();
        items.add(new JournalMenu.Item(tr(datapack ? "cuisinejournal.edit.menu.block" : "cuisinejournal.edit.menu.remove"),
                datapack ? PixelIcons.Icon.SLASH : PixelIcons.Icon.CLOSE, true, !locked,
                tr(root && value != null ? "cuisinejournal.edit.disabled.fallback" : "cuisinejournal.edit.disabled.default_sub"),
                () -> confirmDestroy(id, root, versions)));
        editMenu = new JournalMenu(items); editMenu.open(mx, my, x, y, width, height, font());
    }

    /** Delete (in-game categories) or block (datapack categories) after an explicit confirmation. */
    private void confirmDestroy(ResourceLocation id, boolean root) {
        confirmDestroy(id, root, Map.copyOf(EditClientState.snapshot()));
    }

    private void confirmDestroy(ResourceLocation id, boolean root, Map<String, Long> versions) {
        Packets.CategoryMetadata value = metadata(id, root);
        if (value == null || value.protectedEntry()) return;
        boolean remove = value.source() == 1;
        Component name = categoryName(id);
        List<Component> body = new ArrayList<>();
        if (root) {
            long subs = children.getOrDefault(id, List.of()).stream().filter(sub -> !sub.equals(id)).count();
            if (subs > 0) body.add(tr("cuisinejournal.edit.confirm.children", subs));
        }
        int foods = root ? total(id) : groups.entrySet().stream()
                .filter(e -> e.getKey().subcategory().equals(id)).mapToInt(e -> e.getValue().foods().size()).sum();
        if (foods > 0) body.add(tr("cuisinejournal.edit.confirm.foods", foods));
        body.add(tr("cuisinejournal.edit.confirm.records"));
        body.add(tr(remove ? "cuisinejournal.edit.confirm.permanent" : "cuisinejournal.edit.confirm.recoverable"));
        Packets.CategoryEditRequest.Action action = remove
                ? (root ? Packets.CategoryEditRequest.Action.DELETE_ROOT : Packets.CategoryEditRequest.Action.DELETE_SUB)
                : (root ? Packets.CategoryEditRequest.Action.BLOCK_ROOT : Packets.CategoryEditRequest.Action.BLOCK_SUB);
        modal = new ConfirmModal(tr(remove ? "cuisinejournal.edit.confirm.remove" : "cuisinejournal.edit.confirm.block", name),
                body, tr(remove ? "cuisinejournal.edit.remove" : "cuisinejournal.edit.block"),
                () -> submit(new Packets.CategoryEditRequest(0, action, id, value.parent(), requestName(name),
                        null, List.of(), versions), null),
                () -> modal = null);
        openInContent(modal);
    }

    /** Display name sent along so the server toast reads "已删除：甜点" instead of a raw id. */
    private static String requestName(Component name) {
        String text = name.getString();
        return text.length() > 64 ? text.substring(0, 64) : text;
    }

    private Component categoryName(ResourceLocation id) {
        if (id == null) return Component.empty();
        Packets.CollectionView root = categories.get(id);
        if (root != null) return rootName(root);
        for (Map.Entry<Key, Group> entry : groups.entrySet())
            if (entry.getKey().subcategory().equals(id)) return groupName(entry.getValue().view());
        Packets.CategoryMetadata value = metadata(id);
        if (value != null) return value.literalTitle() ? Component.literal(value.titleKey()) : tr(value.titleKey());
        return Component.literal(id.toString());
    }
    private void summary(GuiGraphics g, boolean small, int mx, int my) {
        Packets.CollectionView root = categories.get(category); Group group = small ? groups.get(key) : null;
        ResourceLocation icon = small ? group.view().icon() : root.icon();
        if (icon != null) g.renderFakeItem(JournalText.stackOf(icon), x, y + 15);
        int found = small ? group.discovered() : found(category), total = small ? group.foods().size() : total(category);
        int countWidth = Math.min(width / 2, font().width(tr("cuisinejournal.category.progress", found, total)));
        Component name = small ? groupName(group.view()) : rootName(root);
        int nameWidth = Math.max(1, width - countWidth - 27 - (small ? (ClientJournalState.perDishMastery()
                ? font().width(totalStars(group.stars(), group.maxStars(), JournalTheme.TEXT_FAINT)) : starsWidth()) + 6 : 0));
        int titleY = y + 19;
        clipped(g, name, x + 21, titleY, nameWidth, JournalTheme.TEXT);
        if (!editing || small) progress(g, found, total, right(), small ? y + 19 : y + 15, countWidth);
        if (!small) {
            List<Group> rootGroups = children.getOrDefault(category, List.of()).stream()
                    .map(sub -> groups.get(new Key(category, sub))).filter(java.util.Objects::nonNull).toList();
            int totalStars = rootGroups.stream().mapToInt(Group::stars).sum();
            Component compactTotal = totalStars(totalStars,
                    rootGroups.stream().mapToInt(Group::maxStars).sum(), JournalTheme.TEXT_FAINT);
            int totalWidth = Math.min(width / 2, font().width(compactTotal));
            if (!editing) clipped(g, compactTotal, right() - totalWidth, y + 26, totalWidth, JournalTheme.TEXT_FAINT);
            else if (root.isModCategory()) {
                Component source = tr("cuisinejournal.edit.source.mod");
                JournalTheme.value(g, font(), source, right(), y + 15, JournalTheme.TEXT_DIM);
                clipped(g, tr("cuisinejournal.edit.mod_locked"), right() - Math.min(font().width(tr("cuisinejournal.edit.mod_locked")), width / 2),
                        y + 26, width / 2, JournalTheme.TEXT_FAINT);
            } else {
                Packets.CategoryMetadata meta = metadata(category, true);
                Component source = sourceText(meta);
                int sourceWidth = Math.min(font().width(source), width / 2);
                boolean created = meta != null && meta.source() == 1;
                PixelIcons.drawSourceBadge(g, created ? PixelIcons.Icon.CREATED : PixelIcons.Icon.PACKET,
                        right() - sourceWidth - 12, y + 14, created ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_FAINT,
                        meta != null && meta.source() == 2);
                JournalTheme.value(g, font(), source, right(), y + 15, JournalTheme.TEXT_DIM);
                link(g, tr("cuisinejournal.edit.edit_root"), right(), y + 26, mx, my);
            }
            g.fill(x, y + 39, right(), y + 40, editing ? JournalTheme.EDIT_TINT : JournalTheme.DIVIDER);
        }
        int starX = x + 21 + Math.min(nameWidth, font().width(name)) + 6;
        if (small && ClientJournalState.perDishMastery()) {
            // Per-dish scope: the subcategory shows its dishes' star total; each dish's own
            // progress lives in its cell and the detail panel.
            Component starTotal = totalStars(group.stars(), group.maxStars(), JournalTheme.TEXT_FAINT);
            g.drawString(font(), starTotal, starX, titleY, JournalTheme.TEXT_FAINT, false);
            Component label = tr("cuisinejournal.category.rank_dish_hint");
            int labelWidth = Math.min(font().width(label), width - 24);
            int labelX = right() - labelWidth;
            int barWidth = Math.max(3, labelX - x - 6);
            JournalTheme.bar(g, x, y + 33, barWidth, 4,
                    group.maxStars() == 0 ? 0 : (float) group.stars() / group.maxStars(), false);
            clipped(g, label, labelX, y + 31, labelWidth, JournalTheme.TEXT_DIM);
        } else if (small) {
            stars(g, group, starX, titleY);
            int threshold = ClientJournalState.masteryCurve().thresholdFor(group.rank() + 1);
            Component label = group.cap() <= 0 ? tr("cuisinejournal.category.rank_locked")
                    : group.rank() >= group.cap() && group.cap() < MasteryRank.highestIndex()
                    ? tr("cuisinejournal.category.rank_capped")
                    : threshold < 0 ? tr("cuisinejournal.category.rank_complete")
                    : tr("cuisinejournal.category.rank_progress", number(group.prepared()), number(threshold));
            int labelWidth = Math.min(font().width(label), width - 24);
            int labelX = right() - labelWidth;
            int barWidth = Math.max(3, labelX - x - 6);
            float fill = group.cap() <= 0 ? 0 : group.rank() >= group.cap() ? 1 : fraction(group);
            JournalTheme.bar(g, x, y + 33, barWidth, 4, fill, false);
            clipped(g, label, labelX, y + 31, labelWidth, JournalTheme.TEXT_DIM);
        }
    }
    private int stars(GuiGraphics g, Group group, int sx, int sy) {
        int cursor = sx;
        for (int i = 1; i <= MasteryRank.highestIndex(); i++) {
            int slot = i - 1;
            if (slot < group.rank()) {
                Component glyph = glyph("\uE001", JournalTheme.TEXT_ACCENT);
                g.drawString(font(), glyph, cursor, sy, JournalTheme.TEXT_ACCENT, false);
            } else if (slot < group.cap()) {
                drawHollowStar(g, cursor, sy, 0xFF8A6F45);
            } else {
                Component glyph = glyph("\uE001", 0xFF3E332A);
                g.drawString(font(), glyph, cursor, sy, 0xFF3E332A, false);
            }
            cursor += 8 + 1;
        }
        return cursor - sx;
    }
    private float fraction(Group group) {
        int threshold = ClientJournalState.masteryCurve().thresholdFor(group.rank() + 1);
        return threshold <= 0 ? 1 : Math.min(1, (float) group.prepared() / threshold);
    }
    private void discovery(GuiGraphics g, int bx, int by, int bw, int found, int total, boolean complete) {
        g.fill(bx, by, bx + bw, by + 2, JournalTheme.BAR_TRACK);
        if (total > 0 && found > 0) g.fill(bx, by, bx + Math.round((float) found / total * bw), by + 2,
                complete ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_ACCENT);
    }
    private void progress(GuiGraphics g, int found, int total, int right, int py, int available) {
        Component text = tr("cuisinejournal.category.progress", number(found), number(total));
        int textWidth = Math.min(font().width(text), Math.max(1, available));
        clipped(g, text, right - textWidth, py, available, JournalTheme.TEXT_DIM);
    }

    private void grandOverview(GuiGraphics g) {
        int discovered = ClientJournalState.discoveredCount();
        int total = ClientJournalState.dishCount();
        int rankTotal = groups.values().stream().mapToInt(Group::stars).sum();
        int maxStars = groups.values().stream().mapToInt(Group::maxStars).sum();
        int unlockable = (int) groups.values().stream().filter(Group::canRankUp).count();

        int leftWidth = Math.max(1, (width - 12) / 2);
        int rightColumn = x + leftWidth + 12;
        int oy = y + ROOT_SHIFT;
        g.drawString(font(), tr("cuisinejournal.category.grand_food"), x, oy + 16,
                JournalTheme.TEXT_DIM, false);
        JournalTheme.value(g, font(), Component.literal(JournalText.fraction(discovered, total)),
                x + leftWidth, oy + 16, JournalTheme.TEXT);
        JournalTheme.bar(g, x, oy + 29, leftWidth, 3,
                total == 0 ? 0 : (float) discovered / total, discovered == total && total > 0);

        if (editing) {
            link(g, tr("cuisinejournal.edit.blocked.count", hiddenCount()), right(), oy + 16, mouseX, mouseY);
            clipped(g, tr("cuisinejournal.edit.hint_right_click"), right() - (width - leftWidth - 12), oy + 26,
                    width - leftWidth - 12, JournalTheme.TEXT_FAINT);
        } else {
            JournalTheme.value(g, font(), totalStars(rankTotal, maxStars, JournalTheme.TEXT_FAINT), right(), oy + 16, JournalTheme.TEXT_FAINT);
            JournalTheme.value(g, font(), tr("cuisinejournal.category.grand_unlockable", unlockable), right(), oy + 26, JournalTheme.TEXT_FAINT);
        }
        g.fill(x, oy + 40, right(), oy + 41, editing ? JournalTheme.EDIT_TINT : JournalTheme.DIVIDER);
    }

    private void renderCenteredEmpty(GuiGraphics g, int areaWidth) {
        Component message = tr("cuisinejournal.empty.search");
        int centerX = x + areaWidth / 2;
        int centerY = bodyY + bodyHeight / 2;
        List<net.minecraft.util.FormattedCharSequence> lines = font().split(message, areaWidth);
        for (int i = 0; i < lines.size(); i++) {
            int lineWidth = font().width(lines.get(i));
            g.drawString(font(), lines.get(i), centerX - lineWidth / 2,
                    centerY + i * font().lineHeight, JournalTheme.TEXT_DIM, false);
        }
    }

    private void drawHollowStar(GuiGraphics g, int x, int y, int color) {
        // The outline follows the bitmap star's silhouette while keeping the
        // same 8x8 slot as the filled glyph.
        String[] shape = {"....#...", "...###..", ".#######", "..#####.",
                "...###..", "..##.##.", "..#...#.", "........"};
        for (int py = 0; py < shape.length; py++) {
            for (int px = 0; px < shape[py].length(); px++) {
                if (shape[py].charAt(px) != '#') continue;
                boolean edge = py == 0 || py == shape.length - 1 || px == 0
                        || px == shape[py].length() - 1
                        || shape[py - 1].charAt(px) == '.'
                        || shape[py + 1].charAt(px) == '.'
                        || shape[py].charAt(px - 1) == '.'
                        || shape[py].charAt(px + 1) == '.';
                if (edge) g.fill(x + px, y + py, x + px + 1, y + py + 1, color);
            }
        }
    }
    private void tools(GuiGraphics g, int mx, int my, float partialTick) {
        g.fill(x, toolY, right(), toolY + toolHeight, 0xFF140F0A);
        JournalTheme.border(g, x, toolY, width, toolHeight, 0xFF3A2C1F);
        if (editing && !allGrid()) {
            // Don't show add button for mod categories
            Packets.CollectionView root = categories.get(category);
            boolean isModCategory = root != null && root.isModCategory();
            if (!isModCategory) {
                boolean addHover = inside(mx, my, x + 3, toolY + 3, 12, 12);
                boolean canAdd = editState.addingFoods || canAddHere();
                squareButton(g, x + 3, toolY + 3, 12, addHover && canAdd, editState.addingFoods);
                // 8x8 plus with a 2px stroke: symmetric, so it lands dead centre in the 12x12 box.
                int plusColor = !canAdd ? JournalTheme.TEXT_FAINT : editState.addingFoods || addHover
                        ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_DIM;
                g.fill(x + 8, toolY + 5, x + 10, toolY + 13, plusColor);
                g.fill(x + 5, toolY + 8, x + 13, toolY + 10, plusColor);
                if (addHover) g.renderTooltip(font(),
                        tr(canAdd ? "cuisinejournal.edit.add_food" : "cuisinejournal.edit.disabled.fallback_add"), mx, my);
            }
        }
        dimensions.renderButton(g, font(), mx, my); statuses.renderButton(g, font(), mx, my); sorts.renderButton(g, font(), mx, my);
        if (search != null) JournalSearchField.render(g, font(), search, gridSearchX, gridSearchY, SEARCH_WIDTH, 12,
                editing && editState.addingFoods ? tr("cuisinejournal.edit.search_all")
                        : tr("cuisinejournal.journal.search_category", groupName(groups.get(key).view())),
                mx, my, partialTick);
    }

    private void smallItem(GuiGraphics g, ResourceLocation item, int x, int y, boolean dim) {
        g.fill(x, y, x + 9, y + 9, 0xFF0E0A07);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5F, 0.5F, 1.0F);
        g.renderFakeItem(JournalText.stackOf(item), 0, 0);
        if (dim) {
            g.pose().translate(0, 0, 200);
            g.fill(0, 0, 16, 16, 0x88140F0A);
        }
        g.pose().popPose();
    }

    private void starTotal(GuiGraphics g, int current, int maximum, int x, int y) {
        g.drawString(font(), totalStars(current, maximum, JournalTheme.TEXT_FAINT), x, y, JournalTheme.TEXT_FAINT, false);
    }
    private MutableComponent totalStars(int current, int maximum, int dim) {
        return Component.empty().append(glyph("\uE001", JournalTheme.TEXT_ACCENT)).append(" ")
                .append(tr("cuisinejournal.category.star_total",
                        Component.literal(Integer.toString(current)).withStyle(s -> s.withColor(JournalTheme.TEXT_ACCENT)),
                        Component.literal(Integer.toString(maximum)).withStyle(s -> s.withColor(dim)))
                        .withStyle(s -> s.withColor(dim)));
    }
    private static MutableComponent glyph(String symbol, int color) {
        return Component.literal(symbol).withStyle(s -> s.withFont(ICON_FONT).withColor(color));
    }
    private int starsWidth() { return (font().width(glyph("\uE001", JournalTheme.TEXT_ACCENT)) + 1)
            * MasteryRank.highestIndex(); }
    private static Component number(long value) {
        return Component.literal(JournalText.number(value)).withStyle(s -> s.withColor(JournalTheme.TEXT));
    }
    private int[] closeButtonBounds() {
        return new int[]{detailX + detailWidth - 15, y + 18, 13, 13};
    }

    /** Dishes whose ingredient detail was already requested this session, so we ask only once. */
    private final java.util.Set<ResourceLocation> requestedDetails = new java.util.HashSet<>();
    private long detailGeneration = -1;

    private void detail(GuiGraphics g, Session s, int mouseX, int mouseY) {
        int dy = wide ? bodyY : y + 16, dh = wide ? bodyHeight : height - 16;
        Packets.DishView dish = ClientJournalState.dish(s.grid.selected()); if (dish == null) return;
        if ((ClientJournalState.detail(dish.target()) == null || !ClientJournalState.isDiscovered(dish.target()))
                && requestedDetails.add(dish.target())) {
            Network.toServer(new Packets.RequestDishDetail(dish.target()));
        }
        if (!wide) {
            g.pose().pushPose(); g.pose().translate(0, 0, 350);
            g.fill(x, y, right(), bottom(), 0xB0000000);
            g.fill(detailX, y + 16, detailX + detailWidth, bottom(), 0xFF140F0A);
            JournalTheme.border(g, detailX, y + 16, detailWidth, bottom() - y - 16, JournalTheme.PANEL_BORDER);
            g.fill(detailX, y + 32, detailX + detailWidth, y + 33, 0xFF3A2C1F);
        }
        int padding = wide ? 0 : 14;
        s.detailMaxScroll = FoodDetailPanel.render(g, font(), dish, detailX, dy + padding, detailWidth, dh - padding,
                s.detailScroll, editing, menusOpen() ? Integer.MIN_VALUE : mouseX, mouseY);
        int categoryY = dy + padding + FoodDetailPanel.categoryY(font(), dish, detailWidth) - s.detailScroll;
        if (editing && categoryY + 13 >= dy + padding && categoryY + 13 < dy + dh && !blocksInput()) {
            // The marker sits at the right end of the path row; keep the two hover areas disjoint.
            boolean onMarker = inside(mouseX, mouseY, detailX + detailWidth - 16, categoryY + 12, 11, 11);
            if (!onMarker && inside(mouseX, mouseY, detailX + 6, categoryY + 13, detailWidth - 24, 11))
                g.renderTooltip(font(), foodPath(dish.target()), mouseX, mouseY);
            else if (onMarker)
                g.renderTooltip(font(), tr(ClientJournalState.categoryEditor().manualItems().contains(dish.target())
                        ? "cuisinejournal.edit.source.manual_food" : "cuisinejournal.edit.source.rules"), mouseX, mouseY);
        }
        if (!wide) {
            clipped(g, tr("cuisinejournal.category.detail.title"), detailX + 6, y + 20, detailWidth - 23, JournalTheme.TEXT_DIM);
            int[] close = closeButtonBounds();
            boolean hover = inside(mouseX, mouseY, close[0], close[1], close[2], close[3]);
            if (hover) {
                g.fill(close[0], close[1], close[0] + close[2], close[1] + close[3], 0x33FFE9C7);
                JournalTheme.border(g, close[0], close[1], close[2], close[3], JournalTheme.TEXT_DIM);
            }
            int color = hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM;
            for (int i = 0; i < 7; i++) {
                g.fill(close[0] + 3 + i, close[1] + 3 + i, close[0] + 4 + i, close[1] + 4 + i, color);
                g.fill(close[0] + 9 - i, close[1] + 3 + i, close[0] + 10 - i, close[1] + 4 + i, color);
            }
            if (hover) g.renderTooltip(font(), tr("cuisinejournal.category.detail.close"), mouseX, mouseY);
            g.pose().popPose();
        }
    }
    private void card(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        g.fill(x, y, x + w, y + h, JournalTheme.CARD);
        // Same highlight as the dashed "new" card so every card brightens equally on hover.
        if (hover) g.fill(x, y, x + w, y + h, 0x22FFE9C7);
        JournalTheme.border(g, x, y, w, h, JournalTheme.PANEL_BORDER);
    }
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        refresh();
        if (modal != null && modal.isOpen()) { modal.mouseClicked(mx, my, button); return true; }
        if (editMenu != null && editMenu.isOpen()) { editMenu.mouseClicked(mx, my, button); return true; }
        if (editing && editHeaderClick(mx, my, button)) return true;
        if (viewToggleClick(mx, my, button)) return true;
        if (browseClick(mx, my, button)) return true;
            if (editing && button == 0) {
            int[] export = exportBounds();
            if (inside(mx, my, export[0], export[1], export[2], export[3])) {
                CuisineUiSoundCompat.click();
                SchemeModal dialog = new SchemeModal(font(), true, request -> exportRequest = request);
                modal = dialog;
                openInContent(dialog);
                return true;
            }
        }
            if (ClientJournalState.canEditCategories() && button == 0) {
            int[] pencil = pencilBounds();
            if (inside(mx, my, pencil[0], pencil[1], pencil[2], pencil[3])) {
                CuisineUiSoundCompat.toggle(!ClientJournalState.categoryEditing());
                Network.toServer(new Packets.CategoryEditModeRequest(!ClientJournalState.categoryEditing()));
                return true;
            }
        }
        // Detail-panel items (dish header, ingredients) open the recipe viewer; checked before
        // the grid because the panel lies outside it and would otherwise never see the click.
        if (grid() && (wide || detailPopup) && session().grid.selected() != null
                && !menusOpen() && detailIngredientClick(mx, my, button)) return true;
        if (grid() && detailPopup && !wide) {
            if (editing && detailChangeClick(mx, my, button)) return true;
            int[] close = closeButtonBounds();
            if (button == 0 && (inside(mx, my, close[0], close[1], close[2], close[3])
                    || !inside(mx, my, detailX, y + 16, detailWidth, height - 16))) {
                CuisineUiSoundCompat.click(); detailPopup = false;
            }
            return true;
        }
        if (editing && grid() && detailChangeClick(mx, my, button)) return true;
        if (grid() && button == 0 && clickMenus(mx, my)) return true;
        if (button == 0) {
            boolean dragged = grid() ? session().grid.mouseClicked(mx, my, button) : switch (level) {
                case ROOT -> roots.mouseClicked(mx, my, button);
                case SUBCATEGORY -> subgrid(category).mouseClicked(mx, my, button);
                case GRID -> session().grid.mouseClicked(mx, my, button);
            };
            if (dragged) return true;
        }
        if (button == 0 && level != Level.ROOT && inside(mx, my, x, y, 11, 11)) { back(); return true; }
        if (button == 0 && level != Level.ROOT && inside(mx, my, x + 15, y, font().width(journalLabel()), 11)) { root(); return true; }
        if (button == 0 && level == Level.GRID && !directModGrid()) {
            int cx = x + 15 + font().width(journalLabel()) + font().width(" \u203a ");
            if (inside(mx, my, cx, y, font().width(rootName(categories.get(category))), 11)) { back(); return true; }
            if (JournalSearchField.click(search, mx, my, button, gridSearchX, gridSearchY, SEARCH_WIDTH, 12)) return true;
        }
        ResourceLocation clicked = grid() ? session().grid.itemAt(mx, my) : switch (level) {
            case ROOT -> roots.itemAt(mx, my); case SUBCATEGORY -> subgrid(category).itemAt(mx, my); case GRID -> session().grid.itemAt(mx, my);
        };
        if (clicked == null) return false;
        if (editing && !grid()) {
            if (NEW_CARD.equals(clicked)) {
                if (button == 0) { CuisineUiSoundCompat.click(); openForm(level == Level.ROOT, null, category); }
                return true;
            }
            // Check if this is a mod category
            boolean isModCategory = false;
            if (level == Level.ROOT) {
                Packets.CollectionView root = categories.get(clicked);
                isModCategory = root != null && root.isModCategory();
            } else if (level == Level.SUBCATEGORY) {
                Packets.CollectionView root = categories.get(category);
                isModCategory = root != null && root.isModCategory();
            }
            // Don't show menu for mod categories on left-click of three dots area
            int[] bounds = level == Level.ROOT ? roots.itemBounds(clicked) : subgrid(category).itemBounds(clicked);
            if (button == 1 || (button == 0 && !isModCategory && inside(mx, my, bounds[0] + bounds[2] - 16, bounds[1] + 3, 12, 10))) {
                CuisineUiSoundCompat.click();
                openCategoryMenu(clicked, level == Level.ROOT, (int) mx, (int) my); return true;
            }
        }
        if (grid()) {
            if (editing && button == 1) { CuisineUiSoundCompat.click(); openFoodMenu(clicked, (int) mx, (int) my); return true; }
            if (button == 0 || button == 1) {
                CuisineUiSoundCompat.item(JournalText.stackOf(clicked));
                session().grid.selected(clicked); session().detailScroll = 0;
                if (screen != null) screen.clearReturn();
                if (editing) {
                    if (editState.addingFoods) {
                        // Foods already in this subcategory are shown for context only.
                        if (belongsHere(clicked)) return true;
                        List<ResourceLocation> pickable = session().grid.items().stream()
                                .filter(food -> !belongsHere(food)).toList();
                        editState.select(clicked, pickable, Screen.hasShiftDown());
                    } else editState.select(clicked, session().grid.items(), Screen.hasShiftDown());
                    layoutTools();
                }
                detailPopup = !wide && !editing;
            }
        } else if (button == 0) {
            ResourceLocation icon = level == Level.ROOT ? categories.get(clicked).icon()
                    : groups.get(new Key(category, clicked)).view().icon();
            CuisineUiSoundCompat.item(icon == null ? net.minecraft.world.item.ItemStack.EMPTY : JournalText.stackOf(icon));
            if (level == Level.ROOT) {
                category = clicked; key = null; level = Level.SUBCATEGORY;
                var root = categories.get(clicked);
                var subs = children.getOrDefault(clicked, List.of());
                if (root != null && root.isModCategory() && subs.size() == 1) {
                    key = new Key(category, subs.get(0)); level = Level.GRID;
                    if (screen != null) screen.clearReturn();
                    if (browsing() && !rootNameMatches(category)) session().query = browseQuery.strip();
                }
            }
            else {
                key = new Key(category, clicked); level = Level.GRID;
                if (screen != null) screen.clearReturn();
                // Matched by a food rather than by a category name: carry the query into the food grid.
                Group entered = groups.get(key);
                if (browsing() && !rootNameMatches(category)
                        && (entered == null || !nameMatches(groupName(entered.view())))) {
                    session().query = browseQuery.strip();
                }
            }
            detailPopup = false; layoutView();
        }
        return true;
    }
    private boolean clickMenus(double mx, double my) {
        if (dimensions.isOpen()) return dimensions.mouseClicked(font(), mx, my);
        if (statuses.isOpen()) return statuses.mouseClicked(font(), mx, my);
        if (sorts.isOpen()) return sorts.mouseClicked(font(), mx, my);
        return dimensions.mouseClicked(font(), mx, my) || statuses.mouseClicked(font(), mx, my) || sorts.mouseClicked(font(), mx, my);
    }

    /**
     * Detail-panel clicks: panel controls (star switch, flavors, recipe arrows) are handled by
     * the panel; the dish icon opens recipes / uses; an ingredient cell opens its tag or
     * ingredient in the ingredient journal, or its dish here. Right click on a cell opens uses.
     */
    private boolean detailIngredientClick(double mx, double my, int button) {
        FoodDetailPanel.Click click = FoodDetailPanel.click(mx, my, button);
        if (click == null) return false;
        switch (click.kind()) {
            case HANDLED -> CuisineUiSoundCompat.click();
            case RECIPES -> { CuisineUiSoundCompat.click();
                com.chi__you.cuisinejournal.client.compat.RecipeViewerBridge.showRecipes(JournalText.stackOf(click.id())); }
            case USES -> { CuisineUiSoundCompat.click();
                com.chi__you.cuisinejournal.client.compat.RecipeViewerBridge.showUses(JournalText.stackOf(click.id())); }
            case DISH -> { CuisineUiSoundCompat.click(); openFood(click.id()); }
            case TAG, INGREDIENT -> {
                if (screen != null && screen.page("ingredients") instanceof IngredientsPage ingredients) {
                    screen.jump(this, ingredients);
                    if (!ingredients.openEntry(click.id())) screen.select(this);
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (modal != null && modal.isOpen()) return modal.mouseDragged(mx, my, button, dragX, dragY);
        if (editMenu != null && editMenu.isOpen()) return true;
        if (level == Level.ROOT && !allView) return roots.mouseDragged(mx, my, button);
        if (level == Level.SUBCATEGORY) return subgrid(category).mouseDragged(mx, my, button);
        return session().grid.mouseDragged(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (modal != null && modal.isOpen()) return modal.mouseReleased(mx, my, button);
        if (editMenu != null && editMenu.isOpen()) return true;
        if (level == Level.ROOT && !allView) return roots.mouseReleased(button);
        if (level == Level.SUBCATEGORY) return subgrid(category).mouseReleased(button);
        return session().grid.mouseReleased(button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (modal != null && modal.isOpen()) return modal.mouseScrolled(mouseX, mouseY, delta);
        if (editMenu != null && editMenu.isOpen()) return true;
        if (level == Level.ROOT && !allView) return roots.mouseScrolled(mouseX, mouseY, delta);
        if (level == Level.SUBCATEGORY) return subgrid(category).mouseScrolled(mouseX, mouseY, delta);
        if (menusOpen()) {
            dimensions.mouseScrolled(font(), mouseX, mouseY, delta); statuses.mouseScrolled(font(), mouseX, mouseY, delta);
            sorts.mouseScrolled(font(), mouseX, mouseY, delta); return true;
        }
        Session s = session();
        if ((wide || detailPopup) && inside(mouseX, mouseY, detailX, wide ? bodyY : y + 16, detailWidth,
                wide ? bodyHeight : height - 16)) {
            int previous = s.detailScroll;
            s.detailScroll = Math.max(0, Math.min(s.detailMaxScroll, s.detailScroll - (int) (delta * 11)));
            CuisineUiSoundCompat.scroll(previous, s.detailScroll);
            return true;
        }
        return detailPopup || s.grid.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override public boolean textInputFocused() {
        if (modal != null && modal.isOpen()) return modal.textInputFocused();
        return search != null && search.isFocused() || browse != null && browse.isFocused();
    }

    @Override public boolean keyPressed(int k, int scan, int modifiers) {
        if (modal != null && modal.isOpen()) { modal.keyPressed(k, scan, modifiers); return true; }
        if (editMenu != null && editMenu.isOpen()) { if (k == GLFW.GLFW_KEY_ESCAPE) editMenu.close(); return true; }
        if (browse != null && browse.isFocused()) {
            // Esc clears the query first, then leaves the box; Enter/Tab just leave it.
            if (k == GLFW.GLFW_KEY_ESCAPE) { if (!browse.getValue().isEmpty()) browse.setValue(""); else browse.setFocused(false); }
            else if (k == GLFW.GLFW_KEY_TAB || k == GLFW.GLFW_KEY_ENTER) browse.setFocused(false);
            else browse.keyPressed(k, scan, modifiers);
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) {
            // Opened from another page's link: Esc goes back there first (the screen handles it).
            if (screen != null && screen.hasReturn() && !editing && !menusOpen()) return false;
            if (detailPopup) { detailPopup = false; return true; }
            if (menusOpen()) { closeMenus(); return true; }
            if (search != null && search.isFocused()) { search.setFocused(false); return true; }
            if (editing) {
                // Esc peels back edit state one layer at a time and never closes the journal directly.
                if (!editState.selected.isEmpty()) {
                    editState.clearSelection(); if (grid()) layoutTools(); return true;
                }
                if (editState.addingFoods) {
                    editState.addingFoods = false;
                    editState.clearSelection();
                    layoutView();
                    return true;
                }
                Network.toServer(new Packets.CategoryEditModeRequest(false));
                return true;
            }
            if (level != Level.ROOT) { back(); return true; } return false;
        }
        if (!grid()) return false;
        if (search != null && search.isFocused()) {
            if (k == GLFW.GLFW_KEY_TAB || k == GLFW.GLFW_KEY_ENTER) search.setFocused(false);
            else search.keyPressed(k, scan, modifiers); return true;
        }
        if (detailPopup || menusOpen()) return true;
        if (editing && k == GLFW.GLFW_KEY_A && Screen.hasControlDown()) {
            CuisineUiSoundCompat.selectAll();
            editState.beginSelection();
            editState.selected.addAll(session().grid.items()); layoutTools(); return true;
        }
        int offset = switch (k) {
            case GLFW.GLFW_KEY_LEFT -> -1; case GLFW.GLFW_KEY_RIGHT -> 1;
            case GLFW.GLFW_KEY_UP -> -session().grid.columns(); case GLFW.GLFW_KEY_DOWN -> session().grid.columns(); default -> 0;
        };
        if (offset == 0) return false;
        ResourceLocation previous = session().grid.selected();
        session().grid.moveSelection(offset);
        if (!java.util.Objects.equals(previous, session().grid.selected()))
            CuisineUiSoundCompat.item(JournalText.stackOf(session().grid.selected()));
        session().detailScroll = 0; return true;
    }
    @Override public boolean charTyped(char c, int modifiers) {
        if (modal != null && modal.isOpen()) { modal.charTyped(c, modifiers); return true; }
        if (editMenu != null && editMenu.isOpen()) return true;
        if (browse != null && browse.isFocused()) return browse.charTyped(c, modifiers);
        return search != null && search.isFocused() && search.charTyped(c, modifiers);
    }
    @Override public void tick() {
        if (search != null) search.tick();
        if (browse != null) browse.tick();
        if (modal != null && modal.isOpen()) modal.tick();
        if (modal instanceof HiddenCategoriesModal hidden && hidden.isOpen()
                && ClientJournalState.categoryEditor() != null
                && refreshHiddenAfterSuccess && ClientJournalState.categoryEditor() != hiddenSnapshot && !editState.waiting()) {
            // The authoritative editor sync can arrive after the edit result.
            hiddenSnapshot = ClientJournalState.categoryEditor();
            hidden.refreshEntries(hiddenSnapshot.categories());
            refreshHiddenAfterSuccess = false;
        }
        if (flashTicks > 0) flashTicks--;
        pollEditResult();
        if (exportRequest != 0) {
            Packets.SchemeResult result = com.chi__you.cuisinejournal.client.SchemeClientState.take(exportRequest);
            if (result != null) {
                exportRequest = 0;
                Component message = IngredientsPage.schemeMessage(result);
                if (screen != null) screen.editToast(result.success() ? message
                        : tr("cuisinejournal.edit.toast.failed", message), result.success(), false);
                if (modal instanceof SchemeModal dialog && dialog.isOpen()) dialog.accept(result);
            }
        }
    }
    @Override public void onHidden() {
        clearPageVisitState();
    }

    /**
     * Clears state that belongs to the whole page visit. This is called only when the
     * page really leaves the screen; JournalScreen deliberately skips it for JEI/REI/EMI
     * round trips, and back()/root() deliberately skip it for category navigation.
     */
    private void clearPageVisitState() {
        if (search != null) search.setFocused(false);
        if (browse != null) browse.setFocused(false);
        // Leaving the page starts the next visit unfiltered.
        if (browsing()) { browseQuery = ""; if (browse != null) browse.setValue(""); updateCards(); }
        detailPopup = false;
        editState.reset();
        editMenu = null;
        modal = null;
        // A new visit starts without a remembered food, while each Session still remembers
        // selection during normal category navigation inside the current visit.
        sessions.values().forEach(Session::clearSelection);
        if (allSession != null) { allSession.clearSelection(); allSession.query = ""; }
        FoodDetailPanel.clearHotspots();
        if (screen != null) screen.toastBatchOffset(0);
    }
    @Override public boolean blocksInput() { return (modal != null && modal.isOpen()) || (editMenu != null && editMenu.isOpen()); }
    private void back() {
        if (screen != null) screen.clearReturn();
        CuisineUiSoundCompat.page();
        editState.reset();
        // A return to the category level may later reopen the same selected
        // food. This is intentionally not a page-exit reset: Session owns the
        // selected food, scroll, filters and sort for this subcategory.
        requestedDetails.clear();
        if (level == Level.GRID && !directModGrid()) { level = Level.SUBCATEGORY; key = null; }
        else { level = Level.ROOT; category = null; key = null; } detailPopup = false; layoutView();
    }
    private boolean directModGrid() {
        var root = categories.get(category);
        return root != null && root.isModCategory() && children.getOrDefault(category, List.of()).size() == 1;
    }
    private void root() {
        if (screen != null) screen.clearReturn();
        CuisineUiSoundCompat.page(); editState.reset(); requestedDetails.clear();
        // Keep subcategory Sessions alive; returning into one restores its browse state.
        level = Level.ROOT; category = null; key = null; detailPopup = false; layoutView();
    }
    private void closeMenus() {
        if (dimensions != null) dimensions.close(); if (statuses != null) statuses.close(); if (sorts != null) sorts.close();
    }
    private boolean menusOpen() { return dimensions != null && (dimensions.isOpen() || statuses.isOpen() || sorts.isOpen()); }
    private void clipped(GuiGraphics g, Component text, int tx, int ty, int max, int color) {
        g.drawString(font(), net.minecraft.locale.Language.getInstance().getVisualOrder(
                font().substrByWidth(text, Math.max(0, max))), tx, ty, color, false);
    }
    private static Component rootName(Packets.CollectionView root) {
        if (root.isModCategory()) return JournalText.modName(root.id().getNamespace(), root.titleKey());
        return root.literalTitle() ? Component.literal(root.titleKey()) : tr(root.titleKey());
    }
    private static Component groupName(Packets.SubcategoryView sub) {
        if (sub.id().getPath().equals("_mod")) return JournalText.modName(sub.id().getNamespace(), sub.titleKey());
        return sub.literalTitle() ? Component.literal(sub.titleKey()) : tr(sub.titleKey());
    }
    private static Component dimensionName(ResourceLocation id) {
        String k = "dimension." + id.getNamespace() + "." + id.getPath();
        return net.minecraft.client.resources.language.I18n.exists(k) ? tr(k) : Component.literal(id.toString());
    }
    private static MutableComponent tr(String k, Object... args) { return Component.translatable(k, args); }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override
    public Component emptyMessage() {
        if (!ClientJournalState.directoryReady()) return Component.translatable(ClientJournalState.directoryFailed()
                ? "cuisinejournal.sync.failed" : "cuisinejournal.sync.loading");
        return browsing() ? Component.translatable("cuisinejournal.category.search_empty")
                : Component.translatable("cuisinejournal.empty.categories");
    }
}
