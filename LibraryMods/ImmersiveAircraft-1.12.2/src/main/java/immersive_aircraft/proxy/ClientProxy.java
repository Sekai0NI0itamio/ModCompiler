package immersive_aircraft.proxy;

import immersive_aircraft.client.ClientAircraftController;
import immersive_aircraft.entity.AirshipEntity;
import immersive_aircraft.entity.BiplaneEntity;
import immersive_aircraft.entity.CargoAirshipEntity;
import immersive_aircraft.entity.GyrodyneEntity;
import immersive_aircraft.entity.QuadrocopterEntity;
import immersive_aircraft.render.model.ModelAirship;
import immersive_aircraft.render.model.ModelBiplane;
import immersive_aircraft.render.model.ModelCargoAirship;
import immersive_aircraft.render.model.ModelGyrodyne;
import immersive_aircraft.render.model.ModelQuadrocopter;
import immersive_aircraft.render.renderer.RenderAircraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        // Register renderers
        RenderingRegistry.registerEntityRenderingHandler(BiplaneEntity.class, manager ->
                new RenderAircraft(manager, new ModelBiplane(), "biplane"));
        RenderingRegistry.registerEntityRenderingHandler(AirshipEntity.class, manager ->
                new RenderAircraft(manager, new ModelAirship(), "airship"));
        RenderingRegistry.registerEntityRenderingHandler(CargoAirshipEntity.class, manager ->
                new RenderAircraft(manager, new ModelCargoAirship(), "cargo_airship"));
        RenderingRegistry.registerEntityRenderingHandler(GyrodyneEntity.class, manager ->
                new RenderAircraft(manager, new ModelGyrodyne(), "gyrodyne"));
        RenderingRegistry.registerEntityRenderingHandler(QuadrocopterEntity.class, manager ->
                new RenderAircraft(manager, new ModelQuadrocopter(), "quadrocopter"));
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new ClientAircraftController());
    }
}
