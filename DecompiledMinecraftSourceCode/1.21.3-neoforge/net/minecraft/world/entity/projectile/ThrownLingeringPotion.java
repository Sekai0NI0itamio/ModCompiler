package net.minecraft.world.entity.projectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

public class ThrownLingeringPotion extends AbstractThrownPotion {
    public ThrownLingeringPotion(EntityType<? extends ThrownLingeringPotion> p_397034_, Level p_392568_) {
        super(p_397034_, p_392568_);
    }

    public ThrownLingeringPotion(Level p_393465_, LivingEntity p_395341_, ItemStack p_393365_) {
        super(EntityType.LINGERING_POTION, p_393465_, p_395341_, p_393365_);
    }

    public ThrownLingeringPotion(Level p_392824_, double p_396351_, double p_397285_, double p_392434_, ItemStack p_395533_) {
        super(EntityType.LINGERING_POTION, p_392824_, p_396351_, p_397285_, p_392434_, p_395533_);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.LINGERING_POTION;
    }

    @Override
    public void onHitAsPotion(ServerLevel p_394078_, ItemStack p_391393_, HitResult p_408590_) {
        AreaEffectCloud areaeffectcloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
        if (this.getOwner() instanceof LivingEntity livingentity) {
            areaeffectcloud.setOwner(livingentity);
        }

        areaeffectcloud.setRadius(3.0F);
        areaeffectcloud.setRadiusOnUse(-0.5F);
        areaeffectcloud.setDuration(600);
        areaeffectcloud.setWaitTime(10);
        areaeffectcloud.setRadiusPerTick(-areaeffectcloud.getRadius() / areaeffectcloud.getDuration());
        areaeffectcloud.applyComponentsFromItemStack(p_391393_);
        p_394078_.addFreshEntity(areaeffectcloud);
    }
}
