package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.EditClientState;
import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.recipe.PinyinMatch;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Searchable category target list for moving subcategories or assigning foods. */
public final class TargetPickerModal extends JournalModal {
    public enum Mode { MOVE_SUB, ASSIGN_FOOD }
    private final Mode mode;
    private final Font font;
    private final ResourceLocation sourceId;
    private final ResourceLocation currentParent;
    private final List<ResourceLocation> items;
    private final List<Packets.CategoryMetadata> allTargets;
    private final Consumer<Packets.CategoryEditRequest> submit;
    private final Consumer<ResourceLocation> chosen;
    private final Map<String, Long> expectedVersions;
    private final JournalTextField search;
    private ResourceLocation selected;
    private ResourceLocation lastClicked;
    private long lastClickTime;
    private List<Packets.CategoryMetadata> visible = List.of();
    private List<Row> rows = List.of();
    private record Row(Packets.CategoryMetadata category, boolean heading, int count) { }
    /** Root groups the player folded shut; ignored while a search query is active. */
    private final Set<ResourceLocation> collapsed = new HashSet<>();
    private static final int ROWS_TOP = 32;
    private static final int ROW_HEIGHT = 18;

    public TargetPickerModal(int x, int y, int width, int height, Font font, Mode mode,
                             ResourceLocation sourceId, ResourceLocation currentParent,
                             List<ResourceLocation> items,
                             List<Packets.CategoryMetadata> targets,
                             Consumer<Packets.CategoryEditRequest> submit, Runnable cancel) {
        this(x, y, width, height, font, mode, sourceId, currentParent, items, targets, submit, null, cancel);
    }

    private TargetPickerModal(int x, int y, int width, int height, Font font, Mode mode,
                              ResourceLocation sourceId, ResourceLocation currentParent,
                              List<ResourceLocation> items,
                              List<Packets.CategoryMetadata> targets,
                              Consumer<Packets.CategoryEditRequest> submit,
                              Consumer<ResourceLocation> chosen, Runnable cancel) {
        this(x, y, width, height, font, mode, sourceId, currentParent, items, targets, submit, chosen, cancel, null);
    }

    private TargetPickerModal(int x, int y, int width, int height, Font font, Mode mode,
                              ResourceLocation sourceId, ResourceLocation currentParent,
                              List<ResourceLocation> items,
                              List<Packets.CategoryMetadata> targets,
                              Consumer<Packets.CategoryEditRequest> submit,
                              Consumer<ResourceLocation> chosen, Runnable cancel, Component sourceName) {
        super(x, y, width, height, mode == Mode.MOVE_SUB
                ? Component.translatable("cuisinejournal.edit.move_title", sourceName == null
                        ? Component.literal(sourceId == null ? "" : sourceId.toString()) : sourceName)
                : Component.translatable("cuisinejournal.edit.assign_title", items == null ? 0 : items.size()));
        this.mode = mode;
        this.font = font;
        this.sourceId = sourceId;
        this.currentParent = currentParent;
        this.items = List.copyOf(items == null ? List.of() : items);
        this.allTargets = List.copyOf(targets == null ? List.of() : targets);
        this.submit = submit;
        this.chosen = chosen;
        this.expectedVersions = Map.copyOf(EditClientState.snapshot());
        search = new JournalTextField(font, x + 8, y + 26, width - 16,
                Component.translatable(mode == Mode.MOVE_SUB ? "cuisinejournal.edit.search_root" : "cuisinejournal.edit.search_sub"));
        search.setResponder(value -> refresh());
        search.setMaxLength(64);
        addFocusable(search);
        refresh();
        setSubmitAction(this::submitTarget);
        setCancelAction(() -> { if (!submitWaiting() && cancel != null) cancel.run(); });
    }

    public TargetPickerModal(int x, int y, int width, int height, Font font,
                             ResourceLocation sourceId, ResourceLocation currentParent,
                             List<Packets.CategoryMetadata> roots,
                             Consumer<Packets.CategoryEditRequest> submit, Runnable cancel) {
        this(x, y, width, height, font, Mode.MOVE_SUB, sourceId, currentParent,
                List.of(), roots, submit, cancel);
    }

    /** Host-facing picker: rootsOnly selects root targets; selection is returned to chosen. */
    public TargetPickerModal(boolean rootsOnly, ResourceLocation current,
                             List<ResourceLocation> foods,
                             Consumer<ResourceLocation> chosen, Runnable cancel) {
        this(rootsOnly, null, null, current, foods, chosen, cancel);
    }

    /** Host-facing picker with the moved subcategory's id and display name for the title. */
    public TargetPickerModal(boolean rootsOnly, ResourceLocation source, Component sourceName,
                             ResourceLocation current, List<ResourceLocation> foods,
                             Consumer<ResourceLocation> chosen, Runnable cancel) {
        this(0, 0, 320, 200, net.minecraft.client.Minecraft.getInstance().font,
                rootsOnly ? Mode.MOVE_SUB : Mode.ASSIGN_FOOD, source, current,
                foods, targets(rootsOnly), null, chosen, cancel, sourceName);
    }

    /**
     * Editor metadata plus, for food assignment, the implicit subcategory of every
     * legacy root (id == root id). Those are not listed in metadata to avoid id
     * clashes, but the server accepts them as assignment targets.
     * Filters out mod categories since foods cannot be manually assigned to them.
     */
    private static List<Packets.CategoryMetadata> targets(boolean rootsOnly) {
        List<Packets.CategoryMetadata> base = ClientJournalState.categoryEditor().categories();
        if (rootsOnly) return base;
        List<Packets.CategoryMetadata> result = new ArrayList<>(base);
        for (Packets.CollectionView collection : ClientJournalState.collections()) {
            // Skip mod categories - foods cannot be manually assigned to them
            if (collection.isModCategory()) continue;

            boolean implicit = collection.subcategories().stream().anyMatch(sub -> sub.id().equals(collection.id()));
            if (!implicit) continue;
            Packets.CategoryMetadata root = base.stream().filter(value -> value.id().equals(collection.id())
                    && value.parent() == null).findFirst().orElse(null);
            if (root != null && root.hidden()) continue;
            String title = root != null ? root.titleKey() : collection.titleKey();
            boolean literal = root != null ? root.literalTitle() : collection.literalTitle();
            ResourceLocation icon = root != null ? root.icon() : collection.icon();
            // Runtime-only roots such as "other food" have no editor metadata; give them a heading entry.
            if (root == null) result.add(new Packets.CategoryMetadata(collection.id(), null, title, literal, icon,
                    (byte) 0, true, false, title, literal, icon));
            result.add(new Packets.CategoryMetadata(collection.id(), collection.id(), title, literal, icon,
                    (byte) 0, false, false, title, literal, icon));
        }
        return result;
    }

    @Override
    protected void renderBody(GuiGraphics g, int bodyX, int bodyY, int bodyWidth, int bodyHeight,
                              int mouseX, int mouseY, float partialTick) {
        // bodyY already includes the scroll offset and the pose is translated back, so
        // "bodyY - scrollOffset()" is the unscrolled body origin used by the click math.
        int top = bodyY - scrollOffset();
        search.setBounds(bodyX + 8, top + 2, bodyWidth - 16);
        search.render(g, mouseX, mouseY);
        int rowY = top + ROWS_TOP;
        int rowHeight = ROW_HEIGHT;
        boolean searching = searching();
        for (Row row : rows) {
            Packets.CategoryMetadata category = row.category();
            boolean hovered = mouseX >= bodyX + 2 && mouseX < bodyX + bodyWidth - 2
                    && mouseY >= rowY && mouseY < rowY + rowHeight;
            if (row.heading()) {
                g.fill(bodyX + 2, rowY + 1, bodyX + bodyWidth - 2, rowY + rowHeight - 1,
                        hovered && !searching ? 0x1AFFE9C7 : JournalTheme.PANEL_BOTTOM);
                g.fill(bodyX + 2, rowY + rowHeight - 1, bodyX + bodyWidth - 2, rowY + rowHeight, 0xFF3A2C1F);
                int textY = rowY + (rowHeight - 8) / 2 + 1;
                g.drawString(font, displayName(category), bodyX + 6, textY,
                        hovered && !searching ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, false);
                String count = Integer.toString(row.count());
                int chevronBox = bodyX + bodyWidth - 16;
                g.drawString(font, count, chevronBox - 3 - font.width(count), textY, JournalTheme.TEXT_FAINT, false);
                boolean open = searching || !collapsed.contains(category.id());
                PixelIcons.drawCentered(g, open ? PixelIcons.Icon.CHEVRON_DOWN : PixelIcons.Icon.CHEVRON_LEFT,
                        chevronBox, rowY, 12, rowHeight, hovered && !searching ? JournalTheme.TEXT : JournalTheme.TEXT_DIM);
                rowY += rowHeight;
                continue;
            }
            int indent = mode == Mode.ASSIGN_FOOD ? 8 : 0;
            boolean disabled = category.id().equals(currentParent);
            boolean chosen = category.id().equals(selected);
            JournalTheme.card(g, bodyX + 2 + indent, rowY, bodyWidth - 4 - indent, rowHeight, hovered && !disabled, chosen);
            renderCategoryIcon(g, category, bodyX + 4 + indent, rowY + 1);
            int textY = rowY + (rowHeight - 8) / 2 + 1;
            g.drawString(font, displayName(category), bodyX + 24 + indent, textY,
                    disabled ? JournalTheme.TEXT_FAINT : JournalTheme.TEXT, false);
            if (disabled) {
                Component current = Component.translatable("cuisinejournal.edit.current");
                g.drawString(font, current, bodyX + bodyWidth - 6 - font.width(current), textY,
                        JournalTheme.TEXT_FAINT, false);
            } else if (chosen) {
                PixelIcons.drawCentered(g, PixelIcons.Icon.CHECK, bodyX + bodyWidth - 16, rowY, 12, rowHeight,
                        JournalTheme.TEXT_ACCENT);
            }
            rowY += rowHeight;
        }
        if (visible.isEmpty()) g.drawString(font, Component.translatable("cuisinejournal.empty.search"),
                bodyX + 8, top + ROWS_TOP + 6, JournalTheme.TEXT_FAINT, false);
        setScrollContentHeight(ROWS_TOP + 4 + rows.size() * rowHeight);
    }

    @Override
    protected boolean bodyClicked(double mx, double my, int button) {
        if (search.mouseClicked(mx, my, button)) return true;
        // my is already in unscrolled body coordinates.
        int row = (int) Math.floor((my - bodyBounds().y - ROWS_TOP) / (double) ROW_HEIGHT);
        if (mx < bodyBounds().x + 2 || mx >= bodyBounds().x + bodyBounds().width - 2
                || my < bodyBounds().y + ROWS_TOP || row < 0 || row >= rows.size()) return false;
        Row rowData = rows.get(row);
        if (rowData.heading()) {
            if (!searching()) {
                ResourceLocation id = rowData.category().id();
                if (!collapsed.remove(id)) collapsed.add(id);
                com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
                refresh();
            }
            return true;
        }
        Packets.CategoryMetadata target = rowData.category();
        if (target.id().equals(currentParent)) return true;
        selected = target.id();
        com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
        long now = System.currentTimeMillis();
        if (target.id().equals(lastClicked) && now - lastClickTime < 350) {
            if (chosen != null) { chosen.accept(selected); close(); }
            else submitTarget();
        }
        lastClicked = target.id();
        lastClickTime = now;
        return true;
    }

    private boolean searching() {
        return !search.getValue().trim().isEmpty();
    }

    @Override protected boolean bodyScrolled(double mx, double my, double delta) { return false; }
    @Override protected boolean bodyKeyPressed(int keyCode, int scanCode, int modifiers) {
        return search.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override protected boolean bodyCharTyped(char c, int modifiers) { return search.charTyped(c, modifiers); }
    @Override protected boolean submitEnabled() { return selected != null && !submitWaiting(); }
    @Override protected void onSubmit() { submitTarget(); }

    private void submitTarget() {
        if (selected == null || submitWaiting()) return;
        if (chosen != null) {
            setSubmitWaiting(true);
            // The host retains this picker while saving, or replaces it with a confirmation.
            chosen.accept(selected);
            return;
        }
        if (submit == null) return;
        Packets.CategoryEditRequest.Action action = mode == Mode.MOVE_SUB
                ? Packets.CategoryEditRequest.Action.MOVE_SUB
                : (items.size() == 1 ? Packets.CategoryEditRequest.Action.ASSIGN_FOOD
                : Packets.CategoryEditRequest.Action.ASSIGN_FOODS);
        setSubmitWaiting(true);
        submit.accept(new Packets.CategoryEditRequest(0, action,
                mode == Mode.MOVE_SUB ? sourceId : selected, mode == Mode.MOVE_SUB ? selected : null,
                null, null, items, expectedVersions));
    }

    public void acceptResult(Packets.CategoryEditResult result) {
        if (result == null) return;
        setSubmitWaiting(false);
        if (result.success()) close();
    }

    public ResourceLocation selectedTarget() { return selected; }

    private void refresh() {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        visible = allTargets.stream().filter(category -> !category.hidden())
                .filter(category -> mode == Mode.ASSIGN_FOOD
                        ? category.parent() != null && !category.protectedEntry()
                        : category.parent() == null)
                .filter(category -> q.isEmpty() || PinyinMatch.contains(displayName(category), q)
                        || PinyinMatch.contains(parentName(category), q)
                        || category.id().toString().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(this::parentName)
                        .thenComparing(c -> c.parent() == null ? "" : c.parent().toString())
                        .thenComparing(TargetPickerModal::displayName)
                        .thenComparing(c -> c.id().toString()))
                .toList();
        List<Row> next = new ArrayList<>();
        if (mode != Mode.ASSIGN_FOOD) {
            for (Packets.CategoryMetadata category : visible) next.add(new Row(category, false, 0));
        } else {
            Map<ResourceLocation, List<Packets.CategoryMetadata>> groups = new LinkedHashMap<>();
            for (Packets.CategoryMetadata category : visible)
                groups.computeIfAbsent(category.parent(), unused -> new ArrayList<>()).add(category);
            boolean searching = !q.isEmpty();
            for (Map.Entry<ResourceLocation, List<Packets.CategoryMetadata>> group : groups.entrySet()) {
                Packets.CategoryMetadata root = find(group.getKey());
                if (root != null) next.add(new Row(root, true, group.getValue().size()));
                if (root != null && !searching && collapsed.contains(root.id())) continue;
                for (Packets.CategoryMetadata category : group.getValue()) next.add(new Row(category, false, 0));
            }
        }
        rows = List.copyOf(next);
    }

    private Packets.CategoryMetadata find(ResourceLocation id) {
        // Implicit subcategories share their root's id; parent lookups want the root entry.
        return allTargets.stream().filter(category -> category.id().equals(id) && category.parent() == null)
                .findFirst().orElseGet(() -> allTargets.stream().filter(category -> category.id().equals(id))
                        .findFirst().orElse(null));
    }

    private String parentName(Packets.CategoryMetadata category) {
        Packets.CategoryMetadata parent = category.parent() == null ? null : find(category.parent());
        return parent == null ? "" : displayName(parent);
    }

    private static void renderCategoryIcon(GuiGraphics g, Packets.CategoryMetadata category, int x, int y) {
        if (category.icon() == null) { PixelIcons.draw(g, PixelIcons.Icon.QUESTION, x + 4, y + 4, JournalTheme.TEXT_FAINT); return; }
        Item item = BuiltInRegistries.ITEM.get(category.icon());
        if (item != null && item != BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", "air"))) {
            g.renderItem(new ItemStack(item), x, y);
        } else PixelIcons.draw(g, PixelIcons.Icon.QUESTION, x + 4, y + 4, JournalTheme.TEXT_FAINT);
    }

    private static String displayName(Packets.CategoryMetadata category) {
        return category.literalTitle() ? category.titleKey() : Component.translatable(category.titleKey()).getString();
    }
}
