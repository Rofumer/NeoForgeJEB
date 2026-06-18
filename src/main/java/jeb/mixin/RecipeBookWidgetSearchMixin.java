package jeb.mixin;

import client.FavoritesManager;
import client.JebClient;
import client.RecipeIndex;
import jeb.accessor.AnimatedResultButtonExtension;
import jeb.accessor.ClientRecipeBookAccessor;
import jeb.accessor.RecipeBookWidgetBridge;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static client.JebClient.customToggleEnabled;
import static client.JebClient.filtered;
import static client.JebClient.string;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookWidgetSearchMixin<T extends RecipeBookMenu> implements RecipeBookWidgetBridge {

    @Final
    @Shadow
    protected RecipeBookMenu menu;

    @Shadow
    @Final
    private StackedItemContents stackedContents;

    @Shadow
    private void initVisuals() {
    }

    @Shadow
    private ClientRecipeBook book;

    @Shadow
    private RecipeBookTabButton selectedTab;

    @Shadow
    protected Minecraft minecraft;

    @Final
    @Shadow
    private RecipeBookPage recipeBookPage;

    @Shadow
    private EditBox searchBox;

    @Shadow
    @Final
    private List<RecipeBookComponent.TabInfo> tabInfos;

    @Shadow
    @Final
    private List<RecipeBookTabButton> tabButtons;

    @Shadow
    protected CycleButton<Boolean> filterButton;

    @Unique
    private CycleButton<Boolean> jeb$customToggleButton;

    @Unique
    private boolean jeb$customToggleState = false;

    @Unique
    private static final WidgetSprites TEXTURES_ALT = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/crafting_overlay"),
            Identifier.withDefaultNamespace("recipe_book/crafting_overlay_highlighted")
    );

    @Unique
    private static final WidgetSprites TEXTURES_DEFAULT = new WidgetSprites(
            Identifier.withDefaultNamespace("recipe_book/crafting_overlay_disabled"),
            Identifier.withDefaultNamespace("recipe_book/crafting_overlay_disabled_highlighted")
    );

    @Override
    public void jeb$refresh() {
        this.initVisuals();
    }

    @Inject(method = "selectMatchingRecipes()V", at = @At("HEAD"), cancellable = true)
    private void populateAllRecipes(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void jeb$addCustomToggleButton(CallbackInfo ci) {
        int x = this.filterButton.getX();
        int y = this.filterButton.getY() + 125;

        jeb$customToggleState = customToggleEnabled;

        jeb$customToggleButton = CycleButton.onOffBuilder(customToggleEnabled)
                .withTooltip(value -> Tooltip.create(Component.literal(value ? "Show 3x3" : "Show 2x2")))
                .withSprite((button, value) -> {
                    WidgetSprites sprites = value ? TEXTURES_ALT : TEXTURES_DEFAULT;
                    return sprites.get(true, button.isHoveredOrFocused());
                })
                .displayState(CycleButton.DisplayState.HIDE)
                .create(x, y, 20, 16, Component.literal("!"), (cycle, value) -> {
                    jeb$customToggleState = value;
                    customToggleEnabled = value;
                    JebClient.saveConfig();
                    ((RecipeBookWidgetBridge) this).jeb$refresh();
                });

        jeb$customToggleButton.visible = true;
    }

    @Inject(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/CycleButton;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void jeb$renderCustomToggle(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.visible) {
            jeb$customToggleButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void jeb$clickCustomToggle(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.mouseClicked(event, doubleClick)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean isFavoritesTabActive() {
        return selectedTab.getCategory() == RecipeBookCategories.CAMPFIRE;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (JebClient.keyBinding2 != null && event.key() == JebClient.keyBinding2.getKey().getValue()) {
            RecipeButton hovered = ((RecipeBookResultsAccessor) recipeBookPage).getHoveredResultButton();
            if (hovered != null) {
                if (isFavoritesTabActive()) {
                    FavoritesManager.removeFavorite(hovered.getDisplayStack());
                    ((RecipeBookWidgetBridge) this).jeb$refresh();
                } else {
                    FavoritesManager.saveFavorite(hovered.getDisplayStack());
                }
                ((AnimatedResultButtonExtension) hovered).jeb$flash();
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(
            method = "tryPlaceRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePlaceRecipe(ILnet/minecraft/world/item/crafting/display/RecipeDisplayId;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRecipeClicked(RecipeCollection recipeCollection, RecipeDisplayId recipe, boolean useMaxItems, CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        ClientRecipeBook recipeBook = client.player.getRecipeBook();
        Map<RecipeDisplayId, RecipeDisplayEntry> recipes =
                ((ClientRecipeBookAccessor) recipeBook).getRecipes();

        RecipeDisplayEntry entry = recipes.get(recipe);
        Screen screen = client.gui.screen();

        if (screen instanceof RecipeUpdateListener provider && entry != null) {
            if (!recipeCollection.isCraftable(recipe) && recipe.index() != 9999) {
                provider.fillGhostRecipe(entry.display());
            }
        }
    }

    @Unique
    private boolean recipeDisplayMatchesIngredientQuery(RecipeDisplayEntry entry, String query) {
        if (entry.craftingRequirements().isEmpty()) {
            return false;
        }

        return entry.craftingRequirements().get().stream().anyMatch(ingredient ->
                ingredient.items().anyMatch(regEntry -> {
                    ItemStack stack = new ItemStack(regEntry.value());
                    String itemName = stack.getItem().getName(stack).getString().toLowerCase(Locale.ROOT);
                    return itemName.contains(query);
                })
        );
    }

    @Unique
    private boolean recipeResultMatchesQuery(RecipeDisplayEntry entry, String query, String modName) {
        SlotDisplay resultSlot = entry.display().result();

        ContextMap context = SlotDisplayContext.fromLevel(
                Objects.requireNonNull(minecraft.level)
        );

        List<ItemStack> stacks = resultSlot.resolveForStacks(context);
        if (stacks.isEmpty()) {
            return false;
        }

        ItemStack stack = stacks.get(0);
        if (stack.isEmpty()) {
            return false;
        }

        String name = stack.getDisplayName().getString().toLowerCase(Locale.ROOT);
        String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
        String key = "";
        Component nameComponent = stack.getHoverName();
        if (nameComponent.getContents() instanceof TranslatableContents translatable) {
            key = translatable.getKey().toLowerCase(Locale.ROOT);
        }

        if (modName != null && !modName.isEmpty()
                && !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(modName)) {
            return false;
        }

        if (name.contains(query) || id.contains(query) || key.contains(query)) {
            return true;
        }

        TooltipFlag tooltipFlag = minecraft.options.advancedItemTooltips
                ? TooltipFlag.Default.ADVANCED
                : TooltipFlag.Default.NORMAL;

        try {
            List<Component> tooltip = stack.getTooltipLines(
                    Item.TooltipContext.of(this.minecraft.level),
                    minecraft.player,
                    tooltipFlag
            );

            for (Component line : tooltip) {
                String clean = net.minecraft.ChatFormatting.stripFormatting(line.getString())
                        .toLowerCase(Locale.ROOT)
                        .trim();
                if (clean.contains(query)) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private static RecipeDisplayEntry createDummySingleItemRecipe(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        RecipeDisplayId recipeId = new RecipeDisplayId(9999);

        List<SlotDisplay> slots = List.of(
                new SlotDisplay.TagSlotDisplay(TagKey.create(Registries.ITEM, id))
        );

        SlotDisplay.ItemStackSlotDisplay resultSlot = new SlotDisplay.ItemStackSlotDisplay(
                ItemStackTemplate.fromNonEmptyStack(stack.copy())
        );

        SlotDisplay.ItemSlotDisplay stationSlot =
                new SlotDisplay.ItemSlotDisplay(
                        BuiltInRegistries.ITEM.getValue(
                                Identifier.fromNamespaceAndPath("minecraft", "crafting_table")
                        )
                );

        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
        OptionalInt group = OptionalInt.empty();
        RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;
        List<Ingredient> ingredients = List.of(Ingredient.of(stack.getItem()));

        return new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
    }

    @Inject(method = "updateCollections", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String rawInput = searchBox.getValue();
        if (rawInput == null) {
            rawInput = "";
        }

        boolean searchIngredients = rawInput.startsWith("#");
        boolean searchByResult = rawInput.startsWith("~");
        String query = (searchIngredients || searchByResult ? rawInput.substring(1) : rawInput)
                .toLowerCase(Locale.ROOT);

        String modName = null;
        if (rawInput.startsWith("@")) {
            int endIndex = rawInput.indexOf(" ");
            if (endIndex != -1) {
                modName = rawInput.substring(1, endIndex).trim();
                query = rawInput.substring(endIndex + 1).toLowerCase(Locale.ROOT);
            } else {
                modName = rawInput.substring(1).trim();
                query = "";
            }
        }

        List<RecipeCollection> collections = book.getCollection(selectedTab.getCategory());
        List<RecipeCollection> filteredList = new ArrayList<>();

        if (rawInput.startsWith("~") && !isFavoritesTabActive()) {
            ContextMap context = SlotDisplayContext.fromLevel(
                    Objects.requireNonNull(minecraft.level)
            );

            List<RecipeCollection> ingredientsList = new ArrayList<>();

            for (RecipeCollection collection : book.getCollection(selectedTab.getCategory())) {
                for (RecipeDisplayEntry recipe1 : collection.getRecipes()) {
                    SlotDisplay resultSlot = recipe1.display().result();
                    List<ItemStack> stacks = resultSlot.resolveForStacks(context);
                    if (stacks.isEmpty()) {
                        continue;
                    }

                    ItemStack result = stacks.get(0);
                    String resultName = BuiltInRegistries.ITEM.getKey(result.getItem())
                            .getPath()
                            .toLowerCase(Locale.ROOT);

                    if (resultName.equals(query)) {
                        for (Ingredient ingredient : recipe1.craftingRequirements().get()) {
                            for (ItemStack stack : ingredient.display().resolveForStacks(context)) {
                                if (!stack.isEmpty()) {
                                    boolean foundReal = false;
                                    for (RecipeCollection subCollection : book.getCollection(SearchRecipeBookCategory.CRAFTING)) {
                                        for (RecipeDisplayEntry subRecipe : subCollection.getRecipes()) {
                                            SlotDisplay subResultSlot = subRecipe.display().result();
                                            List<ItemStack> subStacks = subResultSlot.resolveForStacks(context);
                                            if (subStacks.isEmpty()) {
                                                continue;
                                            }

                                            ItemStack subResult = subStacks.get(0);
                                            if (!subResult.isEmpty() && ItemStack.isSameItem(subResult, stack)) {
                                                subCollection.selectRecipes(stackedContents, r -> true);
                                                ingredientsList.add(subCollection);
                                                foundReal = true;
                                                break;
                                            }
                                        }
                                        if (foundReal) {
                                            break;
                                        }
                                    }
                                    if (!foundReal) {
                                        RecipeDisplayEntry fakeRecipe = createDummySingleItemRecipe(stack);
                                        RecipeCollection fakeCollection = new RecipeCollection(List.of(fakeRecipe));
                                        fakeCollection.selectRecipes(stackedContents, r -> true);
                                        ingredientsList.add(fakeCollection);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            filteredList.addAll(ingredientsList);
            recipeBookPage.updateCollections(filteredList, resetCurrentPage, filteringCraftable);
            ci.cancel();
            return;
        }

        if (isFavoritesTabActive()) {
            collections = book.getCollection(SearchRecipeBookCategory.CRAFTING);
            Set<Identifier> favoriteItems = FavoritesManager.loadFavoriteItemIds();
            ContextMap context = SlotDisplayContext.fromLevel(Objects.requireNonNull(minecraft.level));

            for (RecipeCollection collection : collections) {
                boolean hasFavorite = collection.getRecipes().stream()
                        .flatMap(entry -> entry.resultItems(context).stream())
                        .map(stack1 -> BuiltInRegistries.ITEM.getKey(stack1.getItem()))
                        .anyMatch(favoriteItems::contains);

                if (hasFavorite) {
                    collection.selectRecipes(stackedContents, r -> true);
                    filteredList.add(collection);
                }
            }

            recipeBookPage.updateCollections(filteredList, resetCurrentPage, filteringCraftable);
            ci.cancel();
            return;
        }

        filteredList = new ArrayList<>(RecipeIndex.fastSearch(
                selectedTab.getCategory(), query, modName, searchIngredients));

        for (RecipeCollection col : filteredList) {
            if (customToggleEnabled) {
                col.selectRecipes(stackedContents, r -> true);
            } else {
                col.selectRecipes(stackedContents, this::jEB$canDisplay);
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftable());
        }

        if (!Objects.equals(string, rawInput)) {
            filtered = RecipeIndex.generateCustomRecipeList(rawInput);
        }

        if (!filterButton.getValue()) {
            filteredList.addAll(filtered);
        }

        string = rawInput;

        recipeBookPage.updateCollections(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }

    @Unique
    private boolean jEB$canDisplay(RecipeDisplay display) {
        if (!(this.menu instanceof AbstractCraftingMenu craftingHandler)) {
            return true;
        }

        int w = craftingHandler.getGridWidth();
        int h = craftingHandler.getGridHeight();

        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return w >= shaped.width() && h >= shaped.height();
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return w * h >= shapeless.ingredients().size();
        } else {
            return false;
        }
    }
}