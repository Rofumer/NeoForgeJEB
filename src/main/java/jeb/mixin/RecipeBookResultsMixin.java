package jeb.mixin;

import client.RecipeSearchQueries;
import com.mojang.blaze3d.platform.InputConstants;
import jeb.accessor.ClientRecipeBookAccessor;
import jeb.accessor.RecipeBookWidgetBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.*;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.*;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mixin(RecipeBookPage.class)
public class RecipeBookResultsMixin {

    @Final
    @Shadow
    private RecipeBookComponent<?> parent;
    @Final
    @Shadow
    private OverlayRecipeComponent overlay;

    @Shadow
    private RecipeDisplayId lastClickedRecipe;

    @Shadow
    private RecipeButton hoveredButton;



    @Shadow
    @Nullable
    private RecipeCollection lastClickedRecipeCollection;

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeButton;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
                    shift = At.Shift.AFTER
            ),
            cancellable = true

    )
    private void onRightClickInject(
            MouseButtonEvent p_447008_, int p_100412_, int p_100413_, int p_100414_, int p_100415_, boolean p_435386_, CallbackInfoReturnable<Boolean> cir
    ) {

        ContextMap context = SlotDisplayContext.fromLevel(Minecraft.getInstance().level);
        RecipeButton hovered = this.hoveredButton;

        //if (hovered.mouseClicked(mouseX, mouseY, button)) {
        if (hovered != null) {

            if (p_447008_.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
                ItemStack stack = hovered.getDisplayStack();
                String searchText = RecipeSearchQueries.forResult(stack);

                ((RecipeBookWidgetBridge) parent).jeb$pushHistory(
                        ((RecipeBookWidgetAccessor) parent).getSearchField().getValue(),
                        ((RecipeBookWidgetAccessor) parent).getSelectedTab()
                );

// Устанавливаем в поиск
                ((RecipeBookWidgetAccessor) parent).getSearchField().setValue(searchText);
                ((RecipeBookWidgetAccessor) parent).setSelectedTab((RecipeBookTabButton) ((RecipeBookWidgetAccessor) parent).getTabButtons().get(0));
                ((RecipeBookWidgetAccessor) parent).invokeReset();

                cir.setReturnValue(true);
                cir.cancel();
            }

            if (p_447008_.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                ItemStack stack = hovered.getDisplayStack();
                String searchText = RecipeSearchQueries.forIngredient(stack);

                ((RecipeBookWidgetBridge) parent).jeb$pushHistory(
                        ((RecipeBookWidgetAccessor) parent).getSearchField().getValue(),
                        ((RecipeBookWidgetAccessor) parent).getSelectedTab()
                );

// Устанавливаем в поиск
                ((RecipeBookWidgetAccessor) parent).getSearchField().setValue(searchText);
                ((RecipeBookWidgetAccessor) parent).setSelectedTab((RecipeBookTabButton) ((RecipeBookWidgetAccessor) parent).getTabButtons().get(0));
                ((RecipeBookWidgetAccessor) parent).invokeReset();

                cir.setReturnValue(true);
                cir.cancel();
            }


            if (p_447008_.button() == InputConstants.MOUSE_BUTTON_LEFT) {

                if (!(Minecraft.getInstance().player.containerMenu instanceof AbstractCraftingMenu)) {
                    // Не наш контейнер — не трогаем, пусть работает обычный код!
                    return;
                }

                //System.out.println(animatedResultButton.getCurrentId().toString());

                Minecraft client = Minecraft.getInstance();
                ClientRecipeBook recipeBook = client.player.getRecipeBook();

                Map<RecipeDisplayId, RecipeDisplayEntry> recipes = ((ClientRecipeBookAccessor) recipeBook).getRecipes();

                RecipeDisplayEntry entry = recipes.get(hovered.getCurrentRecipe());

                if(entry != null) {

                    RecipeCollection myCustomRecipeResultCollection = new RecipeCollection(List.of(entry));

                    if(!canDisplay(entry.display())) {
                        overlay.init(myCustomRecipeResultCollection, context, false, hovered.getX(), hovered.getY(), p_100412_ + p_100414_ / 2, p_100413_   + 13 + p_100415_ / 2, hovered.getWidth());
                    }
                    else
                    {
                        this.lastClickedRecipe = hovered.getCurrentRecipe();
                        this.lastClickedRecipeCollection = hovered.getCollection();
                        recipeBook.removeHighlight(hovered.getCurrentRecipe());
                        var connection = Minecraft.getInstance().getConnection();
                        if (connection != null) {
                            if(!(hovered.getCurrentRecipe().index() == 9999)) {
                                connection.send(new ServerboundRecipeBookSeenRecipePacket(hovered.getCurrentRecipe()));
                            }
                        }

                    }
                }

                cir.setReturnValue(true);
                cir.cancel();
            }
        }

    }

    private boolean canDisplay(RecipeDisplay display) {
        RecipeBookComponent<?> widget = this.parent;

        AbstractCraftingMenu handler = (AbstractCraftingMenu) Minecraft.getInstance().player.containerMenu;


        //AbstractCraftingScreenHandler handler = ((RecipeBookWidgetAccessor) widget).getCraftingScreenHandler();
        int i = handler.getGridWidth();
        int j = handler.getGridHeight();

        return switch (display) {
            case net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay shaped -> i >= shaped.width() && j >= shaped.height();
            case net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay shapeless -> i * j >= shapeless.ingredients().size();
            default -> false;
        };
    }

}
