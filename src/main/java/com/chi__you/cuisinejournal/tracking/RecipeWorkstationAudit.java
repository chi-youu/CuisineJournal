package com.chi__you.cuisinejournal.tracking;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Recipe-side evidence only. Candidates never automatically install a workstation adapter. */
public final class RecipeWorkstationAudit {
    public record Sample(ResourceLocation recipe, ResourceLocation type, String implementation,
                         ResourceLocation output, boolean tracked, boolean processing,
                         ResourceLocation toastBlock, ResourceLocation toastItem, String toastEvidence,
                         String problem) {}
    public record Candidate(ResourceLocation block, String evidence, boolean enabled, String adapter,
                            String capabilities, String sources) {}
    public record Row(ResourceLocation type, int recipes, List<ResourceLocation> dishes,
                      int processingRecipes, int emptyOutputs, int readFailures,
                      List<String> implementations, List<ResourceLocation> examples,
                      List<Candidate> candidates, List<String> problems, String status) {}
    private static volatile List<Row> current=List.of();
    private RecipeWorkstationAudit() {}
    public static List<Row> current(){return current;}
    public static void clear(){current=List.of();}

    public static void rebuild(Collection<? extends Recipe<?>> recipes, RegistryAccess access,
                               CulinaryRegistry registry, Path directory) {
        List<Sample> samples=new ArrayList<>();
        for(Recipe<?> recipe:recipes) {
            ResourceLocation recipeId=null,type=null,output=null,toastBlock=null,toastItem=null;
            List<String> failures=new ArrayList<>();String toastEvidence="none";
            try {recipeId=recipe.getId();type=BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());}
            catch(RuntimeException|LinkageError failure){failures.add("identity: "+failure.getClass().getSimpleName());}
            try {
                ItemStack result=recipe.getResultItem(access);
                if(result!=null&&!result.isEmpty())output=BuiltInRegistries.ITEM.getKey(result.getItem());
            }catch(RuntimeException|LinkageError failure){failures.add("output: "+failure.getClass().getSimpleName());}
            try {
                // The Recipe default is a crafting table for ANY recipe. Never use it as evidence.
                java.lang.reflect.Method toast=findToastMethod(recipe.getClass());
                if(toast!=null&&toast.getDeclaringClass()!=Recipe.class) {
                    ItemStack symbol=recipe.getToastSymbol();
                    if(symbol!=null&&!symbol.isEmpty()) {
                        toastItem=BuiltInRegistries.ITEM.getKey(symbol.getItem());
                        if(symbol.getItem() instanceof BlockItem blockItem)
                            toastBlock=BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
                        toastEvidence="recipe_toast_override:"+toast.getDeclaringClass().getName();
                    }
                }else toastEvidence="ignored_default_crafting_table";
            }catch(RuntimeException|LinkageError failure){failures.add("toast: "+failure.getClass().getSimpleName());}
            ResourceLocation stableRecipeId=recipeId;
            boolean tracked=output!=null&&registry.entry(output)!=null&&registry.entry(output).recipes().stream()
                    .anyMatch(entry->entry.recipeId().equals(stableRecipeId));
            boolean processing=type!=null&&com.chi__you.cuisinejournal.recipe.IngredientCatalogueConfig.current().processing(type);
            samples.add(new Sample(recipeId,type,recipe.getClass().getName(),output,tracked,processing,
                    toastBlock,toastItem,toastEvidence,String.join("; ",failures)));
        }
        current=resolve(samples,WorkstationDefinitions.all());
        try{write(directory.resolve("cuisinejournal-recipe-workstations-audit.tsv"));}
        catch(IOException failure){CuisineJournalLog.LOGGER.warn("Could not write recipe-workstation audit: {}",failure.getMessage());}
        CuisineJournalLog.LOGGER.info("Recipe-workstation audit: {} recipe types, {} tracked types without explicit workstation mappings",
                current.size(),current.stream().filter(row->row.status().equals("needs_workstation_mapping")).count());
    }

    private static java.lang.reflect.Method findToastMethod(Class<?> type) {
        for(String name:List.of("getToastSymbol","m_8042_")) {
            try{return type.getMethod(name);}
            catch(NoSuchMethodException ignored){}
        }
        return null;
    }

    public static List<Row> resolve(List<Sample> samples, Map<ResourceLocation,WorkstationDefinitions.Definition> definitions) {
        Map<ResourceLocation,List<Sample>> grouped=new TreeMap<>();
        for(Sample sample:samples) {
            ResourceLocation type=sample.type()==null?ResourceLocation.parse("cuisinejournal:unreadable_recipe_type"):sample.type();
            grouped.computeIfAbsent(type,k->new ArrayList<>()).add(sample);
        }
        List<Row> rows=new ArrayList<>();
        grouped.forEach((type,entries)->{
            Set<ResourceLocation> dishes=new TreeSet<>(),examples=new TreeSet<>();
            Set<String> classes=new TreeSet<>(),problems=new TreeSet<>();
            Map<String,Candidate> candidates=new TreeMap<>();
            int processing=0,empty=0,failed=0;
            for(Sample s:entries) {
                if(s.tracked()&&s.output()!=null)dishes.add(s.output());
                if(s.recipe()!=null)examples.add(s.recipe());
                classes.add(s.implementation());
                if(s.processing())processing++;
                if(s.output()==null)empty++;
                if(s.problem()!=null&&!s.problem().isEmpty()){failed++;problems.add(s.problem());}
                if(s.toastBlock()!=null) {
                    var d=definitions.get(s.toastBlock());
                    if(s.toastEvidence()!=null&&s.toastEvidence().startsWith("recipe_toast_override:"))
                        candidates.put(s.toastBlock()+"|"+s.toastEvidence(),candidate(s.toastBlock(),s.toastEvidence(),d));
                } else if(s.toastItem()!=null) {
                    problems.add("toast is not a block item: "+s.toastItem()+" ("+s.toastEvidence()+")");
                }
            }
            boolean mapped=false;
            for(var d:definitions.values()) {
                // An unrestricted recipe_types list means no guard; it is not a match for every recipe.
                boolean exact=d.recipeTypes().contains(type);
                boolean method=d.recipeTypes().isEmpty()&&d.method()!=null
                        &&d.method().equals(CookingMethod.fromRecipeType(type).id());
                if(!exact&&!method)continue;
                candidates.put(d.block()+"|mapping",candidate(d.block(),exact?"datapack_recipe_type":"datapack_method",d));
                mapped|=d.enabled();
            }
            String status=mapped?"mapped_needs_runtime_verification":!dishes.isEmpty()?"needs_workstation_mapping"
                    :failed>0?"unreadable_recipe_needs_review":empty>0?"empty_item_output_needs_review"
                    :processing>0?"processing_only":"not_tracked_recipe_type";
            rows.add(new Row(type,entries.size(),List.copyOf(dishes),processing,empty,failed,
                    List.copyOf(classes),examples.stream().limit(8).toList(),List.copyOf(candidates.values()),
                    List.copyOf(problems),status));
        });
        return List.copyOf(rows);
    }
    private static Candidate candidate(ResourceLocation block,String evidence,WorkstationDefinitions.Definition d) {
        return new Candidate(block,evidence,d==null||d.enabled(),d==null?"unmapped":d.adapter().id(),
                d==null?"":d.capabilities().stream().map(WorkstationDefinitions.Capability::id).sorted().toList().toString(),
                d==null?"":String.join(" | ",d.sources()));
    }
    public static void write(Path path)throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path,format(current),StandardCharsets.UTF_8);
    }
    public static void writeCatalogueOnly(Path path,CulinaryRegistry registry)throws IOException {
        StringBuilder out=new StringBuilder("dish\tstatus\n");
        registry.entries().stream().filter(entry->entry.recipes().isEmpty())
                .sorted(Comparator.comparing(entry->entry.target().toString()))
                .forEach(entry->out.append(entry.target()).append("\tcatalogue_only_no_readable_item_recipe\n"));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path,out,StandardCharsets.UTF_8);
    }
    public static String format(List<Row> rows) {
        StringBuilder out=new StringBuilder("recipe_type\tstatus\trecipes\ttracked_dishes\tprocessing_recipes\tempty_item_outputs\tread_failures\tcandidate_block\tevidence\tmapping_enabled\tdeclared_adapter\tcapabilities\tsources\trecipe_classes\texample_recipes\tdishes\tproblems\n");
        for(Row row:rows) {
            List<Candidate> candidates=row.candidates().isEmpty()?Collections.singletonList(null):row.candidates();
            for(Candidate c:candidates) {
                Object[] fields={row.type(),row.status(),row.recipes(),row.dishes().size(),row.processingRecipes(),row.emptyOutputs(),
                        row.readFailures(),c==null?"":c.block(),c==null?"":c.evidence(),c==null?"":c.enabled(),
                        c==null?"":c.adapter(),c==null?"":c.capabilities(),c==null?"":c.sources(),
                        row.implementations(),row.examples(),row.dishes(),row.problems()};
                for(int i=0;i<fields.length;i++){if(i>0)out.append('\t');out.append(clean(fields[i]));}out.append('\n');
            }
        }
        return out.toString();
    }
    private static String clean(Object value){return String.valueOf(value).replace('\t',' ').replace('\r',' ').replace('\n',' ');}
}
