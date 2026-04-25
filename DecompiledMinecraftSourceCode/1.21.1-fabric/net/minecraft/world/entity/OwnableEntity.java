package net.minecraft.world.entity;

import java.util.UUID;
import net.minecraft.world.level.EntityGetter;
import org.jetbrains.annotations.Nullable;

public interface OwnableEntity {
	@Nullable
	UUID getOwnerUUID();

	EntityGetter level();

	@Nullable
	default LivingEntity getOwner() {
		UUID uUID = this.getOwnerUUID();
		return uUID == null ? null : this.level().getPlayerByUUID(uUID);
	}
}
