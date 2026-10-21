package com.chi__you.cuisinejournal.data;

import com.chi__you.cuisinejournal.config.ConfigManager;
import com.chi__you.cuisinejournal.recipe.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The small mutable documents involved in an editor/reload transaction. */
public final class LocalEditSnapshot {
    private final Map<Path,String> files=new LinkedHashMap<>();
    private final List<Runnable> runtime;
    public LocalEditSnapshot(Path directory) {
        runtime=List.of(ConfigManager.rollbackPoint(),IngredientCatalogueConfig.rollbackPoint(),
                FlavorPresets.rollbackPoint(),GlobalCategoryStore.rollbackPoint(),SchemeStore.rollbackPoint(),
                IngredientCompatRules.rollbackPoint(), DishServingVariants.rollbackPoint(),
                com.chi__you.cuisinejournal.tracking.WorkstationDefinitions.rollbackPoint(),
                com.chi__you.cuisinejournal.food.CuisineFoodDefinitions.rollbackPoint(),
                com.chi__you.cuisinejournal.food.CuisineFoodOverrides.rollbackPoint(),
                CulinaryRegistries.rollbackPoint());
        try {
            for(String name:List.of("cuisinejournal-server.toml","cuisinejournal-flavors.json",
                    "cuisinejournal-ingredients.json","cuisinejournal-categories.json",
                    "cuisinejournal/schemes/current.json")) {
                Path path=directory.resolve(name);
                files.put(path,Files.exists(path)?Files.readString(path,StandardCharsets.UTF_8):null);
            }
        } catch(IOException failure) {throw new IllegalStateException("Cannot snapshot local edits; change rejected",failure);}
    }
    public void restore() {
        RuntimeException error=null;
        for(var entry:files.entrySet()) try {
            if(entry.getValue()==null)Files.deleteIfExists(entry.getKey());
            else if(!FlavorPresets.writeAtomically(entry.getKey(),entry.getValue()))
                throw new IOException("Cannot restore "+entry.getKey());
        } catch(IOException failure) {
            if(error==null)error=new IllegalStateException("Local edit rollback could not restore every file");
            error.addSuppressed(failure);
        }
        for(int i=runtime.size()-1;i>=0;i--)try{runtime.get(i).run();}catch(RuntimeException failure){
            if(error==null)error=new IllegalStateException("Runtime rollback could not restore every layer");
            error.addSuppressed(failure);
        }
        if(error!=null)throw error;
    }
}
