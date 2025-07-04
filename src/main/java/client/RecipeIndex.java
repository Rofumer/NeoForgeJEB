package client;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.*;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Unique;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;

import static client.JebClient.LOGGER;
import static client.JebClient.nonexistingResultItems;

public class RecipeIndex {
    // Индексы по категориям
    public final Map<RecipeBookCategory, Map<String, List<RecipeCollection>>> byResult = new HashMap<>();
    public final Map<RecipeBookCategory, Map<String, List<RecipeCollection>>> byMod = new HashMap<>();
    public final Map<RecipeBookCategory, Map<String, List<RecipeCollection>>> byIngredientWord = new HashMap<>();
    public final Map<RecipeBookCategory, Map<String, List<RecipeCollection>>> byTooltipWord = new HashMap<>();
    public final Map<RecipeBookCategory, Set<RecipeCollection>> allCollections = new HashMap<>();
    private static final Map<RecipeBookCategory, Map<Item, RecipeCollection>> GLOBAL_COLLECTIONS_BY_RESULT = new HashMap<>();

    public static final RecipeIndex GLOBAL_RECIPE_INDEX = new RecipeIndex();
    public static boolean jebIndexReady = false;

    public static RecipeCollection getOrCreateCollection(RecipeBookCategory category, Item resultItem) {
        Map<Item, RecipeCollection> byItem = GLOBAL_COLLECTIONS_BY_RESULT.computeIfAbsent(category, k -> new HashMap<>());
        RecipeCollection collection = byItem.get(resultItem);
        if (collection == null) {
            collection = new RecipeCollection(new ArrayList<>());
            byItem.put(resultItem, collection);
            GLOBAL_RECIPE_INDEX.allCollections.computeIfAbsent(category, k -> new LinkedHashSet<>()).add(collection);
        }
        return collection;
    }

    @Unique
    public static RecipeCollection findOrCreateCollectionFor(RecipeBookCategory category, RecipeDisplayEntry recipeEntry, ContextMap context) {
        Map<Item, RecipeCollection> byItem = GLOBAL_COLLECTIONS_BY_RESULT.computeIfAbsent(category, k -> new HashMap<>());
        ItemStack result = recipeEntry.display().result().resolveForFirstStack(context);
        if (result == null || result.isEmpty()) return null;
        Item resultItem = result.getItem();

        RecipeCollection collection = byItem.get(resultItem);
        if (collection == null) {
            collection = new RecipeCollection(new ArrayList<>());
            byItem.put(resultItem, collection);
            RecipeIndex.GLOBAL_RECIPE_INDEX.allCollections.computeIfAbsent(category, k -> new LinkedHashSet<>()).add(collection);
        }
        List<RecipeDisplayEntry> recipes = collection.getRecipes();
        if (!recipes.contains(recipeEntry)) {
            try {
                recipes.add(recipeEntry);
            } catch (UnsupportedOperationException e) {
                List<RecipeDisplayEntry> fixed = new ArrayList<>(recipes);
                fixed.add(recipeEntry);
                RecipeCollection newCollection = new RecipeCollection(fixed);
                byItem.put(resultItem, newCollection);
                Set<RecipeCollection> set = RecipeIndex.GLOBAL_RECIPE_INDEX.allCollections.computeIfAbsent(category, k -> new LinkedHashSet<>());
                set.remove(collection);
                set.add(newCollection);
                collection = newCollection;
            }
        }
        return collection;
    }

    // === Индексация ===
    public static void buildRecipeIndex() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("[JEB] buildRecipeIndex started at {}", new Date(startTime));

        jebIndexReady = false;
        Minecraft client = Minecraft.getInstance();
        ClientRecipeBook book = client.player.getRecipeBook();

        GLOBAL_RECIPE_INDEX.byResult.clear();
        GLOBAL_RECIPE_INDEX.byMod.clear();
        GLOBAL_RECIPE_INDEX.byIngredientWord.clear();
        GLOBAL_RECIPE_INDEX.byTooltipWord.clear();
        GLOBAL_RECIPE_INDEX.allCollections.clear();
        GLOBAL_COLLECTIONS_BY_RESULT.clear();

        ContextMap context = SlotDisplayContext.fromLevel(
                Objects.requireNonNull(Minecraft.getInstance().level)
        );

        List<RecipeBookCategory> allCategories = new ArrayList<>();
        for (Field field : RecipeBookCategories.class.getFields()) {
            if (field.getType() == RecipeBookCategory.class) {
                try {
                    RecipeBookCategory category = (RecipeBookCategory) field.get(null);
                    allCategories.add(category);
                } catch (Exception ignored) {}
            }
        }

        int totalIndexedRecipes = 0;

        for (RecipeBookCategory category : allCategories) {
            List<RecipeCollection> collections = book.getCollection(category);
            if (collections.isEmpty()) continue;

            Map<Item, RecipeCollection> collectionsByResult = GLOBAL_COLLECTIONS_BY_RESULT.computeIfAbsent(category, k -> new HashMap<>());
            Set<RecipeCollection> categoryCollections = new LinkedHashSet<>();

            Map<String, List<RecipeCollection>> resultIndex = new HashMap<>();
            Map<String, List<RecipeCollection>> modIndex = new HashMap<>();
            Map<String, List<RecipeCollection>> ingredientIndex = new HashMap<>();
            Map<String, List<RecipeCollection>> tooltipIndex = new HashMap<>();

            for (RecipeCollection collection : collections) {
                List<RecipeDisplayEntry> entries = collection.getRecipes();
                for (RecipeDisplayEntry recipe : entries) {
                    totalIndexedRecipes++;

                    ItemStack result = recipe.display().result().resolveForFirstStack(context);
                    if (result == null || result.isEmpty()) continue;
                    Item resultItem = result.getItem();

                    // Коллекция по результату
                    RecipeCollection realCollection = collectionsByResult.get(resultItem);
                    if (realCollection == null) {
                        realCollection = new RecipeCollection(new ArrayList<>());
                        collectionsByResult.put(resultItem, realCollection);
                        categoryCollections.add(realCollection);
                    }
                    if (!realCollection.getRecipes().contains(recipe)) {
                        realCollection.getRecipes().add(recipe);
                    }

                    // Индексация по ингредиентам
                    Optional<List<Ingredient>> opt = recipe.craftingRequirements();
                    if (opt.isPresent()) {
                        for (Ingredient ingredient : opt.get()) {
                            for (ItemStack stack : ingredient.display().resolveForStacks(context)) {
                                String ingredientId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                                ingredientIndex.computeIfAbsent(ingredientId, k -> new ArrayList<>()).add(realCollection);
                            }
                        }
                    }

                    // Индексация по namespace (моду)
                    String mod = BuiltInRegistries.ITEM.getKey(resultItem).getNamespace().toLowerCase(Locale.ROOT);
                    for (int i = 0; i < mod.length(); i++) {
                        for (int j = i + 1; j <= mod.length(); j++) {
                            String substr = mod.substring(i, j);
                            if (substr.isEmpty()) continue;
                            modIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(realCollection);
                        }
                    }

                    // Индекс по подстрокам результата (id и имя)
                    String resultId = BuiltInRegistries.ITEM.getKey(resultItem).toString().toLowerCase(Locale.ROOT);
                    String name = result.getItemName().getString().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]«»\"]", "");
                    for (String source : List.of(resultId, name)) {
                        for (int i = 0; i < source.length(); i++) {
                            for (int j = i + 1; j <= source.length(); j++) {
                                String substr = source.substring(i, j);
                                if (substr.isEmpty()) continue;
                                resultIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(realCollection);
                            }
                        }
                    }

                    // --- Индексация по подстрокам тултипов ---
                    List<String> tooltipLines = new ArrayList<>();
                    try {
                        TooltipFlag tooltipFlag = TooltipFlag.Default.NORMAL;
                        List<Component> tooltip = result.getTooltipLines(Item.TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, tooltipFlag);
                        for (Component line : tooltip) {
                            String clean = ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                            tooltipLines.add(clean);
                        }
                    } catch (Exception e) {}

                    for (String tooltipLine : tooltipLines) {
                        String[] words = tooltipLine.split("[\\s,;.:!\\-]+");
                        for (String word : words) {
                            if (word.length() < 3) continue;
                            for (int i = 0; i <= word.length() - 3; i++) {
                                for (int j = i + 3; j <= word.length(); j++) {
                                    String substr = word.substring(i, j);
                                    if (substr.isEmpty()) continue;
                                    tooltipIndex.computeIfAbsent(substr, k -> new ArrayList<>()).add(realCollection);
                                }
                            }
                        }
                    }
                }
            }

            GLOBAL_RECIPE_INDEX.allCollections.put(category, categoryCollections);
            GLOBAL_RECIPE_INDEX.byResult.put(category, resultIndex);
            GLOBAL_RECIPE_INDEX.byMod.put(category, modIndex);
            GLOBAL_RECIPE_INDEX.byIngredientWord.put(category, ingredientIndex);
            GLOBAL_RECIPE_INDEX.byTooltipWord.put(category, tooltipIndex);
        }

        jebIndexReady = true;

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        LOGGER.info("[JEB] buildRecipeIndex done at {} ({} ms), total indexed recipes: {}", new Date(endTime), duration, totalIndexedRecipes);
    }

    // Универсальный быстрый поиск по списку категорий
    public static List<RecipeCollection> fastSearch(
            List<RecipeBookCategory> categories,
            String query,
            String modName,
            boolean searchIngredients
    ) {
        if (!jebIndexReady) return List.of();

        query = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        modName = modName == null ? "" : modName.toLowerCase(Locale.ROOT).trim();

        Set<RecipeCollection> result = new LinkedHashSet<>();

        for (RecipeBookCategory category : categories) {
            Map<String, List<RecipeCollection>> modIndex = GLOBAL_RECIPE_INDEX.byMod.getOrDefault(category, Map.of());
            Map<String, List<RecipeCollection>> ingredientIndex = GLOBAL_RECIPE_INDEX.byIngredientWord.getOrDefault(category, Map.of());
            Set<RecipeCollection> all = GLOBAL_RECIPE_INDEX.allCollections.getOrDefault(category, Set.of());
            Map<String, List<RecipeCollection>> resultIndex = GLOBAL_RECIPE_INDEX.byResult.getOrDefault(category, Map.of());
            Map<String, List<RecipeCollection>> tooltipIndex = GLOBAL_RECIPE_INDEX.byTooltipWord.getOrDefault(category, Map.of());

            if ((modName.isEmpty()) && query.isEmpty()) {
                result.addAll(all);
                continue;
            }

            if (!modName.isEmpty()) {
                List<RecipeCollection> modCollections = modIndex.getOrDefault(modName, List.of());
                if (query.isEmpty()) {
                    result.addAll(modCollections);
                    continue;
                }
                for (String word : query.split("[\\s:_\\-]+")) {
                    List<RecipeCollection> byWord = resultIndex.getOrDefault(word, List.of());
                    for (RecipeCollection rc : byWord) {
                        if (modCollections.contains(rc))
                            result.add(rc);
                    }
                }
                continue;
            }

            if (searchIngredients && !query.isEmpty()) {
                List<RecipeCollection> byIng = ingredientIndex.getOrDefault(query, List.of());
                result.addAll(byIng);
                continue;
            }

            if (!query.isEmpty() && !searchIngredients) {
                List<RecipeCollection> byResult = resultIndex.getOrDefault(query, List.of());
                result.addAll(byResult);

                // --- Поиск по тултипам ---
                List<RecipeCollection> byTooltip = tooltipIndex.getOrDefault(query, List.of());
                result.addAll(byTooltip);
            }
        }

        return new ArrayList<>(result);
    }

    public static List<RecipeCollection> fastSearch(
            Object categoryOrType, String query, String modName, boolean searchIngredients
    ) {
        List<RecipeBookCategory> categories;
        if (categoryOrType instanceof List) {
            categories = (List<RecipeBookCategory>) categoryOrType;
        } else if (categoryOrType instanceof SearchRecipeBookCategory) {
            categories = ((SearchRecipeBookCategory) categoryOrType).includedCategories();
        } else if (categoryOrType instanceof RecipeBookCategory) {
            categories = List.of((RecipeBookCategory) categoryOrType);
        } else {
            categories = List.of();
        }
        return fastSearch(categories, query, modName, searchIngredients);
    }

    public static List<RecipeCollection> generateCustomRecipeList(String filter) {
        List<RecipeCollection> list = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();

        filter = filter.trim();
        String _modName = null;
        String _query = "";

        if (filter.startsWith("@")) {
            String[] parts = filter.substring(1).split(" ", 2);
            _modName = parts[0].toLowerCase(java.util.Locale.ROOT);
            if (parts.length > 1) {
                _query = parts[1].toLowerCase(java.util.Locale.ROOT);
            }
        } else {
            _query = filter.toLowerCase(java.util.Locale.ROOT);
        }

        final String modName = _modName;
        final String query = _query;

        RecipeIndex.ITEM_INDEX.stream()
                .filter(idx ->
                        (modName == null || idx.mod.contains(modName)) &&
                                (query.isEmpty() ||
                                        idx.name.contains(query) ||
                                        idx.id.contains(query) ||
                                        idx.key.contains(query) ||
                                        idx.tooltip.stream().anyMatch(line -> line.contains(query))
                                )
                )
                .forEach(idx -> {
                    var dummy = RecipeIndex.createDummyRecipeCollection(idx.item);
                    list.add(dummy);
                });

        return list;
    }

    private static RecipeCollection createDummyRecipeCollection(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        RecipeDisplayId recipeId = new RecipeDisplayId(9999);

        List<SlotDisplay> slots = List.of(
                new SlotDisplay.TagSlotDisplay(TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", id.getPath())))
        );

        SlotDisplay.ItemStackSlotDisplay resultSlot = new SlotDisplay.ItemStackSlotDisplay(new ItemStack(item, 1));
        Item ct = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("minecraft", "crafting_table"));
        SlotDisplay.ItemStackSlotDisplay stationSlot = new SlotDisplay.ItemStackSlotDisplay(new ItemStack(ct));
        List<Ingredient> ingredients = List.of(Ingredient.of(item));

        ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
        OptionalInt group = OptionalInt.empty();
        RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

        RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
        return new RecipeCollection(List.of(entry));
    }

    public static class IndexedItem {
        public final Item item;
        public final String id;
        public final String name;
        public final String mod;
        public final String key;
        public final List<String> tooltip;

        public IndexedItem(Item item, String id, String name, String mod, String key, List<String> tooltip) {
            this.item = item;
            this.id = id;
            this.name = name;
            this.mod = mod;
            this.key = key;
            this.tooltip = tooltip;
        }
    }

    public static List<IndexedItem> ITEM_INDEX = new ArrayList<>();

    public static void fillItemIndex() {
        Minecraft client = Minecraft.getInstance();
        ITEM_INDEX.clear();
        for (Item item : nonexistingResultItems) {
            if (item == Items.AIR) continue;
            String id = item.toString().toLowerCase(Locale.ROOT);
            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            String mod = BuiltInRegistries.ITEM.getKey(item).getNamespace().toLowerCase(Locale.ROOT);
            String key = "";
            Component nameComponent = item.getName();
            if (nameComponent.getContents() instanceof TranslatableContents translatable) {
                key = translatable.getKey().toLowerCase(Locale.ROOT);
            }
            List<String> tooltipLines = new ArrayList<>();

            if (client.level != null) {
                try {
                    TooltipFlag tooltipFlag = TooltipFlag.Default.NORMAL;
                    List<Component> tooltip = item.getDefaultInstance().getTooltipLines(Item.TooltipContext.of(client.level), client.player, tooltipFlag);
                    for (Component line : tooltip) {
                        String clean = ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                        tooltipLines.add(clean);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            ITEM_INDEX.add(new IndexedItem(item, id, name, mod, key, tooltipLines));
        }
    }

    public static void updateIndexesWithRecipe(
            RecipeBookCategory category,
            RecipeCollection collection,
            RecipeDisplayEntry recipeEntry
    ) {
        GLOBAL_RECIPE_INDEX.allCollections.computeIfAbsent(category, k -> new LinkedHashSet<>()).add(collection);

        ContextMap context = SlotDisplayContext.fromLevel(Objects.requireNonNull(Minecraft.getInstance().level));
        ItemStack result = recipeEntry.display().result().resolveForFirstStack(context);
        if (result == null || result.isEmpty()) return;
        Item resultItem = result.getItem();

        // Индексация по модам
        String mod = BuiltInRegistries.ITEM.getKey(resultItem).getNamespace().toLowerCase(Locale.ROOT);
        Map<String, List<RecipeCollection>> modIndex =
                GLOBAL_RECIPE_INDEX.byMod.computeIfAbsent(category, k -> new HashMap<>());
        for (int i = 0; i < mod.length(); i++) {
            for (int j = i + 1; j <= mod.length(); j++) {
                String substr = mod.substring(i, j);
                if (substr.isEmpty()) continue;
                List<RecipeCollection> list = modIndex.computeIfAbsent(substr, k -> new ArrayList<>());
                if (!list.contains(collection)) {
                    list.add(collection);
                }
            }
        }

        // Индексация по результату
        String resultId = BuiltInRegistries.ITEM.getKey(resultItem).toString().toLowerCase(Locale.ROOT);
        String name = result.getItemName().getString().toLowerCase(Locale.ROOT).replaceAll("[\\[\\]«»\"]", "");
        Map<String, List<RecipeCollection>> resultIndex =
                GLOBAL_RECIPE_INDEX.byResult.computeIfAbsent(category, k -> new HashMap<>());
        for (String source : List.of(resultId, name)) {
            for (int i = 0; i < source.length(); i++) {
                for (int j = i + 1; j <= source.length(); j++) {
                    String substr = source.substring(i, j);
                    if (substr.isEmpty()) continue;
                    List<RecipeCollection> list = resultIndex.computeIfAbsent(substr, k -> new ArrayList<>());
                    if (!list.contains(collection)) {
                        list.add(collection);
                    }
                }
            }
        }

        // Индексация по ингредиентам
        Map<String, List<RecipeCollection>> ingredientIndex =
                GLOBAL_RECIPE_INDEX.byIngredientWord.computeIfAbsent(category, k -> new HashMap<>());
        Optional<List<Ingredient>> opt = recipeEntry.craftingRequirements();
        if (opt.isPresent()) {
            for (Ingredient ingredient : opt.get()) {
                for (ItemStack stack : ingredient.display().resolveForStacks(context)) {
                    String ingredientId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                    List<RecipeCollection> list = ingredientIndex.computeIfAbsent(ingredientId, k -> new ArrayList<>());
                    if (!list.contains(collection)) {
                        list.add(collection);
                    }
                }
            }
        }

        // Индексация по тултипам (подстроки длиной >=3)
        Map<String, List<RecipeCollection>> tooltipIndex =
                GLOBAL_RECIPE_INDEX.byTooltipWord.computeIfAbsent(category, k -> new HashMap<>());

        List<String> tooltipLines = new ArrayList<>();
        try {
            TooltipFlag tooltipFlag = TooltipFlag.Default.NORMAL;
            List<Component> tooltip = result.getTooltipLines(Item.TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, tooltipFlag);
            for (Component line : tooltip) {
                String clean = ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                tooltipLines.add(clean);
            }
        } catch (Exception e) {}

        for (String tooltipLine : tooltipLines) {
            String[] words = tooltipLine.split("[\\s,;.:!\\-]+");
            for (String word : words) {
                if (word.length() < 3) continue;
                for (int i = 0; i <= word.length() - 3; i++) {
                    for (int j = i + 3; j <= word.length(); j++) {
                        String substr = word.substring(i, j);
                        if (substr.isEmpty()) continue;
                        List<RecipeCollection> list = tooltipIndex.computeIfAbsent(substr, k -> new ArrayList<>());
                        if (!list.contains(collection)) {
                            list.add(collection);
                        }
                    }
                }
            }
        }
    }
}
