package jeb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Jeb.MODID)
public class Jeb {


    public static Set<Item> existingResultItems = new HashSet<>();

    public static boolean recipesLoaded = false;

    public static boolean customToggleEnabled = true;

    public static List<RecipeCollection> PREGENERATED_RECIPES = generateCustomRecipeList("");

    public static List<RecipeCollection> generateCustomRecipeList(String filter) {
        List<RecipeCollection> list = new ArrayList<>();

        Minecraft client = Minecraft.getInstance();

        String query;

        String modName = null;
        if (filter.startsWith("@")) {
            // Извлекаем имя мода, если оно присутствует в начале строки
            int endIndex = filter.indexOf(" ");
            if (endIndex != -1) {
                modName = filter.substring(1, endIndex).trim();  // Извлекаем имя мода
                query = filter.substring(endIndex + 1).toLowerCase();  // Остальная часть это обычный запрос
            } else {
                modName = filter.substring(1).trim();  // Имя мода без строки запроса
                query = "";  // Если нет строки запроса, то фильтровать только по имени мода
            }
        }
        else
        {
            query = filter.toLowerCase();
        }

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            if (existingResultItems.contains(item)) continue;


            String name = item.getName().getString().toLowerCase(Locale.ROOT);
            String id_item = item.toString().toLowerCase(Locale.ROOT);
            String key = "";
            Component nameComponent = item.getName(); // или getDisplayName()
            if (nameComponent.getContents() instanceof TranslatableContents translatable) {
                key = translatable.getKey().toLowerCase(Locale.ROOT);
            }

            if (modName != null && !modName.isEmpty() && !BuiltInRegistries.ITEM.getKey(item).getNamespace().contains(modName.toLowerCase(Locale.ROOT))) {
                continue;
            }


            boolean tooltip_bool = false;


            if (client.level != null)
            {
                // Поиск по тултипам
                TooltipFlag tooltipFlag = client.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;


                try {
                    List<Component> tooltip = item.getDefaultInstance().getTooltipLines(Item.TooltipContext.of(client.level),client.player, tooltipFlag);
                    for (Component line : tooltip) {
                        String clean = net.minecraft.ChatFormatting.stripFormatting(line.getString()).toLowerCase(Locale.ROOT).trim();
                        if (clean.contains(query)){
                            tooltip_bool = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // Можно также записать лог или безопасно проигнорировать ошибку
                }

            }

            if (!(name.contains(query) || id_item.contains(query) || key.contains(query) || tooltip_bool)) continue;
            ///////if (!(name.contains(query) || id_item.contains(query) || key.contains(query))) continue;


            ///////if (!translate(item.getTranslationKey()).toLowerCase().contains(filter.toLowerCase())) continue;


            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            RecipeDisplayId recipeId = new RecipeDisplayId(9999);

            List<SlotDisplay> slots = List.of(
                    new SlotDisplay.TagSlotDisplay(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", id.getPath())))
            );

            SlotDisplay.ItemStackSlotDisplay resultSlot = new SlotDisplay.ItemStackSlotDisplay(new ItemStack(item, 1));
            Item ct = BuiltInRegistries.ITEM.getValue(ResourceLocation.fromNamespaceAndPath("minecraft", "crafting_table"));
            ItemStack stack = new ItemStack(ct);
            SlotDisplay.ItemStackSlotDisplay stationSlot = new SlotDisplay.ItemStackSlotDisplay(stack);

            OptionalInt group = OptionalInt.empty();
            RecipeBookCategory category = RecipeBookCategories.CRAFTING_MISC;

            List<Ingredient> ingredients = List.of(Ingredient.of(item));

            ShapelessCraftingRecipeDisplay display = new ShapelessCraftingRecipeDisplay(slots, resultSlot, stationSlot);
            RecipeDisplayEntry entry = new RecipeDisplayEntry(recipeId, display, group, category, Optional.of(ingredients));
            list.add(new RecipeCollection(List.of(entry)));
        }

        return list;
    }

    private static final Path CONFIG_PATH = Paths.get(
            Minecraft.getInstance().gameDirectory.getAbsolutePath(),
            "config", "JEB.json"
    );
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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




    // Define mod id in a common place for everything to reference
    public static final String MODID = "jeb";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "jeb" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "jeb" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "jeb" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "jeb:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    // Creates a new BlockItem with the id "jeb:example_block", combining the namespace and path
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "jeb:example_id", nutrition 1 and saturation 2
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder().alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "jeb:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.jeb")).withTabsBefore(CreativeModeTabs.COMBAT).icon(() -> EXAMPLE_ITEM.get().getDefaultInstance()).displayItems((parameters, output) -> {
        output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
    }).build());

    /*
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = Jeb.MODID, value = Dist.CLIENT)
    public static class ClientInit {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                // Загрузка конфигурации
                JEBClient.loadConfig();
                Runtime.getRuntime().addShutdownHook(new Thread(JEBClient::saveConfig));

                // Сброс данных при заходе на сервер
                NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.network.ClientConnectedToServerEvent e) -> {
                    recipesLoaded = false;
                    existingResultItems.clear();
                });
            });
        }*/


        // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Jeb(IEventBus modEventBus, ModContainer modContainer) {


        Jeb.loadConfig();
        Runtime.getRuntime().addShutdownHook(new Thread(Jeb::saveConfig));

        // Сброс данных при заходе на сервер
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> {
            recipesLoaded = false;
            existingResultItems.clear();
        });

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (Jeb) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::onRegisterKeyMappings);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private static KeyMapping keyBinding;

    public void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        keyBinding = new KeyMapping(
                "Optional recipes loading screen",
                GLFW.GLFW_KEY_APOSTROPHE,
                "JEB (Just Enough Book)"
        );
        event.register(keyBinding);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (keyBinding != null && keyBinding.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new client.RecipeListScreen());
            }
        }
    }




    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.logDirtBlock) LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) event.accept(EXAMPLE_BLOCK_ITEM);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
