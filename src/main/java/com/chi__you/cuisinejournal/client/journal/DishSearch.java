package com.chi__you.cuisinejournal.client.journal;

import com.chi__you.cuisinejournal.recipe.PinyinMatch;
import java.util.List;

public final class DishSearch {
    private DishSearch() {}
    public static boolean matches(String query,String id,String name,String mod,List<String> modNames,
                                  List<String> categories,List<String> extras) {
        var parsed=IngredientSearch.parse(query);
        if(!parsed.matchesMod(mod,modNames))return false;
        String term=parsed.text();
        return term.isBlank()||PinyinMatch.contains(name,term)||PinyinMatch.contains(id,term)
                ||categories.stream().anyMatch(value->PinyinMatch.contains(value,term))
                ||extras.stream().anyMatch(value->PinyinMatch.contains(value,term));
    }
}
