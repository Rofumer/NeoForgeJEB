package jeb.mixin;

import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
//target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection;filter(Lnet/minecraft/client/gui/screen/recipebook/RecipeResultCollection$RecipeFilterMode;)Ljava/util/List;",
@Mixin(OverlayRecipeComponent.class)
public class RecipeAlternativesWidgetMixin {
    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;getSelectedRecipes(Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection$CraftableStatus;)Ljava/util/List;",
                    ordinal = 1
            )
    )
    private List<net.minecraft.world.item.crafting.display.RecipeDisplayEntry> redirectList2Filter(RecipeCollection instance, RecipeCollection.CraftableStatus craftableStatus) {
        return instance.getRecipes();
    }

}
