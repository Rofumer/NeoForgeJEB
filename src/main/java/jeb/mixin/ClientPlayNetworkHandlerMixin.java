package jeb.mixin;

import client.JebClient;
import client.RecipeLoader;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static client.JebClient.existingResultItems;
import static client.JebClient.nonexistingResultItems;
import static jeb.Jeb.*;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(
            method = "handleRecipeBookAdd",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ClientRecipeBook;add(Lnet/minecraft/world/item/crafting/display/RecipeDisplayEntry;)V"),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void injectOnRecipeBookAdd(ClientboundRecipeBookAddPacket p_379950_, CallbackInfo ci, ClientRecipeBook clientrecipebook, Iterator var3, ClientboundRecipeBookAddPacket.Entry clientboundrecipebookaddpacket$entry) {

        SlotDisplay resultSlot = clientboundrecipebookaddpacket$entry.contents().display().result();

        //Minecraft client = Minecraft.getInstance();

        ContextMap context = SlotDisplayContext.fromLevel(
                Objects.requireNonNull(Minecraft.getInstance().level)
        );

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

            Minecraft client = Minecraft.getInstance();

            int knownRecipeCount = 0;

            ClientRecipeBook recipeBook = null;

            int craftingStationId = 0;

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


                    if (stack.getItem() == Items.CRAFTING_TABLE) craftingStationId=entry.id().index();


                    knownRecipeCount++;

                }

            }
            // }

            //if (knownRecipeCount < 1358 && craftingStationId == 259) {
            if (knownRecipeCount < 1358 && craftingStationId == 262) { //1.21.6

                try {
                    RecipeLoader.loadRecipesFromLog();
                    JebClient.recipesLoaded = true;

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

            nonexistingResultItems.clear();

            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                if (existingResultItems.contains(item)) continue;
                nonexistingResultItems.add(item);
            }

        }


}

