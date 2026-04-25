package net.minecraft.data.tags;

import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Util;

public abstract class TagsProvider<T> implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final CompletableFuture<HolderLookup.Provider> lookupProvider;
    private final CompletableFuture<Void> contentsDone = new CompletableFuture<>();
    private final CompletableFuture<TagsProvider.TagLookup<T>> parentProvider;
    protected final ResourceKey<? extends Registry<T>> registryKey;
    protected final Map<Identifier, TagBuilder> builders = Maps.newLinkedHashMap();
    protected final String modId;
    @org.jetbrains.annotations.Nullable
    protected final net.minecraftforge.common.data.ExistingFileHelper existingFileHelper;
    private final net.minecraftforge.common.data.ExistingFileHelper.IResourceType resourceType;
    private final net.minecraftforge.common.data.ExistingFileHelper.IResourceType elementResourceType; // FORGE: Resource type for validating required references to datapack registry elements.

    /**
     * @deprecated Forge: Use the {@linkplain #TagsProvider(PackOutput, ResourceKey, CompletableFuture, String, net.minecraftforge.common.data.ExistingFileHelper) mod id variant}
     */
    protected TagsProvider(PackOutput p_256596_, ResourceKey<? extends Registry<T>> p_255886_, CompletableFuture<HolderLookup.Provider> p_256513_) {
        this(p_256596_, p_255886_, p_256513_, "vanilla", null);
    }

    protected TagsProvider(PackOutput p_256596_, ResourceKey<? extends Registry<T>> p_255886_, CompletableFuture<HolderLookup.Provider> p_256513_, String modId, @org.jetbrains.annotations.Nullable net.minecraftforge.common.data.ExistingFileHelper existingFileHelper) {
       this(p_256596_, p_255886_, p_256513_, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()), modId, existingFileHelper);
    }

    /**
     * @deprecated Forge: Use the {@linkplain #TagsProvider(PackOutput, ResourceKey, CompletableFuture, CompletableFuture, String, net.minecraftforge.common.data.ExistingFileHelper) mod id variant}
     */
    protected TagsProvider(PackOutput p_275432_, ResourceKey<? extends Registry<T>> p_275476_, CompletableFuture<HolderLookup.Provider> p_275222_, CompletableFuture<TagsProvider.TagLookup<T>> p_275565_) {
        this(p_275432_, p_275476_, p_275222_, p_275565_, "vanilla", null);
    }

    protected TagsProvider(
        PackOutput p_275432_,
        ResourceKey<? extends Registry<T>> p_275476_,
        CompletableFuture<HolderLookup.Provider> p_275222_,
        CompletableFuture<TagsProvider.TagLookup<T>> p_275565_,
        String modId,
        @org.jetbrains.annotations.Nullable net.minecraftforge.common.data.ExistingFileHelper existingFileHelper
    ) {
        this.pathProvider = p_275432_.createRegistryTagsPathProvider(p_275476_);
        this.registryKey = p_275476_;
        this.parentProvider = p_275565_;
        this.lookupProvider = p_275222_;
        this.modId = modId;
        this.existingFileHelper = existingFileHelper;
        this.resourceType = new net.minecraftforge.common.data.ExistingFileHelper.ResourceType(net.minecraft.server.packs.PackType.SERVER_DATA, ".json", net.minecraft.core.registries.Registries.tagsDirPath(p_275476_));
        this.elementResourceType = new net.minecraftforge.common.data.ExistingFileHelper.ResourceType(net.minecraft.server.packs.PackType.SERVER_DATA, ".json", net.minecraft.core.registries.Registries.elementsDirPath(p_275476_));
    }

    // Forge: Allow customizing the path for a given tag or returning null
    @org.jetbrains.annotations.Nullable
    protected Path getPath(Identifier id) {
        return this.pathProvider.json(id);
    }

    @Override
    public String getName() {
        return "Tags for " + this.registryKey.identifier() + " mod id " + this.modId;
    }

    protected abstract void addTags(HolderLookup.Provider p_256380_);

    @Override
    public CompletableFuture<?> run(CachedOutput p_253684_) {
        record CombinedData<T>(HolderLookup.Provider contents, TagsProvider.TagLookup<T> parent) {
        }

        return this.createContentsProvider()
            .thenApply(p_275895_ -> {
                this.contentsDone.complete(null);
                return (HolderLookup.Provider)p_275895_;
            })
            .thenCombineAsync(this.parentProvider, (p_274778_, p_274779_) -> new CombinedData<>(p_274778_, (TagsProvider.TagLookup<T>)p_274779_), Util.backgroundExecutor())
            .thenCompose(
                p_325926_ -> {
                    HolderLookup.RegistryLookup<T> registrylookup = p_325926_.contents.lookup(this.registryKey).orElseThrow(() -> {
                       // FORGE: Throw a more descriptive error message if this is a Forge registry without tags enabled
                       if (net.minecraftforge.registries.RegistryManager.ACTIVE.getRegistry(this.registryKey) != null) {
                          return new IllegalStateException("Forge registry " + this.registryKey.identifier() + " does not have support for tags");
                       }
                       return new IllegalStateException("Registry " + this.registryKey.identifier() + " not found");
                    });
                    Predicate<Identifier> predicate = p_448741_ -> registrylookup.get(ResourceKey.create(this.registryKey, p_448741_)).isPresent();
                    Predicate<Identifier> predicate1 = p_448739_ -> this.builders.containsKey(p_448739_)
                        || p_325926_.parent.contains(TagKey.create(this.registryKey, p_448739_));
                    return CompletableFuture.allOf(
                        this.builders
                            .entrySet()
                            .stream()
                            .<CompletableFuture<?>>map(
                                p_325931_ -> {
                                    Identifier identifier = p_325931_.getKey();
                                    TagBuilder tagbuilder = p_325931_.getValue();
                                    List<TagEntry> list = tagbuilder.build();
                                    List<TagEntry> list1 = java.util.stream.Stream.concat(list.stream(), tagbuilder.getRemoveEntries()).filter(p_274771_ -> !p_274771_.verifyIfPresent(predicate, predicate1)).filter(this::missing).toList();
                                    if (!list1.isEmpty()) {
                                        throw new IllegalArgumentException(
                                            String.format(
                                                Locale.ROOT,
                                                "Couldn't define tag %s as it is missing following references: %s",
                                                identifier,
                                                list1.stream().map(Objects::toString).collect(Collectors.joining(","))
                                            )
                                        );
                                    } else {
                                        Path path = this.getPath(identifier);
                                        if (path == null) {
                                            return CompletableFuture.completedFuture(null); // Forge: Allow running this data provider without writing it. Recipe provider needs valid tags.
                                        }
                                        return DataProvider.saveStable(p_253684_, p_325926_.contents, TagFile.CODEC, new TagFile(list, tagbuilder.isReplace(), tagbuilder.getRemoveEntries().toList()), path);
                                    }
                                }
                            )
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    protected TagBuilder getOrCreateRawBuilder(TagKey<T> p_236452_) {
        return this.builders.computeIfAbsent(p_236452_.location(), p_460082_ -> {
            if (existingFileHelper != null) {
                existingFileHelper.trackGenerated(p_460082_, resourceType);
            }
            return TagBuilder.create();
        });
    }

    public CompletableFuture<TagsProvider.TagLookup<T>> contentsGetter() {
        return this.contentsDone.thenApply(p_276016_ -> p_448737_ -> Optional.ofNullable(this.builders.get(p_448737_.location())));
    }

    protected CompletableFuture<HolderLookup.Provider> createContentsProvider() {
        return this.lookupProvider.thenApply(p_274768_ -> {
            this.builders.clear();
            this.addTags(p_274768_);
            return (HolderLookup.Provider)p_274768_;
        });
    }


    private boolean missing(TagEntry reference) {
        // Optional tags should not be validated
        if (reference.isRequired()) {
           return existingFileHelper == null || !existingFileHelper.exists(reference.getId(), reference.isTag() ? resourceType : elementResourceType);
        }
        return false;
    }

    @FunctionalInterface
    public interface TagLookup<T> extends Function<TagKey<T>, Optional<TagBuilder>> {
        static <T> TagsProvider.TagLookup<T> empty() {
            return p_275247_ -> Optional.empty();
        }

        default boolean contains(TagKey<T> p_275413_) {
            return this.apply(p_275413_).isPresent();
        }
    }
}
