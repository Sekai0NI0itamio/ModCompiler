package net.minecraft.tags;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public class TagBuilder implements net.minecraftforge.common.extensions.IForgeRawTagBuilder {
    private final List<TagEntry> entries = new ArrayList<>();

    public static TagBuilder create() {
        return new TagBuilder();
    }

    public List<TagEntry> build() {
        return List.copyOf(this.entries);
    }

    public TagBuilder addElement(ResourceLocation p_215901_) {
        this.entries.add(TagEntry.element(p_215901_));
        return this;
    }

    public TagBuilder addOptionalElement(ResourceLocation p_215906_) {
        this.entries.add(TagEntry.optionalElement(p_215906_));
        return this;
    }

    public TagBuilder addTag(ResourceLocation p_215908_) {
        this.entries.add(TagEntry.tag(p_215908_));
        return this;
    }

    public TagBuilder addOptionalTag(ResourceLocation p_215910_) {
        this.entries.add(TagEntry.optionalTag(p_215910_));
        return this;
    }

    /** Forge: Used for datagen */
    private final List<TagEntry> removeEntries = new ArrayList<>();
    private boolean replace = false;

    public java.util.stream.Stream<TagEntry> getRemoveEntries() { // should this return the List instead? Might end up with mem leaks from unterminated streams otherwise -Paint_Ninja
        return this.removeEntries.stream();
    }

    /** Forge: Add an entry to be removed from this tag in datagen */
    public TagBuilder remove(TagEntry entry) {
        this.removeEntries.add(entry);
        return this;
    }

    /** Forge: Set the replace property of this tag */
    public TagBuilder replace(boolean value) {
        this.replace = value;
        return this;
    }

    /** Forge: Is this tag set to replace or not? */
    public boolean isReplace() {
        return this.replace;
    }
}
