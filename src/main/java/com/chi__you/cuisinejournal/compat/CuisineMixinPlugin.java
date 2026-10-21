package com.chi__you.cuisinejournal.compat;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Host drift disables one complete optional adapter, never half of its paired hooks. */
public final class CuisineMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (target.startsWith("net.minecraft.") || target.startsWith("net.minecraftforge.")) return true;
        ClassNode adapter = readAdapter(mixin);
        if (adapter == null || !OptionalMixinContracts.optional(adapter)) return true;
        ClassNode host = read(target);
        if (host == null) return false;
        String failure;
        try { failure = OptionalMixinContracts.incompatibility(adapter, host, CuisineMixinPlugin::read); }
        catch (RuntimeException unsupported) { failure = "cannot verify host contract: " + unsupported.getClass().getSimpleName(); }
        if (failure == null) return true;
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
    @Override public void acceptTargets(Set<String> own, Set<String> other) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
