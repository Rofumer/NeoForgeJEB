package jeb.mixin;

import jeb.accessor.ClientRecipeBookAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.*;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.Locale;
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
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeButton;mouseClicked(DDI)Z",
                    shift = At.Shift.AFTER
            ),
            cancellable = true

    )
    private void onRightClickInject(
            double mouseX, double mouseY, int button, int x, int y, int width, int height, CallbackInfoReturnable<Boolean> cir
    ) {

        if (!(Minecraft.getInstance().player.containerMenu instanceof AbstractCraftingMenu)) {
            // Не наш контейнер — не трогаем, пусть работает обычный код!
            return;
        }

        ContextMap context = SlotDisplayContext.fromLevel(Minecraft.getInstance().level);
        RecipeButton hovered = this.hoveredButton;

        //if (hovered.mouseClicked(mouseX, mouseY, button)) {
        if (hovered != null) {

            if (button == 2) {
                ItemStack stack = hovered.getDisplayStack();
                //String itemName = stack.getItem().getName().getString();
                String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
                String searchText = "~" + itemName.toLowerCase(Locale.ROOT);

// Устанавливаем в поиск
                ((RecipeBookWidgetAccessor) parent).getSearchField().setValue(searchText);
                ((RecipeBookWidgetAccessor) parent).setSelectedTab((RecipeBookTabButton) ((RecipeBookWidgetAccessor) parent).getTabButtons().get(0));
                ((RecipeBookWidgetAccessor) parent).invokeReset();

                cir.setReturnValue(true);
                cir.cancel();
            }

            if (button == 1) {
                ItemStack stack = hovered.getDisplayStack();
                String itemName = stack.getItem().getName().getString(); // Локализованное имя (например, "Булыжник")
                String searchText = "#" + itemName.toLowerCase(Locale.ROOT);

// Устанавливаем в поиск
                ((RecipeBookWidgetAccessor) parent).getSearchField().setValue(searchText);
                ((RecipeBookWidgetAccessor) parent).setSelectedTab((RecipeBookTabButton) ((RecipeBookWidgetAccessor) parent).getTabButtons().get(0));
                ((RecipeBookWidgetAccessor) parent).invokeReset();

                cir.setReturnValue(true);
                cir.cancel();
            }


            if (button == 0) {


                //System.out.println(animatedResultButton.getCurrentId().toString());

                Minecraft client = Minecraft.getInstance();
                ClientRecipeBook recipeBook = client.player.getRecipeBook();

                Map<RecipeDisplayId, RecipeDisplayEntry> recipes = ((ClientRecipeBookAccessor) recipeBook).getRecipes();

                RecipeDisplayEntry entry = recipes.get(hovered.getCurrentRecipe());

                if(entry != null) {

                    RecipeCollection myCustomRecipeResultCollection = new RecipeCollection(List.of(entry));

                    if(!canDisplay(entry.display())) {
                        overlay.init(myCustomRecipeResultCollection, context, false, hovered.getX(), hovered.getY(), x + width / 2, y   + 13 + height / 2, hovered.getWidth());
                    }
                    else
                    {
                        this.lastClickedRecipe = hovered.getCurrentRecipe();
                        this.lastClickedRecipeCollection = hovered.getCollection();
                        recipeBook.removeHighlight(hovered.getCurrentRecipe());
                        var connection = Minecraft.getInstance().getConnection();
                        if (connection != null) {
                            connection.send(new ServerboundRecipeBookSeenRecipePacket(hovered.getCurrentRecipe()));
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
