package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class CommandStorage {
    private static final String ID_PREFIX = "command_storage_";
    private final Map<String, CommandStorage.Container> namespaces = new HashMap<>();
    private final DimensionDataStorage storage;

    public CommandStorage(DimensionDataStorage p_78035_) {
        this.storage = p_78035_;
    }

    public CompoundTag get(ResourceLocation p_78045_) {
        CommandStorage.Container commandstorage$container = this.getContainer(p_78045_.getNamespace());
        return commandstorage$container != null ? commandstorage$container.get(p_78045_.getPath()) : new CompoundTag();
    }

    @Nullable
    private CommandStorage.Container getContainer(String p_393886_) {
        CommandStorage.Container commandstorage$container = this.namespaces.get(p_393886_);
        if (commandstorage$container != null) {
            return commandstorage$container;
        } else {
            CommandStorage.Container commandstorage$container1 = this.storage.get(CommandStorage.Container.type(p_393886_));
            if (commandstorage$container1 != null) {
                this.namespaces.put(p_393886_, commandstorage$container1);
            }

            return commandstorage$container1;
        }
    }

    private CommandStorage.Container getOrCreateContainer(String p_393897_) {
        CommandStorage.Container commandstorage$container = this.namespaces.get(p_393897_);
        if (commandstorage$container != null) {
            return commandstorage$container;
        } else {
            CommandStorage.Container commandstorage$container1 = this.storage.computeIfAbsent(CommandStorage.Container.type(p_393897_));
            this.namespaces.put(p_393897_, commandstorage$container1);
            return commandstorage$container1;
        }
    }

    public void set(ResourceLocation p_78047_, CompoundTag p_78048_) {
        this.getOrCreateContainer(p_78047_.getNamespace()).put(p_78047_.getPath(), p_78048_);
    }

    public Stream<ResourceLocation> keys() {
        return this.namespaces.entrySet().stream().flatMap(p_164841_ -> p_164841_.getValue().getKeys(p_164841_.getKey()));
    }

    static String createId(String p_78038_) {
        return "command_storage_" + p_78038_;
    }

    static class Container extends SavedData {
        public static final Codec<CommandStorage.Container> CODEC = RecordCodecBuilder.create(
            p_391107_ -> p_391107_.group(
                    Codec.unboundedMap(ExtraCodecs.RESOURCE_PATH_CODEC, CompoundTag.CODEC).fieldOf("contents").forGetter(p_391108_ -> p_391108_.storage)
                )
                .apply(p_391107_, CommandStorage.Container::new)
        );
        private final Map<String, CompoundTag> storage;

        private Container(Map<String, CompoundTag> p_397341_) {
            this.storage = new HashMap<>(p_397341_);
        }

        private Container() {
            this(new HashMap<>());
        }

        public static SavedDataType<CommandStorage.Container> type(String p_393598_) {
            return new SavedDataType<>(CommandStorage.createId(p_393598_), CommandStorage.Container::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
        }

        public CompoundTag get(String p_78059_) {
            CompoundTag compoundtag = this.storage.get(p_78059_);
            return compoundtag != null ? compoundtag : new CompoundTag();
        }

        public void put(String p_78064_, CompoundTag p_78065_) {
            if (p_78065_.isEmpty()) {
                this.storage.remove(p_78064_);
            } else {
                this.storage.put(p_78064_, p_78065_);
            }

            this.setDirty();
        }

        public Stream<ResourceLocation> getKeys(String p_78073_) {
            return this.storage.keySet().stream().map(p_341970_ -> ResourceLocation.fromNamespaceAndPath(p_78073_, p_341970_));
        }
    }
}
