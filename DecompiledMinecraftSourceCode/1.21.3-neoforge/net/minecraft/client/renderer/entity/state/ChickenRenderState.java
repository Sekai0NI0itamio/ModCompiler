package net.minecraft.client.renderer.entity.state;

import javax.annotation.Nullable;
import net.minecraft.world.entity.animal.ChickenVariant;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ChickenRenderState extends LivingEntityRenderState {
    public float flap;
    public float flapSpeed;
    @Nullable
    public ChickenVariant variant;
}
