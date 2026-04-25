package net.minecraft.world.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface Saddleable {
	boolean isSaddleable();

	void equipSaddle(ItemStack itemStack, @Nullable SoundSource soundSource);

	default SoundEvent getSaddleSoundEvent() {
		return SoundEvents.HORSE_SADDLE;
	}

	boolean isSaddled();
}
