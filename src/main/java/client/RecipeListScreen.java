// RecipeListScreen.java — экран загрузки рецептов, адаптирован для NeoForge
package client;

import com.microsoft.aad.msal4j.IClientAssertion;
import com.mojang.logging.LogUtils;
import jeb.Jeb;
import net.minecraft.client.Minecraft;
//import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

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
                Minecraft.getInstance().setScreen(null);
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal("All recipes have been loaded")
                    );
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).bounds(this.width / 2 - 100, this.height / 2 - 40, 200, 20).build());
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
