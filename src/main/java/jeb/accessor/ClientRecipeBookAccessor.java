package jeb.accessor;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.util.Map;

public interface ClientRecipeBookAccessor {
    Map<RecipeDisplayId, RecipeDisplayEntry> getRecipes();
}
