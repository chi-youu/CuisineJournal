package com.chi__you.cuisinejournal.client.journal.page;

import com.mojang.blaze3d.systems.RenderSystem;
import com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat;
import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.FlavorClientState;
import com.chi__you.cuisinejournal.client.journal.FlavorUi;
import com.chi__you.cuisinejournal.client.journal.edit.SchemeModal;
import com.chi__you.cuisinejournal.client.journal.edit.FlavorLibraryModal;
import com.chi__you.cuisinejournal.client.journal.edit.FlavorPickerModal;
import com.chi__you.cuisinejournal.client.journal.edit.JournalModal;
import com.chi__you.cuisinejournal.network.Network;
import com.chi__you.cuisinejournal.client.compat.RecipeViewerBridge;
import com.chi__you.cuisinejournal.client.journal.JournalText;
import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.client.journal.ScrollList;
import com.chi__you.cuisinejournal.client.journal.JournalDropdown;
import com.chi__you.cuisinejournal.client.journal.JournalSearchField;
import com.chi__you.cuisinejournal.client.journal.CreativeFoodOrder;
import com.chi__you.cuisinejournal.client.journal.IngredientOrder;
import com.chi__you.cuisinejournal.client.journal.IngredientSearch;
import com.chi__you.cuisinejournal.client.journal.TagTranslationFilter;
import com.chi__you.cuisinejournal.client.journal.JournalPage;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ingredient reverse index. Layout and hit testing share the same control rectangles. */
public final class IngredientsPage extends BasePage {
    private static final int TITLE_TEXT_Y = 2;
    private static final int PROGRESS_Y = TITLE_TEXT_Y + 9 + 5;
    private static final int PROGRESS_HEIGHT = 4;
    private static final int TOOLS_Y = PROGRESS_Y + PROGRESS_HEIGHT + 5;
    private static final int CHIP_HEIGHT = 13;
    private static final int CHIP_PADDING = 4;
    private static final int CHIP_GAP = 3;
    /** Gap between the chip row and the list/detail body; matches progress bar → chips. */
    private static final int SECTION_GAP = 6;
    private static final int ROW_SLOT = 16;
    private static final int ROW_ICON = 14;
    private static final int DETAIL_SLOT = 18;
    private static final int DETAIL_ICON = 16;
    private static final int DISH_ROW_HEIGHT = 13;
    private static final int DETAIL_SCROLL_STEP = DISH_ROW_HEIGHT * 3;
    private static final int DISH_ICON = 11;
    private static final int DETAIL_HEADER_HEIGHT = 48;
    private static final int DETAIL_BG = 0xFF140F0A;
    private static final int BODY_BOTTOM_PADDING = 18;
    /** Gap between the chips and the trailing controls, and between those controls themselves. */
    private static final int TOOL_GAP = 6;
    /** Tag entries are blue, matching the design's tag badge and chip. */
    private static final int TAG_COLOR = JournalTheme.ADJUSTED;
    private static final int MEMBER_CELL = 18;
    // Text tiers: title (page heading, detail name), body (1.0), small (chips, row counts), badge.
    private static final float TITLE_TEXT_SCALE = 10.0F / 9.0F;
    private static final float SMALL_TEXT_SCALE = 8.0F / 9.0F;
    private static final float BADGE_TEXT_SCALE = 7.0F / 9.0F;
    private enum Sort {
        USED("used"), MISSING("missing"), MEALS("meals"), FLAVOR("flavor");
        final String key;
        Sort(String key) { this.key = key; }
    }
    private enum Kind {
        ALL("all", -1), CUISINE("cuisine", 0), PURE("pure", 1), NONFOOD("nonfood", 2), TAG("tag", 3),
        /** Entries that give dishes a flavor: their own binding or one passed down from a tag. */
        FLAVOR("flavor", -2);
        final String key;
        final int value;
        Kind(String key, int value) { this.key = key; this.value = value; }
        boolean accepts(Packets.IngredientView entry) {
            if (this == ALL) return true;
            if (this == FLAVOR) return FlavorUi.hasListedFlavor(entry.flavorInfo());
            return entry.kind() == value;
        }
    }
    private record Box(int x, int y, int width, int height) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + width && my >= y && my < y + height;
        }
    }
    private record Chip(Box box, Component text, Kind kind) {}
    private record DishHit(Box box, ResourceLocation item) {}
    /** A clickable link inside the detail panel: a tag chip or a tag member, opening that entry. */
    private record EntryHit(Box box, ResourceLocation entry) {}

    private final ScrollList<Packets.IngredientView> list = new ScrollList<>(29, 3, this::renderRow);
    private final List<Chip> chips = new ArrayList<>();
    private final List<DishHit> dishHits = new ArrayList<>();
    private final List<EntryHit> entryHits = new ArrayList<>();
    private final CreativeFoodOrder creativeOrder = new CreativeFoodOrder();
    private String query = "";
    private Kind kind = Kind.ALL;
    private Sort sort = Sort.USED;
    private ResourceLocation selected;
    private long revision = -1;
    private long flavorSearchRevision = -1;
    private int listWidth, bodyY, toolsBottom;
    private Box detail, searchBox;
    private JournalDropdown<Sort> sorts;
    private TagTranslationFilter translationFilter = TagTranslationFilter.NORMAL;
    private JournalDropdown<TagTranslationFilter> translations;
    private Language translationLanguage;
    private int detailScroll, detailContentHeight;
    private boolean detailPopup, draggingDetail;
    private double detailDragOffset;

    @Override public String id() { return "ingredients"; }
    @Override public Component title() { return tr("cuisinejournal.page.ingredients"); }
    @Override public boolean usesSearch() { return false; }

    @Override protected void onLayout() {
        boolean wide = width >= 340;
        if (wide) detailPopup = false;
        listWidth = wide ? width - 196 : width;
        // Use the entire content width for controls; shrink only when a viewport requires it.
        layoutCanEdit = FlavorClientState.canEdit();
        searchBox = new Box(right() - 90 - (layoutCanEdit ? PENCIL + 4 : 0), y, 90, 13);
        setupLocalSearch(searchBox.x() + 4, searchBox.y() + TITLE_TEXT_Y, searchBox.width() - 8,
                tr("cuisinejournal.journal.search"), query);
        localSearch.setHeight(9);
        JournalSearchField.bounds(localSearch, searchBox.x(), searchBox.y(), searchBox.width(), searchBox.height());
        if (sorts == null) {
            sorts = new JournalDropdown<>(tr("cuisinejournal.filter.sort"), sort, value -> {
                sort = value; onLayout(); rebuild(true);
            });
            sorts.options(java.util.Arrays.stream(Sort.values()).map(value -> new JournalDropdown.Option<>(
                    value, tr("cuisinejournal.ingredients.sort." + value.key))).toList());
        }
        if (translations == null) {
            translations = new JournalDropdown<>(tr("cuisinejournal.ingredients.translation.title"), translationFilter, value -> {
                translationFilter = value; rebuild(true);
            });
        }
        translations.options(java.util.Arrays.stream(TagTranslationFilter.values()).map(value -> new JournalDropdown.Option<>(
                value, tr("cuisinejournal.ingredients.translation." + value.key))).toList());
        layoutTools();
        bodyY = toolsBottom + SECTION_GAP;
        int bodyHeight = Math.max(1, bottom() - bodyY - BODY_BOTTOM_PADDING);
        list.bounds(x, bodyY, listWidth, bodyHeight);
        detail = wide ? new Box(x + listWidth + 6, bodyY, 190, bodyHeight)
                : new Box(x + Math.max(0, (width - Math.min(190, width)) / 2), bodyY,
                Math.min(190, width), bodyHeight);
        rebuild(false);
    }

    private void layoutTools() {
        // One row of kind chips with the sort button at its right end (sized to its own label).
        // In flavor-edit mode the tag-translation filter sits immediately to the left of the sort
        // button on that same row; only when the page is too narrow for the pair does it fall back
        // to a row of its own under the chips.
        // Only a very narrow page wraps the chips, keeping room for the buttons on the last row.
        int sortWidth = Math.min(width / 2, sorts.preferredWidth(font()));
        int translationWidth = 0;
        if (flavorEditing) {
            int wanted = Math.min(width, translations.stableWidth(font()));
            if (width >= 300 && width - sortWidth - TOOL_GAP - wanted >= 60) translationWidth = wanted;
        }
        int reserved = sortWidth + (translationWidth > 0 ? translationWidth + TOOL_GAP : 0);
        int lastRowEnd = layoutChips(right());
        if (lastRowEnd + TOOL_GAP + reserved > right()) layoutChips(right() - reserved - TOOL_GAP);
        int cy = chips.isEmpty() ? y + TOOLS_Y : chips.get(chips.size() - 1).box().y();
        // The dropdown button is 12 tall with text at +2; chips are 13 tall with text at +3.
        // Offsetting it by one puts both on the same text line and the same bottom edge.
        sorts.bounds(right() - sortWidth, cy + 1, sortWidth, right(), bottom());
        if (translationWidth > 0) {
            int buttonRight = right() - sortWidth - TOOL_GAP;
            translations.bounds(buttonRight - translationWidth, cy + 1, translationWidth, right(), bottom());
        }
        toolsBottom = cy + CHIP_HEIGHT;
        if (flavorEditing && translationWidth == 0) {
            translations.bounds(x, toolsBottom + 3, Math.min(width, translations.stableWidth(font())), right(), bottom());
            toolsBottom += 16;
        }
    }

    /** Lays out the kind chips up to {@code rowRight}; returns where the last row ends. */
    private int layoutChips(int rowRight) {
        chips.clear();
        int cx = x, cy = y + TOOLS_Y;
        for (Kind value : Kind.values()) {
            Component label = tr("cuisinejournal.ingredients.filter." + value.key);
            int w = Math.min(chipWidth(label), Math.max(1, rowRight - x));
            if (cx + w > rowRight && cx > x) { cx = x; cy += 16; }
            chips.add(new Chip(new Box(cx, cy, w, CHIP_HEIGHT), label, value));
            cx += w + CHIP_GAP;
        }
        return cx - CHIP_GAP;
    }

    /** Scaled text width, minus the font's trailing 1px, so the label sits centered in the chip. */
    private int chipWidth(Component label) {
        return scaledWidth(label, SMALL_TEXT_SCALE) - 1 + CHIP_PADDING * 2;
    }

    @Override public void onSearchChanged(String value) {
        query = value == null ? "" : value;
        rebuild(true);
    }

    private void rebuild(boolean reset) {
        TagTranslationFilter filter = flavorEditing && FlavorClientState.canEdit() ? translationFilter : TagTranslationFilter.NORMAL;
        creativeOrder.refresh();
        IngredientSearch search = IngredientSearch.parse(query);
        java.util.Map<String, Boolean> modMatches = new java.util.HashMap<>();
        java.util.function.Predicate<String> selectedMod = namespace -> modMatches.computeIfAbsent(namespace,
                id -> search.matchesMod(id, List.of(JournalText.modName(id).getString(),
                        com.chi__you.cuisinejournal.platform.Services.PLATFORM.modDisplayName(id))));
        java.util.Map<ResourceLocation, Boolean> memberMatches = new java.util.HashMap<>();
        List<Packets.IngredientView> visible = ClientJournalState.ingredients().stream()
                .filter(Packets.IngredientView::listed)
                .filter(kind::accepts)
                .filter(value -> filter.accepts(JournalText.untranslatedTag(value)))
                .filter(value -> search.matches(value, nameOf(value).getString(),
                        member -> memberMatches.computeIfAbsent(member,
                                id -> search.matchesItem(id, item -> JournalText.nameOf(item).getString(), selectedMod)), selectedMod, flavorNames(value)))
                .sorted(filter == TagTranslationFilter.NORMAL ? order() :
                        Comparator.comparing((Packets.IngredientView value) -> !JournalText.untranslatedTag(value)).thenComparing(order())).toList();
        list.items(visible);
        if (reset) list.scrollToTop();
        Packets.IngredientView selection = entry(selected);
        if (selected != null && (selection == null || selection.listed()
                && visible.stream().noneMatch(value -> selected.equals(value.item())))) {
            selected = null;
            detailScroll = 0;
            detailPopup = false;
        }
        if (reset && selected != null) list.ensureVisible(value -> selected.equals(value.item()));
        revision = ClientJournalState.revision();
        flavorSearchRevision = FlavorClientState.revision();
        translationLanguage = Language.getInstance();
    }

    private Comparator<Packets.IngredientView> order() {
        if (sort == Sort.FLAVOR) {
            // Flavor sources first, then the usual "常用" order within each group.
            Comparator<Packets.IngredientView> used = IngredientOrder.comparator(false,
                    creativeOrder.comparator(List.of("minecraft", "farmersdelight")), this::usesForSort);
            return Comparator.comparing((Packets.IngredientView value) -> !FlavorUi.hasListedFlavor(value.flavorInfo())).thenComparing(used);
        }
        if (sort == Sort.MEALS) return IngredientOrder.mealCountComparator(
                creativeOrder.comparator(List.of("minecraft", "farmersdelight")), this::usesForSort);
        // Tags have no use count of their own; they sort by their members' total uses.
        return IngredientOrder.comparator(sort == Sort.MISSING,
                creativeOrder.comparator(List.of("minecraft", "farmersdelight")),
                sort == Sort.MISSING ? Packets.IngredientView::timesUsed : this::usesForSort);
    }

    private List<String> flavorNames(Packets.IngredientView value) {
        List<ResourceLocation> ids = new ArrayList<>();
        if (value.flavorInfo().source()) ids.addAll(value.flavorInfo().flavors());
        value.flavorInfo().inherited().forEach(binding -> ids.addAll(binding.flavors()));
        return ids.stream().distinct().filter(id -> flavorEditing || FlavorUi.visible(id)).map(FlavorClientState::get).filter(java.util.Objects::nonNull)
                .map(entry -> entry.preset().name()).toList();
    }

    private long usesForSort(Packets.IngredientView value) {
        if (!value.tag()) return value.timesUsed();
        long total = 0;
        for (ResourceLocation member : value.members()) {
            Packets.IngredientView entry = entry(member);
            if (entry != null) total += entry.timesUsed();
        }
        return total;
    }

    /** id → entry, rebuilt when the client state changes; avoids scanning the list per lookup. */
    private static java.util.Map<ResourceLocation, Packets.IngredientView> index = java.util.Map.of();
    private static long indexRevision = Long.MIN_VALUE;

    private static Packets.IngredientView entry(ResourceLocation id) {
        if (id == null) return null;
        if (indexRevision != ClientJournalState.revision()) {
            java.util.Map<ResourceLocation, Packets.IngredientView> built = new java.util.HashMap<>();
            for (Packets.IngredientView value : ClientJournalState.ingredients()) built.putIfAbsent(value.item(), value);
            index = built;
            indexRevision = ClientJournalState.revision();
        }
        return index.get(id);
    }

    private Packets.IngredientView selected() {
        var value = entry(selected);
        ClientJournalState.requestTagDetails(value);
        return value;
    }

    /** Display name of an entry: the item's name, or the tag's translated name. */
    private static Component nameOf(Packets.IngredientView value) {
        if (value.displayName() != null && !value.displayName().isBlank()) {
            return Component.literal(value.displayName());
        }
        return value.tag() ? tagName(value.item(), value.tagIds()) : JournalText.nameOf(value.item());
    }

    private static Component tagName(ResourceLocation key) {
        return JournalText.tagName(key);
    }

    /** {@code tag.item.forge.eggs}, then each merged id's key; untranslated tags show their id. */
    private static Component tagName(ResourceLocation key, List<ResourceLocation> ids) {
        return JournalText.tagName(key, ids);
    }

    /** The icon a tag shows this second, cycling through its members like a recipe viewer. */
    private static ResourceLocation tagIcon(Packets.IngredientView value) {
        if (value.members().isEmpty()) return value.item();
        int index = (int) ((System.currentTimeMillis() / 1000L) % value.members().size());
        return value.members().get(index);
    }

    private static ResourceLocation iconOf(Packets.IngredientView value) {
        return value.tag() ? tagIcon(value) : value.item();
    }

    /** Opens an item or tag entry from another page (the dish detail's ingredient cells). */
    public boolean openEntry(ResourceLocation id) {
        if (entry(id) == null) return false;
        open(id);
        return true;
    }

    /** Opens another entry from the detail panel, clearing filters that would hide it. */
    private void open(ResourceLocation id) {
        Packets.IngredientView target = entry(id);
        if (target == null) return;
        if (target.listed() && list.items().stream().noneMatch(value -> id.equals(value.item()))) {
            kind = Kind.ALL; query = "";
            resetTranslationFilter();
            // setValue triggers onSearchChanged, which rebuilds the list.
            if (localSearch != null && !localSearch.getValue().isEmpty()) localSearch.setValue("");
            else rebuild(false);
        }
        selected = id; detailScroll = 0; tagExpand = 0F; tagOpen = false; membersOpen = false; tagsOpen = false;
        previewIndex = 0; previewStars = 0; openFlavorRow = null;
        detailPopup = width < 340;
        list.ensureVisible(value -> id.equals(value.item()));
        CuisineUiSoundCompat.click();
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (layoutCanEdit != FlavorClientState.canEdit()) { if (!FlavorClientState.canEdit()) { flavorEditing = false; resetTranslationFilter(); } onLayout(); }
        if (revision != ClientJournalState.revision() || flavorSearchRevision != FlavorClientState.revision()
                || translationLanguage != Language.getInstance()) rebuild(false);
        int realX = mx, realY = my;
        if (modalOpen()) { mx = -10000; my = -10000; }
        beginFrame();
        dishHits.clear();
        entryHits.clear();
        flavorHits.clear();
        headerIcon = null;
        pendingTooltip = null;
        pendingLines = null;
        header(g, mx, my, partialTick);
        if (list.isEmpty()) {
            clipped(g, emptyMessage(), x + 4, bodyY + 12, listWidth - 8, JournalTheme.TEXT_DIM);
        } else list.render(g, mx, my);
        if (width >= 340) renderDetail(g, mx, my);
        else if (detailPopup) {
            g.pose().pushPose(); g.pose().translate(0, 0, 350);
            g.fill(x, bodyY, right(), bottom(), JournalTheme.MASK);
            renderDetail(g, mx, my);
            g.pose().popPose();
        }
        sorts.renderPopup(g, font(), mx, my);
        if (flavorEditing) translations.renderPopup(g, font(), mx, my);
        if (pendingTooltip != null && !sorts.isOpen() && !translations.isOpen()) {
            g.pose().pushPose(); g.pose().translate(0, 0, 600);
            g.renderTooltip(font(), pendingTooltip, mx, my);
            g.pose().popPose();
        } else if (pendingLines != null && !sorts.isOpen() && !translations.isOpen()) {
            g.pose().pushPose(); g.pose().translate(0, 0, 600);
            g.renderComponentTooltip(font(), pendingLines, mx, my);
            g.pose().popPose();
        }
        if (modalOpen()) modal.render(g, font(), realX, realY, partialTick);
    }

    private void header(GuiGraphics g, int mx, int my, float partialTick) {
        var counted = ClientJournalState.ingredients().stream()
                .filter(Packets.IngredientView::countedIngredient).toList();
        int total = counted.size();
        int known = (int) counted.stream().filter(Packets.IngredientView::known).count();
        int used = (int) counted.stream().filter(v -> v.timesUsed() > 0).count();
        Component heading = tr("cuisinejournal.ingredients.title").withStyle(style -> style.withBold(true));
        // "已发现" dim, numbers bright, a faint "·", then "已用过 N 种" dim (design .counts).
        Component counts = tr("cuisinejournal.ingredients.discovered_label").append(" ")
                .append(tr("cuisinejournal.ingredients.discovered_values", JournalText.number(known), JournalText.number(total))
                        .withStyle(style -> style.withColor(JournalTheme.TEXT)))
                .append(Component.literal(" · ").withStyle(style -> style.withColor(JournalTheme.TEXT_FAINT)))
                .append(tr("cuisinejournal.ingredients.used", JournalText.number(used)));
        // Statistics can be long in a modpack; fit them before the 90px local search.
        int textRight = searchBox.x() - 5 - (flavorEditing ? editHeaderWidth() + 6 : 0);
        int cx = x;
        int titleY = y + TITLE_TEXT_Y;
        styledScaled(g, heading, cx, titleY, textRight - cx, JournalTheme.TEXT_ACCENT, TITLE_TEXT_SCALE);
        cx += scaledWidth(heading, TITLE_TEXT_SCALE) + 5;
        styledScaled(g, counts, cx, titleY + 1, textRight - cx, JournalTheme.TEXT_DIM, 1.0F);
        drawSearch(g, mx, my, partialTick);
        renderEditHeader(g, mx, my);
        int barY = y + PROGRESS_Y;
        g.fill(x, barY, right(), barY + PROGRESS_HEIGHT, JournalTheme.BAR_TRACK);
        JournalTheme.border(g, x, barY, width, PROGRESS_HEIGHT, 0xFF3A2C1F);
        if (known > 0 && total > 0) {
            int innerWidth = Math.max(0, width - 2);
            int fill = Math.min(innerWidth, (int) ((long) innerWidth * known / total));
            for (int i = 0; i < fill; i++) {
                float fraction = fill <= 1 ? 0 : (float) i / (fill - 1);
                g.fill(x + 1 + i, barY + 1, x + 2 + i, barY + PROGRESS_HEIGHT - 1,
                        blend(0xFFE8A93C, 0xFFC4762A, fraction));
            }
        }
        // 渲染 chips 和分隔线
        for (Chip chip : chips) {

            Box b = chip.box();
            boolean active = chip.kind() == kind;
            boolean hover = b.contains(mx, my) && !sorts.isOpen();
            // The "标签" kind chip is blue when active, like the tag badge.
            int activeColor = chip.kind() == Kind.TAG ? TAG_COLOR : JournalTheme.TEXT_ACCENT;

            if (active) {
                g.fill(b.x(), b.y(), b.x() + b.width(), b.y() + 13, (activeColor & 0x00FFFFFF) | 0x21000000);
                JournalTheme.border(g, b.x(), b.y(), b.width(), 13, activeColor);
            } else if (hover) {
                g.fill(b.x(), b.y(), b.x() + b.width(), b.y() + 13, 0x21FFE9C7);
            }

            // Chips keep the one "small" tier the user asked for; text vertically centered in 13px.
            clippedScaled(g, chip.text(), b.x() + CHIP_PADDING,
                    b.y() + 3, b.width() - CHIP_PADDING * 2 + 1,
                    active ? activeColor : hover ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT,
                    SMALL_TEXT_SCALE);
        }
        sorts.renderButton(g, font(), mx, my);
        if (flavorEditing) translations.renderButton(g, font(), mx, my);
    }

    private void drawSearch(GuiGraphics g, int mx, int my, float partialTick) {
        JournalSearchField.render(g, font(), localSearch, searchBox.x(), searchBox.y(),
                searchBox.width(), searchBox.height(), tr("cuisinejournal.ingredients.search"), mx, my, partialTick);
    }

    private void renderRow(GuiGraphics g, Packets.IngredientView value, int rx, int ry, int rw, int rh, boolean hover) {
        boolean active = value.item().equals(selected);
        g.fill(rx, ry, rx + rw, ry + rh, active ? 0x33E8A93C : hover ? 0x21FFE9C7 : 0x40000000);
        if (active) JournalTheme.border(g, rx, ry, rw, rh, 0xFFE8A93C);
        // Icon box (no backing slot); the icon is centered in it.
        int sx = rx + 3, sy = ry + 2;
        int inset = (ROW_SLOT - ROW_ICON) / 2;
        int rowBg = active ? blend(0xFF201811, 0xFFE8A93C, 0x33 / 255.0F)
                : hover ? blend(0xFF201811, 0xFFFFE9C7, 0x21 / 255.0F) : blend(0xFF201811, 0xFF000000, 0x40 / 255.0F);
        icon(g, iconOf(value), sx + inset, sy + inset, ROW_ICON, value.known(), rowBg);
        // Name, badge and use count share the slot's center line; line 2 starts under the name.
        int nameX = sx + ROW_SLOT + 4;
        int center = sy + ROW_SLOT / 2;
        int nameY = center - 4;
        // Tags show how many items they cover, never a use count.
        Component uses = value.tag() ? tr("cuisinejournal.ingredients.tag_members", value.members().size())
                : value.timesUsed() > 0 ? tr("cuisinejournal.ingredients.use_count", JournalText.number(value.timesUsed()))
                : value.known() ? tr("cuisinejournal.ingredients.detail.discovered")
                : tr("cuisinejournal.ingredients.unrecorded");
        int usesWidth = scaledWidth(uses, SMALL_TEXT_SCALE);
        int usesRight = rx + rw - 6;
        valueScaled(g, uses, usesRight, nameY + 1, usesWidth,
                value.tag() || value.timesUsed() > 0 ? JournalTheme.TEXT_DIM
                        : value.known() ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_FAINT, SMALL_TEXT_SCALE);
        Component badge = tr(value.tag() ? "cuisinejournal.ingredients.badge.tag" : "cuisinejournal.ingredients.badge.cuisine");
        int badgeColor = value.tag() ? TAG_COLOR : JournalTheme.TEXT_ACCENT;
        int badgeWidth = value.kind() == 0 || value.tag() ? scaledWidth(badge, BADGE_TEXT_SCALE) + 5 : 0;
        int available = Math.max(1, usesRight - usesWidth - 4 - nameX - (badgeWidth > 0 ? badgeWidth + 3 : 0));
        Component name = nameOf(value);
        String shown = ellipsis(name, available);
        g.drawString(font(), shown, nameX, nameY,
                value.known() || value.tag() ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT, false);
        if (badgeWidth > 0) {
            int bx = nameX + font().width(shown) + 3;
            // 10px tall so the scaled glyphs have a pixel of air above and below.
            int badgeY = center - 5;
            g.fill(bx, badgeY, bx + badgeWidth, badgeY + 10, (badgeColor & 0x00FFFFFF) | 0x33000000);
            JournalTheme.border(g, bx, badgeY, badgeWidth, 10, (badgeColor & 0x00FFFFFF) | 0x80000000);
            clippedScaled(g, badge, bx + 3, badgeY + 2, badgeWidth - 5, badgeColor, BADGE_TEXT_SCALE);
        }
        // "已发现" / "道菜" faint, the count itself in the discovery color (design .l2 .prog).
        boolean complete = value.mealCount() > 0 && value.discoveredCount() == value.mealCount();
        Component line = value.mealCount() == 0 ? tr("cuisinejournal.ingredients.no_direct")
                : complete ? tr("cuisinejournal.ingredients.all_discovered", value.mealCount())
                : tr("cuisinejournal.ingredients.discovered_of",
                        Component.literal(String.valueOf(value.discoveredCount()))
                                .withStyle(style -> style.withColor(JournalTheme.DISCOVERY)),
                        Component.literal(String.valueOf(value.mealCount()))
                                .withStyle(style -> style.withColor(JournalTheme.DISCOVERY)));
        styledScaled(g, line, nameX, sy + ROW_SLOT + 1, rx + rw - 4 - nameX,
                complete ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_FAINT, SMALL_TEXT_SCALE);
    }

    private void renderDetail(GuiGraphics g, int mx, int my) {
        Box b = detail;
        g.fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), 0xFF140F0A);
        JournalTheme.border(g, b.x(), b.y(), b.width(), b.height(), flavorEditing ? JournalTheme.EDIT_TINT : 0xFF3A2C1F);
        Packets.IngredientView value = selected();
        if (value == null) {
            clipped(g, tr("cuisinejournal.ingredients.select"), b.x() + 6, b.y() + 10, b.width() - 12, JournalTheme.TEXT_FAINT);
            return;
        }
        detailContentHeight = detailHeight(value);
        detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll));
        int cx = b.x() + 6, cursor = b.y() + 5 - detailScroll, available = b.width() - 16;
        int rightEdge = cx + available;
        g.enableScissor(b.x() + 1, b.y() + 1, b.x() + b.width() - 5, b.y() + b.height() - 1);
        // Header: icon (no backing slot), name and status on the icon's center line.
        // Like the dish page, the icon grows while hovered and opens the recipe viewer.
        int detailInset = (DETAIL_SLOT - DETAIL_ICON) / 2;
        int iconX = cx + detailInset, iconY = cursor + detailInset;
        boolean iconHover = !value.tag() && inside(mx, my, iconX, iconY, DETAIL_ICON, DETAIL_ICON)
                && b.contains(mx, my) && !sorts.isOpen();
        headerHover = step(headerHover, iconHover);
        float grow = 1F + 0.25F * (1F - (1F - headerHover) * (1F - headerHover) * (1F - headerHover));
        g.pose().pushPose();
        g.pose().translate(iconX + DETAIL_ICON / 2F, iconY + DETAIL_ICON / 2F, headerHover > 0F ? 50 : 0);
        g.pose().scale(grow, grow, 1F);
        icon(g, iconOf(value), -DETAIL_ICON / 2, -DETAIL_ICON / 2, DETAIL_ICON, value.known(), DETAIL_BG);
        g.pose().popPose();
        if (!value.tag() && iconY + DETAIL_ICON > b.y() && iconY < b.y() + b.height())
            headerIcon = new Box(iconX, iconY, DETAIL_ICON, DETAIL_ICON);
        Component status = value.tag() ? tr("cuisinejournal.ingredients.tag_items", value.members().size())
                : tr(value.known() ? "cuisinejournal.ingredients.detail.discovered" : "cuisinejournal.ingredients.detail.undiscovered");
        int reserve = font().width(status) + 4;
        int headCenter = cursor + DETAIL_SLOT / 2;
        Component detailName = nameOf(value)
                .copy().withStyle(style -> style.withBold(true));
        int nameX = cx + DETAIL_SLOT + 5;
        int nameMax = available - DETAIL_SLOT - 5 - reserve;
        Component tagBadge = tr("cuisinejournal.ingredients.badge.tag");
        int tagBadgeWidth = value.tag() ? scaledWidth(tagBadge, BADGE_TEXT_SCALE) + 5 : 0;
        if (tagBadgeWidth > 0) nameMax -= tagBadgeWidth + 4;
        styledScaled(g, detailName, nameX, headCenter - 4, nameMax, JournalTheme.TEXT_ACCENT, TITLE_TEXT_SCALE);
        if (tagBadgeWidth > 0) {
            int bx = nameX + Math.min(nameMax, scaledWidth(detailName, TITLE_TEXT_SCALE)) + 4;
            g.fill(bx, headCenter - 5, bx + tagBadgeWidth, headCenter + 5, (TAG_COLOR & 0x00FFFFFF) | 0x33000000);
            JournalTheme.border(g, bx, headCenter - 5, tagBadgeWidth, 10, (TAG_COLOR & 0x00FFFFFF) | 0x80000000);
            clippedScaled(g, tagBadge, bx + 3, headCenter - 3, tagBadgeWidth - 5, TAG_COLOR, BADGE_TEXT_SCALE);
        }
        JournalTheme.value(g, font(), status, rightEdge, headCenter - 4,
                value.tag() ? TAG_COLOR : value.known() ? JournalTheme.TEXT_GOOD : JournalTheme.TEXT_FAINT);
        // Kind/type and source (mod for items, merged tag ids for tags) sit under the icon.
        clipped(g, kindAndTypes(value), cx, cursor + DETAIL_SLOT + 5, available - tagToggleWidth(value), JournalTheme.TEXT_DIM);
        if (value.tag()) {
            renderTagSources(g, value, cx, cursor + DETAIL_SLOT + 16, available, mx, my);
        } else {
            tagToggle = null;
            clipped(g, JournalText.modName(value.item().getNamespace()), cx, cursor + DETAIL_SLOT + 16, available,
                    JournalTheme.TEXT_DIM);
        }
        cursor += DETAIL_HEADER_HEIGHT + tagIdsExtraHeight(value, available);
        if (usedTimes(value) > 0) {
            JournalTheme.divider(g, cx, cursor, available); cursor += 6;
            Component usedNumber = Component.literal(JournalText.number(usedTimes(value)))
                    .withStyle(style -> style.withBold(true));
            styledScaled(g, tr("cuisinejournal.ingredients.record_used", usedNumber), cx, cursor,
                    available, JournalTheme.TEXT, 1.0F);
            cursor += 11;
            clipped(g, dates(value), cx, cursor, available, JournalTheme.TEXT_DIM);
            cursor += 15;
        }
        int flavorTop = cursor;
        cursor = renderFlavor(g, value, cx, cursor, available, mx, my);
        flavorHeight = cursor - flavorTop;
        if (!value.tag()) membersToggle = null;
        if (value.tag()) tagsToggle = null;
        if (value.tag()) cursor = renderMembers(g, value, cx, cursor, available, mx, my);
        else if (!value.tagIds().isEmpty()) cursor = renderBelongs(g, value, cx, cursor, available, mx, my);
        JournalTheme.divider(g, cx, cursor, available); cursor += 6;
        // Same body size as the record block above; the count's numbers are bold (design .val b).
        Component count = tr("cuisinejournal.ingredients.dish_count",
                Component.literal(String.valueOf(value.discoveredCount())).withStyle(style -> style.withBold(true)),
                Component.literal(String.valueOf(value.mealCount())).withStyle(style -> style.withBold(true)));
        int countWidth = font().width(count);
        styledScaled(g, count, rightEdge - countWidth, cursor + 1, countWidth, JournalTheme.TEXT, 1.0F);
        clippedScaled(g, tr("cuisinejournal.ingredients.detail.dishes"), cx, cursor + 1,
                available - countWidth - 5, JournalTheme.TEXT_DIM, 1.0F);
        cursor += 13;
        for (ResourceLocation meal : sortedMeals(value)) {
            boolean known = ClientJournalState.isDiscovered(meal);
            boolean hit = inside(mx, my, cx, cursor, available, DISH_ROW_HEIGHT) && b.contains(mx, my) && !sorts.isOpen();
            if (hit) g.fill(cx - 2, cursor, rightEdge + 2, cursor + DISH_ROW_HEIGHT, 0x21FFE9C7);
            int rowCenter = cursor + DISH_ROW_HEIGHT / 2;
            int iconBg = hit ? blend(DETAIL_BG, 0xFFFFE9C7, 0x21 / 255.0F) : DETAIL_BG;
            icon(g, meal, cx, rowCenter - DISH_ICON / 2, DISH_ICON, known, iconBg);
            // Dish rows use the small tier so the names sit in proportion to the icons.
            Component unknown = tr("cuisinejournal.ingredients.dish_unknown");
            int reserveWidth = known ? (int) Math.ceil(45 * SMALL_TEXT_SCALE) : scaledWidth(unknown, SMALL_TEXT_SCALE);
            int textY = rowCenter - 3;
            clippedScaled(g, JournalText.nameOf(meal), cx + DISH_ICON + 4, textY,
                    available - DISH_ICON - 4 - reserveWidth - 4, known ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT,
                    SMALL_TEXT_SCALE);
            if (known) {
                g.pose().pushPose();
                g.pose().translate(rightEdge - reserveWidth, textY, 0);
                g.pose().scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE, 1.0F);
                stars(g, meal, 0, 0);
                g.pose().popPose();
            } else valueScaled(g, unknown, rightEdge, textY, reserveWidth, JournalTheme.TEXT_FAINT, SMALL_TEXT_SCALE);
            if (cursor + DISH_ROW_HEIGHT > b.y() + 1 && cursor < b.y() + b.height() - 1) {
                int top = Math.max(cursor, b.y() + 1), end = Math.min(cursor + DISH_ROW_HEIGHT, b.y() + b.height() - 1);
                dishHits.add(new DishHit(new Box(cx, top, available, end - top), meal));
            }
            cursor += DISH_ROW_HEIGHT;
        }
        if (value.meals().isEmpty()) {
            // An item that only takes part through tags points the player at them.
            Component empty = emptyDishes(value);
            for (var line : font().split(empty, available)) {
                g.drawString(font(), line, cx, cursor + 2, JournalTheme.TEXT_FAINT, false); cursor += 10;
            }
            cursor += 4;
        }
        g.disableScissor();
        if (maxDetailScroll() > 0) {
            Box thumb = detailThumb();
            g.fill(b.x() + b.width() - 4, b.y() + 1, b.x() + b.width() - 1, b.y() + b.height() - 1, 0x38000000);
            g.fill(thumb.x(), thumb.y(), thumb.x() + thumb.width(), thumb.y() + thumb.height(), JournalTheme.TEXT_FAINT);
        }
        if (detailPopup) {
            g.fill(b.x() + b.width() - 13, b.y() + 1, b.x() + b.width() - 1, b.y() + 13, JournalTheme.PANEL_TOP);
            g.drawString(font(), "x", b.x() + b.width() - 10, b.y() + 3, JournalTheme.TEXT_DIM, false);
        }
    }

    /** Tag page: every member as a small icon grid; clicking one opens that item. */
    private int renderMembers(GuiGraphics g, Packets.IngredientView value, int cx, int cursor, int available,
                              int mx, int my) {
        JournalTheme.divider(g, cx, cursor, available); cursor += 6;
        int known = (int) value.members().stream().map(IngredientsPage::entry)
                .filter(member -> member != null && member.known()).count();
        Component count = tr("cuisinejournal.ingredients.dish_count",
                Component.literal(String.valueOf(known)).withStyle(style -> style.withBold(true)),
                Component.literal(String.valueOf(value.members().size())).withStyle(style -> style.withBold(true)));
        int countWidth = font().width(count);
        styledScaled(g, count, cx + available - countWidth, cursor + 1, countWidth, JournalTheme.TEXT, 1.0F);
        clipped(g, tr("cuisinejournal.ingredients.detail.members"), cx, cursor + 1, available - countWidth - 5,
                JournalTheme.TEXT_DIM);
        cursor += 13;
        int columns = Math.max(1, (available + 2) / (MEMBER_CELL + 2));
        int totalRows = (value.members().size() + columns - 1) / columns;
        boolean foldable = totalRows > MEMBER_ROWS_FOLDED;
        int shownRows = foldable && !membersOpen ? MEMBER_ROWS_FOLDED : totalRows;
        int index = 0;
        for (ResourceLocation member : sortedMembers(value)) {
            if (index >= shownRows * columns) break;
            Packets.IngredientView memberEntry = entry(member);
            boolean memberKnown = memberEntry != null && memberEntry.known();
            int col = index % columns, row = index / columns;
            int bx = cx + col * (MEMBER_CELL + 2), by = cursor + row * (MEMBER_CELL + 2);
            boolean hover = inside(mx, my, bx, by, MEMBER_CELL, MEMBER_CELL) && detail.contains(mx, my) && !sorts.isOpen();
            // No cell box: just the icon, growing ~25% while hovered (same as the other journal icons).
            float t = step(memberHover.getOrDefault(member, 0F), hover);
            if (t <= 0F) memberHover.remove(member); else memberHover.put(member, t);
            float grow = 1F + 0.25F * (1F - (1F - t) * (1F - t) * (1F - t));
            g.pose().pushPose();
            g.pose().translate(bx + MEMBER_CELL / 2F, by + MEMBER_CELL / 2F, t > 0F ? 50 : 0);
            g.pose().scale(grow, grow, 1F);
            icon(g, member, -8, -8, 16, memberKnown, DETAIL_BG);
            g.pose().popPose();
            // Names stay visible so players know what to collect; undiscovered is shown by the dim icon.
            if (hover) tooltip(JournalText.nameOf(member));
            if (by + MEMBER_CELL > detail.y() + 1 && by < detail.y() + detail.height() - 1)
                entryHits.add(new EntryHit(new Box(bx, by, MEMBER_CELL, MEMBER_CELL), member));
            index++;
        }
        cursor += shownRows * (MEMBER_CELL + 2);
        membersToggle = null;
        if (foldable) {
            // Long tags fold to three rows; a full-width bar under the grid opens / closes the rest.
            Component label = membersOpen ? tr("cuisinejournal.ingredients.members_collapse")
                    : tr("cuisinejournal.ingredients.members_expand", value.members().size());
            String arrow = membersOpen ? " ▴" : " ▾";
            membersToggle = new Box(cx, cursor, available, MEMBERS_BAR_H);
            boolean over = membersToggle.contains(mx, my) && detail.contains(mx, my) && !sorts.isOpen();
            g.fill(cx, cursor, cx + available, cursor + MEMBERS_BAR_H, over ? 0x21FFE9C7 : 0x26000000);
            int w = font().width(label) + font().width(arrow);
            int tx = cx + (available - w) / 2;
            int color = over ? JournalTheme.TEXT : JournalTheme.TEXT_DIM;
            g.drawString(font(), label, tx, cursor + 2, color, false);
            g.drawString(font(), arrow, tx + font().width(label), cursor + 2, over ? JournalTheme.TEXT_ACCENT : color, false);
            cursor += MEMBERS_BAR_H + 2;
        }
        return cursor + 4;
    }

    private static final int MEMBER_ROWS_FOLDED = 2, MEMBERS_BAR_H = 12;
    /** Whether a long tag's member grid is unfolded; resets on a new selection. */
    private boolean membersOpen;
    private Box membersToggle;

    private static final int BELONGS_FOLDED_ROWS = 2, BELONGS_CHIP_H = 13, BELONGS_ROW_PITCH = 15,
            BELONGS_BAR_H = MEMBERS_BAR_H;
    /** Whether the "所属标签" chips are unfolded; resets on a new selection. */
    private boolean tagsOpen;
    private Box tagsToggle;

    /** Item page: the tag entries it belongs to, as small blue chips linking to them. */
    private int renderBelongs(GuiGraphics g, Packets.IngredientView value, int cx, int cursor, int available,
                              int mx, int my) {
        JournalTheme.divider(g, cx, cursor, available); cursor += 6;
        clipped(g, tr("cuisinejournal.ingredients.detail.belongs"), cx, cursor + 1, available, JournalTheme.TEXT_DIM);
        cursor += 13;
        List<List<ResourceLocation>> rows = belongsRows(value, available);
        int shown = tagsOpen ? rows.size() : Math.min(BELONGS_FOLDED_ROWS, rows.size());
        int y = cursor;
        for (int r = 0; r < shown; r++) {
            int bx = cx;
            for (ResourceLocation key : rows.get(r)) {
                Packets.IngredientView tag = entry(key);
                Component label = tagName(key);
                Component meals = belongsMeals(key);
                int nameMax = belongsNameMax(key, available), countW = scaledWidth(meals, SMALL_TEXT_SCALE);
                int w = Math.min(font().width(label), nameMax) + 4 + countW + 6;
                boolean hover = inside(mx, my, bx, y, w, BELONGS_CHIP_H) && detail.contains(mx, my) && !sorts.isOpen();
                g.fill(bx, y, bx + w, y + BELONGS_CHIP_H, (TAG_COLOR & 0x00FFFFFF) | (hover ? 0x33000000 : 0x14000000));
                JournalTheme.border(g, bx, y, w, BELONGS_CHIP_H, (TAG_COLOR & 0x00FFFFFF) | (hover ? 0xD9000000 : 0x73000000));
                // A chip can never outgrow the column: the name is cut first, never the row.
                clipped(g, label, bx + 4, y + 3, nameMax, hover ? JournalTheme.TEXT : TAG_COLOR);
                g.drawString(font(), meals, bx + w - 4 - countW, y + 4,
                        hover ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT, false);
                if (tag != null) entryHits.add(new EntryHit(new Box(bx, y, w, BELONGS_CHIP_H), key));
                bx += w + 4;
            }
            y += BELONGS_ROW_PITCH;
        }
        int bottom = y - 2;
        tagsToggle = null;
        if (rows.size() > BELONGS_FOLDED_ROWS) {
            // Same bar as the member grid: the section folds itself instead of pushing the rest down.
            int barY = bottom + 1;
            tagsToggle = new Box(cx, barY, available, BELONGS_BAR_H);
            boolean over = tagsToggle.contains(mx, my) && detail.contains(mx, my) && !sorts.isOpen();
            g.fill(cx, barY, cx + available, barY + BELONGS_BAR_H, over ? 0x21FFE9C7 : 0x26000000);
            Component label = tagsOpen ? tr("cuisinejournal.ingredients.tag_collapse")
                    : tr("cuisinejournal.ingredients.tag_expand", value.tagIds().size());
            String arrow = tagsOpen ? " ▴" : " ▾";
            int textW = font().width(label) + font().width(arrow);
            int tx = cx + (available - textW) / 2;
            int colour = over ? JournalTheme.TEXT : JournalTheme.TEXT_DIM;
            g.drawString(font(), label, tx, barY + 2, colour, false);
            g.drawString(font(), arrow, tx + font().width(label), barY + 2, over ? JournalTheme.TEXT_ACCENT : colour, false);
            bottom = barY + BELONGS_BAR_H;
        }
        return bottom + 6;
    }

    /** Longest name a chip may draw, leaving room for the count and both paddings. */
    private int belongsNameMax(ResourceLocation key, int available) {
        return Math.max(16, available - 4 - scaledWidth(belongsMeals(key), SMALL_TEXT_SCALE) - 6 - 4);
    }

    /** Chips laid out into rows; drawing and the height estimate must agree, so both use this. */
    private List<List<ResourceLocation>> belongsRows(Packets.IngredientView value, int available) {
        List<List<ResourceLocation>> rows = new ArrayList<>();
        List<ResourceLocation> row = new ArrayList<>();
        int used = 0;
        for (ResourceLocation key : sortedBelongTags(value)) {
            int w = belongsChipWidth(key, available);
            if (!row.isEmpty() && used + w > available) { rows.add(row); row = new ArrayList<>(); used = 0; }
            row.add(key);
            used += w + 4;
        }
        if (!row.isEmpty()) rows.add(row);
        return rows;
    }

    /** Busiest tags first, so the two rows that survive folding are the useful ones. */
    private static List<ResourceLocation> sortedBelongTags(Packets.IngredientView value) {
        List<ResourceLocation> keys = new ArrayList<>(value.tagIds());
        keys.sort(java.util.Comparator
                .<ResourceLocation>comparingInt(key -> {
                    Packets.IngredientView tag = entry(key);
                    return tag == null ? 0 : -tag.mealCount();
                })
                .thenComparing(key -> tagName(key).getString()));
        return keys;
    }

    private Component pendingTooltip;
    /** Header icon hit box from the last frame (items only), and its hover grow 0..1. */
    private Box headerIcon;
    private float headerHover;
    private long lastFrame;

    /** This frame's share of a ~0.12 s hover grow; measured once per frame in {@link #render}. */
    private float frameDelta;
    /** Hover grow (0..1) of each tag-member icon, keyed by item. */
    private final java.util.Map<ResourceLocation, Float> memberHover = new java.util.HashMap<>();

    private void beginFrame() {
        long now = System.nanoTime();
        float seconds = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        frameDelta = seconds / 0.12F;
    }

    /** Advances a hover grow towards 1 or 0 over ~0.12 s, like the dish page's icons. */
    private float step(float t, boolean hovered) {
        return hovered ? Math.min(1F, t + frameDelta) : Math.max(0F, t - frameDelta);
    }

    private void tooltip(Component text) {
        pendingTooltip = text;
    }

    private static Component belongsMeals(ResourceLocation key) {
        Packets.IngredientView tag = entry(key);
        return tr("cuisinejournal.ingredients.tag_dishes", tag == null ? 0 : tag.mealCount());
    }

    /** Width of one "所属标签" chip (name capped); shared by drawing and the height estimate. */
    private int belongsChipWidth(ResourceLocation key, int available) {
        return Math.min(font().width(tagName(key)), belongsNameMax(key, available)) + 4
                + scaledWidth(belongsMeals(key), SMALL_TEXT_SCALE) + 6;
    }

    private Component emptyDishes(Packets.IngredientView value) {
        return tr(!value.tag() && !value.tagIds().isEmpty()
                ? "cuisinejournal.ingredients.detail.no_direct" : "cuisinejournal.ingredients.detail.no_dishes");
    }

    private int detailHeight(Packets.IngredientView value) {
        int dishesHeight = value.meals().isEmpty()
                ? font().split(emptyDishes(value), detail.width() - 16).size() * 10 + 4
                : value.meals().size() * DISH_ROW_HEIGHT;
        int result = 5 + DETAIL_HEADER_HEIGHT + (usedTimes(value) > 0 ? 32 : 0)
                + flavorHeight + 19 + dishesHeight + 6;
        result += tagIdsExtraHeight(value, detail.width() - 16);
        if (value.tag()) {
            int columns = Math.max(1, (detail.width() - 16 + 2) / (MEMBER_CELL + 2));
            int rows = (value.members().size() + columns - 1) / columns;
            boolean foldable = rows > MEMBER_ROWS_FOLDED;
            result += 19 + (foldable && !membersOpen ? MEMBER_ROWS_FOLDED : rows) * (MEMBER_CELL + 2) + 4
                    + (foldable ? MEMBERS_BAR_H + 2 : 0);
        } else if (!value.tagIds().isEmpty()) {
            int available = detail.width() - 16;
            List<List<ResourceLocation>> rows = belongsRows(value, available);
            int shown = tagsOpen ? rows.size() : Math.min(BELONGS_FOLDED_ROWS, rows.size());
            result += 19 + shown * BELONGS_ROW_PITCH - 2
                    + (rows.size() > BELONGS_FOLDED_ROWS ? 1 + BELONGS_BAR_H : 0) + 6;
        }
        return result;
    }

    // ------------------------------------------------------------ flavor

    private static final int PENCIL = 11, FLAVOR_ROW = 13;
    private record FlavorHit(Box box, Runnable action) {}
    private final List<FlavorHit> flavorHits = new ArrayList<>();
    /** Client-side edit mode of the ingredient journal (admins only); the server re-checks every edit. */
    private boolean flavorEditing, layoutCanEdit;
    private JournalModal modal;
    private int flavorHeight, previewIndex, previewStars, pendingRequest;
    private String requestedPreview = "";
    private List<Component> pendingLines;

    private boolean modalOpen() { return modal != null && modal.isOpen(); }

    private Component editLabel() { return tr("cuisinejournal.edit.mode"); }
    private Component libraryLabel() { return tr("cuisinejournal.flavor.library.link"); }

    private int editHeaderWidth() {
        return font().width(editLabel()) + 6 + font().width(libraryLabel()) + 13;
    }

    /** Pencil after the search box; in edit mode "编辑模式" and the library link before it. */
    private void renderEditHeader(GuiGraphics g, int mx, int my) {
        if (!layoutCanEdit) return;
        int px = searchBox.x() + searchBox.width() + 4, py = searchBox.y() + 1;
        boolean hover = inside(mx, my, px, py, PENCIL, PENCIL);
        g.fill(px, py, px + PENCIL, py + PENCIL, flavorEditing ? 0x40E8A93C : hover ? 0xFF2A2018 : 0xFF1B140E);
        JournalTheme.border(g, px, py, PENCIL, PENCIL, flavorEditing ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT_DIM : JournalTheme.PANEL_BORDER);
        String[] shape = {".....##", "....#.#", "...##..", "..###..", ".###...", ".##....", "#......"};
        int color = flavorEditing ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM;
        for (int row = 0; row < 7; row++) for (int col = 0; col < 7; col++)
            if (shape[row].charAt(col) == '#') g.fill(px + 2 + col, py + 2 + row, px + 3 + col, py + 3 + row, row == 6 ? JournalTheme.TEXT_FAINT : color);
        if (hover) tooltip(tr(flavorEditing ? "cuisinejournal.flavor.edit.exit" : "cuisinejournal.flavor.edit.enter"));
        if (!flavorEditing) return;
        Component library = libraryLabel();
        // Same box as the search field (13px): glyphs are 7px of ink, so text sits 3px down,
        // and the font's trailing pixel is left out when centring horizontally.
        int lw = font().width(library) + 9, lx = searchBox.x() - 4 - lw, ly = searchBox.y(), lh = searchBox.height();
        boolean libHover = inside(mx, my, lx, ly, lw, lh);
        g.fill(lx, ly, lx + lw, ly + lh, libHover ? 0xFF2A2018 : 0xFF1B140E);
        JournalTheme.border(g, lx, ly, lw, lh, libHover ? JournalTheme.TEXT_DIM : JournalTheme.PANEL_BORDER);
        g.drawString(font(), library, lx + (lw - font().width(library) + 1) / 2, ly + (lh - 7) / 2,
                libHover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, false);
        Component mode = editLabel();
        g.drawString(font(), mode, lx - 6 - font().width(mode), ly + (lh - 7) / 2, JournalTheme.TEXT_ACCENT, false);
    }

    private boolean headerClick(double mx, double my) {
        if (!layoutCanEdit) return false;
        int px = searchBox.x() + searchBox.width() + 4, py = searchBox.y() + 1;
        if (inside(mx, my, px, py, PENCIL, PENCIL)) {
            flavorEditing = !flavorEditing;
            if (!flavorEditing) resetTranslationFilter();
            sorts.close(); onLayout(); rebuild(true);
            CuisineUiSoundCompat.toggle(flavorEditing);
            return true;
        }
        if (!flavorEditing) return false;
        int lw = font().width(libraryLabel()) + 9, lx = searchBox.x() - 4 - lw;
        if (inside(mx, my, lx, searchBox.y(), lw, searchBox.height())) { CuisineUiSoundCompat.click(); openLibrary(null); return true; }
        return false;
    }

    private void resetTranslationFilter() {
        translationFilter = TagTranslationFilter.NORMAL;
        if (translations != null) translations.value(TagTranslationFilter.NORMAL);
    }

    /**
     * The 风味 block, shown for every ingredient. Browsing: own flavors (when a source), flavors
     * passed down from tags, or a faint "无风味". Editing: source switch, × per bound flavor,
     * "＋ 添加风味", revert, problems and a preview against one of the ingredient's dishes.
     */
    private int renderFlavor(GuiGraphics g, Packets.IngredientView value, int cx, int cursor, int available,
                             int mx, int my) {
        Packets.FlavorInfo info = value.flavorInfo();
        boolean edit = flavorEditing;
        if (!edit && !com.chi__you.cuisinejournal.food.CuisineGameplayRules.flavorsEnabled()) return cursor;
        int right = cx + available;
        JournalTheme.divider(g, cx, cursor, available); cursor += 6;
        clipped(g, tr("cuisinejournal.flavor.title"), cx, cursor + 1, available / 2, JournalTheme.TEXT_DIM);
        boolean anyRows = FlavorUi.hasVisibleFlavor(info);
        if (edit && info.local()) {
            Component revert = tr("cuisinejournal.flavor.revert");
            int rw = scaledWidth(revert, SMALL_TEXT_SCALE);
            boolean hover = inside(mx, my, right - rw, cursor, rw, 10) && detail.contains(mx, my);
            valueScaled(g, revert, right, cursor + 2, rw, hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, SMALL_TEXT_SCALE);
            Component edited = tr("cuisinejournal.flavor.edited");
            int ew = scaledWidth(edited, SMALL_TEXT_SCALE);
            valueScaled(g, edited, right - rw - 5, cursor + 2, ew, JournalTheme.ADJUSTED, SMALL_TEXT_SCALE);
            addFlavorHit(right - rw, cursor, rw, 10, () -> send(Packets.FlavorEditRequest.Action.REVERT_BINDING, value, false, List.of()));
        }
        cursor += 13;
        if (edit) {
            boolean on = info.source();
            boolean hover = inside(mx, my, cx, cursor, available, 11) && detail.contains(mx, my);
            g.fill(cx, cursor + 2, cx + 14, cursor + 9, on ? 0x66E8A93C : 0xFF120D09);
            JournalTheme.border(g, cx, cursor + 2, 14, 7, on ? JournalTheme.TEXT_ACCENT : 0xFF3A2C1F);
            int knob = on ? cx + 8 : cx + 1;
            g.fill(knob, cursor + 3, knob + 5, cursor + 8, on ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_FAINT);
            clippedScaled(g, tr("cuisinejournal.flavor.as_source"), cx + 18, cursor + 2, available - 18,
                    hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, SMALL_TEXT_SCALE);
            addFlavorHit(cx, cursor, available, 11, () -> send(Packets.FlavorEditRequest.Action.BIND, value, !on, info.flavors()));
            cursor += 13;
        }
        if (info.source() || edit) {
            for (ResourceLocation id : info.flavors()) {
                if (!edit && !FlavorUi.visible(id)) continue;
                List<ResourceLocation> without = new ArrayList<>(info.flavors()); without.remove(id);
                cursor = flavorRow(g, id, cx, cursor, available, mx, my, edit,
                        () -> send(Packets.FlavorEditRequest.Action.BIND, value, info.source(), without), !info.source(),
                        "own/" + id);
            }
        }
        if (info.legacy() && (info.source() || edit)) {
            clippedScaled(g, tr("cuisinejournal.flavor.legacy"), cx, cursor + 2, available, JournalTheme.TEXT_FAINT, SMALL_TEXT_SCALE);
            cursor += 11;
        }
        for (Packets.InheritedFlavor inherited : info.inherited()) {
            if (!edit && inherited.flavors().stream().noneMatch(FlavorUi::visible)) continue;
            for (ResourceLocation id : inherited.flavors())
                if (edit || FlavorUi.visible(id))
                cursor = flavorRow(g, id, cx, cursor, available, mx, my, false, null, false, inherited.tag() + "/" + id);
            Component from = tr(edit ? "cuisinejournal.flavor.from_tag_edit" : "cuisinejournal.flavor.from_tag", tagName(inherited.tag()));
            int fw = Math.min(scaledWidth(from, SMALL_TEXT_SCALE), available - 16);
            boolean hover = inside(mx, my, cx + 16, cursor - 2, fw, 9) && detail.contains(mx, my);
            clippedScaled(g, from, cx + 16, cursor - 1, available - 16, hover ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT, SMALL_TEXT_SCALE);
            if (entry(inherited.tag()) != null) entryHits.add(new EntryHit(new Box(cx + 16, cursor - 2, fw, 9), inherited.tag()));
            cursor += 9;
        }
        if (!edit && !anyRows && !(info.legacy() && info.source())) {
            clippedScaled(g, tr("cuisinejournal.flavor.none"), cx, cursor, available, JournalTheme.TEXT_FAINT, SMALL_TEXT_SCALE);
            cursor += 11;
        }
        if (edit) {
            boolean hover = inside(mx, my, cx, cursor + 1, available, 12) && detail.contains(mx, my);
            int dash = hover ? JournalTheme.TEXT_ACCENT : 0xFF4A3A2A;
            for (int i = cx; i < right; i += 4) { g.fill(i, cursor + 1, Math.min(i + 2, right), cursor + 2, dash); g.fill(i, cursor + 12, Math.min(i + 2, right), cursor + 13, dash); }
            g.fill(cx, cursor + 1, cx + 1, cursor + 13, dash); g.fill(right - 1, cursor + 1, right, cursor + 13, dash);
            clippedScaled(g, tr("cuisinejournal.flavor.add"), cx + 5, cursor + 4, available - 10,
                    hover ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_DIM, SMALL_TEXT_SCALE);
            addFlavorHit(cx, cursor + 1, available, 12, () -> openPicker(value));
            cursor += 16;
        }
        for (String problem : info.problems()) {
            Component line = problem.startsWith("unknown:") ? tr("cuisinejournal.flavor.problem.unknown", problem.substring(8))
                    : Component.literal(problem);
            clippedScaled(g, line, cx, cursor + 1, available, JournalTheme.DANGER, SMALL_TEXT_SCALE);
            cursor += 10;
        }
        if (edit && !value.meals().isEmpty()) cursor = renderPreview(g, value, cx, cursor, available, mx, my);
        return cursor + 2;
    }

    /** The flavor row whose stage table is shown (one at a time); keyed with its source so a tag row is separate. */
    private String openFlavorRow;

    /**
     * One flavor: icon, name and a ▾ on the right; nothing else until clicked. A click opens
     * the stage table underneath (★N and its effects, wrapped to the panel width).
     */
    private int flavorRow(GuiGraphics g, ResourceLocation id, int cx, int cursor, int available, int mx, int my,
                          boolean removable, Runnable remove, boolean dimmed, String rowKey) {
        FlavorClientState.Entry entry = FlavorClientState.get(id);
        int right = cx + available;
        int removeW = removable && remove != null ? 10 : 0;
        boolean open = rowKey.equals(openFlavorRow) && entry != null;
        boolean overRemove = removeW > 0 && inside(mx, my, right - 8, cursor, 8, FLAVOR_ROW);
        boolean hover = inside(mx, my, cx - 2, cursor, available + 4, FLAVOR_ROW) && detail.contains(mx, my)
                && !sorts.isOpen() && !overRemove;
        if (open) g.fill(cx - 2, cursor, right + 2, cursor + FLAVOR_ROW, 0x1AE8A93C);
        else if (hover) g.fill(cx - 2, cursor, right + 2, cursor + FLAVOR_ROW, 0x12FFE9C7);
        if (entry != null) FlavorUi.icon(g, entry.preset(), cx, cursor, 12); else FlavorUi.leaf(g, cx, cursor, 12);
        boolean disabled = entry != null && !entry.preset().enabled();
        int nameColor = entry == null ? JournalTheme.DANGER : disabled || dimmed ? JournalTheme.TEXT_FAINT
                : open || hover ? JournalTheme.TEXT : JournalTheme.DISCOVERY;
        Component caret = Component.literal(open ? "\u25b4" : "\u25be");
        int caretW = entry == null ? 0 : scaledWidth(caret, SMALL_TEXT_SCALE);
        clipped(g, FlavorUi.name(id), cx + 16, cursor + 2, available - 16 - caretW - removeW - 6, nameColor);
        if (entry != null) valueScaled(g, caret, right - removeW - (removeW > 0 ? 4 : 0), cursor + 3, caretW,
                open ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT, SMALL_TEXT_SCALE);
        if (entry != null) addFlavorHit(cx - 2, cursor, available + 4 - removeW, FLAVOR_ROW,
                () -> openFlavorRow = rowKey.equals(openFlavorRow) ? null : rowKey);
        if (removeW > 0) {
            g.drawString(font(), "×", right - 6, cursor + 2, overRemove ? JournalTheme.DANGER : JournalTheme.TEXT_FAINT, false);
            addFlavorHit(right - 8, cursor, 8, FLAVOR_ROW, remove);
        }
        cursor += FLAVOR_ROW;
        if (!open) return cursor;
        // Stage table: a gold rule on the left, "★N  effects" per stage, wrapped.
        var stages = entry.preset().stages();
        int labelW = font().width("\u26050") + 5, textX = cx + 5 + labelW, textW = Math.max(1, right - textX);
        int top = cursor, height = 3;
        List<List<net.minecraft.util.FormattedCharSequence>> bodies = new ArrayList<>();
        for (var stage : stages) {
            // Breaks only between items, so "时长 −60%" never splits across lines.
            List<net.minecraft.util.FormattedCharSequence> split = FlavorUi.wrapItems(font(), FlavorUi.lines(stage),
                    (int) (textW / SMALL_TEXT_SCALE));
            bodies.add(split);
            height += Math.max(1, split.size()) * 9 + 1;
        }
        boolean rule = stages.size() > 1;
        if (rule) height += 9;
        height += 2;
        g.fill(cx, top, right, top + height, 0x40000000);
        g.fill(cx, top, cx + 1, top + height, 0x99E8A93C);
        int ty = top + 3;
        for (int i = 0; i < stages.size(); i++) {
            clippedScaled(g, Component.literal("\u2605" + stages.get(i).minStars()), cx + 5, ty, labelW,
                    JournalTheme.TEXT_ACCENT, SMALL_TEXT_SCALE);
            for (var line : bodies.get(i)) {
                g.pose().pushPose();
                g.pose().translate(textX, ty, 0);
                g.pose().scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE, 1F);
                g.drawString(font(), line, 0, 0, disabled || dimmed ? JournalTheme.TEXT_FAINT : JournalTheme.TEXT, false);
                g.pose().popPose();
                ty += 9;
            }
            if (bodies.get(i).isEmpty()) ty += 9;
            ty += 1;
        }
        if (rule) clippedScaled(g, tr("cuisinejournal.flavor.stage_rule"), cx + 5, ty, available - 8, JournalTheme.TEXT_FAINT, SMALL_TEXT_SCALE);
        return top + height + 3;
    }

    private int renderPreview(GuiGraphics g, Packets.IngredientView value, int cx, int cursor, int available, int mx, int my) {
        List<ResourceLocation> meals = sortedMeals(value);
        previewIndex = Math.floorMod(previewIndex, meals.size());
        ResourceLocation dish = meals.get(previewIndex);
        String key = dish + "@" + previewStars;
        Packets.FlavorPreview preview = FlavorClientState.preview();
        boolean ready = preview != null && preview.dish().equals(dish) && preview.stars() == previewStars;
        if (!key.equals(requestedPreview)) {
            requestedPreview = key;
            Network.toServer(new Packets.FlavorPreviewRequest(dish, previewStars));
        }
        int top = cursor + 2;
        boolean visibleFlavors = ready && preview.flavors().stream().anyMatch(FlavorUi::visible);
        int effectCount = ready ? (int) preview.effects().stream().filter(BuiltInRegistries.MOB_EFFECT::containsKey).count() : 0;
        int lines = ready ? (preview.values() ? 3 : 0) + effectCount + (visibleFlavors ? 0 : 1) : 1;
        int boxH = 16 + lines * 10 + 3;
        g.fill(cx, top, cx + available, top + boxH, 0xFF0E0A07);
        JournalTheme.border(g, cx, top, available, boxH, 0xFF2E2318);
        int lx = cx + 4, ly = top + 3;
        clippedScaled(g, tr("cuisinejournal.flavor.preview"), lx, ly + 1, 30, JournalTheme.TEXT_DIM, SMALL_TEXT_SCALE);
        lx += scaledWidth(tr("cuisinejournal.flavor.preview"), SMALL_TEXT_SCALE) + 4;
        Component dishName = Component.literal("‹ ").append(JournalText.nameOf(dish)).append(" ›");
        int dw = Math.min(scaledWidth(dishName, SMALL_TEXT_SCALE) + 4, available - (lx - cx) - 30);
        boolean dishHover = inside(mx, my, lx, ly - 1, dw, 10) && detail.contains(mx, my);
        g.fill(lx, ly - 1, lx + dw, ly + 9, dishHover ? 0xFF2A2018 : 0xFF1B140E);
        clippedScaled(g, dishName, lx + 2, ly + 1, dw - 4, JournalTheme.TEXT, SMALL_TEXT_SCALE);
        addFlavorHit(lx, ly - 1, dw, 10, () -> previewIndex++);
        Component stars = Component.literal("★" + previewStars);
        int sw = scaledWidth(stars, SMALL_TEXT_SCALE) + 6, sx = cx + available - 4 - sw;
        boolean starHover = inside(mx, my, sx, ly - 1, sw, 10) && detail.contains(mx, my);
        g.fill(sx, ly - 1, sx + sw, ly + 9, starHover ? 0xFF2A2018 : 0xFF1B140E);
        clippedScaled(g, stars, sx + 3, ly + 1, sw - 4, JournalTheme.TEXT_ACCENT, SMALL_TEXT_SCALE);
        addFlavorHit(sx, ly - 1, sw, 10, () -> previewStars = (previewStars + 1) % 6);
        ly += 13;
        if (!ready) {
            clippedScaled(g, tr("cuisinejournal.flavor.preview_wait"), cx + 4, ly, available - 8, JournalTheme.TEXT_FAINT, SMALL_TEXT_SCALE);
            return top + boxH + 2;
        }
        if (preview.values()) {
            ly = previewLine(g, cx, ly, available, tr("cuisinejournal.flavor.preview.health"),
                    FlavorUi.trim(preview.baseHealth() / 2.0) , FlavorUi.trim(preview.health() / 2.0), "cuisinejournal.flavor.unit.heart_plain");
            ly = previewLine(g, cx, ly, available, tr("cuisinejournal.flavor.preview.regen"),
                    FlavorUi.trim(preview.baseRegen() / 2.0), FlavorUi.trim(preview.regen() / 2.0), "cuisinejournal.flavor.unit.heart_s_plain");
            ly = previewLine(g, cx, ly, available, tr("cuisinejournal.flavor.preview.duration"),
                    Integer.toString(preview.baseSeconds()), Integer.toString(preview.seconds()), "cuisinejournal.flavor.unit.second_plain");
        }
        for (int i = 0; i < preview.effects().size(); i++) {
            if (!BuiltInRegistries.MOB_EFFECT.containsKey(preview.effects().get(i))) continue;
            Component effect = FlavorUi.effectName(preview.effects().get(i)).copy().append(" " + FlavorUi.roman(preview.levels().get(i)));
            clippedScaled(g, effect, cx + 4, ly, available / 2, JournalTheme.TEXT_DIM, SMALL_TEXT_SCALE);
            Component secs = tr("cuisinejournal.flavor.unit.second_plain", preview.effectSeconds().get(i));
            valueScaled(g, secs, cx + available - 4, ly, available / 2, JournalTheme.TEXT, SMALL_TEXT_SCALE);
            ly += 10;
        }
        if (!visibleFlavors) {
            clippedScaled(g, tr("cuisinejournal.flavor.preview_none"), cx + 4, ly, available - 8, JournalTheme.TEXT_FAINT, SMALL_TEXT_SCALE);
        }
        return top + boxH + 2;
    }

    private int previewLine(GuiGraphics g, int cx, int ly, int available, Component label, String before, String after, String unit) {
        clippedScaled(g, label, cx + 4, ly, available / 2, JournalTheme.TEXT_DIM, SMALL_TEXT_SCALE);
        boolean changed = !before.equals(after);
        double b = Double.parseDouble(before), a = Double.parseDouble(after);
        Component value = tr(unit, before).copy();
        if (changed) value = value.copy().append(Component.literal(" → ").withStyle(st -> st.withColor(JournalTheme.TEXT_FAINT)))
                .append(tr(unit, after).withStyle(st -> st.withColor(a > b ? JournalTheme.TEXT_GOOD : JournalTheme.DANGER)));
        valueScaled(g, value, cx + available - 4, ly, available - 40, JournalTheme.TEXT, SMALL_TEXT_SCALE);
        return ly + 10;
    }

    private void addFlavorHit(int x, int y, int w, int h, Runnable action) {
        if (y + h > detail.y() + 1 && y < detail.y() + detail.height() - 1) flavorHits.add(new FlavorHit(new Box(x, y, w, h), action));
    }

    private void send(Packets.FlavorEditRequest.Action action, Packets.IngredientView value, boolean source, List<ResourceLocation> flavors) {
        send(action, value, source, flavors, com.chi__you.cuisinejournal.client.EditClientState.snapshot());
    }

    private void send(Packets.FlavorEditRequest.Action action, Packets.IngredientView value, boolean source, List<ResourceLocation> flavors, java.util.Map<String, Long> versions) {
        var expected = new java.util.HashMap<String, Long>();
        flavors.forEach(id -> {
            String key = com.chi__you.cuisinejournal.network.EditVersions.preset(id);
            expected.put(key, versions.getOrDefault(key, 0L));
        });
        expected.put(com.chi__you.cuisinejournal.network.EditVersions.binding(value.tag(), value.item()), value.flavorInfo().version());
        pendingRequest = FlavorClientState.nextRequestId();
        Network.toServer(new Packets.FlavorEditRequest(pendingRequest, action, value.item(), value.tag(), source, flavors, "", 0, expected));
    }

    private void openPicker(Packets.IngredientView value) {
        var versions = com.chi__you.cuisinejournal.client.EditClientState.snapshot();
        FlavorPickerModal picker = new FlavorPickerModal(font(), nameOf(value), value.flavorInfo().flavors(),
                chosen -> send(Packets.FlavorEditRequest.Action.BIND, value, true, chosen, versions), () -> openLibrary(null));
        modal = picker;
        picker.openCentered(x, y, width, height);
    }

    private void openLibrary(ResourceLocation id) {
        FlavorLibraryModal library = new FlavorLibraryModal(font(), id, request -> pendingRequest = request, this::openExport);
        modal = library;
        library.openCentered(x, y, width, height);
    }

    /** The flavor scheme editor (replaces the old data-pack export dialog). */
    private void openExport() {
        SchemeModal schemes = new SchemeModal(font(), false, request -> schemeRequest = request);
        modal = schemes;
        schemes.openCentered(x, y, width, height);
    }

    private int schemeRequest;

    /** Toast text for a scheme result; the generated default's empty name reads "百味纪默认". */
    static Component schemeMessage(Packets.SchemeResult result) {
        Object[] args = result.args().stream().map(arg -> (Object) (arg.isEmpty()
                ? Component.translatable("cuisinejournal.scheme.default_name") : Component.literal(arg))).toArray();
        return Component.translatable(result.message(), args);
    }

    /** Toast for the last edit; the library and export dialogs are told too. */
    private void pollFlavorResult() {
        if (schemeRequest != 0) {
            Packets.SchemeResult result = com.chi__you.cuisinejournal.client.SchemeClientState.take(schemeRequest);
            if (result != null) {
                schemeRequest = 0;
                Component message = schemeMessage(result);
                if (screen != null) screen.editToast(result.success() ? message : tr("cuisinejournal.edit.toast.failed", message),
                        result.success(), false);
                if (modal instanceof SchemeModal schemes && schemes.isOpen()) schemes.accept(result);
            }
        }
        if (pendingRequest == 0) return;
        Packets.FlavorEditResult result = FlavorClientState.take(pendingRequest);
        if (result == null) return;
        pendingRequest = 0;
        requestedPreview = "";
        Component message = tr(result.message(), result.args().toArray());
        if (screen != null) screen.editToast(result.success() ? message : tr("cuisinejournal.edit.toast.failed", message),
                result.success(), false);
        if (modal instanceof FlavorLibraryModal library && library.isOpen()) library.accept(result);
        if (modal instanceof FlavorPickerModal picker && picker.isOpen()) picker.accept(result.success());

    }

    // ------------------------------------------------------------ tag sources

    /** 0 = folded (one row of chips), 1 = expanded (one line per real tag); eases both ways. */
    private float tagExpand;
    private long tagExpandFrame;
    /** Pinned open by a click on the "N 个标签 ▾" toggle; folds again on a second click or a new selection. */
    private boolean tagOpen;
    /** Where the toggle was drawn this frame; null when the entry has a single tag. */
    private Box tagToggle;
    /** 0..1 hover glow of the toggle; drives the faint background and the arrow nudge. */
    private float tagToggleHover;
    private static final int TAG_CHIP_H = 10, TAG_LINE_H = 9;

    /** Members of one real tag, from {@code tagSources}; -1 when the server did not send it. */
    private static int tagSize(Packets.IngredientView value, ResourceLocation id) {
        for (Packets.IngredientTagSource source : value.tagSources())
            if (source.tag().equals(id)) return source.members().size();
        return -1;
    }

    /** Height of the folded row; the expanded block adds {@link #tagExpandedExtra}. */
    private int tagFoldedHeight(Packets.IngredientView value) {
        return value.tag() && !value.tagIds().isEmpty() ? TAG_CHIP_H : 0;
    }

    private int tagExpandedExtra(Packets.IngredientView value) {
        if (!value.tag() || value.tagIds().size() <= 1) return 0;
        return value.tagIds().size() * TAG_LINE_H + 4 - TAG_CHIP_H;
    }

    /**
     * The merged real tags of a tag entry. Folded: one line of full ids, the rest collapsed
     * into a gold "+N"; a "N 个标签 ▾" toggle sits on the kind line above. Clicking it pins the
     * block open — one line per tag with its own member count, content below slides down —
     * until it is clicked again or another ingredient is selected.
     */
    private void renderTagSources(GuiGraphics g, Packets.IngredientView value, int left, int top, int available,
                                  int mx, int my) {
        List<ResourceLocation> ids = value.tagIds();
        if (ids.isEmpty()) { tagToggle = null; return; }
        int height = tagFoldedHeight(value) + Math.round(tagExpandedExtra(value) * eased(tagExpand));
        long now = System.nanoTime();
        float step = tagExpandFrame == 0 ? 0 : Math.min(0.1F, (now - tagExpandFrame) / 1_000_000_000F) / 0.15F;
        tagExpandFrame = now;
        boolean several = ids.size() > 1;
        if (!several) tagOpen = false;
        tagExpand = tagOpen ? Math.min(1F, tagExpand + step) : Math.max(0F, tagExpand - step);
        float t = eased(tagExpand);

        // Toggle sits on the "标签 · 肉类" line above, right-aligned, so every tag row below keeps
        // the same width and its count column lines up. Plain text, no background: hovering
        // brightens it and nudges the arrow a pixel.
        tagToggle = null;
        if (several) {
            Component word = tagOpen ? tr("cuisinejournal.ingredients.tag_collapse")
                    : tr("cuisinejournal.ingredients.tag_count", ids.size());
            String arrow = tagOpen ? " ▴" : " ▾";
            int wordW = font().width(word), arrowW = font().width(arrow);
            int lineY = top - 11;
            int tx = left + available - wordW - arrowW;
            tagToggle = new Box(tx - 2, lineY - 1, wordW + arrowW + 4, 11);
            boolean over = tagToggle.contains(mx, my) && detail.contains(mx, my) && !sorts.isOpen();
            tagToggleHover = over ? Math.min(1F, tagToggleHover + step) : Math.max(0F, tagToggleHover - step);
            float h = eased(tagToggleHover);
            int color = h > 0.5F ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT;
            g.drawString(font(), word, tx, lineY, color, false);
            g.pose().pushPose();
            g.pose().translate(0, (tagOpen ? -1F : 1F) * h, 0);
            g.drawString(font(), arrow, tx + wordW, lineY, h > 0.5F ? JournalTheme.TEXT_ACCENT : color, false);
            g.pose().popPose();
        }

        if (t < 0.5F) {
            // Folded row: same size and position as the first expanded row, full ids joined by
            // " · ", the rest as a gold "+N".
            int a = (int) (255 * Math.min(1F, 1F - t * 2));
            int width = (int) ((available) / SMALL_TEXT_SCALE);
            StringBuilder line = new StringBuilder();
            int shown = 0;
            for (int i = 0; i < ids.size(); i++) {
                String next = (line.length() == 0 ? "" : line + " · ") + ids.get(i);
                int remaining = ids.size() - i - 1;
                String more = remaining > 0 ? " +" + remaining : "";
                if (font().width(next) + font().width(more) > width) break;
                line.setLength(0);
                line.append(next);
                shown++;
            }
            if (shown == 0) {
                line.append(font().plainSubstrByWidth(ids.get(0).toString(),
                        Math.max(0, width - font().width("…")))).append("…");
                shown = 1;
            }
            if (a >= 8) {
                // Drawn with the same 8/9 scale as the expanded rows; two parts so "+N" keeps its gold.
                g.pose().pushPose();
                g.pose().translate(left, top + 2, 0);
                g.pose().scale(SMALL_TEXT_SCALE, SMALL_TEXT_SCALE, 1F);
                g.drawString(font(), line.toString(), 0, 0, (a << 24) | (JournalTheme.TEXT_DIM & 0x00FFFFFF), false);
                if (shown < ids.size())
                    g.drawString(font(), " +" + (ids.size() - shown), font().width(line.toString()), 0,
                            (a << 24) | (JournalTheme.TEXT_ACCENT & 0x00FFFFFF), false);
                g.pose().popPose();
            }
        }
        if (t > 0F) {
            // Expanded list: full id on the left, its own member count on the right.
            float alpha = Math.max(0F, t * 2 - 1);
            int boxH = height;
            // No box: the expanded lines read as plain text, like the folded row.
            if (alpha > 0F) {
                g.enableScissor(left - 2, top - 1, left + available + 2, top - 1 + boxH);
                int ly = top + 2;
                int faint = ((int) (255 * alpha) << 24) | (JournalTheme.TEXT_FAINT & 0x00FFFFFF);
                int dim = ((int) (255 * alpha) << 24) | (JournalTheme.TEXT_DIM & 0x00FFFFFF);
                for (ResourceLocation id : ids) {
                    int size = tagSize(value, id);
                    Component count = size < 0 ? Component.empty()
                            : tr("cuisinejournal.ingredients.tag_members", size);
                    int countW = scaledWidth(count, SMALL_TEXT_SCALE);
                    clippedScaled(g, Component.literal(id.toString()), left, ly, available - countW - 6, dim, SMALL_TEXT_SCALE);
                    if (size >= 0) valueScaled(g, count, left + available, ly, countW, faint, SMALL_TEXT_SCALE);
                    ly += TAG_LINE_H;
                }
                // GuiGraphics keeps a scissor stack: disabling pops back to the detail panel's own
                // scissor. (Re-enabling it here pushed an extra copy every frame that was never
                // popped, so the whole screen ended up clipped to the panel and flickered.)
                g.disableScissor();
            }
        }
    }

    /** Width the toggle takes on the kind line (0 when the entry has a single tag). */
    private int tagToggleWidth(Packets.IngredientView value) {
        if (!value.tag() || value.tagIds().size() <= 1) return 0;
        Component word = tagOpen ? tr("cuisinejournal.ingredients.tag_collapse")
                : tr("cuisinejournal.ingredients.tag_count", value.tagIds().size());
        return font().width(word) + font().width(" ▾") + 8;
    }

    private static float eased(float t) {
        return 1F - (1F - t) * (1F - t) * (1F - t);
    }

    /** Extra height below the header for the tag-source block (folded row is inside the header). */
    private int tagIdsExtraHeight(Packets.IngredientView value, int available) {
        if (!value.tag()) return 0;
        return Math.round(tagExpandedExtra(value) * eased(tagExpand));
    }

    private int maxDetailScroll() { return Math.max(0, detailContentHeight - detail.height()); }
    private Box detailThumb() {
        int h = Math.min(detail.height() - 2, Math.max(8,
                (detail.height() - 2) * (detail.height() - 2) / Math.max(1, detailContentHeight)));
        int top = detail.y() + 1 + (detail.height() - 2 - h) * detailScroll / Math.max(1, maxDetailScroll());
        return new Box(detail.x() + detail.width() - 4, top, 3, h);
    }

    private Component kindAndTypes(Packets.IngredientView value) {
        String key = value.tag() ? "tag" : value.kind() == 0 ? "cuisine" : value.kind() == 1 ? "pure" : "nonfood";
        var result = tr("cuisinejournal.ingredients.kind." + key);
        for (ResourceLocation id : value.types()) result.append(" · ").append(tr("cuisinejournal.ingredients.type." + id.getPath()));
        return result;
    }

    private Component dates(Packets.IngredientView value) {
        long first = value.firstUsedDay(), last = value.lastUsedDay();
        if (first == Long.MIN_VALUE || last == Long.MIN_VALUE) return Component.empty();
        return tr("cuisinejournal.ingredients.dates", first, last);
    }

    /** Both item and tag progress are server-authoritative. */
    private static long usedTimes(Packets.IngredientView value) {
        return value.timesUsed();
    }

    private void stars(GuiGraphics g, ResourceLocation meal, int sx, int sy) {
        int rank = ClientJournalState.rank(meal).ordinal();
        for (int i = 0; i < 5; i++) {
            int px = sx + i * 9;
            if (i < rank) {
                Component star = Component.literal("\uE001").withStyle(style -> style.withFont(
                        ResourceLocation.fromNamespaceAndPath("cuisinejournal", "tooltip")));
                g.drawString(font(), star, px, sy, JournalTheme.TEXT_ACCENT, false);
            } else {
                String[] shape = {"....#...", "...###..", ".#######", "..#####.",
                        "...###..", "..##.##.", "..#...#.", "........"};
                for (int row = 0; row < 8; row++) for (int col = 0; col < 8; col++) {
                    if (shape[row].charAt(col) != '#') continue;
                    if (row == 0 || row == 7 || col == 0 || col == 7
                            || shape[row - 1].charAt(col) == '.' || shape[row + 1].charAt(col) == '.'
                            || shape[row].charAt(col - 1) == '.' || shape[row].charAt(col + 1) == '.')
                        g.fill(px + col, sy + row, px + col + 1, sy + row + 1, 0xFF8A6F45);
                }
            }
        }
    }

    /** Discovered dishes first (highest star rank first), undiscovered after; stable otherwise. */
    private List<ResourceLocation> sortedMeals(Packets.IngredientView value) {
        List<ResourceLocation> meals = new ArrayList<>(value.meals());
        meals.sort(IngredientOrder.mealComparator(ClientJournalState::isDiscovered,
                meal -> ClientJournalState.rank(meal).ordinal(),
                creativeOrder.comparator(List.of("minecraft", "farmersdelight"))));
        return meals;
    }

    /** Tag members: discovered first, then creative-tab order (same tie-breaker as the list). */
    private List<ResourceLocation> sortedMembers(Packets.IngredientView value) {
        List<ResourceLocation> members = new ArrayList<>(value.members());
        Comparator<ResourceLocation> creative = creativeOrder.comparator(List.of("minecraft", "farmersdelight"));
        members.sort(Comparator.comparing((ResourceLocation member) -> {
            Packets.IngredientView entry = entry(member);
            return entry == null || !entry.known();
        }).thenComparing(creative));
        return members;
    }

    /**
     * Draws an item icon. Undiscovered items become dark, mostly desaturated silhouettes
     * (design: grayscale + brightness ≈ .3): the item is drawn dimmed, then washed with
     * {@code background} — the color directly behind the icon — so only the item's own
     * pixels change and no square shows.
     */
    private void icon(GuiGraphics g, ResourceLocation item, int ix, int iy, int size, boolean known, int background) {
        // Flush before changing shader tint so only the item is dimmed.
        g.flush();
        float brightness = known ? 1.0F : 0.5F;
        RenderSystem.setShaderColor(brightness, brightness, brightness, 1.0F);
        g.pose().pushPose();
        try {
            g.pose().translate(ix, iy, 0);
            g.pose().scale(size / 16.0F, size / 16.0F, 1);
            g.renderFakeItem(JournalText.stackOf(item), 0, 0);
            g.flush();
        } finally {
            g.pose().popPose();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (!known) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(ix, iy, ix + size, iy + size, (background & 0x00FFFFFF) | 0x88000000);
            g.pose().popPose();
        }
    }

    private void clippedScaled(GuiGraphics g, Component text, int tx, int ty, int maximum, int color, float scale) {
        if (maximum <= 0) return;
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font(), ellipsis(text, (int) (maximum / scale)), 0, 0, color, false);
        g.pose().popPose();
    }

    private int scaledWidth(Component text, float scale) {
        return (int) Math.ceil(font().width(text) * scale);
    }

    private void valueScaled(GuiGraphics g, Component text, int right, int ty, int maximum, int color, float scale) {
        int textWidth = Math.min(maximum, scaledWidth(text, scale));
        clippedScaled(g, text, right - textWidth, ty, textWidth, color, scale);
    }

    private void styledScaled(GuiGraphics g, Component text, int tx, int ty, int maximum, int color, float scale) {
        if (maximum <= 0) return;
        int limit = (int) (maximum / scale);
        FormattedText shown = text;
        if (font().width(text) > limit) {
            shown = FormattedText.composite(font().substrByWidth(text, Math.max(0, limit - font().width("…"))),
                    Component.literal("…"));
        }
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font(), Language.getInstance().getVisualOrder(shown), 0, 0, color, false);
        g.pose().popPose();
    }

    private void clipped(GuiGraphics g, Component text, int tx, int ty, int maximum, int color) {
        if (maximum > 0) g.drawString(font(), ellipsis(text, maximum), tx, ty, color, false);
    }
    private String ellipsis(Component text, int maximum) {
        if (maximum <= 0) return "";
        String value = text.getString();
        if (font().width(value) <= maximum) return value;
        return font().plainSubstrByWidth(value, Math.max(0, maximum - font().width("…"))) + "…";
    }
    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gr = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }
    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return new Box(x, y, Math.max(0, w), Math.max(0, h)).contains(mx, my);
    }
    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (modalOpen()) { modal.mouseClicked(mx, my, button); return true; }
        if (button == 0 && headerClick(mx, my)) return true;
        if (flavorEditing && translations != null && translations.isOpen()) {
            if (button == 0) translations.mouseClicked(font(), mx, my);
            else translations.close();
            return true;
        }
        if (sorts.isOpen()) {
            if (button == 0) sorts.mouseClicked(font(), mx, my);
            else sorts.close();
            return true;
        }
        if (detailPopup && !detail.contains(mx, my)) { detailPopup = false; return true; }
        if (detailPopup && inside(mx, my, detail.x() + detail.width() - 13, detail.y(), 13, 13)) {
            detailPopup = false; return true;
        }
        if (!detailPopup) {
            if (flavorEditing && button == 0 && translations.mouseClicked(font(), mx, my)) { sorts.close(); return true; }
            if (JournalSearchField.click(localSearch, mx, my, button, searchBox.x(), searchBox.y(),
                    searchBox.width(), searchBox.height())) return true;
            if (button == 0 && sorts.mouseClicked(font(), mx, my)) return true;
            for (Chip chip : chips) {
                if (button == 0 && chip.box().contains(mx, my)) {
                    kind = chip.kind();
                    rebuild(true); CuisineUiSoundCompat.click(); return true;
                }
            }
            if (list.mouseClicked(mx, my, button)) return true;
            Packets.IngredientView clicked = list.itemAt(mx, my);
            // The list only selects; it never opens the recipe viewer.
            if (clicked != null && button == 0) {
                selected = clicked.item(); detailScroll = 0; tagExpand = 0F; tagOpen = false; membersOpen = false; tagsOpen = false; previewIndex = 0; previewStars = 0; openFlavorRow = null; detailPopup = width < 340;
                CuisineUiSoundCompat.item(JournalText.stackOf(iconOf(clicked))); return true;
            }
            if (clicked != null) return true;
        }
        if (selected() != null && detail.contains(mx, my)) {
            for (FlavorHit hit : flavorHits) if (hit.box().contains(mx, my)) {
                if (button == 0) { CuisineUiSoundCompat.click(); hit.action().run(); }
                return true;
            }
            if (button == 0 && membersToggle != null && membersToggle.contains(mx, my)) {
                membersOpen = !membersOpen;
                CuisineUiSoundCompat.click();
                return true;
            }
            if (button == 0 && tagsToggle != null && tagsToggle.contains(mx, my)) {
                tagsOpen = !tagsOpen;
                CuisineUiSoundCompat.click();
                return true;
            }
            if (button == 0 && tagToggle != null && tagToggle.contains(mx, my)) {
                tagOpen = !tagOpen;
                CuisineUiSoundCompat.click();
                return true;
            }
            if (button == 0 && mx >= detail.x() + detail.width() - 4 && maxDetailScroll() > 0) {
                Box thumb = detailThumb();
                detailDragOffset = thumb.contains(mx, my) ? my - thumb.y() : thumb.height() / 2.0;
                draggingDetail = true; dragDetail(my); return true;
            }
            // Header icon: left = recipes, right = uses (same as the dish page's icon).
            if (headerIcon != null && headerIcon.contains(mx, my)) {
                if (button == 0) RecipeViewerBridge.showRecipes(JournalText.stackOf(selected));
                else if (button == 1) RecipeViewerBridge.showUses(JournalText.stackOf(selected));
                return true;
            }
            for (EntryHit hit : entryHits) if (hit.box().contains(mx, my)) {
                if (button == 0) open(hit.entry());
                return true;
            }
            // Dishes open their page in the category journal; Esc there comes back here.
            for (DishHit hit : dishHits) if (hit.box().contains(mx, my)) {
                if (button == 0) openDish(hit.item());
                return true;
            }
        }
        return detailPopup;
    }

    private void openDish(ResourceLocation dish) {
        if (screen == null) return;
        if (screen.page("collections") instanceof CollectionsPage collections) {
            JournalPage from = this;
            screen.jump(from, collections);
            if (!collections.openFood(dish)) screen.select(from);
        }
    }

    private void dragDetail(double my) {
        Box thumb = detailThumb();
        int previous = detailScroll;
        int travel = Math.max(1, detail.height() - 2 - thumb.height());
        detailScroll = (int) ((my - detail.y() - 1 - detailDragOffset) / travel * maxDetailScroll());
        detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll));
        CuisineUiSoundCompat.scroll(previous / 14, detailScroll / 14);
    }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (modalOpen()) return modal.mouseScrolled(mx, my, delta);
        if (flavorEditing && translations != null && translations.isOpen()) { translations.mouseScrolled(font(), mx, my, delta); return true; }
        if (sorts.isOpen()) { sorts.mouseScrolled(font(), mx, my, delta); return true; }
        if (selected() != null && detail.contains(mx, my) && (width >= 340 || detailPopup)) {
            int previous = detailScroll;
            detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll - (int) Math.round(delta * DETAIL_SCROLL_STEP)));
            CuisineUiSoundCompat.scroll(previous, detailScroll); return true;
        }
        return !detailPopup && list.mouseScrolled(mx, my, delta);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (modalOpen()) return modal.mouseDragged(mx, my, button, dx, dy);
        if (draggingDetail && button == 0) { dragDetail(my); return true; }
        return !detailPopup && list.mouseDragged(mx, my, button);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (modalOpen()) return modal.mouseReleased(mx, my, button);
        if (button == 0 && draggingDetail) { draggingDetail = false; return true; }
        return list.mouseReleased(button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (modalOpen()) { modal.keyPressed(key, scan, modifiers); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (translations != null && translations.isOpen()) { translations.close(); return true; }
            if (sorts.isOpen()) { sorts.close(); return true; }
            if (detailPopup) { detailPopup = false; return true; }
            if (textInputFocused()) { localSearch.setFocused(false); return true; }
            if (flavorEditing) { flavorEditing = false; resetTranslationFilter(); onLayout(); rebuild(true); CuisineUiSoundCompat.toggle(false); return true; }
        }
        if (textInputFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_TAB) localSearch.setFocused(false);
            else super.keyPressed(key, scan, modifiers);
            return true;
        }
        return false;
    }
    @Override public boolean blocksInput() { return modalOpen() || sorts != null && sorts.isOpen()
            || flavorEditing && translations != null && translations.isOpen() || detailPopup; }
    @Override public boolean charTyped(char c, int modifiers) {
        if (modalOpen()) { modal.charTyped(c, modifiers); return true; }
        return super.charTyped(c, modifiers);
    }
    @Override public boolean textInputFocused() {
        if (modalOpen()) return modal.textInputFocused();
        return super.textInputFocused();
    }
    @Override public void tick() {
        super.tick();
        if (modalOpen()) modal.tick();
        pollFlavorResult();
    }
    @Override public void onHidden() {
        super.onHidden(); if (sorts != null) sorts.close(); detailPopup = false; draggingDetail = false;
        flavorEditing = false; resetTranslationFilter(); if (modal != null) modal.close(); modal = null;
        list.mouseReleased(0);
    }
    @Override public Component emptyMessage() {
        if (!ClientJournalState.directoryReady()) return tr(ClientJournalState.directoryFailed()
                ? "cuisinejournal.sync.failed" : "cuisinejournal.sync.loading");
        return tr(query.isBlank() ? "cuisinejournal.empty.ingredients" : "cuisinejournal.empty.search");
    }
}
