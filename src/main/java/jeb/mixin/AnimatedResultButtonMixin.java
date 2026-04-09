package jeb.mixin;

import jeb.accessor.AnimatedResultButtonExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeButton.class)
public class AnimatedResultButtonMixin implements AnimatedResultButtonExtension {

    @Unique
    private long jeb$flashUntil = 0L;

    @Unique
    @Override
    public void jeb$flash() {
        this.jeb$flashUntil = System.currentTimeMillis() + 300L;
    }

    @Unique
    private boolean jeb$isFlashing() {
        return System.currentTimeMillis() < this.jeb$flashUntil;
    }

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void jeb$renderFlash(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!jeb$isFlashing()) {
            return;
        }

        RecipeButton self = (RecipeButton) (Object) this;

        graphics.fill(self.getX(), self.getY(), self.getX() + self.getWidth(), self.getY() + 1, 0xFFFFFF00);
        graphics.fill(self.getX(), self.getY() + self.getHeight() - 1, self.getX() + self.getWidth(), self.getY() + self.getHeight(), 0xFFFFFF00);
        graphics.fill(self.getX(), self.getY(), self.getX() + 1, self.getY() + self.getHeight(), 0xFFFFFF00);
        graphics.fill(self.getX() + self.getWidth() - 1, self.getY(), self.getX() + self.getWidth(), self.getY() + self.getHeight(), 0xFFFFFF00);
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection;getSelectedRecipes(Lnet/minecraft/client/gui/screens/recipebook/RecipeCollection$CraftableStatus;)Ljava/util/List;"
            )
    )
    private List<RecipeDisplayEntry> redirectFilter(RecipeCollection instance, RecipeCollection.CraftableStatus craftableStatus) {
        return instance.getRecipes();
    }

    @Unique
    private static final Component MORE_RECIPES_TEXT = Component.translatable("items.craftsfromitem");

    @Inject(method = "getTooltipText", at = @At("HEAD"), cancellable = true)
    private void onGetTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        try {
            List<Component> list = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
            list.add(MORE_RECIPES_TEXT);
            cir.setReturnValue(list);
        } catch (Exception e) {
            e.printStackTrace();
            cir.setReturnValue(List.of(Component.literal("§c[Error rendering tooltip]")));
        }
    }
}