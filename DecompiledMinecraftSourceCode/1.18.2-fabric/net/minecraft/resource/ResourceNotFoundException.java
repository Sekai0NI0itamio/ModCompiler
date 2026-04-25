/*
 * Decompiled with CFR 0.1.1 (FabricMC 57d88659).
 */
package net.minecraft.resource;

import java.io.File;
import java.io.FileNotFoundException;

public class ResourceNotFoundException
extends FileNotFoundException {
    public ResourceNotFoundException(File packSource, String resource) {
        super(String.format("'%s' in ResourcePack '%s'", resource, packSource));
    }
}

