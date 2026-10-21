package com.chi__you.cuisinejournal.client;

import com.chi__you.cuisinejournal.network.Packets;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.FrameType;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Presentation contracts without starting a renderer or awarding real advancements. */
public final class ClientPresentationTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        // Plain Java regressions need item registries, not Forge's transformed network bootstrap.
        var bootstrap = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true);
        bootstrap.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        var food = ResourceLocation.parse("minecraft:cooked_mutton");
        for (var kind : Packets.Notify.Kind.values()) {
            var notice = JournalNotification.advancement(new Packets.Notify(kind, food, 3));
            var display = Objects.requireNonNull(notice.getDisplay());
            check(display.getIcon().is(Items.COOKED_MUTTON), kind + " retains the subject item icon");
            check(display.getTitle().getContents() instanceof TranslatableContents title
                    && title.getKey().equals("cuisinejournal.notify.with_subject")
                    && title.getArgs().length == 2, kind + " keeps event and subject in the visible title");
            check(display.getFrame() == FrameType.TASK, kind + " cannot play an uncontrolled challenge fanfare");
            check(display.shouldShowToast() && !display.shouldAnnounceChat() && display.isHidden(),
                    kind + " is toast-only, not a chat announcement");
            check(notice.getParent() == null && !notice.getChildren().iterator().hasNext()
                    && notice.getCriteria().isEmpty() && notice.getRequirements().length == 0
                    && notice.getRewards() == AdvancementRewards.EMPTY && !notice.sendsTelemetryEvent(),
                    kind + " has no progress, rewards, parent, or telemetry");
            check(notice.getId().getNamespace().equals("cuisinejournal")
                    && notice.getId().getPath().startsWith("notification/"), kind + " has a namespaced display identity");
        }
        var level = JournalNotification.advancement(new Packets.Notify(Packets.Notify.Kind.LEVEL_UP, null, 7));
        check(level.getDisplay().getIcon().is(Items.BOOK), "subjectless notifications use a nonempty fallback icon");
        check(level.getDisplay().getTitle().getContents() instanceof TranslatableContents title
                && title.getKey().equals("cuisinejournal.notify.level_up")
                && title.getArgs()[0].equals(7L), "level-up title retains its level without an empty separator");
        var unknown = JournalNotification.advancement(new Packets.Notify(Packets.Notify.Kind.MILESTONE,
                ResourceLocation.parse("missing:achievement"), 0));
        check(unknown.getDisplay().getIcon().is(Items.BOOK), "non-item subjects safely use the fallback");

        for (String screen : List.of("JournalScreen", "ClientSettingsScreen")) {
            var type = compiled("journal/" + screen);
            check(type.methods.stream().noneMatch(method -> method.name.equals("renderBackground")),
                    screen + " inherits the vanilla background and Forge event");
            var renderCalls = calls(type, "render");
            int background = indexOf(renderCalls, "renderBackground");
            int panel = indexOf(renderCalls, "panel");
            check(background >= 0 && panel > background, screen + " draws vanilla background before its panels");
            check(renderCalls.stream().filter(call -> call.name.equals("renderBackground")).count() == 1,
                    screen + " renders the background exactly once");
            check(type.methods.stream().flatMap(method -> calls(type, method.name).stream()).noneMatch(call ->
                    call.owner.contains("PostChain") || call.owner.contains("RenderSystem") || call.owner.contains("JournalBlur")),
                    screen + " never installs a post chain or mutates the renderer state");
        }
        var toasts = calls(compiled("CuisineJournalClient"), "showToast");
        check(toasts.stream().anyMatch(call -> call.owner.equals("net/minecraft/client/gui/components/toasts/AdvancementToast")
                && call.name.equals("<init>")), "notifications enter the real vanilla advancement toast path");
        check(toasts.stream().noneMatch(call -> call.owner.contains("SystemToast")), "no tutorial/system toast blue box remains");
        check(Files.notExists(Path.of("src/main/resources/assets/cuisinejournal/shaders/post/journal_blur.json")),
                "obsolete journal shader resource removed");
        check(Files.notExists(Path.of("src/main/java/com/chi__you/cuisinejournal/client/journal/JournalBlur.java")),
                "obsolete journal postprocessor removed");
        System.out.println("ClientPresentationTest: " + checks + " checks passed");
    }

    private static ClassNode compiled(String path) throws Exception {
        var type = new ClassNode();
        try (var input = ClientPresentationTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/client/" + path + ".class")) {
            new ClassReader(Objects.requireNonNull(input, path)).accept(type, 0);
        }
        return type;
    }

    private static List<MethodInsnNode> calls(ClassNode type, String methodName) {
        var result = new ArrayList<MethodInsnNode>();
        for (var method : type.methods) if (method.name.equals(methodName))
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static int indexOf(List<MethodInsnNode> calls, String name) {
        for (int i = 0; i < calls.size(); i++) if (calls.get(i).name.equals(name)) return i;
        return -1;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
