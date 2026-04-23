# Template Provenance

- Source: Official Forge MDK downloaded from `https://maven.minecraftforge.net/net/minecraftforge/forge/26.1.2-64.0.4/forge-26.1.2-64.0.4-mdk.zip`
- Snapshot: Forge 26.1.2-64.0.4 (April 15, 2026)
- Java target: 25 (Minecraft 26.1+ requirement)
- Gradle: 9.3.1
- ForgeGradle: [7.0.17,8)
- Notes:
  - No mappings block — obfuscation removed in 26.1.
  - EventBus 7: @SubscribeEvent from net.minecraftforge.eventbus.api.listener.
  - Constructor uses FMLJavaModLoadingContext + context.getModBusGroup().
  - annotationProcessor eventbus-validator:7.0.1 added for compile-time validation.
