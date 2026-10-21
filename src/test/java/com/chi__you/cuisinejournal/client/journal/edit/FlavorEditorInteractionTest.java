package com.chi__you.cuisinejournal.client.journal.edit;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.util.*;

public final class FlavorEditorInteractionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(EffectSearch.matches("minecraft:speed", "Speed", "SPEED"), "case-insensitive localized name");
        check(EffectSearch.matches("smc:frost", "冰霜", "冰"), "Chinese substring");
        check(EffectSearch.matches("minecraft:fire_resistance", "抗火", "minecraft:fire"), "resource id search");
        check(EffectSearch.matches("smc:frost", "冰霜", "smc 冰"), "id and localized tokens combine");
        check(EffectSearch.matches("smc:frost", "冰霜", "smc	冰"), "all whitespace separates tokens");
        check(!EffectSearch.matches("minecraft:speed", "Speed", "strength"), "unmatched query excluded");
        check(EffectSearch.matches("minecraft:speed", "Speed", "  "), "empty query returns all");
        var top=ModalScrollbar.layout(100,20,100,1000,0);
        check(top.thumb().height==10&&top.maximum()==900, "thumb size and maximum share viewport geometry");
        check(top.contains(99,20)&&!top.contains(95,20), "thin visible bar has bounded mouse padding");
        double grab=top.grabOffset(23);
        check(grab==3&&top.offset(23,grab)==0, "grabbing thumb keeps pointer offset");
        check(top.offset(113,grab)==900, "drag to bottom reaches last content");
        check(top.offset(-100,grab)==0&&top.offset(1000,grab)==900, "dragging outside clamps to track");
        check(top.offset(70,top.grabOffset(70))==450, "track click centers thumb at requested position");
        var end=ModalScrollbar.layout(100,20,100,1000,900);
        check(end.thumb().y==110, "rendered bottom thumb matches dragged offset");
        var small=ModalScrollbar.layout(100,20,5,1000,0);
        check(small.thumb().height==5&&small.offset(100,0)==0, "tiny viewport cannot yield negative thumb travel");
        check(!ModalScrollbar.layout(100,20,100,90,0).contains(100,30), "no draggable track when content fits");
        var modal=compiled("edit/JournalModal");
        check(calls(modal,"mouseClicked","contains")&&calls(modal,"mouseDragged","offset")
                &&calls(modal,"mouseDragged","setScrollOffset"), "modal click and drag use shared scroll geometry");
        check(modal.methods.stream().filter(m->m.name.equals("mouseReleased")).flatMap(m-> {
            List<FieldInsnNode> fields=new ArrayList<>();for(var insn:m.instructions)if(insn instanceof FieldInsnNode field)fields.add(field);return fields.stream();
        }).anyMatch(field->field.name.equals("draggingScrollbar")&&field.getOpcode()==181), "mouse release clears drag state");
        var library=compiled("edit/FlavorLibraryModal");
        check(calls(library,"<init>","startNew"),"opening flavor library without an explicit preset starts a new draft");
        check(calls(library,"selectGroup","moveGroupValues"),"changing modifier target transfers editable values before saving");
        check(calls(library,"load","effectTarget") && calls(library,"load","targetGroup"),
                "reopening editor restores persisted effect target selection");
        check(library.methods.stream().filter(method -> method.name.equals("selectGroup"))
                .anyMatch(method -> { for (var instruction : method.instructions)
                    if (instruction instanceof FieldInsnNode field && field.name.equals("effectTarget")
                            && field.getOpcode() == 181) return true; return false; }),
                "effect-only scope changes become serializable draft edits");
        double[][] values = new double[3][7];
        values[0] = new double[]{2, .1, .4, .2, 30, .3, .05};
        FlavorLibraryModal.moveGroupValues(values, 0, 1);
        check(values[0][0] == 0 && values[1][0] == 2 && values[0][4] == 0 && values[1][4] == 30
                && values[0][6] == .05, "common values move to food while drink-only value stays common");
        FlavorLibraryModal.moveGroupValues(values, 1, 2);
        check(values[1][0] == 2 && values[2][4] == 30 && values[1][4] == 0 && values[2][5] == .3,
                "unsupported fields stay in the old group when moving to drinks");
        check(calls(library,"updateEffectDefaults","effectName"),"new flavor default name uses localized potion name");
        check(calls(library,"renderEditor","updateEffectDefaults")&&calls(library,"renderEffectPicker","updateEffectDefaults"),
                "adding removing and selecting effects refresh draft defaults");
        check(calls(library,"renderEffectPicker","matches"), "effect picker uses shared localized/pinyin matcher");
        check(!calls(library,"renderEffectPicker","limit"), "effect picker no longer silently truncates to eighty rows");
        check(calls(library,"renderEffectPicker","effectIcon"), "picker renders actual potion sprites");
        check(calls(library,"<init>","setResponder"), "query changes reset stale modal scroll");
        var ui=compiled("FlavorUi");
        check(calls(ui,"effectIcon","getMobEffectTextures")&&calls(ui,"effectIcon","blit"), "flavor icons use Minecraft effect atlas");
        check(calls(ui,"icon","effectIcon"), "preset icons reuse same potion rendering");
        System.out.println("FlavorEditorInteractionTest: "+checks+" checks passed");
    }
    private static ClassNode compiled(String path) throws Exception {
        ClassNode node=new ClassNode();
        try(var input=FlavorEditorInteractionTest.class.getClassLoader().getResourceAsStream(
                "com/chi__you/cuisinejournal/client/journal/"+path+".class")) {
            new ClassReader(Objects.requireNonNull(input)).accept(node,0);
        }
        return node;
    }
    private static boolean calls(ClassNode node,String method,String target) {
        for(var m:node.methods)if(m.name.equals(method)||m.name.startsWith("lambda$"+method+"$"))for(var insn:m.instructions)
            if(insn instanceof MethodInsnNode call&&call.name.equals(target))return true;
        return false;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
