package jeb.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerMixin {

    // JEB показывает игроку все рецепты сервера, а ванильный handlePlaceRecipe раскладывает
    // в сетку только разблокированные — по клику на остальные сервер молча ничего не делал.
    // Раскладка ничего не выдаёт сама по себе: при doLimitedCrafting результат крафта
    // всё равно проверяется отдельно в слоте результата.
    @ModifyExpressionValue(
            method = "handlePlaceRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/stats/ServerRecipeBook;contains(Lnet/minecraft/resources/ResourceKey;)Z"
            )
    )
    private boolean jeb$allowPlacingLockedRecipes(boolean unlocked) {
        return true;
    }
}
