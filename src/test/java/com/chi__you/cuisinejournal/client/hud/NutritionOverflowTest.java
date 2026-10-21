package com.chi__you.cuisinejournal.client.hud;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public final class NutritionOverflowTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        for (int value=0;value<=20;value++) {
            check(NutritionOverflow.rows(value)==0, "no overflow at or below twenty");
            check(NutritionOverflow.hungerSprite(value,0,0,false)==-1, "no phantom chicken leg");
        }
        check(NutritionOverflow.rows(24)==1 && NutritionOverflow.rows(40)==1
                && NutritionOverflow.rows(41)==2 && NutritionOverflow.rows(60)==2, "overflow row boundaries");
        check(NutritionOverflow.rows(Float.NaN)==0, "invalid reserve does not create rows");
        check(NutritionOverflow.hungerSprite(21,0,0,false)==1
                && NutritionOverflow.hungerSprite(22,0,0,false)==2
                && NutritionOverflow.hungerSprite(24,0,1,false)==2
                && NutritionOverflow.hungerSprite(24,0,2,false)==-1, "half/full and remaining positions");
        check(NutritionOverflow.hungerSprite(21,0,0,true)==3
                && NutritionOverflow.hungerSprite(22,0,0,true)==4, "hunger effect selects its alternate palette");
        check(NutritionOverflow.hungerSprite(41,1,0,false)==1, "third reserve band starts with a half leg");
        float[] values={20,20.1F,20.5F,20.51F,21,21.01F,21.99F,22};
        int[] sprites={-1,0,0,1,1,2,2,3};
        for(int i=0;i<values.length;i++)check(NutritionOverflow.saturationSprite(values[i],0,0)==sprites[i],
                "four-stage saturation threshold "+values[i]);
        check(NutritionOverflow.saturationSprite(23,0,0)==3
                && NutritionOverflow.saturationSprite(23,0,1)==1, "three extra saturation is full plus half");
        var hunger=read("overflow_hunger.png",45); var saturation=read("overflow_saturation.png",36);
        var canvas=new BufferedImage(360,144,BufferedImage.TYPE_INT_ARGB);
        var graphics=canvas.createGraphics();
        graphics.setColor(new Color(40,40,40));graphics.fillRect(0,0,360,144);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(hunger,0,0,360,72,null);graphics.drawImage(saturation,0,72,288,72,null);
        graphics.dispose();
        Path report=Path.of("build/reports/nutrition-overflow-sprites.png");Files.createDirectories(report.getParent());
        ImageIO.write(canvas,"png",report.toFile());
        String mods=System.getenv("CUISINEJOURNAL_COMPAT_MODS");
        if(mods!=null)try(var files=Files.list(Path.of(mods))) {
            var host=files.filter(path->path.getFileName().toString().startsWith("twilight_spark_delight-"))
                    .findFirst().orElse(null);
            if(host!=null)try(var jar=new JarFile(host.toFile())) {
                method(jar,"xy177/twilightsparksdelight/client/TSDExtraFoodHud","render",
                        "(Lnet/minecraft/client/gui/GuiGraphics;)V");
                method(jar,"xy177/twilightsparksdelight/integration/appleskin/TSDAppleSkinCompat","afterOverlay",
                        "(Lnet/minecraftforge/client/event/RenderGuiOverlayEvent$Post;)V");
            }
        }
        String apple=System.getenv("CUISINEJOURNAL_APPLESKIN_JAR");
        if(apple!=null)try(var jar=new JarFile(apple)) {
            method(jar,"squeek/appleskin/helpers/FoodHelper","canConsume",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Z");
            method(jar,"squeek/appleskin/helpers/FoodHelper","getModifiedFoodValues",
                    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Lsqueek/appleskin/api/food/FoodValues;");
            method(jar,"squeek/appleskin/api/food/FoodValues","getSaturationIncrement","()F");
        }
        var renderer=Files.readString(Path.of("src/main/java/com/chi__you/cuisinejournal/client/hud/VanillaNutritionHud.java"));
        check(!renderer.contains("hungerCap()+")&&!renderer.contains("skills.saturationCap()"),
                "old numeric reserve HUD removed");
        check(renderer.contains("nutritionDisplayEnabled()"), "saturation and preview follow nutrition display toggle");
        System.out.println("NutritionOverflowTest: "+checks+" checks passed");
    }
    private static BufferedImage read(String name,int width) throws Exception {
        var image=ImageIO.read(Path.of("src/main/resources/assets/cuisinejournal/textures/gui/"+name).toFile());
        check(image.getWidth()==width&&image.getHeight()==9, "native nine-pixel atlas dimensions");
        for(int frame=0;frame<width/9;frame++) {
            int filled=0;
            for(int y=0;y<9;y++)for(int x=frame*9;x<(frame+1)*9;x++) {
                int pixel=image.getRGB(x,y);
                if((pixel>>>24)!=0){filled++;check((pixel&0xFFFFFF)!=0x343434, "preview background not included");}
            }
            check(filled>0&&filled<81, "each sprite is nonblank with transparent surround");
        }
        return image;
    }
    private static void method(JarFile jar,String path,String method,String descriptor) throws Exception {
        var node=new ClassNode();
        try(var input=jar.getInputStream(Objects.requireNonNull(jar.getJarEntry(path+".class")))) {new ClassReader(input).accept(node,0);}
        check(node.methods.stream().anyMatch(value->value.name.equals(method)&&value.desc.equals(descriptor)),
                "optional host API matches "+method);
    }
    private static void check(boolean condition,String label) {if(!condition)throw new AssertionError(label);checks++;}
}
