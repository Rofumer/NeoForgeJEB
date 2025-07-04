package jeb.mixin;

import client.JebClient;
import client.RecipeIndex;
import client.RecipeLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.util.*;

import static client.JebClient.existingResultItems;
import static client.JebClient.nonexistingResultItems;
import static client.RecipeIndex.*;


@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Unique
    private static int jEB$knownRecipeCount = 0;

    @Unique
    private static int jEB$craftingStationId = 0;

    @Unique
    private static final Map<String, Integer> VANILLA_RECIPE_COUNTS = Map.of(
            "1.21.4", 1358,
            "1.21.5", 1361,
            "1.21.6", 1395,
            "1.21.7", 1395
    );

    @Unique
    private static final Map<String, Integer> VANILLA_CT_ID = Map.of(
            "1.21.4", 259,
            "1.21.5", 259,
            "1.21.6", 262,
            "1.21.7", 262
    );

    @Inject(
            method = "handleRecipeBookAdd",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ClientRecipeBook;add(Lnet/minecraft/world/item/crafting/display/RecipeDisplayEntry;)V"),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void injectOnRecipeBookAdd(ClientboundRecipeBookAddPacket p_379950_, CallbackInfo ci, ClientRecipeBook clientrecipebook, Iterator var3, ClientboundRecipeBookAddPacket.Entry clientboundrecipebookaddpacket$entry) {

        ContextMap context = SlotDisplayContext.fromLevel(
                Objects.requireNonNull(Minecraft.getInstance().level)
        );

        if(client.JebClient.recipesLoaded) {
        //if(1==0) {

            RecipeBookCategory category = clientboundrecipebookaddpacket$entry.contents().category();

            RecipeCollection collection = findOrCreateCollectionFor(category, clientboundrecipebookaddpacket$entry.contents(), context);
            if (collection == null) {
                // Ошибка, не смогли создать коллекцию
                return;
            }

            RecipeIndex.updateIndexesWithRecipe(category, collection, clientboundrecipebookaddpacket$entry.contents());

            jebIndexReady = true;
        }

        SlotDisplay resultSlot = clientboundrecipebookaddpacket$entry.contents().display().result();

        List<net.minecraft.world.item.ItemStack> stacks = resultSlot.resolveForStacks(context);

        if (stacks.isEmpty()) {
            System.err.println("Warning: Empty stacks for resultSlot in recipe " + clientboundrecipebookaddpacket$entry);
            return; // или можно continue, если это цикл, или просто не делать ничего дальше
        }

        net.minecraft.world.item.ItemStack stack = stacks.get(0);

        // Добавляем в Set
        if(clientboundrecipebookaddpacket$entry.contents().display().craftingStation().resolveForStacks(context).getFirst().getItem() == Items.CRAFTING_TABLE) {
            existingResultItems.add(stack.getItem());
        }

        // Можно сделать что угодно с display — лог, анализ, модификация
        // System.out.println("Получен display: " + display);
    }



        @Inject(method = "handleRecipeBookAdd", at = @At("TAIL"))
        private void afterRecipeBookAdd(ClientboundRecipeBookAddPacket p_379950_, CallbackInfo ci) {

            if(client.JebClient.recipesLoaded) return;


            String version = SharedConstants.getCurrentVersion().name(); // примерная функция

            int vanillaMaxRecipes = VANILLA_RECIPE_COUNTS.getOrDefault(version, 1358);
            int vanillaCTID = VANILLA_CT_ID.getOrDefault(version, 259);

            Minecraft client = Minecraft.getInstance();

            ClientRecipeBook recipeBook = null;

            //if (client.player != null) {
            recipeBook = client.player.getRecipeBook();
            List<RecipeCollection> recipes = recipeBook.getCollections();


            // Проходим по всем коллекциям рецептов
            for (RecipeCollection collection : recipes) {
                List<net.minecraft.world.item.crafting.display.RecipeDisplayEntry> entries = collection.getRecipes();

                // Преобразуем в строку и выводим подробности для каждого рецепта
                for (net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry : entries) {


                    SlotDisplay resultSlot = entry.display().result();

                    ContextMap context = SlotDisplayContext.fromLevel(
                            Objects.requireNonNull(Minecraft.getInstance().level)
                    );

                    List<net.minecraft.world.item.ItemStack> stacks = resultSlot.resolveForStacks(context);


                    net.minecraft.world.item.ItemStack stack = stacks.getFirst();


                    if (stack.getItem() == Items.CRAFTING_TABLE) jEB$craftingStationId =entry.id().index();


                    jEB$knownRecipeCount++;

                }

            }
            // }

            //if (knownRecipeCount < 1358 && craftingStationId == 259) {
            if (jEB$knownRecipeCount < vanillaMaxRecipes && jEB$craftingStationId == vanillaCTID) { //1.21.6

                try {
                    RecipeLoader.loadRecipesFromLog();
                    JebClient.recipesLoaded = true;
                    buildRecipeIndex();

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }


            if(jEB$knownRecipeCount >= vanillaMaxRecipes || (jEB$craftingStationId != vanillaCTID && jEB$craftingStationId !=0)) {  //for 1.21.6
                JebClient.recipesLoaded = true;
                buildRecipeIndex();
            }

            if(JebClient.recipesLoaded) {

                nonexistingResultItems.clear();

                for (Item item : BuiltInRegistries.ITEM) {
                    if (item == Items.AIR) continue;
                    if (existingResultItems.contains(item)) continue;
                    nonexistingResultItems.add(item);
                }

                fillItemIndex();
            }
        }


}

