package immersive_aircraft;

import immersive_aircraft.entity.AirshipEntity;
import immersive_aircraft.entity.BiplaneEntity;
import immersive_aircraft.entity.CargoAirshipEntity;
import immersive_aircraft.entity.GyrodyneEntity;
import immersive_aircraft.entity.QuadrocopterEntity;
import immersive_aircraft.item.ItemAircraft;
import immersive_aircraft.network.Messages;
import immersive_aircraft.proxy.CommonProxy;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Main.MODID,
        name = Main.NAME,
        version = Main.VERSION,
        acceptedMinecraftVersions = "[1.12.2]"
)
public class Main {
    public static final String MODID = "immersive_aircraft";
    public static final String NAME = "Immersive Aircraft";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(NAME);

    @Mod.Instance(MODID)
    public static Main INSTANCE;

    @SidedProxy(
            clientSide = "immersive_aircraft.proxy.ClientProxy",
            serverSide = "immersive_aircraft.proxy.CommonProxy"
    )
    public static CommonProxy PROXY;

    public static CreativeTabs creativeTab;

    // Item registry (populated during preInit)
    public static Item itemBiplane;
    public static Item itemAirship;
    public static Item itemCargoAirship;
    public static Item itemGyrodyne;
    public static Item itemQuadrocopter;

    public static ResourceLocation locate(String path) {
        return new ResourceLocation(MODID, path);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("[" + NAME + "] Starting preInit");

        creativeTab = new CreativeTabs(MODID) {
            @Override
            public Item getTabIconItem() {
                return itemBiplane;
            }
        };

        registerEntities();
        registerItems();
        Messages.registerMessages();

        PROXY.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("[" + NAME + "] Starting init");
        ModRecipes.register();
        PROXY.init(event);
    }

    private void registerEntities() {
        int id = 0;
        EntityRegistry.registerModEntity(
                locate("biplane"),
                BiplaneEntity.class,
                MODID + ".biplane",
                id++,
                INSTANCE,
                80, 3, true
        );
        EntityRegistry.registerModEntity(
                locate("airship"),
                AirshipEntity.class,
                MODID + ".airship",
                id++,
                INSTANCE,
                80, 3, true
        );
        EntityRegistry.registerModEntity(
                locate("cargo_airship"),
                CargoAirshipEntity.class,
                MODID + ".cargo_airship",
                id++,
                INSTANCE,
                80, 3, true
        );
        EntityRegistry.registerModEntity(
                locate("gyrodyne"),
                GyrodyneEntity.class,
                MODID + ".gyrodyne",
                id++,
                INSTANCE,
                80, 3, true
        );
        EntityRegistry.registerModEntity(
                locate("quadrocopter"),
                QuadrocopterEntity.class,
                MODID + ".quadrocopter",
                id++,
                INSTANCE,
                80, 3, true
        );
    }

    private void registerItems() {
        itemBiplane = registerItem(new ItemAircraft("biplane", BiplaneEntity.class));
        itemAirship = registerItem(new ItemAircraft("airship", AirshipEntity.class));
        itemCargoAirship = registerItem(new ItemAircraft("cargo_airship", CargoAirshipEntity.class));
        itemGyrodyne = registerItem(new ItemAircraft("gyrodyne", GyrodyneEntity.class));
        itemQuadrocopter = registerItem(new ItemAircraft("quadrocopter", QuadrocopterEntity.class));
    }

    private Item registerItem(Item item) {
        item.setCreativeTab(creativeTab);
        // 1.12.2 item registration: set the registry name then call register
        if (item.getRegistryName() == null) {
            String name = item.getUnlocalizedName().substring("item.".length());
            item.setRegistryName(MODID, name);
        }
        GameRegistry.register(item);
        return item;
    }
}
