// RecipeListScreen.java — экран загрузки рецептов, адаптирован для NeoForge
package client;

import com.microsoft.aad.msal4j.IClientAssertion;
import com.mojang.logging.LogUtils;
import jeb.Jeb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static client.RecipeLoader.loadRecipesFromLog;
import static client.JebClient.generateCustomRecipeList;

//import static jeb.client.JEBClient.generateCustomRecipeList;
//import static jeb.client.RecipeLoader.loadRecipesFromLog;

public class RecipeListScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static Boolean sent = false;

    public RecipeListScreen() {
        super(Component.literal("Recipe List"));
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("Load All Recipes"), button -> {
            try {
                loadAllRecipes();
                JebClient.PREGENERATED_RECIPES = generateCustomRecipeList("");
                Minecraft.getInstance().gui.setScreen(null);
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal("All recipes have been loaded")
                    );
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).bounds(this.width / 2 - 100, this.height / 2 - 40, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.nullToEmpty("Dump Recipes to File"), button -> {
                    try {
                        showAllRecipes();
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(
                                    Component.literal("Recipes dumped to recipes_output.txt")
                            );
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).bounds(this.width / 2 - 100, this.height / 2 - 15, 200, 20).build());
    }

    public void showAllRecipes() throws InterruptedException {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;

            List<RecipeCollection> collections = mc.player.getRecipeBook().getCollections();
            Path outputPath = Paths.get(mc.gameDirectory.getAbsolutePath(), "recipes_output.txt");
            try (PrintWriter writer = new PrintWriter(outputPath.toFile())) {
                for (RecipeCollection collection : collections) {
                    collection.getRecipes().forEach(entry -> writer.println(entry.toString()));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error dumping recipes", e);
        }
    }

    public void loadAllRecipes() throws InterruptedException {
        try {
            loadRecipesFromLog();
            Thread.sleep(1000);
            LOGGER.info("Recipes loaded successfully.");
        } catch (Exception e) {
            LOGGER.error("Error occurred while loading recipes", e);
        }
    }

    //@Override
    //public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    //    super.render(graphics, mouseX, mouseY, delta);
    //}

    @Override
    public void onClose() {
        executorService.shutdownNow();
        super.onClose();
    }
}
