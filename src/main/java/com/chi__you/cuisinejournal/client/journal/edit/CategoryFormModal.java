package com.chi__you.cuisinejournal.client.journal.edit;

import com.chi__you.cuisinejournal.client.ClientJournalState;
import com.chi__you.cuisinejournal.client.EditClientState;
import com.chi__you.cuisinejournal.client.journal.JournalDropdown;
import com.chi__you.cuisinejournal.client.journal.JournalTheme;
import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Shared form for creating roots/subcategories and editing existing entries. */
public final class CategoryFormModal extends JournalModal {
    public enum Mode { CREATE_ROOT, CREATE_SUB, EDIT_ROOT, EDIT_SUB }
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_]+(?:/[a-z0-9_]+)*");

    private final Mode mode;
    private final Packets.CategoryMetadata original;
    private final Map<String, Long> expectedVersions;
    private final Consumer<Packets.CategoryEditRequest> submit;
    private final Runnable cancel;
    private final Font font;
    private final JournalTextField name;
    private final JournalTextField id;
    private final IconPicker iconPicker;
    private JournalDropdown<ResourceLocation> parent;
    private boolean idManual;
    private boolean initializing;
    private int customCounter = 1;
    private final Runnable danger;
    /** Parent passed in by the page; CREATE_SUB always creates under it. */
    private final ResourceLocation fixedParent;
    private final JournalButton dangerButton;
    /** The name error is only shown once the player has typed or tried to submit. */
    private boolean nameTouched;
    private final JournalModal.Focusable parentFocus = new JournalModal.Focusable() {
        private boolean focused;
        @Override public void setFocused(boolean focused) { this.focused = focused; }
        @Override public boolean isFocused() { return focused; }
    };

    public CategoryFormModal(int x, int y, int width, int height, Font font, Mode mode,
                            Packets.CategoryMetadata original, ResourceLocation initialParent,
                            Consumer<Packets.CategoryEditRequest> submit, Runnable cancel) {
        this(x, y, width, height, font, mode, original, initialParent, List.of(), submit, cancel, null);
    }

    private CategoryFormModal(int x, int y, int width, int height, Font font, Mode mode,
                              Packets.CategoryMetadata original, ResourceLocation initialParent,
                              List<ResourceLocation> recommended,
                              Consumer<Packets.CategoryEditRequest> submit, Runnable cancel,
                              Runnable danger) {
        this(x, y, width, height, font, mode, original, initialParent, recommended, submit, cancel, danger,
                EditClientState.snapshot());
    }

    private CategoryFormModal(int x, int y, int width, int height, Font font, Mode mode,
                              Packets.CategoryMetadata original, ResourceLocation initialParent,
                              List<ResourceLocation> recommended, Consumer<Packets.CategoryEditRequest> submit,
                              Runnable cancel, Runnable danger, Map<String, Long> expectedVersions) {
        super(x, y, width, height, titleFor(mode));
        this.mode = mode;
        this.original = original;
        this.expectedVersions = Map.copyOf(expectedVersions);
        this.submit = submit;
        this.cancel = cancel;
        this.danger = danger;
        this.fixedParent = original == null ? initialParent : original.parent();
        this.font = font;
        name = new JournalTextField(font, x + 8, y + 26, width - 16,
                Component.translatable("cuisinejournal.edit.field.name"));
        name.setMaxLength(24);
        id = new JournalTextField(font, x + 8, y + 58, width - 16,
                Component.translatable("cuisinejournal.edit.field.id"));
        id.setMaxLength(128);
        iconPicker = new IconPicker(font, x + 8, y + 96, width - 16);
        iconPicker.setGridHeight(Math.max(40, height - 150));
        addFocusable(name);
        if (mode != Mode.EDIT_ROOT && mode != Mode.EDIT_SUB) addFocusable(id);
        name.setResponder(value -> {
            if (!initializing) nameTouched = true;
            if (!initializing && !idManual && isCreate()) {
                // CREATE_SUB uses the initialParent directly; no dropdown to consult.
                ResourceLocation effectiveParent = mode == Mode.CREATE_SUB || parent == null
                        ? initialParent : parent.value();
                setGeneratedId(generateId(value, effectiveParent));
            }
            validate();
        });
        id.setResponder(value -> { if (!initializing) idManual = true; validate(); });
        iconPicker.setRecommendations(recommended);
        initializing = true;
        if (original != null) {
            name.setValue(displayName(original));
            id.setValue(original.id().toString());
            iconPicker.setSelectedIcon(original.icon());
        } else {
            name.setValue("");
            id.setValue(generateId("", initialParent));
            iconPicker.clearSelection();
        }
        initializing = false;
        // Only editing a subcategory offers a parent dropdown; a new subcategory
        // is always created in the root the player is currently viewing.
        if (mode == Mode.EDIT_SUB) {
            parent = new JournalDropdown<>(Component.translatable("cuisinejournal.edit.field.parent"),
                    original == null ? initialParent : original.parent(), value -> {
                        if (isCreate() && !idManual) setGeneratedId(generateId(name.getValue(), value));
                        validate();
                    });
            rebuildParents();
            addFocusable(parentFocus);
        }
        if (!isCreate() && original != null && danger != null) {
            boolean datapack = original.source() != 1;
            dangerButton = new JournalButton(Component.translatable(datapack
                    ? "cuisinejournal.edit.block" : "cuisinejournal.edit.remove"),
                    JournalButton.Variant.DANGER, this::invokeDanger);
            dangerButton.setEnabled(!original.protectedEntry());
            if (original.protectedEntry()) {
                dangerButton.setTooltip(Component.translatable(original.parent() == null
                        ? "cuisinejournal.edit.disabled.fallback" : "cuisinejournal.edit.disabled.default_sub"));
            }
        } else {
            dangerButton = null;
        }
        setSubmitAction(this::submitForm);
        setCancelAction(this::cancelForm);
        validate();
    }

    public CategoryFormModal(int x, int y, int width, int height, Font font, Mode mode,
                            Consumer<Packets.CategoryEditRequest> submit, Runnable cancel) {
        this(x, y, width, height, font, mode, null, null, submit, cancel);
    }

    /** Host-facing constructor; the host supplies bounds after construction. */
    public CategoryFormModal(Mode mode, Packets.CategoryMetadata existing,
                             ResourceLocation parent, List<ResourceLocation> recommended,
                             Consumer<Packets.CategoryEditRequest> submit,
                             Runnable cancel, Runnable danger) {
        this(0, 0, 320, 200, Minecraft.getInstance().font, mode, existing, parent,
                recommended, submit, cancel, danger);
    }

    public CategoryFormModal(Mode mode, Packets.CategoryMetadata existing,
                             ResourceLocation parent, List<ResourceLocation> recommended,
                             Consumer<Packets.CategoryEditRequest> submit, Runnable cancel, Runnable danger,
                             Map<String, Long> expectedVersions) {
        this(0, 0, 320, 200, Minecraft.getInstance().font, mode, existing, parent,
                recommended, submit, cancel, danger, expectedVersions);
    }

    private static Component titleFor(Mode mode) {
        return switch (mode) {
            case CREATE_ROOT -> Component.translatable("cuisinejournal.edit.new_root");
            case CREATE_SUB -> Component.translatable("cuisinejournal.edit.new_sub");
            case EDIT_ROOT -> Component.translatable("cuisinejournal.edit.edit_title_root");
            case EDIT_SUB -> Component.translatable("cuisinejournal.edit.edit_title_sub");
        };
    }

    private boolean isCreate() { return mode == Mode.CREATE_ROOT || mode == Mode.CREATE_SUB; }

    private static final int FIELD_HEIGHT = 35;
    private static final int ICON_GRID_HEIGHT = 60;
    private int restoreY = -1;
    private int iconTop;

    /** Lays out every widget in unscrolled body coordinates (the same space render and clicks use). */
    private void layout() {
        java.awt.Rectangle body = bodyBounds();
        int x = body.x;
        int y = body.y;
        int width = Math.max(80, body.width - 4);
        int cursor;
        if (isCreate()) {
            name.setBounds(x, y, width);
            // FIELD_HEIGHT already includes the 9px error row, so the ID label follows right after it.
            id.setBounds(x, y + FIELD_HEIGHT + 2, width);
            // The ID's hint ("自动生成，可修改") sits in its error row; leave a gap before "图标".
            cursor = y + FIELD_HEIGHT + 2 + FIELD_HEIGHT + 4;
            // CREATE_SUB no longer shows a parent dropdown — the parent is set by the page.
            restoreY = -1;
        } else {
            name.setBounds(x, y + 26, width);
            cursor = y + 26 + FIELD_HEIGHT;
            if (restoreVisible()) {
                restoreY = cursor - 2;
                cursor += 12;
            } else restoreY = -1;
        }
        iconTop = cursor;
        // "图标" label, then the picker (preview + search on one 18px line, 4px gap, grid).
        iconPicker.setBounds(x, cursor + 12, width);
        iconPicker.setGridHeight(ICON_GRID_HEIGHT);
        cursor += 12 + 22 + ICON_GRID_HEIGHT + 4;
        if (!isCreate() && parent != null) {
            parent.bounds(x, cursor + 12, width, x + body.width, y + scrollOffset() + body.height);
            cursor += 12 + 12 + 4;
        }
        setScrollContentHeight(cursor - y + 4);
    }

    private boolean restoreVisible() {
        return original != null && original.source() == 2;
    }

    @Override
    protected void renderBody(GuiGraphics g, int bodyX, int bodyY, int bodyWidth, int bodyHeight,
                              int mouseX, int mouseY, float partialTick) {
        layout();
        int top = bodyY - scrollOffset();
        int right = bodyX + bodyWidth - 4;
        if (!isCreate() && original != null) {
            PixelIcons.drawSourceBadge(g, sourceIcon(original), bodyX, top + 1,
                    sourceColour(original), original.source() == 2);
            Component source = sourceText(original);
            int sourceWidth = font.width(source);
            g.drawString(font, source, right - sourceWidth, top + 2, sourceColour(original), false);
            g.drawString(font, font.plainSubstrByWidth(displayName(original),
                            Math.max(0, right - sourceWidth - 6 - (bodyX + 12))),
                    bodyX + 12, top + 2, JournalTheme.TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(original.id().toString(), right - bodyX),
                    bodyX, top + 13, JournalTheme.TEXT_FAINT, false);
        }
        name.render(g, mouseX, mouseY);
        if (isCreate()) {
            id.render(g, mouseX, mouseY);
            if (id.isValid()) {
                java.awt.Rectangle input = id.bounds();
                g.drawString(font, Component.translatable("cuisinejournal.edit.id.auto"),
                        input.x, input.y + input.height + 1, JournalTheme.TEXT_FAINT, false);
            }
        }
        if (restoreY >= 0) {
            Component link = Component.translatable("cuisinejournal.edit.restore_datapack");
            boolean hovered = mouseX >= bodyX && mouseX < bodyX + font.width(link)
                    && mouseY >= restoreY && mouseY < restoreY + 10;
            g.drawString(font, link, bodyX, restoreY + 1,
                    hovered ? JournalTheme.TEXT : JournalTheme.TEXT_ACCENT, false);
        }
        g.drawString(font, Component.translatable("cuisinejournal.edit.field.icon"), bodyX, iconTop,
                JournalTheme.TEXT_DIM, false);
        iconPicker.render(g, mouseX, mouseY);
        if (parent != null) {
            parent.renderButton(g, font, mouseX, mouseY);
            parent.renderPopup(g, font, mouseX, mouseY);
        }
    }

    @Override
    protected boolean bodyClickedFirst(double mx, double my, int button) {
        layout();
        // An open dropdown popup overlays the widgets below it, so it gets the click first.
        if (parent != null && parent.isOpen()) {
            parent.mouseClicked(font, mx, my);
            return true;
        }
        EditBox search = iconPicker.searchBox();
        boolean searchHit = mx >= search.getX() && mx < search.getX() + search.getWidth()
                && my >= search.getY() && my < search.getY() + search.getHeight();
        if (searchHit) {
            name.setFocused(false);
            id.setFocused(false);
            parentFocus.setFocused(false);
            iconPicker.mouseClicked(mx, my, button);
            return true;
        }
        iconPicker.setFocused(false);
        return false;
    }

    @Override
    protected boolean bodyClicked(double mx, double my, int button) {
        if (parent != null && parent.mouseClicked(font, mx, my)) {
            parentFocus.setFocused(true);
            return true;
        }
        if (iconPicker.mouseClicked(mx, my, button)) return true;
        if (restoreY >= 0) {
            int width = font.width(Component.translatable("cuisinejournal.edit.restore_datapack"));
            if (mx >= bodyBounds().x && mx < bodyBounds().x + width && my >= restoreY && my < restoreY + 10) {
                restoreDatapack();
                return true;
            }
        }
        return false;
    }

    @Override
    protected void focusNext(boolean backwards) {
        iconPicker.setFocused(false);
        if (parent != null) parent.close();
        super.focusNext(backwards);
    }

    @Override
    protected void onTick() {
        name.editBox().tick();
        if (isCreate()) id.editBox().tick();
        iconPicker.tick();
    }

    @Override
    protected Component submitLabel() {
        return Component.translatable(isCreate() ? "cuisinejournal.edit.create" : "cuisinejournal.edit.save");
    }

    @Override
    protected JournalButton footerButton() { return dangerButton; }

    @Override
    protected boolean bodyScrolled(double mx, double my, double delta) {
        return iconPicker.mouseScrolled(mx, my, delta)
                || (parent != null && parent.mouseScrolled(font, mx, my, delta));
    }

    @Override
    protected boolean bodyKeyPressed(int keyCode, int scanCode, int modifiers) {
        return name.keyPressed(keyCode, scanCode, modifiers)
                || (isCreate() && id.keyPressed(keyCode, scanCode, modifiers))
                || iconPicker.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected boolean bodyCharTyped(char c, int modifiers) {
        return name.charTyped(c, modifiers)
                || (isCreate() && id.charTyped(c, modifiers))
                || iconPicker.charTyped(c, modifiers);
    }

    @Override
    protected boolean submitEnabled() {
        return isValid() && !submitWaiting() && (isCreate() || changedFromCurrent());
    }

    /** Edit forms keep “保存” disabled until something actually differs from the live entry. */
    private boolean changedFromCurrent() {
        if (original == null) return true;
        if (!displayName(original).equals(name.getValue().trim())) return true;
        if (!java.util.Objects.equals(original.icon(), iconPicker.selectedIcon())) return true;
        return parent != null && !java.util.Objects.equals(original.parent(), parent.value());
    }

    @Override
    protected void onSubmit() { submitForm(); }

    @Override
    protected void onCancel() { cancelForm(); }

    private void submitForm() {
        nameTouched = true;
        validate();
        if (!submitEnabled() || submit == null) return;
        ResourceLocation categoryId = ResourceLocation.tryParse(id.getValue().trim());
        // CREATE_SUB always uses the initialParent that was passed in, not a dropdown value.
        ResourceLocation parentId = mode == Mode.CREATE_SUB ? fixedParent
                : (parent == null ? null : parent.value());
        Packets.CategoryEditRequest.Action action = switch (mode) {
            case CREATE_ROOT -> Packets.CategoryEditRequest.Action.CREATE_ROOT;
            case CREATE_SUB -> Packets.CategoryEditRequest.Action.CREATE_SUB;
            case EDIT_ROOT -> Packets.CategoryEditRequest.Action.EDIT_ROOT;
            // EDIT_SUB carries name, icon and parent in one request; MOVE_SUB would drop the rename.
            case EDIT_SUB -> Packets.CategoryEditRequest.Action.EDIT_SUB;
        };
        if (!isCreate() && original != null) categoryId = original.id();
        if (categoryId == null) return;
        setSubmitWaiting(true);
        submit.accept(new Packets.CategoryEditRequest(0, action, categoryId,
                mode == Mode.CREATE_ROOT || mode == Mode.EDIT_ROOT ? null : parentId,
                name.getValue().trim(), iconPicker.selectedIcon(), List.of(), expectedVersions));
    }

    private void cancelForm() {
        if (submitWaiting()) return;
        if (cancel != null) cancel.run();
        close();
    }

    public void acceptResult(Packets.CategoryEditResult result) {
        if (result == null) return;
        setSubmitWaiting(false);
        if (result.success()) { close(); return; }
        Component error = Component.translatable(result.message(), result.args().toArray());
        // In edit mode the ID field is not shown, so the error has to sit under the name.
        if (isCreate() && "cuisinejournal.edit.failed.id".equals(result.message())) id.setError(error);
        else name.setError(error);
    }

    public JournalTextField nameField() { return name; }
    public JournalTextField idField() { return id; }
    public IconPicker iconPicker() { return iconPicker; }

    @Override
    public boolean textInputFocused() {
        return super.textInputFocused() || (isOpen() && iconPicker.isFocused());
    }
    public JournalDropdown<ResourceLocation> parentPicker() { return parent; }
    public Map<String, Long> expectedVersions() { return expectedVersions; }
    public void invokeDanger() {
        if (danger == null || submitWaiting() || original == null || original.protectedEntry()) return;
        close();
        danger.run();
    }
    /** Drops the in-game override on the server so the entry is a plain datapack category again. */
    public void restoreDatapack() {
        if (original == null || original.source() != 2 || submitWaiting() || submit == null) return;
        setSubmitWaiting(true);
        submit.accept(new Packets.CategoryEditRequest(0, original.parent() == null
                ? Packets.CategoryEditRequest.Action.RESET_ROOT : Packets.CategoryEditRequest.Action.RESET_SUB,
                original.id(), null, displayNameOriginal(original), null, List.of(), expectedVersions));
    }

    private boolean isValid() {
        return !name.getValue().trim().isEmpty() && (isCreate() ? ID.matcher(id.getValue().trim()).matches() : true)
                && !idExists(id.getValue().trim());
    }

    private void validate() {
        String value = id.getValue().trim();
        if (name.getValue().trim().isEmpty()) {
            if (nameTouched) name.setError(Component.translatable("cuisinejournal.edit.name_required"));
            else name.clearError();
        } else name.clearError();
        if (!isCreate()) id.clearError();
        else if (!ID.matcher(value).matches()) id.setError(Component.translatable("cuisinejournal.edit.id.err.charset"));
        else if (idExists(value)) id.setError(Component.translatable("cuisinejournal.edit.id.err.taken"));
        else id.clearError();
    }

    private boolean idExists(String value) {
        // The server owns the effective category tree. Client metadata can be
        // one sync behind after a reload, so it must not reject a new ID here.
        return false;
    }

    private void rebuildParents() {
        if (parent == null) return;
        List<JournalDropdown.Option<ResourceLocation>> options = new ArrayList<>();
        for (Packets.CategoryMetadata category : ClientJournalState.categoryEditor().categories()) {
            if (category.parent() == null && !category.hidden()
                    && (original == null || !category.id().equals(original.id()))) {
                options.add(new JournalDropdown.Option<>(category.id(), Component.literal(displayName(category))));
            }
        }
        if (options.isEmpty()) {
            for (Packets.CollectionView collection : ClientJournalState.collections()) {
                if (original != null && collection.id().equals(original.id())) continue;
                Component label = collection.literalTitle() ? Component.literal(collection.titleKey())
                        : Component.translatable(collection.titleKey());
                options.add(new JournalDropdown.Option<>(collection.id(), label));
            }
        }
        parent.options(options);
    }

    private String generateId(String value, ResourceLocation parentId) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isEmpty()) {
            do {
                slug = "custom_" + customCounter++;
                final String candidate = slug;
                if (ClientJournalState.categoryEditor().categories().stream()
                        .noneMatch(category -> category.id().getPath().equals(candidate))) break;
            } while (true);
        }
        if (parentId == null) return "cuisinejournal:" + slug;
        return parentId.getNamespace() + ":" + parentId.getPath() + "/" + slug;
    }

    private static String displayName(Packets.CategoryMetadata category) {
        if (category == null) return "";
        return category.literalTitle() ? category.titleKey() : Component.translatable(category.titleKey()).getString();
    }

    private static String displayNameOriginal(Packets.CategoryMetadata category) {
        return category.originalLiteralTitle() ? category.originalTitleKey()
                : Component.translatable(category.originalTitleKey()).getString();
    }
    private static PixelIcons.Icon sourceIcon(Packets.CategoryMetadata category) {
        return category.source() == 1 ? PixelIcons.Icon.CREATED : PixelIcons.Icon.PACKET;
    }
    private static int sourceColour(Packets.CategoryMetadata category) {
        return category.source() == 1 ? JournalTheme.TEXT_GOOD
                : category.source() == 2 ? JournalTheme.ADJUSTED : JournalTheme.TEXT_FAINT;
    }
    private static Component sourceText(Packets.CategoryMetadata category) {
        return Component.translatable(category.source() == 1 ? "cuisinejournal.edit.source.ingame"
                : category.source() == 2 ? "cuisinejournal.edit.source.adjusted"
                : "cuisinejournal.edit.source.datapack");
    }
    private void setGeneratedId(String value) {
        initializing = true;
        id.setValue(value);
        initializing = false;
    }
}
