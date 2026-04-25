/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code BeforeBatch} methods are ran before the batch specified has started.
 * 
 * <p>{@code BeforeBatch} methods must take 1 parameter of {@link net.minecraft.server.world.ServerWorld}.
 */
@Target(value={ElementType.METHOD})
@Retention(value=RetentionPolicy.RUNTIME)
public @interface BeforeBatch {
    public String batchId();
}

