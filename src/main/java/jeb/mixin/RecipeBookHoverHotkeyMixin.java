package jeb.mixin;

import client.JebClient;
import client.RecipeSearchQueries;
import jeb.accessor.RecipeBookWidgetBridge;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class RecipeBookHoverHotkeyMixin {

    @Shadow
    @Final
    private RecipeBookComponent<?> recipeBookComponent;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void jeb$onHoverHotkey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        boolean viewRecipe = JebClient.keyViewRecipe != null
                && event.key() == JebClient.keyViewRecipe.getKey().getValue();
        boolean viewUses = JebClient.keyViewUses != null
                && event.key() == JebClient.keyViewUses.getKey().getValue();

        if (!viewRecipe && !viewUses) {
            return;
        }

        Slot hovered = ((AbstractContainerScreenAccessor) this).jeb$getHoveredSlot();
        if (hovered == null || !hovered.hasItem()) {
            return;
        }

        ItemStack stack = hovered.getItem();
        String searchText = viewRecipe
                ? RecipeSearchQueries.forResult(stack)
                : RecipeSearchQueries.forIngredient(stack);

        RecipeBookComponent<?> component = this.recipeBookComponent;
        RecipeBookWidgetAccessor accessor = (RecipeBookWidgetAccessor) component;
        RecipeBookWidgetBridge bridge = (RecipeBookWidgetBridge) component;

        if (!component.isVisible()) {
            component.toggleVisibility();
        }

        bridge.jeb$pushHistory(accessor.getSearchField().getValue(), accessor.getSelectedTab());
        accessor.getSearchField().setValue(searchText);
        accessor.setSelectedTab((RecipeBookTabButton) accessor.getTabButtons().get(0));
        accessor.invokeReset();

        cir.setReturnValue(true);
    }
}
