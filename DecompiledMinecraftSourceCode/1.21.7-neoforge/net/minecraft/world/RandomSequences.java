package net.minecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class RandomSequences extends SavedData {
    public static final SavedDataType<RandomSequences> TYPE = new SavedDataType<>(
        "random_sequences",
        p_390464_ -> new RandomSequences(p_390464_.worldSeed()),
        p_390466_ -> codec(p_390466_.worldSeed()),
        DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
    );
    private final long worldSeed;
    private int salt;
    private boolean includeWorldSeed = true;
    private boolean includeSequenceId = true;
    private final Map<ResourceLocation, RandomSequence> sequences = new Object2ObjectOpenHashMap<>();

    public RandomSequences(long p_287622_) {
        this.worldSeed = p_287622_;
    }

    private RandomSequences(long p_397697_, int p_397246_, boolean p_391262_, boolean p_391812_, Map<ResourceLocation, RandomSequence> p_393825_) {
        this.worldSeed = p_397697_;
        this.salt = p_397246_;
        this.includeWorldSeed = p_391262_;
        this.includeSequenceId = p_391812_;
        this.sequences.putAll(p_393825_);
    }

    public static Codec<RandomSequences> codec(long p_393995_) {
        return RecordCodecBuilder.create(
            p_390468_ -> p_390468_.group(
                    RecordCodecBuilder.point(p_393995_),
                    Codec.INT.fieldOf("salt").forGetter(p_390465_ -> p_390465_.salt),
                    Codec.BOOL.optionalFieldOf("include_world_seed", true).forGetter(p_390469_ -> p_390469_.includeWorldSeed),
                    Codec.BOOL.optionalFieldOf("include_sequence_id", true).forGetter(p_390470_ -> p_390470_.includeSequenceId),
                    Codec.unboundedMap(ResourceLocation.CODEC, RandomSequence.CODEC).fieldOf("sequences").forGetter(p_390463_ -> p_390463_.sequences)
                )
                .apply(p_390468_, RandomSequences::new)
        );
    }

    public RandomSource get(ResourceLocation p_287751_) {
        RandomSource randomsource = this.sequences.computeIfAbsent(p_287751_, this::createSequence).random();
        return new RandomSequences.DirtyMarkingRandomSource(randomsource);
    }

    private RandomSequence createSequence(ResourceLocation p_299723_) {
        return this.createSequence(p_299723_, this.salt, this.includeWorldSeed, this.includeSequenceId);
    }

    private RandomSequence createSequence(ResourceLocation p_299881_, int p_299267_, boolean p_300525_, boolean p_297272_) {
        long i = (p_300525_ ? this.worldSeed : 0L) ^ p_299267_;
        return new RandomSequence(i, p_297272_ ? Optional.of(p_299881_) : Optional.empty());
    }

    public void forAllSequences(BiConsumer<ResourceLocation, RandomSequence> p_299883_) {
        this.sequences.forEach(p_299883_);
    }

    public void setSeedDefaults(int p_299968_, boolean p_298395_, boolean p_298518_) {
        this.salt = p_299968_;
        this.includeWorldSeed = p_298395_;
        this.includeSequenceId = p_298518_;
    }

    public int clear() {
        int i = this.sequences.size();
        this.sequences.clear();
        return i;
    }

    public void reset(ResourceLocation p_298741_) {
        this.sequences.put(p_298741_, this.createSequence(p_298741_));
    }

    public void reset(ResourceLocation p_301350_, int p_298554_, boolean p_298049_, boolean p_301283_) {
        this.sequences.put(p_301350_, this.createSequence(p_301350_, p_298554_, p_298049_, p_301283_));
    }

    class DirtyMarkingRandomSource implements RandomSource {
        private final RandomSource random;

        DirtyMarkingRandomSource(final RandomSource p_299209_) {
            this.random = p_299209_;
        }

        @Override
        public RandomSource fork() {
            RandomSequences.this.setDirty();
            return this.random.fork();
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            RandomSequences.this.setDirty();
            return this.random.forkPositional();
        }

        @Override
        public void setSeed(long p_300098_) {
            RandomSequences.this.setDirty();
            this.random.setSeed(p_300098_);
        }

        @Override
        public int nextInt() {
            RandomSequences.this.setDirty();
            return this.random.nextInt();
        }

        @Override
        public int nextInt(int p_301106_) {
            RandomSequences.this.setDirty();
            return this.random.nextInt(p_301106_);
        }

        @Override
        public long nextLong() {
            RandomSequences.this.setDirty();
            return this.random.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            RandomSequences.this.setDirty();
            return this.random.nextBoolean();
        }

        @Override
        public float nextFloat() {
            RandomSequences.this.setDirty();
            return this.random.nextFloat();
        }

        @Override
        public double nextDouble() {
            RandomSequences.this.setDirty();
            return this.random.nextDouble();
        }

        @Override
        public double nextGaussian() {
            RandomSequences.this.setDirty();
            return this.random.nextGaussian();
        }

        @Override
        public boolean equals(Object p_299603_) {
            if (this == p_299603_) {
                return true;
            } else {
                return p_299603_ instanceof RandomSequences.DirtyMarkingRandomSource randomsequences$dirtymarkingrandomsource
                    ? this.random.equals(randomsequences$dirtymarkingrandomsource.random)
                    : false;
            }
        }
    }
}
