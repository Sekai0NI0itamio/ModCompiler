package net.minecraft.advancements.critereon;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueOutput;
import org.slf4j.Logger;

public record NbtPredicate(CompoundTag tag) {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<NbtPredicate> CODEC = TagParser.LENIENT_CODEC.xmap(NbtPredicate::new, NbtPredicate::tag);
    public static final StreamCodec<ByteBuf, NbtPredicate> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(NbtPredicate::new, NbtPredicate::tag);
    public static final String SELECTED_ITEM_TAG = "SelectedItem";

    public boolean matches(DataComponentGetter p_395988_) {
        CustomData customdata = p_395988_.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customdata.matchedBy(this.tag);
    }

    public boolean matches(Entity p_57478_) {
        return this.matches(getEntityTagToCompare(p_57478_));
    }

    public boolean matches(@Nullable Tag p_57484_) {
        return p_57484_ != null && NbtUtils.compareNbt(this.tag, p_57484_, true);
    }

    public static CompoundTag getEntityTagToCompare(Entity p_57486_) {
        CompoundTag compoundtag;
        try (ProblemReporter.ScopedCollector problemreporter$scopedcollector = new ProblemReporter.ScopedCollector(p_57486_.problemPath(), LOGGER)) {
            TagValueOutput tagvalueoutput = TagValueOutput.createWithContext(problemreporter$scopedcollector, p_57486_.registryAccess());
            p_57486_.saveWithoutId(tagvalueoutput);
            if (p_57486_ instanceof Player player) {
                ItemStack itemstack = player.getInventory().getSelectedItem();
                if (!itemstack.isEmpty()) {
                    tagvalueoutput.store("SelectedItem", ItemStack.CODEC, itemstack);
                }
            }

            compoundtag = tagvalueoutput.buildResult();
        }

        return compoundtag;
    }
}
