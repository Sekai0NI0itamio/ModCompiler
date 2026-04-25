package net.minecraft.world.entity.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

public record VillagerData(Holder<VillagerType> type, Holder<VillagerProfession> profession, int level) {
    public static final int MIN_VILLAGER_LEVEL = 1;
    public static final int MAX_VILLAGER_LEVEL = 5;
    private static final int[] NEXT_LEVEL_XP_THRESHOLDS = new int[]{0, 10, 70, 150, 250};
    public static final Codec<VillagerData> CODEC = RecordCodecBuilder.create(
        p_390725_ -> p_390725_.group(
                BuiltInRegistries.VILLAGER_TYPE
                    .holderByNameCodec()
                    .fieldOf("type")
                    .orElseGet(() -> BuiltInRegistries.VILLAGER_TYPE.getOrThrow(VillagerType.PLAINS))
                    .forGetter(p_390724_ -> p_390724_.type),
                BuiltInRegistries.VILLAGER_PROFESSION
                    .holderByNameCodec()
                    .fieldOf("profession")
                    .orElseGet(() -> BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE))
                    .forGetter(p_390726_ -> p_390726_.profession),
                Codec.INT.fieldOf("level").orElse(1).forGetter(p_150020_ -> p_150020_.level)
            )
            .apply(p_390725_, VillagerData::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.holderRegistry(Registries.VILLAGER_TYPE),
        VillagerData::type,
        ByteBufCodecs.holderRegistry(Registries.VILLAGER_PROFESSION),
        VillagerData::profession,
        ByteBufCodecs.VAR_INT,
        VillagerData::level,
        VillagerData::new
    );

    public VillagerData(Holder<VillagerType> type, Holder<VillagerProfession> profession, int level) {
        level = Math.max(1, level);
        this.type = type;
        this.profession = profession;
        this.level = level;
    }

    public VillagerData withType(Holder<VillagerType> p_392073_) {
        return new VillagerData(p_392073_, this.profession, this.level);
    }

    public VillagerData withType(HolderGetter.Provider p_392550_, ResourceKey<VillagerType> p_393502_) {
        return this.withType(p_392550_.getOrThrow(p_393502_));
    }

    public VillagerData withProfession(Holder<VillagerProfession> p_396042_) {
        return new VillagerData(this.type, p_396042_, this.level);
    }

    public VillagerData withProfession(HolderGetter.Provider p_393790_, ResourceKey<VillagerProfession> p_391167_) {
        return this.withProfession(p_393790_.getOrThrow(p_391167_));
    }

    public VillagerData withLevel(int p_397668_) {
        return new VillagerData(this.type, this.profession, p_397668_);
    }

    public static int getMinXpPerLevel(int p_35573_) {
        return canLevelUp(p_35573_) ? NEXT_LEVEL_XP_THRESHOLDS[p_35573_ - 1] : 0;
    }

    public static int getMaxXpPerLevel(int p_35578_) {
        return canLevelUp(p_35578_) ? NEXT_LEVEL_XP_THRESHOLDS[p_35578_] : 0;
    }

    public static boolean canLevelUp(int p_35583_) {
        return p_35583_ >= 1 && p_35583_ < 5;
    }
}
