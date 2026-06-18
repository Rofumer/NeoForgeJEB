package client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;

import static client.JebClient.*;

//@EventBusSubscriber(modid = "jeb", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
@EventBusSubscriber(modid = "jeb", value = Dist.CLIENT)
public class JebClientGameEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (JebClient.keyBinding != null && keyBinding.consumeClick()) {
            if (client.gui.screen() == null) {
                client.gui.setScreen(new client.RecipeListScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onClientLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        JebClient.recipesLoaded = false;
        existingResultItems.clear();
        nonexistingResultItems.clear();
        string = "-";
        emptysearch.clear();
    }
}
