package client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

//@EventBusSubscriber(modid = "jeb", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
@EventBusSubscriber(modid = "jeb", value = Dist.CLIENT)
public class JebClient {
    public static final Logger LOGGER = LoggerFactory.getLogger("JEB");
    // --- Клиентские переменные и "кэш" ---
    public static Set<Item> existingResultItems = new HashSet<>();
    public static Set<Item> nonexistingResultItems = new HashSet<>();
    public static String string = "-";
    public static List<RecipeCollection> filtered = new ArrayList<>();
    public static List<RecipeCollection> emptysearch = new ArrayList<>();
    public static boolean recipesLoaded = false;
    public static boolean customToggleEnabled = true;
    public static List<RecipeCollection> PREGENERATED_RECIPES;

    public static Path CONFIG_PATH;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static KeyMapping keyBinding;
    public static KeyMapping keyBinding2;

    // --- Keybindings ---
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        keyBinding = new KeyMapping(
                "key.jeb.optional_recipes_loading_screen",
                GLFW.GLFW_KEY_APOSTROPHE,
                "JEB (Just Enough Book)"
        );
        event.register(keyBinding);

        keyBinding2 = new KeyMapping(
                "key.jeb.add_remove_favorite_recipes",
                GLFW.GLFW_KEY_A,
                "JEB (Just Enough Book)"
        );
        event.register(keyBinding2);
    }

    // --- Client tick обработчик ---
    /*@SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (keyBinding != null && keyBinding.consumeClick()) {
            if (client.screen == null) {
                // client.setScreen(new jeb.client.RecipeListScreen());
                // Твой custom GUI
            }
        }
    }*/

    // --- Сброс данных при подключении к серверу ---
    /*@SubscribeEvent
    public static void onClientLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        recipesLoaded = false;
        existingResultItems.clear();
        nonexistingResultItems.clear();
        string = "-";
        emptysearch.clear();
    }*/

    // --- Client setup: инициализация config и pregenerated recipes ---
    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        PREGENERATED_RECIPES = generateCustomRecipeList("");
        CONFIG_PATH = Paths.get(
                Minecraft.getInstance().gameDirectory.getAbsolutePath(),
                "config", "JEB.json"
        );
        loadConfig();
    }

    // --- Сохранение/загрузка конфига ---
    public static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json.has("customToggleEnabled")) {
                        customToggleEnabled = json.get("customToggleEnabled").getAsBoolean();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveConfig() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("customToggleEnabled", customToggleEnabled);

            Files.createDirectories(CONFIG_PATH.getParent());
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Поисковик "фейковых" рецептов по фильтру ---
    public static List<RecipeCollection> generateCustomRecipeList(String filter) {
        List<RecipeCollection> result = new ArrayList<>();
        Minecraft client = Minecraft.getInstance();

        String query = "";
        String modName = null;
        if (filter.startsWith("@")) {
            int endIndex = filter.indexOf(" ");
            if (endIndex != -1) {
                modName = filter.substring(1, endIndex).trim();
                query = filter.substring(endIndex + 1).toLowerCase(Locale.ROOT);
            } else {
                modName = filter.substring(1).trim();
            }
        } else {
            query = filter.toLowerCase(Locale.ROOT);
        }

        TooltipFlag tooltipFlag = client.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;

        for (Item item : nonexistingResultItems) {
            if (item == Items.AIR) continue;

            if (modName != null && !modName.isEmpty() &&
                    !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace().contains(modName.toLowerCase(Locale.ROOT))) {
                continue;
            }

            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            String idString = item.toString().toLowerCase(Locale.ROOT);
            String key = "";
            Component nameComponent = item.getName();
            if (nameComponent.getContents() instanceof TranslatableContents translatable) {
                key = translatable.getKey().toLowerCase(Locale.ROOT);
            }

            boolean matches = query.isEmpty()
                    || name.contains(query)
                    || idString.contains(query)
                    || key.contains(query);

            if (!matches && query.length() >= 3 && client.level != null) {
                try {
                    List<Component> tooltip = item.getDefaultInstance().getTooltipLines(Item.TooltipContext.of(client.level), client.player, tooltipFlag);
                    for (Component line : tooltip) {
                        String clean = ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                        if (clean.contains(query)) {
                            matches = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (!matches) continue;
            result.add(createDummyRecipeCollection(item));
        }

        return result;
    }

    private static RecipeCollection createDummyRecipeCollection(Item item) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
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
}
