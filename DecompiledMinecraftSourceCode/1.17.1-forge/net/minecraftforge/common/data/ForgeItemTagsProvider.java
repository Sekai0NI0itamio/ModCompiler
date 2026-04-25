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

import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.world.level.block.Block;
import net.minecraft.data.tags.BlockTagsProvider;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.tags.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

public final class ForgeItemTagsProvider extends ItemTagsProvider
{
    public ForgeItemTagsProvider(DataGenerator gen, BlockTagsProvider blockTagProvider, ExistingFileHelper existingFileHelper)
    {
        super(gen, blockTagProvider, "forge", existingFileHelper);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void m_6577_()
    {
        m_126533_(Tags.Blocks.BARRELS, Tags.Items.BARRELS);
        m_126533_(Tags.Blocks.BARRELS_WOODEN, Tags.Items.BARRELS_WOODEN);
        m_126548_(Tags.Items.BONES).m_126582_(Items.f_42500_);
        m_126548_(Tags.Items.BOOKSHELVES).m_126582_(Items.f_41997_);
        m_126533_(Tags.Blocks.CHESTS, Tags.Items.CHESTS);
        m_126533_(Tags.Blocks.CHESTS_ENDER, Tags.Items.CHESTS_ENDER);
        m_126533_(Tags.Blocks.CHESTS_TRAPPED, Tags.Items.CHESTS_TRAPPED);
        m_126533_(Tags.Blocks.CHESTS_WOODEN, Tags.Items.CHESTS_WOODEN);
        m_126533_(Tags.Blocks.COBBLESTONE, Tags.Items.COBBLESTONE);
        m_126548_(Tags.Items.CROPS).addTags(Tags.Items.CROPS_BEETROOT, Tags.Items.CROPS_CARROT, Tags.Items.CROPS_NETHER_WART, Tags.Items.CROPS_POTATO, Tags.Items.CROPS_WHEAT);
        m_126548_(Tags.Items.CROPS_BEETROOT).m_126582_(Items.f_42732_);
        m_126548_(Tags.Items.CROPS_CARROT).m_126582_(Items.f_42619_);
        m_126548_(Tags.Items.CROPS_NETHER_WART).m_126582_(Items.f_42588_);
        m_126548_(Tags.Items.CROPS_POTATO).m_126582_(Items.f_42620_);
        m_126548_(Tags.Items.CROPS_WHEAT).m_126582_(Items.f_42405_);
        m_126548_(Tags.Items.DUSTS).addTags(Tags.Items.DUSTS_GLOWSTONE, Tags.Items.DUSTS_PRISMARINE, Tags.Items.DUSTS_REDSTONE);
        m_126548_(Tags.Items.DUSTS_GLOWSTONE).m_126582_(Items.f_42525_);
        m_126548_(Tags.Items.DUSTS_PRISMARINE).m_126582_(Items.f_42695_);
        m_126548_(Tags.Items.DUSTS_REDSTONE).m_126582_(Items.f_42451_);
        addColored(m_126548_(Tags.Items.DYES)::addTags, Tags.Items.DYES, "{color}_dye");
        m_126548_(Tags.Items.EGGS).m_126582_(Items.f_42521_);
        m_126548_(Tags.Items.ENCHANTING_FUELS).m_126580_(Tags.Items.GEMS_LAPIS);
        m_126533_(Tags.Blocks.END_STONES, Tags.Items.END_STONES);
        m_126548_(Tags.Items.ENDER_PEARLS).m_126582_(Items.f_42584_);
        m_126548_(Tags.Items.FEATHERS).m_126582_(Items.f_42402_);
        m_126533_(Tags.Blocks.FENCE_GATES, Tags.Items.FENCE_GATES);
        m_126533_(Tags.Blocks.FENCE_GATES_WOODEN, Tags.Items.FENCE_GATES_WOODEN);
        m_126533_(Tags.Blocks.FENCES, Tags.Items.FENCES);
        m_126533_(Tags.Blocks.FENCES_NETHER_BRICK, Tags.Items.FENCES_NETHER_BRICK);
        m_126533_(Tags.Blocks.FENCES_WOODEN, Tags.Items.FENCES_WOODEN);
        m_126548_(Tags.Items.GEMS).addTags(Tags.Items.GEMS_DIAMOND, Tags.Items.GEMS_EMERALD, Tags.Items.GEMS_LAPIS, Tags.Items.GEMS_PRISMARINE, Tags.Items.GEMS_QUARTZ);
        m_126548_(Tags.Items.GEMS_DIAMOND).m_126582_(Items.f_42415_);
        m_126548_(Tags.Items.GEMS_EMERALD).m_126582_(Items.f_42616_);
        m_126548_(Tags.Items.GEMS_LAPIS).m_126582_(Items.f_42534_);
        m_126548_(Tags.Items.GEMS_PRISMARINE).m_126582_(Items.f_42696_);
        m_126548_(Tags.Items.GEMS_QUARTZ).m_126582_(Items.f_42692_);
        m_126533_(Tags.Blocks.GLASS, Tags.Items.GLASS);
        func_240521_a_Colored(Tags.Blocks.GLASS, Tags.Items.GLASS);
        m_126533_(Tags.Blocks.GLASS_PANES, Tags.Items.GLASS_PANES);
        func_240521_a_Colored(Tags.Blocks.GLASS_PANES, Tags.Items.GLASS_PANES);
        m_126533_(Tags.Blocks.GRAVEL, Tags.Items.GRAVEL);
        m_126548_(Tags.Items.GUNPOWDER).m_126582_(Items.f_42403_);
        m_126548_(Tags.Items.HEADS).m_126584_(Items.f_42682_, Items.f_42683_, Items.f_42680_, Items.f_42678_, Items.f_42679_, Items.f_42681_);
        m_126548_(Tags.Items.INGOTS).addTags(Tags.Items.INGOTS_IRON, Tags.Items.INGOTS_GOLD, Tags.Items.INGOTS_BRICK, Tags.Items.INGOTS_NETHER_BRICK, Tags.Items.INGOTS_NETHERITE);
        m_126548_(Tags.Items.INGOTS_BRICK).m_126582_(Items.f_42460_);
        m_126548_(Tags.Items.INGOTS_GOLD).m_126582_(Items.f_42417_);
        m_126548_(Tags.Items.INGOTS_IRON).m_126582_(Items.f_42416_);
        m_126548_(Tags.Items.INGOTS_NETHERITE).m_126582_(Items.f_42418_);
        m_126548_(Tags.Items.INGOTS_NETHER_BRICK).m_126582_(Items.f_42691_);
        m_126548_(Tags.Items.LEATHER).m_126582_(Items.f_42454_);
        m_126548_(Tags.Items.MUSHROOMS).m_126584_(Items.f_41952_, Items.f_41953_);
        m_126548_(Tags.Items.NETHER_STARS).m_126582_(Items.f_42686_);
        m_126533_(Tags.Blocks.NETHERRACK, Tags.Items.NETHERRACK);
        m_126548_(Tags.Items.NUGGETS).addTags(Tags.Items.NUGGETS_IRON, Tags.Items.NUGGETS_GOLD);
        m_126548_(Tags.Items.NUGGETS_IRON).m_126582_(Items.f_42749_);
        m_126548_(Tags.Items.NUGGETS_GOLD).m_126582_(Items.f_42587_);
        m_126533_(Tags.Blocks.OBSIDIAN, Tags.Items.OBSIDIAN);
        m_126533_(Tags.Blocks.ORES, Tags.Items.ORES);
        m_126533_(Tags.Blocks.ORES_COAL, Tags.Items.ORES_COAL);
        m_126533_(Tags.Blocks.ORES_DIAMOND, Tags.Items.ORES_DIAMOND);
        m_126533_(Tags.Blocks.ORES_EMERALD, Tags.Items.ORES_EMERALD);
        m_126533_(Tags.Blocks.ORES_GOLD, Tags.Items.ORES_GOLD);
        m_126533_(Tags.Blocks.ORES_IRON, Tags.Items.ORES_IRON);
        m_126533_(Tags.Blocks.ORES_LAPIS, Tags.Items.ORES_LAPIS);
        m_126533_(Tags.Blocks.ORES_QUARTZ, Tags.Items.ORES_QUARTZ);
        m_126533_(Tags.Blocks.ORES_REDSTONE, Tags.Items.ORES_REDSTONE);
        m_126533_(Tags.Blocks.ORES_NETHERITE_SCRAP, Tags.Items.ORES_NETHERITE_SCRAP);
        m_126548_(Tags.Items.RODS).addTags(Tags.Items.RODS_BLAZE, Tags.Items.RODS_WOODEN);
        m_126548_(Tags.Items.RODS_BLAZE).m_126582_(Items.f_42585_);
        m_126548_(Tags.Items.RODS_WOODEN).m_126582_(Items.f_42398_);
        m_126533_(Tags.Blocks.SAND, Tags.Items.SAND);
        m_126533_(Tags.Blocks.SAND_COLORLESS, Tags.Items.SAND_COLORLESS);
        m_126533_(Tags.Blocks.SAND_RED, Tags.Items.SAND_RED);
        m_126533_(Tags.Blocks.SANDSTONE, Tags.Items.SANDSTONE);
        m_126548_(Tags.Items.SEEDS).addTags(Tags.Items.SEEDS_BEETROOT, Tags.Items.SEEDS_MELON, Tags.Items.SEEDS_PUMPKIN, Tags.Items.SEEDS_WHEAT);
        m_126548_(Tags.Items.SEEDS_BEETROOT).m_126582_(Items.f_42733_);
        m_126548_(Tags.Items.SEEDS_MELON).m_126582_(Items.f_42578_);
        m_126548_(Tags.Items.SEEDS_PUMPKIN).m_126582_(Items.f_42577_);
        m_126548_(Tags.Items.SEEDS_WHEAT).m_126582_(Items.f_42404_);
        m_126548_(Tags.Items.SHEARS).m_126582_(Items.f_42574_);
        m_126548_(Tags.Items.SLIMEBALLS).m_126582_(Items.f_42518_);
        m_126533_(Tags.Blocks.STAINED_GLASS, Tags.Items.STAINED_GLASS);
        m_126533_(Tags.Blocks.STAINED_GLASS_PANES, Tags.Items.STAINED_GLASS_PANES);
        m_126533_(Tags.Blocks.STONE, Tags.Items.STONE);
        m_126533_(Tags.Blocks.STORAGE_BLOCKS, Tags.Items.STORAGE_BLOCKS);
        m_126533_(Tags.Blocks.STORAGE_BLOCKS_COAL, Tags.Items.STORAGE_BLOCKS_COAL);
        m_126533_(Tags.Blocks.STORAGE_BLOCKS_DIAMOND, Tags.Items.STORAGE_BLOCKS_DIAMOND);
        m_126533_(Tags.Blocks.STORAGE_BLOCKS_EMERALD, Tags.Items.STORAGE_BLOCKS_EMERALD);
        m_126533_(Tags.Blocks.STORAGE_BLOCKS_GOLD, Tags.Items.STORAGE_BLOCKS_GOLD);
        m_126533_(Tags.Blocks.STORAGE_BLOCKS_IRON, Tags.Items.STORAGE_BLOCKS_IRON);
        m_126533_(Tags.Blocks.STORAGE_BLOCKS_LAPIS, Tags.Items.STORAGE_BLOCKS_LAPIS);
        m_126533_(Tags.Blocks.STORAGE_BLOCKS_QUARTZ, Tags.Items.STORAGE_BLOCKS_QUARTZ);
        m_126533_(Tags.Blocks.STORAGE_BLOCKS_REDSTONE, Tags.Items.STORAGE_BLOCKS_REDSTONE);
        m_126533_(Tags.Blocks.STORAGE_BLOCKS_NETHERITE, Tags.Items.STORAGE_BLOCKS_NETHERITE);
        m_126548_(Tags.Items.STRING).m_126582_(Items.f_42401_);
    }

    private void addColored(Consumer<Tag.Named<Item>> consumer, Tag.Named<Item> group, String pattern)
    {
        String prefix = group.m_6979_().m_135815_().toUpperCase(Locale.ENGLISH) + '_';
        for (DyeColor color  : DyeColor.values())
        {
            ResourceLocation key = new ResourceLocation("minecraft", pattern.replace("{color}",  color.m_41065_()));
            Tag.Named<Item> tag = getForgeItemTag(prefix + color.m_41065_());
            Item item = ForgeRegistries.ITEMS.getValue(key);
            if (item == null || item  == Items.f_41852_)
                throw new IllegalStateException("Unknown vanilla item: " + key.toString());
            m_126548_(tag).m_126582_(item);
            consumer.accept(tag);
        }
    }

    private void func_240521_a_Colored(Tag.Named<Block> blockGroup, Tag.Named<Item> itemGroup)
    {
        String blockPre = blockGroup.m_6979_().m_135815_().toUpperCase(Locale.ENGLISH) + '_';
        String itemPre = itemGroup.m_6979_().m_135815_().toUpperCase(Locale.ENGLISH) + '_';
        for (DyeColor color  : DyeColor.values())
        {
            Tag.Named<Block> from = getForgeBlockTag(blockPre + color.m_41065_());
            Tag.Named<Item> to = getForgeItemTag(itemPre + color.m_41065_());
            m_126533_(from, to);
        }
        m_126533_(getForgeBlockTag(blockPre + "colorless"), getForgeItemTag(itemPre + "colorless"));
    }

    @SuppressWarnings("unchecked")
    private Tag.Named<Block> getForgeBlockTag(String name)
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

    @SuppressWarnings("unchecked")
    private Tag.Named<Item> getForgeItemTag(String name)
    {
        try
        {
            name = name.toUpperCase(Locale.ENGLISH);
            return (Tag.Named<Item>)Tags.Items.class.getDeclaredField(name).get(null);
        }
        catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e)
        {
            throw new IllegalStateException(Tags.Items.class.getName() + " is missing tag name: " + name);
        }
    }

    @Override
    public String m_6055_()
    {
        return "Forge Item Tags";
    }
}
