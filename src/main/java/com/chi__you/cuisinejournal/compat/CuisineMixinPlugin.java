package com.chi__you.cuisinejournal.compat;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;

/** Host drift and destructive third-party injections disable complete optional host groups. */
public final class CuisineMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<String, ClassNode>> accepted = new HashMap<>();
    private final Set<String> strictTargets = new HashSet<>();
    private final Map<String, String> rejectedTargets = new HashMap<>();
    private final Function<String, List<IMixinInfo>> selected;
    private final Function<String, ClassNode> hosts;

    public CuisineMixinPlugin() { this(SelectedTargetMixins::of, CuisineMixinPlugin::read); }
    CuisineMixinPlugin(Function<String, List<IMixinInfo>> selected, Function<String, ClassNode> hosts) {
        this.selected = selected;
        this.hosts = hosts;
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (OptionalMixinConflicts.protectedTarget(target)) return true;
        ClassNode adapter = readAdapter(mixin);
        if (adapter == null || !OptionalMixinContracts.optional(adapter)) { strictTargets.add(target);return true; }
        ClassNode host = hosts.apply(target);
        if (host == null) { rejectedTargets.put(target, "cannot read optional host");return false; }
        String failure;
        try {
            failure = OptionalMixinContracts.incompatibility(adapter, host, CuisineMixinPlugin::read);
            if (failure == null) failure = OptionalMixinConflicts.incompatibility(host, List.of(adapter), List.of());
        }
        catch (RuntimeException unsupported) { failure = "cannot verify host contract: " + unsupported.getClass().getSimpleName(); }
        if (failure == null) {
            accepted.computeIfAbsent(target, ignored -> new HashMap<>()).put(mixin, adapter);
            return true;
        }
        rejectedTargets.put(target, failure);
        if (REPORTED.add(mixin + "->" + target)) CuisineJournalLog.LOGGER.warn(
                "Optional compatibility disabled: {} -> {}: {}. Native gameplay remains; this adapter is not covered.",
                mixin, target, failure);
        return false;
    }
    private static ClassNode read(String name) {
        try { return MixinService.getService().getBytecodeProvider().getClassNode(name.replace('.', '/')); }
        catch (java.io.IOException | ClassNotFoundException | LinkageError missing) { return null; }
    }
    private static ClassNode readAdapter(String name) {
        // Own mixin resources must not enter the transformer's target-class pipeline recursively.
        try (var input=CuisineMixinPlugin.class.getClassLoader().getResourceAsStream(name.replace('.','/')+".class")) {
            if(input==null)return null;
            var node=new ClassNode();new org.objectweb.asm.ClassReader(input).accept(node,0);return node;
        } catch(java.io.IOException failure) { return null; }
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> own, Set<String> other) {
        // Mixin passes the live key set of THIS config's target mapping at this stage.
        // Removing one optional host drops every paired hook on it before MAIN/PREINJECT.
        for (String target : List.copyOf(own)) {
            if (OptionalMixinConflicts.protectedTarget(target) || strictTargets.contains(target)) continue;
            var candidates = accepted.get(target);
            if (candidates == null || candidates.isEmpty()) continue;
            String failure = rejectedTargets.get(target);
            if (failure == null && !other.contains(target)) continue;
            if (failure == null) try {
                var ours = new ArrayList<ClassNode>();
                var others = new ArrayList<ClassNode>();
                for (var info : selected.apply(target)) {
                    if (candidates.containsKey(info.getClassName())) ours.add(candidates.get(info.getClassName()));
                    else if (info.getClassName().startsWith("com.chi__you.cuisinejournal.mixin."))
                        ours.add(info.getClassNode(org.objectweb.asm.ClassReader.SKIP_DEBUG));
                    else others.add(info.getClassNode(org.objectweb.asm.ClassReader.SKIP_DEBUG));
                }
                if (ours.isEmpty()) throw new IllegalStateException("Selected own adapters unavailable");
                ClassNode host = hosts.apply(target);
                failure = host == null ? "cannot read shared optional host"
                        : OptionalMixinConflicts.incompatibility(host, ours, others);
            } catch (RuntimeException | LinkageError unavailable) {
                failure = "cannot inspect shared optional host: " + unavailable.getClass().getSimpleName();
            }
            if (failure != null) {
                own.remove(target);
                if (REPORTED.add("shared->" + target)) CuisineJournalLog.LOGGER.warn(
                        "Optional compatibility group disabled: {}: {}. All Cuisine Journal adapters on this host are skipped; third-party behavior is retained.",
                        target, failure);
            }
        }
        accepted.clear();
        strictTargets.clear();
        rejectedTargets.clear();
    }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
