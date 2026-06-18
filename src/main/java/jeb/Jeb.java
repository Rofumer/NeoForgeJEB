package jeb;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;

@Mod(Jeb.MODID)
public class Jeb {

    public static final String MODID = "jeb";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Jeb(IEventBus modEventBus, ModContainer modContainer) {
    }
}
