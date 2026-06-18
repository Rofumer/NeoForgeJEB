package client;

import net.minecraft.SharedConstants;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.item.equipment.trim.TrimPattern;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RecipeLoader {

    private static int cachedVanillaRecipeCount = -1;
    private static int cachedVanillaCTID = -1;

    private static final Pattern CT_RESULT_PATTERN = Pattern.compile(
        "result=ItemStackSlotDisplay\\[stack=ItemStackTemplate\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:crafting_table\\]" +
        ".*?NetworkID:RecipeDisplayId\\[index=(\\d+)\\]"
    );

    private static void loadVanillaStats() {
        String name = "recipes_" + SharedConstants.getCurrentVersion().name() + ".txt";
        try (InputStream input = RecipeLoader.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                cachedVanillaRecipeCount = 1358;
                cachedVanillaCTID = 259;
                return;
            }
            int count = 0;
            int ctId = -1;
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                count++;
                if (ctId == -1) {
                    Matcher m = CT_RESULT_PATTERN.matcher(line);
                    if (m.find()) ctId = Integer.parseInt(m.group(1));
                }
            }
            cachedVanillaRecipeCount = count;
            cachedVanillaCTID = ctId != -1 ? ctId : 259;
        } catch (Exception e) {
            cachedVanillaRecipeCount = 1358;
            cachedVanillaCTID = 259;
        }
    }

    public static int getVanillaRecipeCount() {
        if (cachedVanillaRecipeCount == -1) loadVanillaStats();
        return cachedVanillaRecipeCount;
    }

    public static int getVanillaCTID() {
        if (cachedVanillaCTID == -1) loadVanillaStats();
        return cachedVanillaCTID;
    }

    private static SlotDisplay.ItemStackSlotDisplay stackDisplay(Item item) {
        return new SlotDisplay.ItemStackSlotDisplay(
                ItemStackTemplate.fromNonEmptyStack(new ItemStack(item))
        );
    }

    private static SlotDisplay.ItemStackSlotDisplay stackDisplay(Item item, int count) {
        return new SlotDisplay.ItemStackSlotDisplay(
                ItemStackTemplate.fromNonEmptyStack(new ItemStack(item, count))
        );
    }

    private static List<String> parseBracketedList(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketLevel = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ',' && bracketLevel == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                if (c == '[') {
                    bracketLevel++;
                } else if (c == ']') {
                    bracketLevel--;
                }
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private static String fixResourceName(String resourceName) {
        if (resourceName.startsWith("minecraft:")) {
            return resourceName.substring("minecraft:".length());
        }
        return resourceName;
    }

    public static void loadRecipesFromLog() throws IOException {
        String name = "recipes_" + SharedConstants.getCurrentVersion().name() + ".txt";
        try (InputStream input = RecipeLoader.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                System.err.println("Не удалось найти файл " + name + " в ресурсах");
                return;
            }

            List<String> lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.toList());

            for (String line : lines) {
                if (line.startsWith("Display:")) {
                    Optional<RecipeDisplayEntry> recipeEntry = parseLineToRecipeEntry(line);
                    recipeEntry.ifPresent(RecipeLoader::sendToClient);
                }
            }
        }
    }

    private static Optional<RecipeDisplayEntry> parseLineToRecipeEntry(String line) {
        try {
            if (line.contains("StonecutterRecipeDisplay")) {
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                OptionalInt group = OptionalInt.empty();
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                if (groupMatcher.find()) {
                    group = OptionalInt.of(Integer.parseInt(groupMatcher.group(1)));
                }

                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? BuiltInRegistries.RECIPE_BOOK_CATEGORY.getValue(
                        Identifier.fromNamespaceAndPath("minecraft", categoryMatcher.group(1))
                )
                        : RecipeBookCategories.CRAFTING_MISC;

                Matcher inputMatcher = Pattern.compile(
                        "input=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)]=minecraft:(.*?)\\}\\]\\]]"
                ).matcher(line);
                if (!inputMatcher.find()) {
                    return Optional.empty();
                }
                String inputItem = fixResourceName(inputMatcher.group(2));

                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                if (!resultMatcher.find()) {
                    return Optional.empty();
                }
                int resultCount = Integer.parseInt(resultMatcher.group(1));
                String resultItem = fixResourceName(resultMatcher.group(2));

                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)]=minecraft:(.*?)\\}\\]").matcher(line);
                if (!stationMatcher.find()) {
                    return Optional.empty();
                }
                String stationName = fixResourceName(stationMatcher.group(2));

                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) {
                    return Optional.empty();
                }
                String itemsSection = itemsMatcher.group(1);
                List<Ingredient> ingredients = new ArrayList<>();

                for (String rawItem : itemsSection.split(";")) {
                    List<Item> alternatives = new ArrayList<>();

                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            String[] splitItem = itemName.split(":");
                            String namespace = splitItem.length == 2 ? splitItem[0] : "minecraft";
                            String path = splitItem[splitItem.length - 1];
                            Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
                            Item item = BuiltInRegistries.ITEM.getValue(id);
                            if (item != Items.AIR) {
                                alternatives.add(item);
                            }
                        }
                    }

                    if (!alternatives.isEmpty()) {
                        ingredients.add(Ingredient.of(alternatives.stream()));
                    }
                }

                SlotDisplay.ItemStackSlotDisplay itemSlot = stackDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", inputItem))
                );

                SlotDisplay.Composite inputSlot = new SlotDisplay.Composite(List.of(itemSlot));

                SlotDisplay.ItemStackSlotDisplay resultSlot = stackDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", resultItem)),
                        resultCount
                );

                SlotDisplay.ItemStackSlotDisplay stationSlot = stackDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", stationName))
                );

                RecipeDisplayId recipeId = new RecipeDisplayId(index);
                StonecutterRecipeDisplay display = new StonecutterRecipeDisplay(
                        inputSlot,
                        resultSlot,
                        stationSlot
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(
                        recipeId,
                        display,
                        group,
                        category,
                        Optional.of(ingredients)
                );

                return Optional.of(entry);
            }

            if (line.contains("SmithingRecipeDisplay")) {
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                OptionalInt group = OptionalInt.empty();
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                if (groupMatcher.find()) {
                    group = OptionalInt.of(Integer.parseInt(groupMatcher.group(1)));
                }

                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? BuiltInRegistries.RECIPE_BOOK_CATEGORY.getValue(
                        Identifier.fromNamespaceAndPath("minecraft", categoryMatcher.group(1))
                )
                        : RecipeBookCategories.CRAFTING_MISC;

                Matcher templateMatcher = Pattern.compile("template=CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]\\]]").matcher(line);
                String templateItem = templateMatcher.find() ? fixResourceName(templateMatcher.group(2)) : "air";

                String baseItem = null;
                boolean isBaseTag = false;

                Matcher baseMatcher = Pattern.compile(
                        "base=(?:CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:[^\\]]+\\]=minecraft:([^}\\]]+)\\}\\]\\]\\]|TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([^\\]]+)\\]\\])"
                ).matcher(line);

                if (baseMatcher.find()) {
                    if (baseMatcher.group(1) != null) {
                        baseItem = fixResourceName(baseMatcher.group(1));
                        isBaseTag = true;
                    } else if (baseMatcher.group(2) != null) {
                        baseItem = baseMatcher.group(2);
                        isBaseTag = false;
                    }
                }

                if (baseItem == null) {
                    baseItem = "air";
                    isBaseTag = false;
                }

                String additionTag = null;
                Matcher additionMatcher = Pattern.compile(
                        "addition=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:(.*?)\\]\\]"
                ).matcher(line);

                if (additionMatcher.find()) {
                    if (additionMatcher.group(1) != null) {
                        additionTag = fixResourceName(additionMatcher.group(1));
                    }
                }
                if (additionTag == null) {
                    additionTag = "air";
                }

                SlotDisplay resultSlot = null;

                Pattern trimPattern = Pattern.compile(
                        "result=SmithingTrimSlotDisplay\\[base=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)\\]\\],\\s*" +
                                "material=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)\\]\\],\\s*" +
                                "pattern=(?:Reference\\{ResourceKey\\[minecraft:trim_pattern / minecraft:([\\w_]+)\\]=ArmorTrimPattern\\[assetId=minecraft:([\\w_]+),\\s*" +
                                "description=translation\\{key='trim_pattern\\.minecraft\\.([\\w_]+)',\\s*args=\\[\\]\\},\\s*decal=false\\]\\}\\]|" +
                                "CompositeSlotDisplay\\[contents=\\[ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:([\\w_]+)\\]=minecraft:[\\w_]+\\}\\]\\]\\])\\]"
                );

                Matcher trimResultMatcher = trimPattern.matcher(line);

                if (trimResultMatcher.find()) {
                    String baseTag = trimResultMatcher.group(1);
                    String materialTag = trimResultMatcher.group(2);
                    String patternId = trimResultMatcher.group(3);

                    TagKey<Item> baseTagKey = TagKey.create(
                            Registries.ITEM,
                            Identifier.fromNamespaceAndPath("minecraft", baseTag)
                    );
                    TagKey<Item> materialTagKey = TagKey.create(
                            Registries.ITEM,
                            Identifier.fromNamespaceAndPath("minecraft", materialTag)
                    );

                    if (patternId != null) {
                        RegistryAccess registries = Minecraft.getInstance().level.registryAccess();
                        var trimPatterns = registries.lookupOrThrow(Registries.TRIM_PATTERN);
                        Holder.Reference<TrimPattern> patternEntry =
                                trimPatterns.get(Identifier.fromNamespaceAndPath("minecraft", patternId)).orElse(null);

                        if (patternEntry != null) {
                            resultSlot = new SlotDisplay.SmithingTrimDemoSlotDisplay(
                                    new SlotDisplay.TagSlotDisplay(baseTagKey),
                                    new SlotDisplay.TagSlotDisplay(materialTagKey),
                                    patternEntry
                            );
                        }
                    }
                }

                if (resultSlot == null) {
                    Pattern stackPattern = Pattern.compile("result=StackSlotDisplay\\[stack=\\d+ minecraft:([\\w_]+)]");
                    Matcher stackMatcher = stackPattern.matcher(line);
                    if (stackMatcher.find()) {
                        String itemId = fixResourceName(stackMatcher.group(1));
                        Item item = BuiltInRegistries.ITEM.getValue(
                                Identifier.fromNamespaceAndPath("minecraft", itemId)
                        );
                        resultSlot = stackDisplay(item);
                    }
                }

                if (resultSlot == null) {
                    return Optional.empty();
                }

                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(.*?)\\]=minecraft:(.*?)\\}\\]").matcher(line);
                if (!stationMatcher.find()) {
                    return Optional.empty();
                }
                String stationName = fixResourceName(stationMatcher.group(2));

                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) {
                    return Optional.empty();
                }

                String itemsSection = itemsMatcher.group(1);
                List<Ingredient> ingredients = new ArrayList<>();

                for (String rawItem : itemsSection.split(";")) {
                    List<Item> alternatives = new ArrayList<>();
                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            String[] splitItem = itemName.split(":");
                            String namespace = splitItem.length == 2 ? splitItem[0] : "minecraft";
                            String path = splitItem[splitItem.length - 1];
                            Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
                            Item item = BuiltInRegistries.ITEM.getValue(id);
                            if (item != Items.AIR) {
                                alternatives.add(item);
                            }
                        }
                    }
                    if (!alternatives.isEmpty()) {
                        ingredients.add(Ingredient.of(alternatives.stream()));
                    }
                }

                SlotDisplay baseSlot = createSlotDisplay(baseItem, isBaseTag);
                SlotDisplay additionSlot = createSlotDisplay(additionTag, false);
                SlotDisplay.ItemStackSlotDisplay stationSlot = stackDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", stationName))
                );

                RecipeDisplayId recipeId = new RecipeDisplayId(index);
                SmithingRecipeDisplay display = new SmithingRecipeDisplay(
                        new SlotDisplay.Composite(
                                Arrays.asList(
                                        stackDisplay(
                                                BuiltInRegistries.ITEM.getValue(
                                                        Identifier.fromNamespaceAndPath("minecraft", templateItem)
                                                )
                                        )
                                )
                        ),
                        baseSlot,
                        additionSlot,
                        resultSlot,
                        stationSlot
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
                return Optional.of(entry);
            }

            if (line.contains("FurnaceRecipeDisplay")) {
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                OptionalInt group = OptionalInt.empty();
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                if (groupMatcher.find()) {
                    int groupValue = Integer.parseInt(groupMatcher.group(1));
                    group = OptionalInt.of(groupValue);
                }

                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? BuiltInRegistries.RECIPE_BOOK_CATEGORY.getValue(
                        Identifier.fromNamespaceAndPath("minecraft", categoryMatcher.group(1))
                )
                        : RecipeBookCategories.CRAFTING_MISC;

                SlotDisplay ingredientSlot = null;

                Matcher tagMatcher = Pattern.compile("ingredient=TagSlotDisplay\\[tag=TagKey\\[minecraft:item / minecraft:([\\w_]+)]]").matcher(line);
                if (tagMatcher.find()) {
                    String tag = tagMatcher.group(1);
                    ingredientSlot = new SlotDisplay.TagSlotDisplay(
                            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", tag))
                    );
                } else {
                    Matcher ingredientMatcher = Pattern.compile("ingredient=CompositeSlotDisplay\\[contents=\\[(.*?)\\]\\]").matcher(line);
                    if (!ingredientMatcher.find()) {
                        return Optional.empty();
                    }

                    String ingredientsSection = ingredientMatcher.group(1);
                    List<SlotDisplay> ingredientSlots = new ArrayList<>();

                    Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]=]+)]=").matcher(ingredientsSection);
                    while (itemMatcher.find()) {
                        String ingredientItem = fixResourceName(itemMatcher.group(1));
                        ingredientSlots.add(
                                stackDisplay(
                                        BuiltInRegistries.ITEM.getValue(
                                                Identifier.fromNamespaceAndPath("minecraft", ingredientItem)
                                        )
                                )
                        );
                    }

                    ingredientSlot = new SlotDisplay.Composite(ingredientSlots);
                }

                Matcher fuelMatcher = Pattern.compile("fuel=<([^>]+)>").matcher(line);
                String fuel = null;
                if (fuelMatcher.find()) {
                    String fuelValue = fuelMatcher.group(1);
                    if (!"any fuel".equals(fuelValue)) {
                        fuel = fixResourceName(fuelValue);
                    }
                }

                SlotDisplay fuelSlot = fuel != null
                        ? stackDisplay(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", fuel)))
                        : SlotDisplay.AnyFuel.INSTANCE;

                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                if (!resultMatcher.find()) {
                    return Optional.empty();
                }
                int resultCount = Integer.parseInt(resultMatcher.group(1));
                String resultItem = fixResourceName(resultMatcher.group(2));

                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(line);
                if (!stationMatcher.find()) {
                    return Optional.empty();
                }
                String stationName = fixResourceName(stationMatcher.group(1));

                Matcher durationMatcher = Pattern.compile("duration=(\\d+)").matcher(line);
                int duration = durationMatcher.find() ? Integer.parseInt(durationMatcher.group(1)) : 0;

                Matcher experienceMatcher = Pattern.compile("experience=(\\d+\\.\\d+)").matcher(line);
                float experience = experienceMatcher.find() ? Float.parseFloat(experienceMatcher.group(1)) : 0.0f;

                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) {
                    return Optional.empty();
                }

                String itemsSection = itemsMatcher.group(1);
                List<Ingredient> ingredients = new ArrayList<>();

                for (String rawItem : itemsSection.split(";")) {
                    List<Item> alternatives = new ArrayList<>();

                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            String[] splitItem = itemName.split(":");
                            String namespace = splitItem.length == 2 ? splitItem[0] : "minecraft";
                            String path = splitItem[splitItem.length - 1];
                            Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
                            Item item = BuiltInRegistries.ITEM.getValue(id);
                            if (item != Items.AIR) {
                                alternatives.add(item);
                            }
                        }
                    }

                    if (!alternatives.isEmpty()) {
                        ingredients.add(Ingredient.of(alternatives.stream()));
                    }
                }

                SlotDisplay.ItemStackSlotDisplay result = stackDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", resultItem)),
                        resultCount
                );

                SlotDisplay.ItemStackSlotDisplay station = stackDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", stationName))
                );

                RecipeDisplayId recipeId = new RecipeDisplayId(index);

                FurnaceRecipeDisplay display = new FurnaceRecipeDisplay(
                        ingredientSlot,
                        fuelSlot,
                        result,
                        station,
                        duration,
                        experience
                );

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                return Optional.of(entry);
            }

            if (line.contains("ShapelessCraftingRecipeDisplay")) {
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index\\s*=\\s*(\\d+)\\]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                OptionalInt group = OptionalInt.empty();
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                if (groupMatcher.find()) {
                    int groupValue = Integer.parseInt(groupMatcher.group(1));
                    group = OptionalInt.of(groupValue);
                }

                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? BuiltInRegistries.RECIPE_BOOK_CATEGORY.getValue(
                        Identifier.fromNamespaceAndPath("minecraft", categoryMatcher.group(1))
                )
                        : RecipeBookCategories.CRAFTING_MISC;

                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                String resultItem = null;
                int resultCount = 1;

                if (!resultMatcher.find()) {
                    resultMatcher = Pattern.compile("result=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(\\w+)]=minecraft:(\\w+)}\\]").matcher(line);
                    if (!resultMatcher.find()) {
                        return Optional.empty();
                    }
                }

                if (resultMatcher.group(1) != null && resultMatcher.group(2) != null) {
                    try {
                        resultCount = Integer.parseInt(resultMatcher.group(1));
                    } catch (NumberFormatException e) {
                        resultCount = 1;
                    }
                    resultItem = fixResourceName(resultMatcher.group(2));
                } else if (resultMatcher.group(2) != null) {
                    resultItem = fixResourceName(resultMatcher.group(2));
                    resultCount = 1;
                }

                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(line);
                if (!stationMatcher.find()) {
                    return Optional.empty();
                }
                String stationName = fixResourceName(stationMatcher.group(1));

                Matcher ingredientsMatcher = Pattern.compile("ingredients=\\[(.*)]").matcher(line);
                if (!ingredientsMatcher.find()) {
                    return Optional.empty();
                }
                String ingredientsSection = ingredientsMatcher.group(1);

                List<SlotDisplay> slots = new ArrayList<>();
                for (String rawSlot : splitTopLevelSlotDisplays(ingredientsSection)) {
                    rawSlot = rawSlot.trim();

                    if (rawSlot.startsWith("<empty")) {
                        slots.add(SlotDisplay.Empty.INSTANCE);
                    } else if (rawSlot.startsWith("TagSlotDisplay")) {
                        String tagName = rawSlot.substring(
                                rawSlot.indexOf("minecraft:") + "minecraft:".length(),
                                rawSlot.indexOf("]")
                        ).trim();
                        String[] splitTag = tagName.split(":");
                        String lastWord = splitTag[splitTag.length - 1];
                        TagKey<Item> tagKey = TagKey.create(
                                Registries.ITEM,
                                Identifier.fromNamespaceAndPath("minecraft", lastWord)
                        );
                        slots.add(new SlotDisplay.TagSlotDisplay(tagKey));
                    } else if (rawSlot.startsWith("CompositeSlotDisplay")) {
                        List<String> innerItems = extractItemSlotDisplays(rawSlot);
                        List<SlotDisplay> compositeContents = new ArrayList<>();

                        for (String nested : innerItems) {
                            nested = nested.trim();

                            if (nested.startsWith("WithRemainderSlotDisplay")) {
                                Matcher inputMatcher = Pattern.compile("input=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(nested);
                                Matcher remainderMatcher = Pattern.compile("remainder=StackSlotDisplay\\[stack=(\\d+) minecraft:([a-z0-9_]+)]").matcher(nested);

                                if (inputMatcher.find()) {
                                    String inputItemName = fixResourceName(inputMatcher.group(1));
                                    Item inputItem = BuiltInRegistries.ITEM.getValue(
                                            Identifier.fromNamespaceAndPath("minecraft", inputItemName)
                                    );
                                    SlotDisplay.ItemSlotDisplay input = new SlotDisplay.ItemSlotDisplay(inputItem);

                                    if (remainderMatcher.find()) {
                                        int count = Integer.parseInt(remainderMatcher.group(1));
                                        String remainderItemName = fixResourceName(remainderMatcher.group(2));
                                        Item remainderItem = BuiltInRegistries.ITEM.getValue(
                                                Identifier.fromNamespaceAndPath("minecraft", remainderItemName)
                                        );
                                        SlotDisplay.ItemStackSlotDisplay remainder = stackDisplay(remainderItem, count);

                                        compositeContents.add(new SlotDisplay.WithRemainder(input, remainder));
                                    } else {
                                        compositeContents.add(input);
                                    }
                                }

                            } else if (nested.startsWith("ItemSlotDisplay")) {
                                Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(nested);
                                if (itemMatcher.find()) {
                                    String itemName = fixResourceName(itemMatcher.group(1));
                                    Item item = BuiltInRegistries.ITEM.getValue(
                                            Identifier.fromNamespaceAndPath("minecraft", itemName)
                                    );
                                    compositeContents.add(new SlotDisplay.ItemSlotDisplay(item));
                                }
                            }
                        }

                        slots.add(new SlotDisplay.Composite(compositeContents));
                    } else if (rawSlot.startsWith("ItemSlotDisplay")) {
                        Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(rawSlot);
                        if (itemMatcher.find()) {
                            String itemName = fixResourceName(itemMatcher.group(1));
                            Item item = BuiltInRegistries.ITEM.getValue(
                                    Identifier.fromNamespaceAndPath("minecraft", itemName)
                            );
                            slots.add(new SlotDisplay.ItemSlotDisplay(item));
                        }
                    }
                }

                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) {
                    return Optional.empty();
                }

                String itemsSection = itemsMatcher.group(1);
                List<Ingredient> ingredients = new ArrayList<>();

                for (String rawItem : itemsSection.split(";")) {
                    List<Item> alternatives = new ArrayList<>();

                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            String[] splitItem = itemName.split(":");
                            String namespace = splitItem.length == 2 ? splitItem[0] : "minecraft";
                            String path = splitItem[splitItem.length - 1];
                            Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
                            Item item = BuiltInRegistries.ITEM.getValue(id);
                            if (item != Items.AIR) {
                                alternatives.add(item);
                            }
                        }
                    }

                    if (!alternatives.isEmpty()) {
                        ingredients.add(Ingredient.of(alternatives.stream()));
                    }
                }

                SlotDisplay resultDisplay;
                if (line.contains("result=StackSlotDisplay")) {
                    resultDisplay = stackDisplay(
                            BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", resultItem)),
                            resultCount
                    );
                } else {
                    resultDisplay = new SlotDisplay.ItemSlotDisplay(
                            BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", resultItem))
                    );
                }

                SlotDisplay.ItemSlotDisplay station = new SlotDisplay.ItemSlotDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", stationName))
                );

                RecipeDisplayId recipeId = new RecipeDisplayId(index);
                ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultDisplay, station);

                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));

                return Optional.of(entry);
            }

            if (line.contains("ShapedCraftingRecipeDisplay")) {
                Matcher idMatcher = Pattern.compile("NetworkID:NetworkRecipeId\\[index=(\\d+)]").matcher(line);
                int index = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : -1;

                OptionalInt group = OptionalInt.empty();
                Matcher groupMatcher = Pattern.compile("Group:OptionalInt\\[(\\d+)]").matcher(line);
                if (groupMatcher.find()) {
                    group = OptionalInt.of(Integer.parseInt(groupMatcher.group(1)));
                }

                Matcher categoryMatcher = Pattern.compile("Category:Optional\\[ResourceKey\\[minecraft:recipe_book_category / minecraft:(\\w+)]]").matcher(line);
                RecipeBookCategory category = categoryMatcher.find()
                        ? BuiltInRegistries.RECIPE_BOOK_CATEGORY.getValue(
                        Identifier.fromNamespaceAndPath("minecraft", categoryMatcher.group(1))
                )
                        : RecipeBookCategories.CRAFTING_MISC;

                Matcher dimMatcher = Pattern.compile("ShapedCraftingRecipeDisplay\\[width=(\\d+), height=(\\d+)").matcher(line);
                if (!dimMatcher.find()) {
                    return Optional.empty();
                }
                int width = Integer.parseInt(dimMatcher.group(1));
                int height = Integer.parseInt(dimMatcher.group(2));

                Matcher resultMatcher = Pattern.compile("result=StackSlotDisplay\\[stack=(\\d+) minecraft:(\\w+)]").matcher(line);
                String resultItem = null;
                int resultCount = 1;
                if (!resultMatcher.find()) {
                    resultMatcher = Pattern.compile("result=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / minecraft:(\\w+)]=minecraft:(\\w+)}\\]").matcher(line);
                    if (!resultMatcher.find()) {
                        return Optional.empty();
                    }
                }
                if (resultMatcher.group(1) != null && resultMatcher.group(2) != null) {
                    try {
                        resultCount = Integer.parseInt(resultMatcher.group(1));
                    } catch (NumberFormatException e) {
                        resultCount = 1;
                    }
                    resultItem = fixResourceName(resultMatcher.group(2));
                } else if (resultMatcher.group(2) != null) {
                    resultItem = fixResourceName(resultMatcher.group(2));
                    resultCount = 1;
                }

                Matcher stationMatcher = Pattern.compile("craftingStation=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(line);
                if (!stationMatcher.find()) {
                    return Optional.empty();
                }
                String stationName = fixResourceName(stationMatcher.group(1));

                Matcher ingredientsMatcher = Pattern.compile("ingredients=\\[(.*)]").matcher(line);
                if (!ingredientsMatcher.find()) {
                    return Optional.empty();
                }
                String ingredientsSection = ingredientsMatcher.group(1);

                List<SlotDisplay> slots = new ArrayList<>();
                for (String rawSlot : splitTopLevelSlotDisplays(ingredientsSection)) {
                    rawSlot = rawSlot.trim();

                    if (rawSlot.startsWith("<empty")) {
                        slots.add(SlotDisplay.Empty.INSTANCE);
                    } else if (rawSlot.startsWith("TagSlotDisplay")) {
                        String tagName = rawSlot.substring(
                                rawSlot.indexOf("minecraft:") + "minecraft:".length(),
                                rawSlot.indexOf("]")
                        ).trim();
                        String[] splitTag = tagName.split(":");
                        String lastWord = splitTag[splitTag.length - 1];
                        TagKey<Item> tagKey = TagKey.create(
                                Registries.ITEM,
                                Identifier.fromNamespaceAndPath("minecraft", lastWord)
                        );
                        slots.add(new SlotDisplay.TagSlotDisplay(tagKey));
                    } else if (rawSlot.startsWith("CompositeSlotDisplay")) {
                        List<String> innerItems = extractItemSlotDisplays(rawSlot);
                        List<SlotDisplay> compositeContents = new ArrayList<>();

                        for (String nested : innerItems) {
                            nested = nested.trim();

                            if (nested.startsWith("WithRemainderSlotDisplay")) {
                                Matcher inputMatcher = Pattern.compile("input=ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(nested);
                                Matcher remainderMatcher = Pattern.compile("remainder=StackSlotDisplay\\[stack=(\\d+) minecraft:([a-z0-9_]+)]").matcher(nested);

                                if (inputMatcher.find()) {
                                    String inputItemName = fixResourceName(inputMatcher.group(1));
                                    Item inputItem = BuiltInRegistries.ITEM.getValue(
                                            Identifier.fromNamespaceAndPath("minecraft", inputItemName)
                                    );
                                    SlotDisplay.ItemSlotDisplay input = new SlotDisplay.ItemSlotDisplay(inputItem);

                                    if (remainderMatcher.find()) {
                                        int count = Integer.parseInt(remainderMatcher.group(1));
                                        String remainderItemName = fixResourceName(remainderMatcher.group(2));
                                        Item remainderItem = BuiltInRegistries.ITEM.getValue(
                                                Identifier.fromNamespaceAndPath("minecraft", remainderItemName)
                                        );
                                        SlotDisplay.ItemStackSlotDisplay remainder = stackDisplay(remainderItem, count);

                                        compositeContents.add(new SlotDisplay.WithRemainder(input, remainder));
                                    } else {
                                        compositeContents.add(input);
                                    }
                                }

                            } else if (nested.startsWith("ItemSlotDisplay")) {
                                Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(nested);
                                if (itemMatcher.find()) {
                                    String itemName = fixResourceName(itemMatcher.group(1));
                                    Item item = BuiltInRegistries.ITEM.getValue(
                                            Identifier.fromNamespaceAndPath("minecraft", itemName)
                                    );
                                    compositeContents.add(new SlotDisplay.ItemSlotDisplay(item));
                                }
                            }
                        }

                        slots.add(new SlotDisplay.Composite(compositeContents));
                    } else if (rawSlot.startsWith("ItemSlotDisplay")) {
                        Matcher itemMatcher = Pattern.compile("ItemSlotDisplay\\[item=Reference\\{ResourceKey\\[minecraft:item / ([^\\]]+)]").matcher(rawSlot);
                        if (itemMatcher.find()) {
                            String itemName = fixResourceName(itemMatcher.group(1));
                            Item item = BuiltInRegistries.ITEM.getValue(
                                    Identifier.fromNamespaceAndPath("minecraft", itemName)
                            );
                            slots.add(new SlotDisplay.ItemSlotDisplay(item));
                        }
                    }
                }

                Matcher itemsMatcher = Pattern.compile("Crafting Requirements Items:(.*)").matcher(line);
                if (!itemsMatcher.find()) {
                    return Optional.empty();
                }
                String itemsSection = itemsMatcher.group(1);
                List<Ingredient> ingredients = new ArrayList<>();

                for (String rawItem : itemsSection.split(";")) {
                    List<Item> alternatives = new ArrayList<>();
                    for (String itemVariant : rawItem.split(",")) {
                        String itemName = itemVariant.trim();
                        if (!itemName.isBlank()) {
                            String[] splitItem = itemName.split(":");
                            String namespace = splitItem.length == 2 ? splitItem[0] : "minecraft";
                            String path = splitItem[splitItem.length - 1];
                            Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
                            Item item = BuiltInRegistries.ITEM.getValue(id);
                            if (item != Items.AIR) {
                                alternatives.add(item);
                            }
                        }
                    }
                    if (!alternatives.isEmpty()) {
                        ingredients.add(Ingredient.of(alternatives.stream()));
                    }
                }

                int total = width * height;
                while (slots.size() < total) {
                    slots.add(new SlotDisplay.Composite(List.of()));
                }
                if (slots.size() > total) {
                    slots = slots.subList(0, total);
                }

                SlotDisplay.ItemStackSlotDisplay result = stackDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", resultItem)),
                        resultCount
                );
                SlotDisplay.ItemSlotDisplay station = new SlotDisplay.ItemSlotDisplay(
                        BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", stationName))
                );

                RecipeDisplayId recipeId = new RecipeDisplayId(index);
                ShapedCraftingRecipeDisplay display = new ShapedCraftingRecipeDisplay(width, height, slots, result, station);
                RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
                return Optional.of(entry);
            }

        } catch (Exception e) {
            System.out.println("Ошибка при парсинге строки: " + e.getMessage());
            e.printStackTrace();
        }

        return Optional.empty();
    }

    private static List<String> extractItemSlotDisplays(String rawSlot) {
        List<String> result = new ArrayList<>();

        Matcher contentsMatcher = Pattern.compile("contents=\\[(.*)]\\]?").matcher(rawSlot);
        if (!contentsMatcher.find()) {
            return result;
        }

        String contents = contentsMatcher.group(1).trim();

        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < contents.length(); i++) {
            char c = contents.charAt(i);

            if (c == '[') {
                depth++;
            }
            if (c == ']') {
                depth--;
            }

            current.append(c);

            if ((c == ',' && depth == 0) || i == contents.length() - 1) {
                String part = current.toString().trim();
                if (!part.isEmpty() && !part.equals(",")) {
                    result.add(part.replaceAll(",$", ""));
                }
                current.setLength(0);
            }
        }

        return result;
    }

    private static List<String> splitTopLevelSlotDisplays(String input) {
        List<String> result = new ArrayList<>();
        int bracketLevel = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '[') {
                bracketLevel++;
            } else if (c == ']') {
                bracketLevel--;
            }

            if (c == ',' && bracketLevel == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (!current.toString().isBlank()) {
            result.add(current.toString().trim());
        }

        return result;
    }

    private static SlotDisplay createSlotDisplay(String itemOrTag, boolean isBase) {
        if (itemOrTag.equals("air")) {
            return new SlotDisplay.ItemSlotDisplay(Items.AIR);
        }

        if (isBase) {
            return new SlotDisplay.Composite(
                    Arrays.asList(
                            new SlotDisplay.ItemSlotDisplay(
                                    BuiltInRegistries.ITEM.getValue(
                                            Identifier.fromNamespaceAndPath("minecraft", itemOrTag)
                                    )
                            )
                    )
            );
        } else {
            return new SlotDisplay.TagSlotDisplay(
                    TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("minecraft", itemOrTag))
            );
        }
    }

    static void sendToClient(RecipeDisplayEntry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player != null && minecraft.level != null) {
            ClientRecipeBook recipeBook = player.getRecipeBook();
            recipeBook.add(entry);
            recipeBook.rebuildCollections();

            SlotDisplay resultSlot = entry.display().result();
            ContextMap context = SlotDisplayContext.fromLevel(
                    Objects.requireNonNull(Minecraft.getInstance().level)
            );

            List<ItemStack> resultStacks = resultSlot.resolveForStacks(context);

            if (!resultStacks.isEmpty()) {
                ItemStack result = resultStacks.get(0);

                List<ItemStack> stations = entry.display().craftingStation().resolveForStacks(context);
                if (!stations.isEmpty() && stations.get(0).is(Items.CRAFTING_TABLE)) {
                    JebClient.existingResultItems.add(result.getItem());
                }
            }
        }
    }
}