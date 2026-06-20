Immersive Aircraft - 1.12.2 Port
================================

A 1.12.2 Forge port of the original Immersive Aircrafts mod (Luke100000) that
targets Minecraft 1.20.1. The original mod is licensed GPL-3.0 and is
maintained at https://github.com/Luke100000/ImmersiveAircraft.

This port brings the core "fly a custom aircraft" experience to 1.12.2:

- Biplane, Airship, Cargo Airship, Gyrodyne, and Quadrocopter
- Engine throttle, lift / drag / thrust simulation
- Simple Java models rendered with the 1.12.2 `ModelBase` API
- Multiplayer-safe control via `SimpleNetworkWrapper`

Modelling choices
-----------------
The 1.20.1 mod uses a custom BBModel loader that does not exist in 1.12.2, so
the aircraft in this port are simple coloured `ModelRenderer` boxes. They are
deliberately basic; replace the textures and tweak the model box coordinates
in `render.model.*` to suit your taste.

Files
-----
- src/main/java/immersive_aircraft/        Java sources
- src/main/resources/                      Assets, recipes, mcmod.info
- build.gradle                             ForgeGradle 3 / 1.12.2-14.23.5.2864

Building
--------
1. Ensure Java 8 is on your PATH (1.12.2 is JDK 8 only).
2. Run `./gradlew setupDecompWorkspace` once.
3. Run `./gradlew build` to produce the mod jar in `build/libs/`.

Controls while flying
---------------------
- W / S: pitch (climb / dive)
- A / D: yaw (turn left / right)
- Space: throttle up
- Shift: throttle down
- Sneak + jump: dismount (vanilla behaviour)

License
-------
GPL-3.0-only. See LICENSE for the full text.
