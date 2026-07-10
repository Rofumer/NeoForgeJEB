package jeb.mixin;

import client.JebClient;
import client.RecipeIndex;
import client.SearchHistoryEntry;
import jeb.accessor.AnimatedResultButtonExtension;
import jeb.accessor.ClientRecipeBookAccessor;
import jeb.accessor.RecipeBookWidgetBridge;
import client.FavoritesManager;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.*;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

import static client.JebClient.customToggleEnabled;
import static client.JebClient.string;
import static client.JebClient.filtered;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookWidgetSearchMixin<T extends RecipeBookMenu> implements RecipeBookWidgetBridge {

    @Final
    @Shadow protected RecipeBookMenu menu;

    @Shadow @Final
    private StackedItemContents stackedContents;

    @Shadow
    private void initVisuals() {}

    @Override
    public void jeb$refresh() {
        this.initVisuals();
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
    private static final int JEB_HISTORY_LIMIT = 30;

    @Unique
    private Button jeb$backButton;

    // true после первого initVisuals() этого экземпляра — не даёт повторно
    // затирать текст поиска при каждом invokeReset() в рамках одной сессии.
    @Unique
    private boolean jeb$searchRestored = false;

    // Стек истории хранится в JebClient (по RecipeBookType), а не в @Unique-поле
    // этого миксина, чтобы переживать закрытие/переоткрытие экрана крафта —
    // по той же причине, что и восстановление текста поиска.
    @Unique
    private Deque<SearchHistoryEntry> jeb$history() {
        return JebClient.searchHistoryByType.computeIfAbsent(menu.getRecipeBookType(), k -> new ArrayDeque<>());
    }

    @Override
    public void jeb$pushHistory(String query, RecipeBookTabButton tab) {
        ExtendedRecipeBookCategory category = tab != null ? tab.getCategory() : null;
        Deque<SearchHistoryEntry> history = jeb$history();
        SearchHistoryEntry top = history.peekLast();
        if (top != null && top.query().equals(query) && Objects.equals(top.category(), category)) return;

        history.addLast(new SearchHistoryEntry(query, category));
        if (history.size() > JEB_HISTORY_LIMIT) {
            history.removeFirst();
        }
    }

    @Override
    public boolean jeb$goBack() {
        SearchHistoryEntry entry = jeb$history().pollLast();
        if (entry == null) return false;

        searchBox.setValue(entry.query());
        if (entry.category() != null) {
            // Кнопки вкладок пересоздаются при каждом initVisuals(), поэтому
            // сохранённую вкладку ищем заново по категории, а не по ссылке на объект.
            for (RecipeBookTabButton candidate : tabButtons) {
                if (candidate.getCategory().equals(entry.category())) {
                    selectedTab = candidate;
                    break;
                }
            }
        }
        ((RecipeBookWidgetAccessor) (Object) this).invokeReset();
        return true;
    }

    @Override
    public boolean jeb$hasHistory() {
        return !jeb$history().isEmpty();
    }

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

    // --- глушим стандартный selectMatchingRecipes ---
    @Inject(method = "selectMatchingRecipes()V", at = @At("HEAD"), cancellable = true)
    private void populateAllRecipes(CallbackInfo ci) {
        ci.cancel();
    }

    // Подменяем "oldEdit" ровно в момент его вычисления в оригинальном initVisuals():
    // ванильный код тут же (в конце того же метода) сам вызывает updateCollections()
    // на ещё пустом searchBox, поэтому восстанавливать текст нужно ДО этого вызова,
    // а не после него в отдельном @Inject(at = TAIL) — иначе onCustomSearch успевает
    // затереть сохранённый запрос пустой строкой раньше, чем мы его восстановим.
    @ModifyVariable(method = "initVisuals", at = @At("STORE"), ordinal = 0)
    private String jeb$restoreSearchOnFirstInit(String oldEdit) {
        if (this.searchBox == null && !jeb$searchRestored) {
            jeb$searchRestored = true;
            String saved = JebClient.lastSearchByType.get(menu.getRecipeBookType());
            if (saved != null && !saved.isEmpty()) {
                return saved;
            }
        }
        return oldEdit;
    }

    // --- создаём нашу кастомную кнопку на основе CycleButton<Boolean> ---
    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void jeb$addCustomToggleButton(CallbackInfo ci) {
        int x = this.filterButton.getX();
        int y = this.filterButton.getY() + 125;

        jeb$customToggleState = customToggleEnabled;

        jeb$customToggleButton = CycleButton.onOffBuilder(customToggleEnabled)
                .withTooltip(value ->
                        Tooltip.create(Component.literal(value ? "Show 3x3" : "Show 2x2")))
                .withSprite((button, value) -> {
                    WidgetSprites sprites = value ? TEXTURES_ALT : TEXTURES_DEFAULT;
                    return sprites.get(true, button.isHoveredOrFocused());
                })
                .displayState(CycleButton.DisplayState.HIDE)
                .create(x, y, 20, 16, Component.literal("!"),
                        (cycle, value) -> {
                            // коллбэк при смене состояния
                            jeb$customToggleState = value;
                            customToggleEnabled = value;
                            JebClient.saveConfig();
                            ((RecipeBookWidgetBridge) this).jeb$refresh();
                        });

        jeb$customToggleButton.visible = true;

        jeb$backButton = Button.builder(Component.literal("<"), button -> this.jeb$goBack())
                .tooltip(Tooltip.create(Component.translatable("jeb.recipe_book.back")))
                .pos(x + 22, y)
                .size(16, 16)
                .build();
    }

    // рисуем кнопку сразу после ванильной filterButton
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/CycleButton;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void jeb$renderCustomToggle(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.visible) {
            jeb$customToggleButton.render(graphics, mouseX, mouseY, partialTick);
        }

        if (jeb$backButton != null) {
            jeb$backButton.visible = jeb$hasHistory();
            if (jeb$backButton.visible) {
                jeb$backButton.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    // клики по нашей кнопке
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void jeb$clickCustomToggle(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.mouseClicked(event, doubleClick)) {
            // состояние уже обработано в onValueChange колбэке
            cir.setReturnValue(true);
        }

        if (jeb$backButton != null && jeb$backButton.visible && jeb$backButton.mouseClicked(event, doubleClick)) {
            // jeb$goBack() уже вызван в callback у builder
            cir.setReturnValue(true);
        }
    }

    // === Favourites tab ===

    @Unique
    private boolean isFavoritesTabActive() {
        return selectedTab.getCategory() == net.minecraft.world.item.crafting.RecipeBookCategories.CAMPFIRE;
    }

    // хоткей на наведённый рецепт (избранное / удалить из избранного)
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

    // после tryPlaceRecipe – показываем ghost, если крафт невозможен
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
        if (client.player == null) return;

        ClientRecipeBook recipeBook = client.player.getRecipeBook();
        Map<RecipeDisplayId, RecipeDisplayEntry> recipes =
                ((ClientRecipeBookAccessor) recipeBook).getRecipes();

        RecipeDisplayEntry entry = recipes.get(recipe);
        Screen screen = client.screen;

        if (screen instanceof RecipeUpdateListener provider && entry != null) {
            if (!recipeCollection.isCraftable(recipe) && recipe.index() != 9999) {
                provider.fillGhostRecipe(entry.display());
            }
        }
    }

    // === поиск по ингредиентам ===
    @Unique
    private boolean recipeDisplayMatchesIngredientQuery(RecipeDisplayEntry entry, String query) {
        if (entry.craftingRequirements().isEmpty()) return false;

        return entry.craftingRequirements().get().stream().anyMatch(ingredient ->
                ingredient.items().anyMatch(regEntry -> {
                    ItemStack stack = new ItemStack(regEntry.value());
                    String itemName = stack.getItem().getName().getString().toLowerCase(Locale.ROOT);
                    return itemName.contains(query);
                })
        );
    }

    // === поиск по результату + мод + тултипы ===
    @Unique
    private boolean recipeResultMatchesQuery(RecipeDisplayEntry entry, String query, String modName) {
        SlotDisplay resultSlot = entry.display().result();

        ContextMap context = SlotDisplayContext.fromLevel(
                Objects.requireNonNull(minecraft.level)
        );

        List<ItemStack> stacks = resultSlot.resolveForStacks(context);
        if (stacks.isEmpty()) return false;

        ItemStack stack = stacks.get(0);
        if (stack == null || stack.isEmpty()) return false;

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
                if (clean.contains(query)) return true;
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
        SlotDisplay.ItemStackSlotDisplay resultSlot = new SlotDisplay.ItemStackSlotDisplay(stack.copy());
        SlotDisplay.ItemSlotDisplay stationSlot =
                new SlotDisplay.ItemSlotDisplay(BuiltInRegistries.ITEM.getValue(
                        Identifier.fromNamespaceAndPath("minecraft", "crafting_table")));

        RecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
        OptionalInt group = OptionalInt.empty();
        RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;
        List<Ingredient> ingredients = List.of(Ingredient.of(stack.getItem()));

        return new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
    }

    // === основной кастомный поиск ===
    @Inject(method = "updateCollections", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String rawInput = searchBox.getValue();
        if (rawInput == null) rawInput = "";

        JebClient.lastSearchByType.put(menu.getRecipeBookType(), rawInput);

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

        // === режим ~item_id → показывать ингредиенты результата ===
        if (rawInput.startsWith("~") && !isFavoritesTabActive()) {
            ContextMap context = SlotDisplayContext.fromLevel(
                    Objects.requireNonNull(minecraft.level)
            );

            List<RecipeCollection> ingredientsList = new ArrayList<>();

            for (RecipeCollection collection : book.getCollection(selectedTab.getCategory())) {
                for (RecipeDisplayEntry recipe1 : collection.getRecipes()) {
                    SlotDisplay resultSlot = recipe1.display().result();
                    List<ItemStack> stacks = resultSlot.resolveForStacks(context);
                    if (stacks.isEmpty()) continue;

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
                                            if (subStacks.isEmpty()) continue;

                                            ItemStack subResult = subStacks.get(0);
                                            if (!subResult.isEmpty() && ItemStack.isSameItem(subResult, stack)) {
                                                subCollection.selectRecipes(stackedContents, r -> true);
                                                ingredientsList.add(subCollection);
                                                foundReal = true;
                                                break;
                                            }
                                        }
                                        if (foundReal) break;
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

        // === вкладка избранного ===
        if (isFavoritesTabActive()) {
            collections = book.getCollection(SearchRecipeBookCategory.CRAFTING);
            Set<Identifier> favoriteItems = FavoritesManager.loadFavoriteItemIds();
            ContextMap context = SlotDisplayContext.fromLevel(Objects.requireNonNull(minecraft.level));

            for (RecipeCollection collection : collections) {
                boolean hasFavorite = collection.getRecipes().stream()
                        .flatMap(entry -> entry.resultItems(context).stream())
                        .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
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

        // === обычный / расширенный поиск через индекс ===
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

        // filterButton.getValue() == true → «только крафтимые»
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
