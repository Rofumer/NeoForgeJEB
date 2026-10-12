package jeb.mixin;

import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeDisplayEntry.class)
public class RecipeDisplayEntryMixin {

    // Рецепты модов с PlacementInfo.NOT_PLACEABLE приходят с пустым (но present) списком требований,
    // а пустой список ванильный tryPick считает выполненным — такие рецепты светились как
    // доступные к крафту без единого ингредиента в инвентаре. Разложить их в сетку сервер всё равно не может.
    @Inject(method = "canCraft", at = @At("HEAD"), cancellable = true)
    private void jeb$notCraftableWithoutRequirements(StackedItemContents providedContents, CallbackInfoReturnable<Boolean> cir) {
        RecipeDisplayEntry self = (RecipeDisplayEntry) (Object) this;
        if (self.craftingRequirements().isPresent() && self.craftingRequirements().get().isEmpty()) {
            cir.setReturnValue(false);
        }
    }
}
