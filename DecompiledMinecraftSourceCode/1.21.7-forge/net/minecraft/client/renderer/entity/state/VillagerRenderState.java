package net.minecraft.client.renderer.entity.state;

import javax.annotation.Nullable;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VillagerRenderState extends HoldingEntityRenderState implements VillagerDataHolderRenderState {
    public boolean isUnhappy;
    @Nullable
    public VillagerData villagerData;

    @Nullable
    @Override
    public VillagerData getVillagerData() {
        return this.villagerData;
    }
}
