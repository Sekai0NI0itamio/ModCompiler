/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.common.data;

import net.minecraft.data.DataGenerator;
import net.minecraft.world.item.DyeColor;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.data.tags.BlockTagsProvider;

import static net.minecraftforge.common.Tags.Blocks.*;

import java.util.Locale;
import java.util.function.Consumer;

public final class ForgeBlockTagsProvider extends BlockTagsProvider
{
    public ForgeBlockTagsProvider(DataGenerator gen, ExistingFileHelper existingFileHelper)
    {
        super(gen, "forge", existingFileHelper);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void m_6577_()
    {
        m_126548_(BARRELS).m_126580_(BARRELS_WOODEN);
        m_126548_(BARRELS_WOODEN).m_126582_(Blocks.f_50618_);
        m_126548_(CHESTS).addTags(CHESTS_ENDER, CHESTS_TRAPPED, CHESTS_WOODEN);
        m_126548_(CHESTS_ENDER).m_126582_(Blocks.f_50265_);
        m_126548_(CHESTS_TRAPPED).m_126582_(Blocks.f_50325_);
        m_126548_(CHESTS_WOODEN).m_126584_(Blocks.f_50087_, Blocks.f_50325_);
        m_126548_(COBBLESTONE).m_126584_(Blocks.f_50652_, Blocks.f_50227_, Blocks.f_50079_);
        m_126548_(DIRT).m_126584_(Blocks.f_50493_, Blocks.f_50440_, Blocks.f_50546_, Blocks.f_50599_, Blocks.f_50195_);
        m_126548_(END_STONES).m_126582_(Blocks.f_50259_);
        m_126548_(ENDERMAN_PLACE_ON_BLACKLIST);
        m_126548_(FENCE_GATES).addTags(FENCE_GATES_WOODEN);
        m_126548_(FENCE_GATES_WOODEN).m_126584_(Blocks.f_50192_, Blocks.f_50474_, Blocks.f_50475_, Blocks.f_50476_, Blocks.f_50477_, Blocks.f_50478_, Blocks.f_50665_, Blocks.f_50666_);
        m_126548_(FENCES).addTags(FENCES_NETHER_BRICK, FENCES_WOODEN);
        m_126548_(FENCES_NETHER_BRICK).m_126582_(Blocks.f_50198_);
        m_126548_(FENCES_WOODEN).m_126584_(Blocks.f_50132_, Blocks.f_50479_, Blocks.f_50480_, Blocks.f_50481_, Blocks.f_50482_, Blocks.f_50483_,  Blocks.f_50661_, Blocks.f_50662_);
        m_126548_(GLASS).addTags(GLASS_COLORLESS, STAINED_GLASS);
        m_126548_(GLASS_COLORLESS).m_126582_(Blocks.f_50058_);
        addColored(m_126548_(STAINED_GLASS)::m_126582_, GLASS, "{color}_stained_glass");
        m_126548_(GLASS_PANES).addTags(GLASS_PANES_COLORLESS, STAINED_GLASS_PANES);
        m_126548_(GLASS_PANES_COLORLESS).m_126582_(Blocks.f_50185_);
        addColored(m_126548_(STAINED_GLASS_PANES)::m_126582_, GLASS_PANES, "{color}_stained_glass_pane");
        m_126548_(GRAVEL).m_126582_(Blocks.f_49994_);
        m_126548_(NETHERRACK).m_126582_(Blocks.f_50134_);
        m_126548_(OBSIDIAN).m_126582_(Blocks.f_50080_);
        m_126548_(ORES).addTags(ORES_COAL, ORES_DIAMOND, ORES_EMERALD, ORES_GOLD, ORES_IRON, ORES_LAPIS, ORES_REDSTONE, ORES_QUARTZ, ORES_NETHERITE_SCRAP);
        m_126548_(ORES_COAL).m_126582_(Blocks.f_49997_);
        m_126548_(ORES_DIAMOND).m_126582_(Blocks.f_50089_);
        m_126548_(ORES_EMERALD).m_126582_(Blocks.f_50264_);
        m_126548_(ORES_GOLD).m_126580_(BlockTags.f_13043_);
        m_126548_(ORES_IRON).m_126582_(Blocks.f_49996_);
        m_126548_(ORES_LAPIS).m_126582_(Blocks.f_50059_);
        m_126548_(ORES_QUARTZ).m_126582_(Blocks.f_50331_);
        m_126548_(ORES_REDSTONE).m_126582_(Blocks.f_50173_);
        m_126548_(ORES_NETHERITE_SCRAP).m_126582_(Blocks.f_50722_);
        m_126548_(SAND).addTags(SAND_COLORLESS, SAND_RED);
        m_126548_(SAND_COLORLESS).m_126582_(Blocks.f_49992_);
        m_126548_(SAND_RED).m_126582_(Blocks.f_49993_);
        m_126548_(SANDSTONE).m_126584_(Blocks.f_50062_, Blocks.f_50064_, Blocks.f_50063_, Blocks.f_50471_, Blocks.f_50394_, Blocks.f_50396_, Blocks.f_50395_, Blocks.f_50473_);
        m_126548_(STONE).m_126584_(Blocks.f_50334_, Blocks.f_50228_, Blocks.f_50122_, Blocks.f_50226_, Blocks.f_50069_, Blocks.f_50387_, Blocks.f_50281_, Blocks.f_50175_);
        m_126548_(STORAGE_BLOCKS).addTags(STORAGE_BLOCKS_COAL, STORAGE_BLOCKS_DIAMOND, STORAGE_BLOCKS_EMERALD, STORAGE_BLOCKS_GOLD, STORAGE_BLOCKS_IRON, STORAGE_BLOCKS_LAPIS, STORAGE_BLOCKS_QUARTZ, STORAGE_BLOCKS_REDSTONE, STORAGE_BLOCKS_NETHERITE);
        m_126548_(STORAGE_BLOCKS_COAL).m_126582_(Blocks.f_50353_);
        m_126548_(STORAGE_BLOCKS_DIAMOND).m_126582_(Blocks.f_50090_);
        m_126548_(STORAGE_BLOCKS_EMERALD).m_126582_(Blocks.f_50268_);
        m_126548_(STORAGE_BLOCKS_GOLD).m_126582_(Blocks.f_50074_);
        m_126548_(STORAGE_BLOCKS_IRON).m_126582_(Blocks.f_50075_);
        m_126548_(STORAGE_BLOCKS_LAPIS).m_126582_(Blocks.f_50060_);
        m_126548_(STORAGE_BLOCKS_QUARTZ).m_126582_(Blocks.f_50333_);
        m_126548_(STORAGE_BLOCKS_REDSTONE).m_126582_(Blocks.f_50330_);
        m_126548_(STORAGE_BLOCKS_NETHERITE).m_126582_(Blocks.f_50721_);
    }

    private void addColored(Consumer<Block> consumer, Tag.Named<Block> group, String pattern)
    {
        String prefix = group.m_6979_().m_135815_().toUpperCase(Locale.ENGLISH) + '_';
        for (DyeColor color  : DyeColor.values())
        {
            ResourceLocation key = new ResourceLocation("minecraft", pattern.replace("{color}",  color.m_41065_()));
            Tag.Named<Block> tag = getForgeTag(prefix + color.m_41065_());
            Block block = ForgeRegistries.BLOCKS.getValue(key);
            if (block == null || block  == Blocks.f_50016_)
                throw new IllegalStateException("Unknown vanilla block: " + key.toString());
            m_126548_(tag).m_126582_(block);
            consumer.accept(block);
        }
    }

    @SuppressWarnings("unchecked")
    private Tag.Named<Block> getForgeTag(String name)
    {
        try
        {
            name = name.toUpperCase(Locale.ENGLISH);
            return (Tag.Named<Block>)Tags.Blocks.class.getDeclaredField(name).get(null);
        }
        catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e)
        {
            throw new IllegalStateException(Tags.Blocks.class.getName() + " is missing tag name: " + name);
        }
    }

    @Override
    public String m_6055_()
    {
        return "Forge Block Tags";
    }
}
