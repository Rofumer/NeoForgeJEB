package jeb.mixin;

import jeb.accessor.AnimatedResultButtonExtension;
import jeb.accessor.ClientRecipeBookAccessor;
import jeb.accessor.RecipeBookWidgetBridge;
import client.FavoritesManager;
import jeb.Jeb;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.display.*;
import net.minecraft.client.gui.screens.recipebook.*;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.google.common.collect.Lists;
import net.minecraft.world.inventory.RecipeBookMenu;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


import java.util.*;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookWidgetSearchMixin<T extends RecipeBookMenu> implements RecipeBookWidgetBridge {

    // Это будет вызов приватного метода
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
    protected StateSwitchingButton filterButton;

    @Unique
    private StateSwitchingButton jeb$customToggleButton;

    @Unique
    private boolean jeb$customToggleState = false;

    @Unique
    private static final WidgetSprites TEXTURES_ALT = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/crafting_overlay"),
            ResourceLocation.withDefaultNamespace("recipe_book/crafting_overlay_highlighted")
    );

    @Unique
    private static final WidgetSprites TEXTURES_DEFAULT = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("recipe_book/crafting_overlay_disabled"),
            ResourceLocation.withDefaultNamespace("recipe_book/crafting_overlay_disabled_highlighted")
    );


    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void jeb$addCustomToggleButton(CallbackInfo ci) {
        int x = this.filterButton.getX();
        int y = this.filterButton.getY()+125;

        jeb$customToggleButton = new StateSwitchingButton(x, y, 20, 16, false);
        if(Jeb.customToggleEnabled){
            jeb$customToggleButton.setTooltip(Tooltip.create(Component.literal("Show 3x3")));
            jeb$customToggleButton.initTextureValues(TEXTURES_ALT);
        }
        else
        {
            jeb$customToggleButton.setTooltip(Tooltip.create(Component.literal("Show 2x2")));
            jeb$customToggleButton.initTextureValues(TEXTURES_DEFAULT);
        }
        jeb$customToggleButton.setMessage(Component.literal("!"));
        jeb$customToggleButton.visible = true;

    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/StateSwitchingButton;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    ordinal = 0, // если их несколько, выбирай нужный
                    shift = At.Shift.AFTER
            )
    )
    private void jeb$renderCustomToggle(GuiGraphics p_283597_, int p_282668_, int p_283506_, float p_282813_, CallbackInfo ci) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.visible) {
            jeb$customToggleButton.render(p_283597_, p_282668_, p_283506_, p_282813_);
        }
    }


    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void jeb$clickCustomToggle(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (jeb$customToggleButton != null && jeb$customToggleButton.mouseClicked(mouseX, mouseY, button)) {
            jeb$customToggleState = !jeb$customToggleState;
            jeb$customToggleButton.setStateTriggered(jeb$customToggleState);
            Jeb.customToggleEnabled = !Jeb.customToggleEnabled;

            Jeb.saveConfig();
            // Меняем текстуру в зависимости от состояния
            jeb$customToggleButton.initTextureValues(Jeb.customToggleEnabled ? TEXTURES_ALT : TEXTURES_DEFAULT);

            jeb$customToggleButton.setTooltip(Jeb.customToggleEnabled ? Tooltip.create(Component.literal("Show 3x3")):Tooltip.create(Component.literal("Show 2x2")));

            //System.out.println("Кастомная кнопка: " + (jeb$customToggleState ? "включена" : "выключена"));

            // Рефреш через reflection
            /*try {
                Method method = RecipeBookWidget.class.getDeclaredMethod("refresh");
                method.setAccessible(true);
                method.invoke(this);
            } catch (Exception e) {
                e.printStackTrace();
            }*/

            ((RecipeBookWidgetBridge) this).jeb$refresh();

            cir.setReturnValue(true);
        }
    }


    /*@Inject(
            method = "reset",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;clear()V",
                    shift = At.Shift.AFTER
            )
    )
    private void injectCustomTab(CallbackInfo ci) {


        // Создаём кнопку вкладки
        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(Items.WRITABLE_BOOK, RecipeBookCategories.CAMPFIRE);
        RecipeGroupButtonWidget tabButton = new RecipeGroupButtonWidget(newTab);
        tabButton.setMessage(Text.of("Favorites"));


        this.tabButtons.add(tabButton);

    }*/

    /*@Inject(method = "<init>", at = @At("RETURN"))
    private void injectAfterConstructor(T craftingScreenHandler, List<RecipeBookWidget.Tab> tabs, CallbackInfo ci) {

        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(Items.WRITABLE_BOOK, RecipeBookCategories.CAMPFIRE);
        //RecipeGroupButtonWidget tabButton = new RecipeGroupButtonWidget(newTab);
        //tabButton.setMessage(Text.of("Favorites"));
        this.tabs.add(newTab);

    }*/

    /*@Inject(
            method = "reset",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/recipebook/RecipeGroupButtonWidget;setToggled(Z)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void jeb$replaceFavoritesAsDefaultTab(CallbackInfo ci) {
        if (this.currentTab == tabButtons.get(0) && tabButtons.size() > 1) {
            RecipeGroupButtonWidget maybeFavorites = tabButtons.get(0);
            //if ("Favorites".equals(maybeFavorites.getMessage().getString())) {
                // Сбросить подсветку со старой
                //maybeFavorites.setToggled(true);
                maybeFavorites.setToggled(true);

            //this.refreshTabButtons(bl);

            ((RecipeBookWidgetAccessor) this).jeb$populateAllRecipes();

            ((RecipeBookWidgetAccessor) this).jeb$refreshTabButtons(true);

                // Назначить новую
                this.currentTab = tabButtons.get(1);

            ((RecipeBookWidgetAccessor) this).jeb$populateAllRecipes();

            ((RecipeBookWidgetAccessor) this).jeb$refreshTabButtons(true);
            //}
        }
    }*/

    /*@Unique
    private boolean isFavoritesTabActive() {
        if (currentTab == null) return false;

        return tabButtons.stream()
                .filter(button -> button.isSelected())
                .anyMatch(button -> "Favorites".equals(button.getMessage().getString()));
    }*/

    @Unique
    private boolean isFavoritesTabActive() {
        //return currentTab != null
        //        && currentTab.getMessage() != null
        //        && "Favorites".equals(currentTab.getMessage().getString());
        return selectedTab.getCategory() == net.minecraft.world.item.crafting.RecipeBookCategories.CAMPFIRE;
    }


    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // Проверка на нужную клавишу (например, клавиша G, keyCode = 71)
        if (keyCode == GLFW.GLFW_KEY_A) {
            RecipeButton hovered = ((RecipeBookResultsAccessor) recipeBookPage).getHoveredResultButton();
            if (hovered != null) {
                //System.out.println("Над кнопкой: " + hovered.getDisplayStack().getItem().toString());
                //ItemStack stack = hovered.getDisplayStack();
                if (isFavoritesTabActive()) {
                    FavoritesManager.removeFavorite(hovered.getDisplayStack());
                    // Рефреш через reflection
                    /*try {
                        Method method = RecipeBookWidget.class.getDeclaredMethod("refresh");
                        method.setAccessible(true);
                        method.invoke(this);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }*/
                    ((RecipeBookWidgetBridge) this).jeb$refresh();
                } else {
                    FavoritesManager.saveFavorite(hovered.getDisplayStack());
                }
                //FavoritesManager.saveFavorite(stack);
                ((AnimatedResultButtonExtension) hovered).jeb$flash();
                // Здесь можно выполнить любое действие, например, выбрать рецепт, показать информацию и т.д.
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "tryPlaceRecipe", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePlaceRecipe(ILnet/minecraft/world/item/crafting/display/RecipeDisplayId;Z)V",
            shift = At.Shift.AFTER
    ))
    private void onRecipeClicked(RecipeCollection recipeCollection, RecipeDisplayId recipe, CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        ClientRecipeBook recipeBook = client.player.getRecipeBook();

        Map<RecipeDisplayId, RecipeDisplayEntry> recipes = ((ClientRecipeBookAccessor) recipeBook).getRecipes();

        RecipeDisplayEntry entry = recipes.get(recipe);

        Screen screen = client.screen;

        if (screen instanceof RecipeUpdateListener provider && entry != null) {
            //System.out.println("РецептL " + entry.display().toString());
            if(!recipeCollection.isCraftable(recipe) && recipe.index()!=9999) {
                provider.fillGhostRecipe(entry.display());
            }
        }
    }


    /*@Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomIngredientSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String string = searchField.getText();
        if (!string.startsWith("#")) return;

        String query = string.substring(1).toLowerCase(Locale.ROOT);
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        List<RecipeResultCollection> originalList = recipeBook.getResultsForCategory(currentTab.getCategory());
        List<RecipeResultCollection> filteredList = Lists.newArrayList();

        for (RecipeResultCollection collection : originalList) {
            if (!collection.hasDisplayableRecipes()) continue;

            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                if (recipeDisplayMatchesIngredientQuery(entry, query)) {
                    filteredList.add(collection);
                    break;
                }
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
        }

        recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }*/

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

    /****@Unique
    private boolean recipeResultMatchesQuery(RecipeDisplayEntry entry, String query) {
        if (entry.display() == null || entry.display().result() == null) return false;

        SlotDisplay resultSlot = entry.display().result();

        ContextParameterMap context = SlotDisplayContexts.createParameters(
                Objects.requireNonNull(this.client.world)
        );

        List<ItemStack> stacks = resultSlot.getStacks(context);
        if (stacks.isEmpty()) return false;

        ItemStack stack = stacks.get(0);
        if (stack == null || stack.isEmpty()) return false;

        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        String id = stack.getItem().toString().toLowerCase(Locale.ROOT);
        String key = stack.getItem().getTranslationKey().toLowerCase(Locale.ROOT);

        if (name.contains(query) || id.contains(query) || key.contains(query)) {
            return true;
        }

        // Поиск по тултипам
        RegistryWrapper.WrapperLookup lookup = client.world.getRegistryManager();
        Item.TooltipContext tooltipContext = Item.TooltipContext.create(lookup);
        TooltipType tooltipType = TooltipType.Default.BASIC;

        List<Text> tooltip = stack.getTooltip(tooltipContext, client.player, tooltipType);
        for (Text line : tooltip) {
            String clean = Formatting.strip(line.getString()).toLowerCase(Locale.ROOT).trim();
            if (clean.contains(query)) return true;
        }

        return false;
    }



    @Inject(method = "refreshResults", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String string = searchField.getText();
        //if (string.isEmpty()) return;

        boolean searchIngredients = string.startsWith("#");
        String query = (searchIngredients ? string.substring(1) : string).toLowerCase();

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return;

        List<RecipeResultCollection> originalList = recipeBook.getResultsForCategory(currentTab.getCategory());
        List<RecipeResultCollection> filteredList = Lists.newArrayList();

        for (RecipeResultCollection collection : originalList) {
            if (!collection.hasDisplayableRecipes()) continue;

            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                boolean match =
                        recipeResultMatchesQuery(entry, query) ||
                                (searchIngredients && recipeDisplayMatchesIngredientQuery(entry, query));

                if (match) {
                    filteredList.add(collection);
                    break;
                }
            }
        }

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
        }****/

        //System.out.println("filteredList содержит " + filteredList.size() + " рецептов");

        // Получаем доступ к searchField через наш accessor

        // Получаем текст из поля поиска

        // 🔹 Собираем все предметы, уже встречающиеся в filteredList как результат
        /*Set<Item> existingResultItems = new HashSet<>();
        for (RecipeResultCollection collection : filteredList) {
            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                getItemFromSlotDisplay(entry.display().result()).ifPresent(existingResultItems::add);
            }
        }*/


        /// ////////
        /*for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            //if (existingResultItems.contains(item)) continue;

            if (!translate(item.getTranslationKey()).toLowerCase().contains(string.toLowerCase())) continue;

            Identifier id = Registries.ITEM.getId(item);
            System.out.println("Item: " + id);

            NetworkRecipeId recipeId = new NetworkRecipeId(9999);

            List<SlotDisplay> slots = new ArrayList<>();
            slots.add(new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, Identifier.of("minecraft", id.getPath()))));

            SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item, 1));

            SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(
                    Registries.ITEM.get(Identifier.of("minecraft", "crafting_table"))
            );

            OptionalInt group = OptionalInt.empty();
            RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

            List<Ingredient> ingredients = List.of(Ingredient.ofItems(item));

            ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
            RecipeDisplayEntry recipeDisplayEntry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
            RecipeResultCollection myCustomRecipeResultCollection = new RecipeResultCollection(List.of(recipeDisplayEntry));

            filteredList.add(myCustomRecipeResultCollection);
        }*/

        /****filteredList.addAll(JEBClient.generateCustomRecipeList(string));****/

        //if (!string.isEmpty()) {
            /*for (Item item : Registries.ITEM) {
                if (item == Items.AIR) continue;
                if (existingResultItems.contains(item)) continue;

                Identifier id = Registries.ITEM.getId(item);
                String idString = id.toString().toLowerCase(); // без Locale
                String name = item.getName().getString().toLowerCase(); // без Locale
                String searchLower = string.toLowerCase(); // без Locale

                // Если id или имя содержит текст поиска
                if (!idString.contains(searchLower) && !name.contains(searchLower)) continue;

                NetworkRecipeId recipeId = new NetworkRecipeId(9999);

                List<SlotDisplay> slots = List.of(
                        new SlotDisplay.TagSlotDisplay(TagKey.of(RegistryKeys.ITEM, id))
                );

                SlotDisplay.StackSlotDisplay resultSlot = new SlotDisplay.StackSlotDisplay(new ItemStack(item));
                SlotDisplay.ItemSlotDisplay stationSlot = new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE);

                ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);

                OptionalInt group = OptionalInt.empty();
                RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;
                List<Ingredient> ingredients = List.of(Ingredient.ofItems(item));

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
                RecipeResultCollection resultCollection = new RecipeResultCollection(List.of(entry));

                filteredList.add(resultCollection);
            }*/
        //}



        //System.out.println("2: filteredList содержит " + filteredList.size() + " рецептов");
        //System.out.println("Текст в поисковом поле: " + string);
        
    /****    recipesArea.setResults(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }****/



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
        Component nameComponent = stack.getHoverName(); // или getDisplayName()
        if (nameComponent.getContents() instanceof TranslatableContents translatable) {
            key = translatable.getKey().toLowerCase(Locale.ROOT);
        }

        // Проверка на имя мода
        if (modName != null && !modName.isEmpty() && !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(modName)) {
            return false;  // Не принадлежит указанному моду
        }

        // Обычный поиск по строкам
        if (name.contains(query) || id.contains(query) || key.contains(query)) {
            return true;
        }

        // Поиск по тултипам
        TooltipFlag tooltipFlag = minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;



        try {
            List<Component> tooltip = stack.getTooltipLines(Item.TooltipContext.of(this.minecraft.level),minecraft.player, tooltipFlag);

            for (Component line : tooltip) {
                String clean = net.minecraft.ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                if (clean.contains(query)) return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Можно также записать лог или безопасно проигнорировать ошибку
        }

        return false;
    }

    @Inject(method = "updateCollections", at = @At("HEAD"), cancellable = true)
    private void onCustomSearch(boolean resetCurrentPage, boolean filteringCraftable, CallbackInfo ci) {
        String string = searchBox.getValue();
        boolean searchIngredients = string.startsWith("#");
        String query = (searchIngredients ? string.substring(1) : string).toLowerCase();

        String modName = null;
        if (string.startsWith("@")) {
            int endIndex = string.indexOf(" ");
            if (endIndex != -1) {
                modName = string.substring(1, endIndex).trim();
                query = string.substring(endIndex + 1).toLowerCase();
            } else {
                modName = string.substring(1).trim();
                query = "";
            }
        }

        //ClientPlayNetworkHandler handler = client.getNetworkHandler();
        //if (handler == null) return;

        List<RecipeCollection> originalList = book.getCollection(selectedTab.getCategory());
        List<RecipeCollection> filteredList = Lists.newArrayList();

        // === Если на вкладке избранного (используем CAMPFIRE как временную категорию) ===
        if (isFavoritesTabActive()) {
            originalList = book.getCollection(SearchRecipeBookCategory.CRAFTING);

            Set<ResourceLocation> favoriteItems = FavoritesManager.loadFavoriteItemIds();

            ContextMap context = SlotDisplayContext.fromLevel(
                    Objects.requireNonNull(minecraft.level)
            );

            List<RecipeCollection> matching = null;
            for (RecipeCollection collection : originalList) {
                matching = new ArrayList<>();
                for (RecipeDisplayEntry entry : collection.getRecipes()) {

                    List<ItemStack> stacks = entry.resultItems(context);
                    if (!stacks.isEmpty()) {
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stacks.get(0).getItem());
                        if (favoriteItems.contains(itemId)) {
                            matching.add(new RecipeCollection(List.of(entry)));
                        }
                    }
                }

                if(!matching.isEmpty()) {
                    filteredList.add(collection);
                }

            }

            //if (!matching.isEmpty()) {
            //    filteredList.addAll(matching);
            //}

            //if (filteringCraftable) {
            //    filteredList.removeIf(rc -> !rc.hasCraftableRecipes());
            //}

            recipeBookPage.updateCollections(filteredList, resetCurrentPage, filteringCraftable);
            ci.cancel();
            return;
        }

        // === Обычный поиск ===
        for (RecipeCollection collection : originalList) {
            if (!collection.hasAnySelected()) continue;

            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                boolean match;
                if (searchIngredients) {
                    match = recipeDisplayMatchesIngredientQuery(entry, query);
                } else {
                    match = recipeResultMatchesQuery(entry, query, modName);
                }
                if (match) {
                    filteredList.add(collection);
                    break;
                }
            }
        }

        //if(jeb$customToggleState) {
        //    filteredList.removeIf((resultCollection) -> !resultCollection.hasDisplayableRecipes());
        //}

        if (filteringCraftable) {
            filteredList.removeIf(rc -> !rc.hasCraftable());
        }

        filteredList.addAll(Jeb.generateCustomRecipeList(string));

        recipeBookPage.updateCollections(filteredList, resetCurrentPage, filteringCraftable);
        ci.cancel();
    }




    /*@Unique
    private static Optional<Item> getItemFromSlotDisplay(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.StackSlotDisplay(ItemStack stack)) {
            return Optional.of(stack.getItem());
        }

        if (slot instanceof SlotDisplay.ItemSlotDisplay(RegistryEntry<Item> item)) {
            return Optional.of(item.value());
        }

        if (slot instanceof SlotDisplay.TagSlotDisplay(TagKey<Item> tag)) {

            // В 1.21.5 можно безопасно использовать iterateEntries
            for (RegistryEntry<Item> entry : Registries.ITEM.iterateEntries(tag)) {
                return Optional.of(entry.value());
            }
        }

        if (slot instanceof SlotDisplay.CompositeSlotDisplay(List<SlotDisplay> contents)) {
            for (SlotDisplay inner : contents) {
                Optional<Item> maybeItem = getItemFromSlotDisplay(inner);
                if (maybeItem.isPresent()) return maybeItem;
            }
        }

        return Optional.empty();
    }*/

    /*@Inject(method = "reset", at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V", shift = At.Shift.AFTER))
    private void addNewTab(CallbackInfo ci) {
        RecipeBookWidget<?> recipeBookWidget = (RecipeBookWidget<?>) (Object) this;

        // Получаем список вкладок через @Accessor
        List<RecipeBookWidget.Tab> tabs = ((RecipeBookWidgetAccessor) recipeBookWidget).gettabs();

        // Создаем изменяемую копию списка tabs
        List<RecipeBookWidget.Tab> newTabs = new ArrayList<>(tabs);

        // Создаем новую вкладку (Tab) с иконкой и категорией
        ItemStack primaryIcon = new ItemStack(Items.WRITABLE_BOOK);  // Иконка из алмаза
        RecipeBookCategory category = RecipeBookCategories.CAMPFIRE; // Категория рецептов
        RecipeBookWidget.Tab newTab = new RecipeBookWidget.Tab(primaryIcon.getItem(), category);

        // Добавляем новую кнопку вкладки в список tabButtons
        newTabs.add(newTab);  // Добавляем новую кнопку вкладки

        try {
            java.lang.reflect.Field tabsField = RecipeBookWidget.class.getDeclaredField("tabs");
            tabsField.setAccessible(true);  // Даем доступ к приватному полю
            tabsField.set(recipeBookWidget, newTabs);  // Устанавливаем новое значение
        } catch (Exception e) {
            e.printStackTrace();
        }

    }*/

}
