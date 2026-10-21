package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.FlavorClientState;
import com.chi__you.cuisinejournal.client.journal.FlavorUi;
import com.chi__you.cuisinejournal.client.journal.JournalSearchField;
import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.network.Network;
import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.recipe.FlavorModifiers;
import com.chi__you.cuisinejournal.recipe.FlavorPresets;
import com.chi__you.cuisinejournal.recipe.FlavorStage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The flavor library: every preset on the left, the selected one's editor on the right
 * (icon, name, description, enabled, up to six star stages, each with potion effects as a
 * share of the food's duration and three groups of numeric modifiers).
 */
public final class FlavorLibraryModal extends JournalModal {
    private static final int LIST_W = 92, ROW = 12;
    private static final String[] MOD_KEYS = {"health", "health_pct", "regen", "regen_pct", "duration", "duration_pct", "drink_bonus_pct"};
    /**
     * Fields each target group edits: 通用 all of them; 仅食物 health/regen/duration; 仅饮品
     * duration and the drink bonus (drinks have no health or regen).
     */
    private static final int[][] GROUP_FIELDS = {{0, 1, 2, 3, 4, 5, 6}, {0, 1, 2, 3, 4, 5}, {4, 5, 6}};
    /** Step per click; shift multiplies by ten. Health / regen are stored in points (2 = one heart). */
    private static final double[] MOD_STEP = {1, 0.05, 0.1, 0.05, 5, 0.05, 0.05};

    private enum Mode { EDIT, ICON, EFFECT }

    private static final class EffectDraft {
        ResourceLocation id; int level; float fraction; int seconds;
        EffectDraft(ResourceLocation id, int level, float fraction, int seconds) { this.id = id; this.level = level; this.fraction = fraction; this.seconds = seconds; }
    }
    private static final class StageDraft {
        String effectTarget = "all";
        int minStars; final List<EffectDraft> effects = new ArrayList<>(); final double[][] mods = new double[3][7];
        final List<FlavorStage.EffectLevelBonus> effectLevelBonuses = new ArrayList<>();
    }
    private record Hit(int x, int y, int w, int h, Runnable action, EditBox field) {
        Hit(int x, int y, int w, int h, Runnable action) { this(x, y, w, h, action, null); }
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final Font font;
    private final IntConsumer requestStarted;
    private final Runnable openExport;
    private final List<Hit> hits = new ArrayList<>();
    private final EditBox name, description, effectSearch, groupBox;
    /** Groups folded in the list (session only); "" is the ungrouped section. */
    private static final java.util.Set<String> FOLDED = new java.util.HashSet<>();
    private final IconPicker icons;
    private final JournalButton footer;

    private ResourceLocation selectedId;
    private boolean creating;
    private String automaticName;
    private boolean automaticIcon;
    private ResourceLocation iconEffect;
    private ResourceLocation icon;
    private boolean enabled = true;
    private final List<StageDraft> stages = new ArrayList<>();
    private int stage, group, effectIndex;
    /** The effect picker is choosing for a level bonus row (else for a potion effect row). */
    private boolean pickingBonus;
    private Mode mode = Mode.EDIT;
    private String original = "";
    private boolean discardArmed;
    private int pendingRequest;
    private long seenRevision = -1;
    private java.util.Map<String, Long> expectedVersions = java.util.Map.of();

    public FlavorLibraryModal(Font font, ResourceLocation initial, IntConsumer requestStarted, Runnable openExport) {
        super(0, 0, 420, 300, Component.translatable("cuisinejournal.flavor.library.title"));
        this.font = font;
        this.requestStarted = requestStarted;
        this.openExport = openExport;
        name = box(48); description = box(256); effectSearch = box(64); groupBox = box(32);
        effectSearch.setResponder(value -> resetScroll());
        icons = new IconPicker(font, 0, 0, 120).withEffects();
        footer = new JournalButton(Component.empty(), JournalButton.Variant.DANGER, this::footerAction);
        setSubmitAction(this::save);
        if (initial != null && FlavorClientState.get(initial) != null) select(initial);
        else startNew();
    }

    private EditBox box(int max) {
        EditBox value = new EditBox(font, 0, 0, 60, 9, Component.empty());
        value.setBordered(false); value.setMaxLength(max); value.setTextColor(JournalTheme.TEXT);
        return value;
    }

    @Override protected int maxModalWidth() { return 360; }
    @Override protected int maxModalHeight() { return 260; }
    @Override protected Component submitLabel() { return Component.translatable("cuisinejournal.flavor.library.save"); }
    @Override protected Component cancelLabel() { return Component.translatable("cuisinejournal.edit.close"); }
    @Override protected boolean onBack() {
        if (mode != Mode.EFFECT) return false;
        mode = Mode.EDIT;
        focus(null);
        resetScroll();
        hits.clear();
        refreshButtons();
        return true;
    }
    @Override protected boolean submitEnabled() { return pendingRequest == 0 && (creating || dirty()) && !name.getValue().isBlank(); }
    @Override protected JournalButton footerButton() {
        byte origin = origin();
        if (creating || origin == 0) return null;
        footer.setLabel(Component.translatable(origin == 2 ? "cuisinejournal.flavor.library.delete" : "cuisinejournal.flavor.library.revert"));
        return footer;
    }

    private byte origin() {
        FlavorClientState.Entry entry = FlavorClientState.get(selectedId);
        return entry == null ? 0 : entry.origin();
    }

    // ------------------------------------------------------------------ draft

    private void select(ResourceLocation id) {
        expectedVersions = com.chi__you.cuisinejournal.client.EditClientState.snapshot();
        resetScroll(); focus(null);
        selectedId = id; creating = false; mode = Mode.EDIT; stage = 0; group = 0; discardArmed = false;
        FlavorClientState.Entry entry = FlavorClientState.get(id);
        load(entry == null ? null : entry.preset());
    }

    private void startNew() {
        expectedVersions = com.chi__you.cuisinejournal.client.EditClientState.snapshot();
        // A new flavor starts in the same group as the one being viewed.
        String keepGroup = groupBox.getValue().strip();
        resetScroll(); focus(null);
        selectedId = null; creating = true; mode = Mode.EDIT; stage = 0; group = 0; discardArmed = false;
        load(null);
        name.setValue(Component.translatable("cuisinejournal.flavor.library.new_name").getString());
        automaticName = name.getValue(); automaticIcon = true;
        groupBox.setValue(keepGroup);
        StageDraft first = new StageDraft(); first.minStars = 0; stages.add(first);
        original = json();
        refreshButtons();
    }

    private void load(FlavorPresets.Preset preset) {
        automaticName = null; automaticIcon = false;
        stages.clear();
        name.setValue(preset == null ? "" : preset.name());
        description.setValue(preset == null ? "" : preset.description());
        groupBox.setValue(preset == null ? "" : preset.group());
        icon = preset == null ? null : preset.icon();
        iconEffect = preset == null ? null : preset.iconEffect();
        enabled = preset == null || preset.enabled();
        if (preset != null) for (FlavorStage s : preset.stages()) {
            StageDraft d = new StageDraft(); d.minStars = s.minStars();
            d.effectTarget = s.effectTarget();
            d.effectLevelBonuses.addAll(s.effectLevelBonuses());
            for (FlavorStage.Effect e : s.effects()) d.effects.add(new EffectDraft(e.id(), e.level(), e.activeFraction(), e.durationSeconds()));
            put(d.mods[0], s.modifiers()); put(d.mods[1], s.foodModifiers()); put(d.mods[2], s.drinkModifiers());
            stages.add(d);
        }
        if (!stages.isEmpty()) group = targetGroup(stages.get(stage).effectTarget);
        original = preset == null ? "" : json();
        refreshButtons();
    }

    private static void put(double[] target, FlavorModifiers m) {
        target[0] = m.healthPoints(); target[1] = m.healthMultiplier(); target[2] = m.regenPerSecond();
        target[3] = m.regenMultiplier(); target[4] = m.durationSeconds(); target[5] = m.durationMultiplier();
        target[6] = m.drinkBonusMultiplier();
    }

    private void updateEffectDefaults() {
        if (!creating) return;
        ResourceLocation first = stages.stream().flatMap(value -> value.effects.stream())
                .map(value -> value.id).findFirst().orElse(null);
        if (first == null) return;
        if (automaticName != null && name.getValue().equals(automaticName)) {
            automaticName = FlavorUi.effectName(first).getString();
            name.setValue(automaticName);
            automaticName = name.getValue();
        } else automaticName = null;
        if (automaticIcon) { icon = null; iconEffect = first; }
    }

    private static FlavorModifiers mods(double[] v) { return new FlavorModifiers(v[0], v[1], v[2], v[3], v[4], v[5], v[6]); }

    /** Only the fields a group shows are saved, so a hidden value can never linger in the preset. */
    private static FlavorModifiers mods(double[] v, int group) {
        double[] kept = new double[7];
        for (int field : GROUP_FIELDS[group]) kept[field] = v[field];
        return mods(kept);
    }

    /** Move the currently edited target-specific values without dropping fields the new target cannot use. */
    static void moveGroupValues(double[][] values, int from, int to) {
        if (values == null || from < 0 || from >= GROUP_FIELDS.length || to < 0
                || to >= GROUP_FIELDS.length || from == to) return;
        for (int field : GROUP_FIELDS[from]) {
            boolean supported = false;
            for (int targetField : GROUP_FIELDS[to]) if (targetField == field) {
                supported = true;
                break;
            }
            if (!supported || Math.abs(values[from][field]) < 1e-9) continue;
            values[to][field] = values[from][field];
            values[from][field] = 0;
        }
    }

    private void selectGroup(int next) {
        if (next == group) return;
        moveGroupValues(stages.get(stage).mods, group, next);
        stages.get(stage).effectTarget = next == 1 ? "food" : next == 2 ? "drink" : "all";
        group = next;
    }

    private static int targetGroup(String target) {
        return target.equals("food") ? 1 : target.equals("drink") ? 2 : 0;
    }

    /** The draft as preset JSON (what the server stores). */
    private String json() {
        List<FlavorStage> built = new ArrayList<>();
        for (StageDraft d : stages) {
            List<FlavorStage.Effect> effects = new ArrayList<>();
            for (EffectDraft e : d.effects) effects.add(new FlavorStage.Effect(e.id, e.level, Math.max(1, e.seconds), e.fraction));
            built.add(new FlavorStage(d.minStars, effects, mods(d.mods[0], 0), mods(d.mods[1], 1), mods(d.mods[2], 2), d.effectLevelBonuses, d.effectTarget));
        }
        built.sort(Comparator.comparingInt(FlavorStage::minStars));
        ResourceLocation id = selectedId == null ? ResourceLocation.fromNamespaceAndPath(FlavorPresets.CUSTOM_NAMESPACE, "draft") : selectedId;
        return new FlavorPresets.Preset(id, name.getValue().strip(), description.getValue().strip(), enabled, built, icon, iconEffect,
                groupBox.getValue().strip()).toJson().toString();
    }

    /** Unsaved changes: only real edits count (a fresh "新建" draft is not dirty until changed). */
    private boolean dirty() {
        if (!creating && selectedId == null) return false;
        try { return !json().equals(original); } catch (RuntimeException invalid) { return false; }
    }

    // ------------------------------------------------------------------ actions

    private void save() {
        if (!submitEnabled()) return;
        String body;
        try { body = json(); } catch (RuntimeException invalid) { return; }
        send(Packets.FlavorEditRequest.Action.SAVE_PRESET, creating ? null : selectedId, body);
    }

    private void footerAction() {
        if (selectedId == null || pendingRequest != 0) return;
        send(origin() == 2 ? Packets.FlavorEditRequest.Action.DELETE_PRESET : Packets.FlavorEditRequest.Action.REVERT_PRESET, selectedId, "");
    }

    private void send(Packets.FlavorEditRequest.Action action, ResourceLocation target, String json) {
        pendingRequest = FlavorClientState.nextRequestId();
        setSubmitWaiting(true);
        String key = com.chi__you.cuisinejournal.network.EditVersions.preset(target);
        var versions = target == null ? java.util.Map.<String, Long>of() : java.util.Map.of(key, expectedVersions.getOrDefault(key, 0L));
        Network.toServer(new Packets.FlavorEditRequest(pendingRequest, action, target, false, false, List.of(), json, 0, versions));
        if (requestStarted != null) requestStarted.accept(pendingRequest);
    }

    public int pendingRequest() { return pendingRequest; }

    /** Host passes the server's answer for {@link #pendingRequest()}. */
    public void accept(Packets.FlavorEditResult result) {
        pendingRequest = 0;
        setSubmitWaiting(false);
        if (!result.success()) return;
        if (result.created() != null) { selectedId = result.created(); creating = false; }
        // The preset sync follows the result; reload the draft from it on the next frame.
        seenRevision = -1;
        pendingReload = true;
    }

    private boolean pendingReload;

    @Override protected void onTick() {
        if (name.isFocused()) name.tick();
        if (description.isFocused()) description.tick();
        if (effectSearch.isFocused()) effectSearch.tick();
        icons.tick();
        if (pendingReload && seenRevision != FlavorClientState.revision()) {
            seenRevision = FlavorClientState.revision();
            if (selectedId != null && FlavorClientState.get(selectedId) != null) select(selectedId);
            else { List<FlavorClientState.Entry> all = FlavorClientState.sorted(); select(all.isEmpty() ? null : all.get(0).preset().id()); }
            pendingReload = false;
        }
    }

    // ------------------------------------------------------------------ rendering

    /** The flavor list scrolls on its own; the modal's scroll belongs to the editor on the right. */
    private int listScroll, listContent, listViewport;

    @Override protected void renderBody(GuiGraphics g, int bx, int by, int bw, int bh, int mx, int my, float tick) {
        int scroll = scrollOffset();
        by -= scroll;
        hits.clear();
        // Left column: drawn in fixed screen space (undoing the modal's scroll), with its own scroll.
        java.awt.Rectangle body = bodyBounds();
        g.pose().pushPose();
        g.pose().translate(0, scroll, 0);
        renderList(g, bx, body.y, body.height, mx, my - scroll, scroll);
        g.fill(bx + LIST_W + 2, body.y, bx + LIST_W + 3, body.y + body.height, 0x33FFE9C7);
        g.pose().popPose();
        int rx = bx + LIST_W + 6, rw = bw - LIST_W - 6;
        int end;
        if (selectedId == null && !creating) {
            g.drawString(font, Component.translatable("cuisinejournal.flavor.library.empty"), rx, by + 4, JournalTheme.TEXT_FAINT, false);
            end = by + 20;
        } else if (mode == Mode.ICON) end = renderIconPicker(g, rx, by, rw, mx, my);
        else if (mode == Mode.EFFECT) end = renderEffectPicker(g, rx, by, rw, mx, my);
        else end = renderEditor(g, rx, by, rw, mx, my);
        setScrollContentHeight(end - by + 4);
    }

    /**
     * Left column in screen coordinates: "＋ 新建风味" and "❖ 方案…" pinned at the top, then the
     * flavor list in its own clipped, scrollable area. Hit boxes are stored in the modal's
     * scrolled space (screen y + {@code scroll}) like every other hit.
     */
    private void renderList(GuiGraphics g, int bx, int top, int height, int mx, int my, int scroll) {
        int cy = top;
        cy = linkRow(g, bx, cy, mx, my, scroll, Component.translatable("cuisinejournal.flavor.library.create"), JournalTheme.TEXT_ACCENT,
                () -> trySelect(this::startNew));
        cy = linkRow(g, bx, cy, mx, my, scroll, Component.translatable("cuisinejournal.scheme.open"), JournalTheme.TEXT_DIM,
                () -> { if (openExport != null) { close(); openExport.run(); } });
        if (discardArmed) {
            for (var line : font.split(Component.translatable("cuisinejournal.flavor.library.discard"), LIST_W)) {
                g.drawString(font, line, bx, cy + 2, JournalTheme.DANGER, false); cy += 10;
            }
        }
        cy += 2;
        g.fill(bx, cy, bx + LIST_W, cy + 1, 0x33FFE9C7);
        cy += 3;
        int listTop = cy, bottom = top + height;
        listViewport = Math.max(0, bottom - listTop);
        // Rows: each named group gets a header "▾ 名称  N" (click folds it), then its flavors;
        // flavors without a group follow under no header (or "未分组" once groups exist).
        java.util.TreeMap<String, List<FlavorClientState.Entry>> grouped = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<FlavorClientState.Entry> loose = new ArrayList<>();
        for (FlavorClientState.Entry entry : FlavorClientState.sorted()) {
            String group = entry.preset().group();
            if (group.isEmpty()) loose.add(entry); else grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(entry);
        }
        List<Object> rows = new ArrayList<>();
        grouped.forEach((group, list) -> { rows.add(group); if (!FOLDED.contains(group)) rows.addAll(list); });
        if (!loose.isEmpty()) {
            if (!grouped.isEmpty()) { rows.add(""); if (!FOLDED.contains("")) rows.addAll(loose); }
            else rows.addAll(loose);
        }
        listContent = rows.size() * ROW;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, listContent - listViewport)));
        boolean overflow = listContent > listViewport;
        int rowW = overflow ? LIST_W - 3 : LIST_W;
        g.enableScissor(bx, listTop, bx + LIST_W, bottom);
        int ry = listTop - listScroll;
        for (Object row : rows) {
            if (ry + ROW > listTop && ry < bottom) {
                int hitTop = Math.max(ry, listTop), hitBottom = Math.min(ry + ROW, bottom);
                boolean hover = my >= listTop && my < bottom && mx >= bx && mx < bx + rowW && my >= ry && my < ry + ROW;
                if (row instanceof String group) {
                    boolean folded = FOLDED.contains(group);
                    int count = group.isEmpty() ? loose.size() : grouped.get(group).size();
                    if (hover) g.fill(bx, ry, bx + rowW, ry + ROW, 0x14FFE9C7);
                    g.drawString(font, folded ? "\u25b8" : "\u25be", bx + 2, ry + 2, hover ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT, false);
                    String label = group.isEmpty() ? Component.translatable("cuisinejournal.flavor.library.ungrouped").getString() : group;
                    String n = Integer.toString(count);
                    int nw = font.width(n);
                    g.drawString(font, font.plainSubstrByWidth(label, rowW - 14 - nw - 4), bx + 11, ry + 2,
                            hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, false);
                    g.drawString(font, n, bx + rowW - nw - 1, ry + 2, JournalTheme.TEXT_FAINT, false);
                    hits.add(new Hit(bx, hitTop + scroll, rowW, hitBottom - hitTop,
                            () -> { if (!FOLDED.remove(group)) FOLDED.add(group); }));
                } else {
                    FlavorClientState.Entry entry = (FlavorClientState.Entry) row;
                    // Flavors inside a group are indented under its header.
                    int indent = entry.preset().group().isEmpty() && grouped.isEmpty() ? 0 : 6;
                    boolean on = !creating && entry.preset().id().equals(selectedId);
                    if (on) { g.fill(bx, ry, bx + rowW, ry + ROW, 0x29E8A93C); g.fill(bx, ry, bx + 2, ry + ROW, JournalTheme.TEXT_ACCENT); }
                    else if (hover) g.fill(bx, ry, bx + rowW, ry + ROW, 0x14FFE9C7);
                    FlavorUi.icon(g, entry.preset(), bx + 4 + indent, ry + 1, 10);
                    int color = !entry.preset().enabled() ? JournalTheme.TEXT_FAINT : on ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT;
                    String mark = entry.origin() == 1 ? "•" : entry.origin() == 2 ? "+" : "";
                    int markW = mark.isEmpty() ? 0 : font.width(mark) + 2;
                    g.drawString(font, font.plainSubstrByWidth(entry.preset().name(), rowW - 18 - indent - markW), bx + 17 + indent, ry + 2, color, false);
                    if (!mark.isEmpty()) g.drawString(font, mark, bx + rowW - markW, ry + 2,
                            entry.origin() == 2 ? JournalTheme.TEXT_GOOD : JournalTheme.ADJUSTED, false);
                    ResourceLocation id = entry.preset().id();
                    hits.add(new Hit(bx, hitTop + scroll, rowW, hitBottom - hitTop, () -> trySelect(() -> select(id))));
                }
            }
            ry += ROW;
        }
        g.disableScissor();
        if (overflow) {
            int trackX = bx + LIST_W - 2;
            g.fill(trackX, listTop, trackX + 2, bottom, 0x662A2018);
            int thumb = Math.max(8, listViewport * listViewport / Math.max(1, listContent));
            int thumbY = listTop + (listViewport - thumb) * listScroll / Math.max(1, listContent - listViewport);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumb, JournalTheme.TEXT_FAINT);
        }
    }

    private boolean overList(double mx, double screenY) {
        java.awt.Rectangle body = bodyBounds();
        return mx >= body.x && mx < body.x + LIST_W + 3 && screenY >= body.y && screenY < body.y + body.height;
    }

    private int linkRow(GuiGraphics g, int x, int y, int mx, int my, int scroll, Component text, int color, Runnable action) {
        boolean hover = mx >= x && mx < x + LIST_W && my >= y && my < y + ROW;
        if (hover) g.fill(x, y, x + LIST_W, y + ROW, 0x14FFE9C7);
        g.drawString(font, font.plainSubstrByWidth(text.getString(), LIST_W - 4), x + 3, y + 2, hover ? JournalTheme.TEXT : color, false);
        hits.add(new Hit(x, y + scroll, LIST_W, ROW, action));
        return y + ROW;
    }


    /** Leaving unsaved changes needs a second click. */
    private void trySelect(Runnable change) {
        if (pendingRequest != 0) return;
        if (dirty() && !discardArmed) { discardArmed = true; return; }
        discardArmed = false;
        change.run();
        refreshButtons();
    }

    private int renderEditor(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int right = x + w;
        // Icon + name
        boolean iconHover = mx >= x && mx < x + 14 && my >= y && my < y + 14;
        g.fill(x, y, x + 14, y + 14, 0xFF0A0705);
        JournalTheme.border(g, x, y, 14, 14, iconHover ? JournalTheme.TEXT_DIM : 0xFF4A3A2A);
        FlavorUi.icon(g, previewPreset(), x + 1, y + 1, 12);
        hits.add(new Hit(x, y, 14, 14, () -> {
            mode = Mode.ICON;
            if (iconEffect != null) icons.setSelectedEffect(iconEffect); else icons.setSelectedIcon(icon);
        }));
        field(g, name, x + 18, y + 1, w - 18, Component.translatable("cuisinejournal.flavor.library.name_hint"), mx, my);
        y += 17;
        int groupW = Math.min(70, w / 3);
        field(g, description, x, y, w - groupW - 4, Component.translatable("cuisinejournal.flavor.library.desc_hint"), mx, my);
        field(g, groupBox, x + w - groupW, y, groupW, Component.translatable("cuisinejournal.flavor.library.group_hint"), mx, my);
        y += 15;
        // id, origin, enabled
        String id = creating ? Component.translatable("cuisinejournal.flavor.library.auto_id").getString()
                : selectedId == null ? "" : selectedId.toString();
        small(g, font.plainSubstrByWidth(id, (int) ((w - 50) / 0.75F)), x, y + 2, JournalTheme.TEXT_FAINT);
        toggle(g, right - 44, y, enabled, Component.translatable("cuisinejournal.flavor.library.enabled"), mx, my, () -> enabled = !enabled);
        y += 12;
        // Stage tabs
        int tx = x;
        stages.sort(Comparator.comparingInt(s -> s.minStars));
        stage = Math.max(0, Math.min(stage, stages.size() - 1));
        for (int i = 0; i < stages.size(); i++) {
            String label = "★" + stages.get(i).minStars;
            int tw = font.width(label) + 6;
            boolean on = i == stage;
            boolean hover = mx >= tx && mx < tx + tw && my >= y && my < y + 11;
            g.fill(tx, y, tx + tw, y + 11, on ? 0x33E8A93C : hover ? 0x14FFE9C7 : 0);
            JournalTheme.border(g, tx, y, tw, 11, on ? JournalTheme.TEXT_ACCENT : 0xFF3A2C1F);
            g.drawString(font, label, tx + 3, y + 2, on ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_DIM, false);
            int index = i;
            hits.add(new Hit(tx, y, tw, 11, () -> { stage = index; group = targetGroup(stages.get(stage).effectTarget); }));
            tx += tw + 2;
        }
        if (stages.size() < 6) {
            boolean hover = mx >= tx && mx < tx + 11 && my >= y && my < y + 11;
            JournalTheme.border(g, tx, y, 11, 11, hover ? JournalTheme.TEXT_ACCENT : 0xFF3A2C1F);
            g.drawString(font, "+", tx + 3, y + 2, JournalTheme.TEXT_ACCENT, false);
            hits.add(new Hit(tx, y, 11, 11, this::addStage));
        }
        y += 14;
        if (stages.isEmpty()) {
            small(g, Component.translatable("cuisinejournal.flavor.library.no_stage").getString(), x, y, JournalTheme.TEXT_FAINT);
            return y + 12;
        }
        StageDraft current = stages.get(stage);
        // Stage start + remove
        small(g, Component.translatable("cuisinejournal.flavor.library.stage_from").getString(), x, y + 2, JournalTheme.TEXT_DIM);
        int sx = x + (int) (font.width(Component.translatable("cuisinejournal.flavor.library.stage_from")) * 0.75F) + 4;
        stepper(g, sx, y, "★" + current.minStars, mx, my, d -> {
            int next = Math.max(0, Math.min(5, current.minStars + (int) Math.signum(d)));
            if (stages.stream().noneMatch(s -> s != current && s.minStars == next)) current.minStars = next;
        });
        if (stages.size() > 1) {
            String remove = Component.translatable("cuisinejournal.flavor.library.remove_stage").getString();
            int rw2 = (int) (font.width(remove) * 0.75F);
            boolean hover = mx >= right - rw2 && mx < right && my >= y && my < y + 10;
            small(g, remove, right - rw2, y + 2, hover ? JournalTheme.DANGER : JournalTheme.TEXT_FAINT);
            hits.add(new Hit(right - rw2, y, rw2, 10, () -> { stages.remove(current); stage = Math.max(0, stage - 1); updateEffectDefaults(); }));
        }
        y += 13;
        // Potion effects
        // Header actions: "＋ 效果" gives a potion effect; "＋ 等级加成" raises an effect the food
        // (or another flavor) already gives by N levels.
        {
            Component title = Component.translatable("cuisinejournal.flavor.library.effects");
            g.drawString(font, title, x, y, JournalTheme.TEXT_DIM, false);
            int ax = right;
            if (current.effectLevelBonuses.size() < 8) {
                Component addBonus = Component.translatable("cuisinejournal.flavor.library.add_bonus");
                int bw = font.width(addBonus);
                ax -= bw;
                boolean hov = mx >= ax && mx < ax + bw && my >= y && my < y + 10;
                g.drawString(font, addBonus, ax, y, hov ? JournalTheme.TEXT : JournalTheme.ADJUSTED, false);
                hits.add(new Hit(ax, y, bw, 10, () -> {
                    // Default to strength (the usual case), else the first effect not used yet.
                    ResourceLocation chosen = ResourceLocation.fromNamespaceAndPath("minecraft", "strength");
                    if (current.effectLevelBonuses.stream().anyMatch(b -> b.id().equals(ResourceLocation.fromNamespaceAndPath("minecraft", "strength")))) {
                        chosen = BuiltInRegistries.MOB_EFFECT.keySet().stream().sorted()
                                .filter(c -> current.effectLevelBonuses.stream().noneMatch(b -> b.id().equals(c)))
                                .findFirst().orElse(null);
                        if (chosen == null) return;
                    }
                    current.effectLevelBonuses.add(new FlavorStage.EffectLevelBonus(chosen, 1));
                }));
                ax -= 8;
            }
            if (current.effects.size() < 8) {
                Component add = Component.translatable("cuisinejournal.flavor.library.add");
                int aw = font.width(add);
                ax -= aw;
                boolean hov = mx >= ax && mx < ax + aw && my >= y && my < y + 10;
                g.drawString(font, add, ax, y, hov ? JournalTheme.TEXT : JournalTheme.TEXT_ACCENT, false);
                hits.add(new Hit(ax, y, aw, 10, () -> {
                    current.effects.add(new EffectDraft(ResourceLocation.fromNamespaceAndPath("minecraft", "speed"), 1, 1.0F, 30));
                    updateEffectDefaults();
                }));
                ax -= 4;
            }
            int lineX = x + font.width(title) + 4;
            if (ax > lineX) g.fill(lineX, y + 4, ax, y + 5, 0x33FFE9C7);
            y += 12;
        }
        if (current.effects.isEmpty() && current.effectLevelBonuses.isEmpty()) {
            small(g, Component.translatable("cuisinejournal.flavor.library.no_effects").getString(), x, y, JournalTheme.TEXT_FAINT);
            y += 10;
        }
        boolean vanilla = ClientJournalState.vanillaFood();
        for (int i = 0; i < current.effects.size(); i++) {
            EffectDraft e = current.effects.get(i);
            int ex = x;
            Component effectName = FlavorUi.effectName(e.id);
            int nameW = Math.min(64, font.width(effectName) + 10);
            boolean hover = mx >= ex && mx < ex + nameW && my >= y && my < y + 11;
            g.fill(ex, y, ex + nameW, y + 11, hover ? 0x21FFE9C7 : 0x26000000);
            JournalTheme.border(g, ex, y, nameW, 11, 0xFF3A2C1F);
            g.drawString(font, font.plainSubstrByWidth(effectName.getString(), nameW - 9), ex + 2, y + 2, JournalTheme.TEXT, false);
            g.drawString(font, "▾", ex + nameW - 7, y + 2, JournalTheme.TEXT_FAINT, false);
            int index = i;
            hits.add(new Hit(ex, y, nameW, 11, () -> {
                mode = Mode.EFFECT; effectIndex = index; pickingBonus = false; effectSearch.setValue("");
                resetScroll(); focus(effectSearch);
            }));
            ex += nameW + 3;
            ex = stepper(g, ex, y, FlavorUi.roman(e.level), 20, mx, my, d -> e.level = Math.max(1, Math.min(10, e.level + (int) Math.signum(d)))) + 3;
            if (vanilla) ex = stepper(g, ex, y, e.seconds + "s", 28, mx, my, d -> e.seconds = Math.max(1, Math.min(3600, e.seconds + (int) d * 5))) + 3;
            else ex = stepper(g, ex, y, Math.round(e.fraction * 100) + "%", 28, mx, my,
                    d -> e.fraction = Math.max(0.05F, Math.min(1F, Math.round((e.fraction + d * 0.05F) * 20) / 20F))) + 3;
            boolean xHover = mx >= right - 8 && mx < right && my >= y && my < y + 11;
            g.drawString(font, "×", right - 6, y + 2, xHover ? JournalTheme.DANGER : JournalTheme.TEXT_FAINT, false);
            hits.add(new Hit(right - 8, y, 8, 11, () -> { current.effects.remove(e); updateEffectDefaults(); }));
            y += 13;
        }
        // Level bonus rows: [效果 ▾] [− +N级 +] … ×
        for (int i = 0; i < current.effectLevelBonuses.size(); i++) {
            FlavorStage.EffectLevelBonus bonus = current.effectLevelBonuses.get(i);
            int ex = x;
            Component effectName = FlavorUi.effectName(bonus.id());
            int nameW = Math.min(64, font.width(effectName) + 10);
            boolean hover = mx >= ex && mx < ex + nameW && my >= y && my < y + 11;
            g.fill(ex, y, ex + nameW, y + 11, hover ? 0x21FFE9C7 : 0x26000000);
            JournalTheme.border(g, ex, y, nameW, 11, (JournalTheme.ADJUSTED & 0x00FFFFFF) | 0x80000000);
            g.drawString(font, font.plainSubstrByWidth(effectName.getString(), nameW - 9), ex + 2, y + 2, JournalTheme.TEXT, false);
            g.drawString(font, "▾", ex + nameW - 7, y + 2, JournalTheme.TEXT_FAINT, false);
            int index = i;
            hits.add(new Hit(ex, y, nameW, 11, () -> {
                mode = Mode.EFFECT; effectIndex = index; pickingBonus = true; effectSearch.setValue("");
                resetScroll(); focus(effectSearch);
            }));
            ex += nameW + 3;
            ex = stepper(g, ex, y, Component.translatable("cuisinejournal.flavor.library.bonus_value", bonus.levels()).getString(), 30, mx, my,
                    d -> current.effectLevelBonuses.set(index, new FlavorStage.EffectLevelBonus(bonus.id(),
                            Math.max(1, Math.min(255, bonus.levels() + (int) Math.signum(d)))))) + 4;
            small(g, Component.translatable("cuisinejournal.flavor.library.bonus_tag").getString(), ex, y + 2, JournalTheme.ADJUSTED);
            boolean xHover = mx >= right - 8 && mx < right && my >= y && my < y + 11;
            g.drawString(font, "×", right - 6, y + 2, xHover ? JournalTheme.DANGER : JournalTheme.TEXT_FAINT, false);
            hits.add(new Hit(right - 8, y, 8, 11, () -> current.effectLevelBonuses.remove(index)));
            y += 13;
        }
        small(g, Component.translatable(vanilla ? "cuisinejournal.flavor.library.effects_vanilla"
                : "cuisinejournal.flavor.library.effects_hint").getString(), x, y, JournalTheme.TEXT_FAINT);
        if (!current.effectLevelBonuses.isEmpty()) {
            y += 9;
            small(g, Component.translatable("cuisinejournal.flavor.library.bonus_hint").getString(), x, y, JournalTheme.TEXT_FAINT);
        }
        y += 11;
        // Numeric modifiers
        y = blockHeader(g, x, y, w, Component.translatable("cuisinejournal.flavor.library.modifiers"), null, mx, my, null);
        int gx = x, gw = 0;
        for (int i = 0; i < 3; i++) gw = Math.max(gw, font.width(Component.translatable("cuisinejournal.flavor.library.group." + i)) + 12);
        for (int i = 0; i < 3; i++) {
            Component label = Component.translatable("cuisinejournal.flavor.library.group." + i);
            double[] groupValues = current.mods[i];
            boolean changed = java.util.Arrays.stream(GROUP_FIELDS[i]).anyMatch(f -> Math.abs(groupValues[f]) > 1e-9);
            boolean on = group == i;
            boolean hover = mx >= gx && mx < gx + gw && my >= y && my < y + 11;
            g.fill(gx, y, gx + gw, y + 11, on ? 0x33E8A93C : hover ? 0x14FFE9C7 : 0);
            JournalTheme.border(g, gx, y, gw, 11, on ? JournalTheme.TEXT_ACCENT : 0xFF3A2C1F);
            g.drawString(font, label, gx + (gw - font.width(label) + 1) / 2, y + 2, on ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_DIM, false);
            if (changed) g.fill(gx + gw - 4, y + 2, gx + gw - 2, y + 4, JournalTheme.TEXT_ACCENT);
            int index = i;
            hits.add(new Hit(gx, y, gw, 11, () -> selectGroup(index)));
            gx += gw + 2;
        }
        y += 14;
        double[] values = current.mods[group];
        int colW = (w - 4) / 2;
        int[] fields = GROUP_FIELDS[group];
        for (int n = 0; n < fields.length; n++) {
            int i = fields[n];
            int cx = x + (n % 2) * (colW + 4), cy = y + (n / 2) * 13;
            String label = Component.translatable("cuisinejournal.flavor.library.mod." + MOD_KEYS[i]).getString();
            small(g, font.plainSubstrByWidth(label, (int) ((colW - MOD_VALUE_W - 20) / 0.75F)), cx, cy + 2,
                    Math.abs(values[i]) > 1e-9 ? JournalTheme.TEXT : JournalTheme.TEXT_DIM);
            // Units live in the label, so the stepper only shows a short number.
            int slot = i;
            stepperRight(g, cx + colW, cy, modText(i, values[i]), MOD_VALUE_W, mx, my, d -> {
                double step = MOD_STEP[slot] * (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 10 : 1);
                values[slot] = Math.round((values[slot] + d * step) * 1000) / 1000.0;
            });
        }
        y += (fields.length + 1) / 2 * 13 + 2;
        FlavorClientState.Entry entry = FlavorClientState.get(selectedId);
        if (entry != null && !creating)
            small(g, Component.translatable("cuisinejournal.flavor.used_by", entry.usedBy()).getString(), x, y, JournalTheme.TEXT_DIM);
        return y + 12;
    }

    private static final int MOD_VALUE_W = 30;

    /** Number only; the unit is part of the row label. */
    private static String modText(int index, double value) {
        if (Math.abs(value) < 1e-9) return "0";
        return switch (index) {
            case 0, 2 -> FlavorUi.hearts(value);
            case 4 -> FlavorUi.signed(value);
            default -> FlavorUi.signed(value * 100.0);
        };
    }

    private FlavorPresets.Preset previewPreset() {
        try { return FlavorPresets.read(ResourceLocation.fromNamespaceAndPath(FlavorPresets.CUSTOM_NAMESPACE, "draft"),
                com.google.gson.JsonParser.parseString(json()).getAsJsonObject()); }
        catch (RuntimeException invalid) { return null; }
    }

    private void addStage() {
        // Prefer the star after the highest stage (stronger stages are added on top).
        int highest = stages.stream().mapToInt(st -> st.minStars).max().orElse(-1);
        int next = highest + 1 <= 5 ? highest + 1 : -1;
        for (int candidate = 0; candidate <= 5 && next < 0; candidate++) {
            final int value = candidate;
            if (stages.stream().noneMatch(s -> s.minStars == value)) next = value;
        }
        if (next < 0) return;
        StageDraft d = new StageDraft(); d.minStars = next;
        if (!stages.isEmpty()) {
            // Start from the stage just below so stronger stages are quick to make.
            StageDraft base = stages.get(stages.size() - 1);
            d.effectTarget = base.effectTarget;
            d.effectLevelBonuses.addAll(base.effectLevelBonuses);
            for (EffectDraft e : base.effects) d.effects.add(new EffectDraft(e.id, e.level, e.fraction, e.seconds));
            for (int i = 0; i < 3; i++) d.mods[i] = base.mods[i].clone();
        }
        stages.add(d);
        stages.sort(Comparator.comparingInt(s -> s.minStars));
        stage = stages.indexOf(d);
        group = targetGroup(d.effectTarget);
    }

    private int renderIconPicker(GuiGraphics g, int x, int y, int w, int mx, int my) {
        g.drawString(font, Component.translatable("cuisinejournal.flavor.library.pick_icon"), x, y + 2, JournalTheme.TEXT_DIM, false);
        int bx = x + w;
        bx = smallButton(g, bx, y, Component.translatable("cuisinejournal.flavor.library.done"), true, mx, my, () -> {
            icon = icons.selectedIcon(); iconEffect = icons.selectedEffect(); automaticIcon = false; mode = Mode.EDIT;
        });
        smallButton(g, bx - 4, y, Component.translatable("cuisinejournal.flavor.library.no_icon"), false, mx, my, () -> {
            icons.clearSelection(); icon = null; iconEffect = null; automaticIcon = false; mode = Mode.EDIT;
        });
        icons.setBounds(x, y + 16, w);
        icons.setGridHeight(140);
        icons.render(g, mx, my);
        return y + 16 + 22 + 140;
    }

    /** Right-aligned bordered button ending at {@code right}; returns its left edge. */
    private int smallButton(GuiGraphics g, int right, int y, Component label, boolean primary, int mx, int my, Runnable action) {
        int w = font.width(label) + 10, x = right - w;
        boolean hover = mx >= x && mx < right && my >= y && my < y + 12;
        g.fill(x, y, right, y + 12, primary ? (hover ? 0xFF6B4A1C : 0xFF4A3418) : (hover ? 0xFF2A2018 : 0xFF1B140E));
        JournalTheme.border(g, x, y, w, 12, primary ? JournalTheme.TEXT_ACCENT : hover ? JournalTheme.TEXT_DIM : JournalTheme.PANEL_BORDER);
        g.drawString(font, label, x + 5, y + 2, primary ? JournalTheme.TEXT : hover ? JournalTheme.TEXT : JournalTheme.TEXT_DIM, false);
        hits.add(new Hit(x, y, w, 12, action));
        return x;
    }

    private int renderEffectPicker(GuiGraphics g, int x, int y, int w, int mx, int my) {
        StageDraft draft = stages.isEmpty() ? null : stages.get(stage);
        if (draft == null || effectIndex >= (pickingBonus ? draft.effectLevelBonuses.size() : draft.effects.size())) { mode = Mode.EDIT; return y; }
        EffectDraft target = pickingBonus ? null : draft.effects.get(effectIndex);
        ResourceLocation currentId = pickingBonus ? draft.effectLevelBonuses.get(effectIndex).id() : target.id;
        field(g, effectSearch, x, y, w, Component.translatable("cuisinejournal.flavor.library.search_effect"), mx, my);
        y += 14;
        String query = effectSearch.getValue();
        List<ResourceLocation> ids = BuiltInRegistries.MOB_EFFECT.keySet().stream()
                .filter(id -> EffectSearch.matches(id.toString(), FlavorUi.effectName(id).getString(), query))
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        if (ids.isEmpty()) {
            g.drawString(font, Component.translatable("cuisinejournal.empty.search"), x + 2, y + 2, JournalTheme.TEXT_FAINT, false);
            return y + 12;
        }
        for (ResourceLocation id : ids) {
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + 11;
            boolean on = id.equals(com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(currentId));
            if (on || hover) g.fill(x, y, x + w, y + 11, on ? 0x29E8A93C : 0x14FFE9C7);
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
            FlavorUi.effectIcon(g, effect, x + 1, y + 1, 9);
            g.drawString(font, font.plainSubstrByWidth(FlavorUi.effectName(id).getString(), w - 14), x + 12, y + 2,
                    on ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT, false);
            hits.add(new Hit(x, y, w, 11, () -> {
                if (pickingBonus) {
                    FlavorStage.EffectLevelBonus old = draft.effectLevelBonuses.get(effectIndex);
                    boolean duplicate = draft.effectLevelBonuses.stream().anyMatch(b -> b != old && com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(b.id()).equals(id));
                    if (!duplicate) draft.effectLevelBonuses.set(effectIndex, new FlavorStage.EffectLevelBonus(id, old.levels()));
                } else {
                    boolean duplicate = draft.effects.stream().anyMatch(e -> e != target && com.chi__you.cuisinejournal.food.CuisineEffectIds.resolve(e.id).equals(id));
                    if (!duplicate) { target.id = id; updateEffectDefaults(); }
                }
                mode = Mode.EDIT;
                resetScroll(); focus(null);
            }));
            y += 11;
        }
        return y;
    }

    // ------------------------------------------------------------------ widgets

    private void field(GuiGraphics g, EditBox box, int x, int y, int w, Component hint, int mx, int my) {
        JournalSearchField.bounds(box, x, y, w, 12);
        boolean focused = box.isFocused();
        g.fill(x, y, x + w, y + 12, focused ? 0xFF050302 : 0xFF0A0705);
        JournalTheme.border(g, x, y, w, 12, focused ? JournalTheme.TEXT_ACCENT : 0xFF4A3A2A);
        box.render(g, mx, my, 0F);
        if (box.getValue().isEmpty())
            g.drawString(font, font.plainSubstrByWidth(hint.getString(), w - 6), x + 3, y + 2, JournalTheme.TEXT_FAINT, false);
        hits.add(new Hit(x, y, w, 12, () -> focus(box), box));
    }

    private void focus(EditBox target) {
        for (EditBox box : List.of(name, description, effectSearch, groupBox)) box.setFocused(box == target);
        icons.setFocused(false);
    }

    private void small(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(0.75F, 0.75F, 1F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void toggle(GuiGraphics g, int x, int y, boolean on, Component label, int mx, int my, Runnable action) {
        g.fill(x, y + 1, x + 14, y + 8, on ? 0x66E8A93C : 0xFF120D09);
        JournalTheme.border(g, x, y + 1, 14, 7, on ? JournalTheme.TEXT_ACCENT : 0xFF3A2C1F);
        int knob = on ? x + 8 : x + 1;
        g.fill(knob, y + 2, knob + 5, y + 7, on ? JournalTheme.TEXT_ACCENT : JournalTheme.TEXT_FAINT);
        small(g, label.getString(), x + 17, y + 2, on ? JournalTheme.TEXT : JournalTheme.TEXT_DIM);
        hits.add(new Hit(x, y, 44, 10, action));
    }

    /** {@code − value +}; returns the x after it. */
    private int stepper(GuiGraphics g, int x, int y, String value, int mx, int my, java.util.function.DoubleConsumer change) {
        return stepper(g, x, y, value, 18, mx, my, change);
    }

    /** Fixed value width so − and + stay put while the number changes (hold the mouse still and click). */
    private int stepper(GuiGraphics g, int x, int y, String value, int valueWidth, int mx, int my, java.util.function.DoubleConsumer change) {
        int vw = Math.max(valueWidth, font.width(value) + 4), w = vw + 16;
        g.fill(x, y, x + w, y + 11, 0xFF0A0705);
        JournalTheme.border(g, x, y, w, 11, 0xFF3A2C1F);
        boolean minus = mx >= x && mx < x + 8 && my >= y && my < y + 11, plus = mx >= x + w - 8 && mx < x + w && my >= y && my < y + 11;
        g.drawString(font, "−", x + 2, y + 2, minus ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT, false);
        g.drawString(font, "+", x + w - 7, y + 2, plus ? JournalTheme.TEXT : JournalTheme.TEXT_FAINT, false);
        g.drawString(font, value, x + 8 + (vw - font.width(value)) / 2, y + 2, JournalTheme.TEXT, false);
        hits.add(new Hit(x, y, 8, 11, () -> change.accept(-1)));
        hits.add(new Hit(x + w - 8, y, 8, 11, () -> change.accept(1)));
        return x + w;
    }

    private void stepperRight(GuiGraphics g, int right, int y, String value, int valueWidth, int mx, int my, java.util.function.DoubleConsumer change) {
        stepper(g, right - valueWidth - 16, y, value, valueWidth, mx, my, change);
    }

    private int blockHeader(GuiGraphics g, int x, int y, int w, Component title, Component action, int mx, int my, Runnable run) {
        g.drawString(font, title, x, y, JournalTheme.TEXT_DIM, false);
        int lineX = x + font.width(title) + 4, lineEnd = x + w;
        if (action != null) {
            int aw = font.width(action);
            boolean hover = mx >= x + w - aw && mx < x + w && my >= y && my < y + 10;
            g.drawString(font, action, x + w - aw, y, hover ? JournalTheme.TEXT : JournalTheme.TEXT_ACCENT, false);
            hits.add(new Hit(x + w - aw, y, aw, 10, run));
            lineEnd -= aw + 4;
        }
        if (lineEnd > lineX) g.fill(lineX, y + 4, lineEnd, y + 5, 0x33FFE9C7);
        return y + 12;
    }

    // ------------------------------------------------------------------ input

    @Override protected boolean bodyClickedFirst(double mx, double my, int button) {
        if (button != 0 || pendingRequest != 0) return pendingRequest != 0;
        if (mode == Mode.ICON && icons.mouseClicked(mx, my, button)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) continue;
            EditBox box = hit.field();
            if (box == null) focus(null);
            hit.action().run();
            if (box != null) box.mouseClicked(Math.max(box.getX(), Math.min(box.getX() + box.getWidth() - 1, mx)), box.getY() + 1, button);
            else com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.click();
            refreshButtons();
            return true;
        }
        focus(null);
        return false;
    }

    @Override protected boolean bodyScrolled(double mx, double my, double delta) {
        // The flavor list scrolls by itself; the rest of the body is the editor (modal scroll).
        if (overList(mx, my - scrollOffset())) {
            int previous = listScroll;
            listScroll = Math.max(0, Math.min(Math.max(0, listContent - listViewport), listScroll - (int) Math.round(delta * ROW)));
            com.chi__you.cuisinejournal.client.compat.CuisineUiSoundCompat.scroll(previous, listScroll);
            return true;
        }
        return mode == Mode.ICON && icons.mouseScrolled(mx, my, delta);
    }

    @Override protected boolean bodyKeyPressed(int key, int scan, int modifiers) {
        for (EditBox box : List.of(name, description, effectSearch, groupBox)) if (box.isFocused()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) { box.setFocused(false); return true; }
            box.keyPressed(key, scan, modifiers);
            refreshButtons();
            return true;
        }
        if (mode == Mode.ICON && icons.keyPressed(key, scan, modifiers)) return true;
        return false;
    }

    @Override protected boolean bodyCharTyped(char c, int modifiers) {
        for (EditBox box : List.of(name, description, effectSearch, groupBox)) if (box.isFocused()) {
            boolean typed = box.charTyped(c, modifiers);
            refreshButtons();
            return typed;
        }
        return mode == Mode.ICON && icons.charTyped(c, modifiers);
    }

    @Override public boolean textInputFocused() {
        return isOpen() && (name.isFocused() || description.isFocused() || effectSearch.isFocused() || groupBox.isFocused() || icons.isFocused());
    }
}
