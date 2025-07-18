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

import static client.JebClient.*;
import static client.RecipeIndex.*;


@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Unique
    private static final Map<String, Integer> VANILLA_RECIPE_COUNTS = Map.of(
            "1.21.4", 1358,
            "1.21.5", 1361,
            "1.21.6", 1395,
            "1.21.7", 1395,
            "1.21.8", 1395
    );

    @Unique
    private static final Map<String, Integer> VANILLA_CT_ID = Map.of(
            "1.21.4", 259,
            "1.21.5", 259,
            "1.21.6", 262,
            "1.21.7", 262,
            "1.21.8", 262
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

        if (client.JebClient.recipesLoaded) {
            long startTime = System.currentTimeMillis();
            RecipeBookCategory category = clientboundrecipebookaddpacket$entry.contents().category();
            RecipeDisplayEntry entry = clientboundrecipebookaddpacket$entry.contents();
            LOGGER.info("[JEB] checking recipe {} started at {}", entry.display().result().resolveForFirstStack(context).getItem().toString() ,new Date(startTime));
            // Проверяем по id (по новому методу!)
            if (!RecipeIndex.recipeIdExistsInIndex(category, entry)) {
                RecipeIndex.addAndIndexRecipeIfAbsent(category, entry, context);
                LOGGER.info("[JEB] The recipe has been added: {}", entry.display().result().resolveForFirstStack(context).toString());
            }


            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            LOGGER.info("[JEB] checking recipe done at {} ({} ms)", new Date(endTime), duration);

            return;
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
            //String version = SharedConstants.getCurrentVersion().getName(); // примерная функция

            int vanillaMaxRecipes = VANILLA_RECIPE_COUNTS.getOrDefault(version, 1358);
            int vanillaCTID = VANILLA_CT_ID.getOrDefault(version, 259);

            Minecraft client = Minecraft.getInstance();

            int knownRecipeCount = 0;

            int craftingStationId = 0;


            ClientRecipeBook recipeBook;

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


                    if (stack.getItem() == Items.CRAFTING_TABLE) craftingStationId =entry.id().index();


                    knownRecipeCount++;

                }

            }
            // }

            //if (knownRecipeCount < 1358 && craftingStationId == 259) {
            if (knownRecipeCount < vanillaMaxRecipes && craftingStationId == vanillaCTID) { //1.21.6

                try {
                    RecipeLoader.loadRecipesFromLog();
                    JebClient.recipesLoaded = true;
                    buildRecipeIndex();

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }


            if(knownRecipeCount >= vanillaMaxRecipes || (craftingStationId != vanillaCTID && craftingStationId !=0)) {  //for 1.21.6
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

