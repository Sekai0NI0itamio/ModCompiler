package asd.itamio.shop;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;

@Mod(modid = ShopMod.MODID, name = ShopMod.NAME, version = ShopMod.VERSION)
public class ShopMod {

    public static final String MODID = "shop";
    public static final String NAME = "Shop";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MODID)
    public static ShopMod instance;

    public static Logger logger;

    public static SimpleNetworkWrapper NETWORK;

    private static List<ShopCategory> categories = Collections.emptyList();
    private static PriceEngine priceEngine = new PriceEngine();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        logger.info("Shop mod initializing...");

        NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        NETWORK.registerMessage(ShopPacketHandler.class, ShopPacket.class, 0, Side.SERVER);
        NETWORK.registerMessage(ShopClientPacketHandler.class, ShopPacket.class, 1, Side.CLIENT);

        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        buildShopCategories();
        logger.info("Shop mod initialized with " + categories.size() + " categories");
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandShop());
        event.registerServerCommand(new CommandSellHand());
        event.registerServerCommand(new CommandSellGui());
        event.registerServerCommand(new CommandBalance());
        event.registerServerCommand(new CommandPay());

        // Register /bal as alias
        event.registerServerCommand(new CommandBalance() {
            @Override
            public String getName() {
                return "bal";
            }
        });

        // Rebuild categories in case recipes changed
        buildShopCategories();
        logger.info("Shop commands registered");
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            EconomyData economy = EconomyData.get(player.getEntityWorld());
            economy.registerPlayer(player.getName(), player.getUniqueID());
            logger.info("Registered player {} -> {}", player.getName(), player.getUniqueID());
        }
    }

    private void buildShopCategories() {
        categories = ShopCategory.buildFromCreativeTabs();
        priceEngine.clearCache();
        logger.info("Built " + categories.size() + " shop categories from creative tabs");
    }

    public static List<ShopCategory> getCategories() {
        return categories;
    }

    public static PriceEngine getPriceEngine() {
        return priceEngine;
    }
}
