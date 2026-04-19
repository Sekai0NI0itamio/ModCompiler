import os
import shutil

src_dir = "src/main/java/com/itamio/stayinthelight"
os.makedirs(src_dir, exist_ok=True)

main_class = """package com.itamio.stayinthelight;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = "stayinthelight", name = "Stay In The Light", version = "1.0", acceptedMinecraftVersions = "[1.12.2]")
public class StayInTheLight {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println("Stay In The Light Initialized");
    }
}
"""

with open(f"{src_dir}/StayInTheLight.java", "w") as f:
    f.write(main_class)

print("Created main class.")
