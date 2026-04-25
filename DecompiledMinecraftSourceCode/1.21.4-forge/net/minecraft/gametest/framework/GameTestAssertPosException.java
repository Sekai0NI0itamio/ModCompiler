package net.minecraft.gametest.framework;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class GameTestAssertPosException extends GameTestAssertException {
    private final BlockPos absolutePos;
    private final BlockPos relativePos;

    public GameTestAssertPosException(Component p_397350_, BlockPos p_177052_, BlockPos p_177053_, int p_395387_) {
        super(p_397350_, p_395387_);
        this.absolutePos = p_177052_;
        this.relativePos = p_177053_;
    }

    @Override
    public Component getDescription() {
        return Component.translatable(
            "test.error.position",
            this.message,
            this.absolutePos.getX(),
            this.absolutePos.getY(),
            this.absolutePos.getZ(),
            this.relativePos.getX(),
            this.relativePos.getY(),
            this.relativePos.getZ(),
            this.tick
        );
    }

    @Nullable
    public String getMessageToShowAtBlock() {
        return super.getMessage();
    }

    @Nullable
    public BlockPos getRelativePos() {
        return this.relativePos;
    }

    @Nullable
    public BlockPos getAbsolutePos() {
        return this.absolutePos;
    }
}
