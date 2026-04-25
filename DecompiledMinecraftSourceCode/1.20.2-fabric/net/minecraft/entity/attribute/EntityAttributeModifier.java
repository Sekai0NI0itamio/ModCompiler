/*
 * Decompiled with CFR 0.2.2 (FabricMC 7c48b8c4).
 */
package net.minecraft.entity.attribute;

import com.mojang.datafixers.kinds.Applicative;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.IntFunction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.Uuids;
import net.minecraft.util.function.ValueLists;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public record EntityAttributeModifier(UUID uuid, String name, double value, Operation operation) {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<EntityAttributeModifier> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(((MapCodec)Uuids.INT_STREAM_CODEC.fieldOf("uuid")).forGetter(EntityAttributeModifier::uuid), ((MapCodec)Codec.STRING.fieldOf("name")).forGetter(modifier -> modifier.name), ((MapCodec)Codec.DOUBLE.fieldOf("amount")).forGetter(EntityAttributeModifier::value), ((MapCodec)Operation.CODEC.fieldOf("operation")).forGetter(EntityAttributeModifier::operation)).apply((Applicative<EntityAttributeModifier, ?>)instance, EntityAttributeModifier::new));
    public static final Codec<EntityAttributeModifier> CODEC = MAP_CODEC.codec();
    public static final PacketCodec<ByteBuf, EntityAttributeModifier> PACKET_CODEC = PacketCodec.tuple(Uuids.PACKET_CODEC, EntityAttributeModifier::uuid, PacketCodecs.STRING, modifier -> modifier.name, PacketCodecs.DOUBLE, EntityAttributeModifier::value, Operation.PACKET_CODEC, EntityAttributeModifier::operation, EntityAttributeModifier::new);

    public EntityAttributeModifier(String name, double value, Operation operation) {
        this(MathHelper.randomUuid(Random.createLocal()), name, value, operation);
    }

    public NbtCompound toNbt() {
        NbtCompound nbtCompound = new NbtCompound();
        nbtCompound.putString("Name", this.name);
        nbtCompound.putDouble("Amount", this.value);
        nbtCompound.putInt("Operation", this.operation.getId());
        nbtCompound.putUuid("UUID", this.uuid);
        return nbtCompound;
    }

    @Nullable
    public static EntityAttributeModifier fromNbt(NbtCompound nbt) {
        try {
            UUID uUID = nbt.getUuid("UUID");
            Operation operation = Operation.ID_TO_VALUE.apply(nbt.getInt("Operation"));
            return new EntityAttributeModifier(uUID, nbt.getString("Name"), nbt.getDouble("Amount"), operation);
        } catch (Exception exception) {
            LOGGER.warn("Unable to create attribute: {}", (Object)exception.getMessage());
            return null;
        }
    }

    public static enum Operation implements StringIdentifiable
    {
        ADD_VALUE("add_value", 0),
        ADD_MULTIPLIED_BASE("add_multiplied_base", 1),
        ADD_MULTIPLIED_TOTAL("add_multiplied_total", 2);

        public static final IntFunction<Operation> ID_TO_VALUE;
        public static final PacketCodec<ByteBuf, Operation> PACKET_CODEC;
        public static final Codec<Operation> CODEC;
        private final String name;
        private final int id;

        private Operation(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public int getId() {
            return this.id;
        }

        @Override
        public String asString() {
            return this.name;
        }

        static {
            ID_TO_VALUE = ValueLists.createIdToValueFunction(Operation::getId, Operation.values(), ValueLists.OutOfBoundsHandling.ZERO);
            PACKET_CODEC = PacketCodecs.indexed(ID_TO_VALUE, Operation::getId);
            CODEC = StringIdentifiable.createCodec(Operation::values);
        }
    }
}

