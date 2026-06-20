package immersive_aircraft.network;

import immersive_aircraft.Main;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Network entry point for the mod. Equivalent to the 1.20.1
 * {@code cobalt.network.NetworkHandler} but using the 1.12.2
 * {@link SimpleNetworkWrapper}.
 */
public class Messages {
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(Main.MODID);

    public static void registerMessages() {
        int id = 0;
        INSTANCE.registerMessage(MessageAircraftControl.Handler.class, MessageAircraftControl.class, id++, Side.SERVER);
        INSTANCE.registerMessage(MessageEnginePower.Handler.class, MessageEnginePower.class, id++, Side.SERVER);
    }
}
