package client;

import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;

public record SearchHistoryEntry(String query, ExtendedRecipeBookCategory category) {}
