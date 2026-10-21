package com.chi__you.foodinteractions.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Select the same owner for early mixins as for the later Forge event registration. */
public final class InteractionMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        var mods=net.minecraftforge.fml.loading.LoadingModList.get();
        if(mods==null||mods.getModFileById("lychee")==null)return false;
        if(mixin.endsWith("LycheeJeiBlockClickMixin")&&mods.getModFileById("jei")==null)return false;
        return getClass().getName().contains("foodinteractionsstandalone")
                ||mods.getModFileById("food_interaction_recipes")==null;
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig(){return null;}
    @Override public void acceptTargets(Set<String> own,Set<String> other) {}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
}
