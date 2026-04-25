/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.data;

import net.minecraft.data.DataGenerator;
import net.minecraft.item.DyeColor;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ITag;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.data.BlockTagsProvider;

import static net.minecraftforge.common.Tags.Blocks.*;

import java.util.Locale;
import java.util.function.Consumer;

public class ForgeBlockTagsProvider extends BlockTagsProvider
{
    public ForgeBlockTagsProvider(DataGenerator gen, ExistingFileHelper existingFileHelper)
    {
        super(gen, "forge", existingFileHelper);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void func_200432_c()
    {
        func_240522_a_(BARRELS).func_240531_a_(BARRELS_WOODEN);
        func_240522_a_(BARRELS_WOODEN).func_240532_a_(Blocks.field_222422_lK);
        func_240522_a_(CHESTS).addTags(CHESTS_ENDER, CHESTS_TRAPPED, CHESTS_WOODEN);
        func_240522_a_(CHESTS_ENDER).func_240532_a_(Blocks.field_150477_bB);
        func_240522_a_(CHESTS_TRAPPED).func_240532_a_(Blocks.field_150447_bR);
        func_240522_a_(CHESTS_WOODEN).func_240534_a_(Blocks.field_150486_ae, Blocks.field_150447_bR);
        func_240522_a_(COBBLESTONE).func_240534_a_(Blocks.field_150347_e, Blocks.field_196687_dd, Blocks.field_150341_Y);
        func_240522_a_(DIRT).func_240534_a_(Blocks.field_150346_d, Blocks.field_196658_i, Blocks.field_196660_k, Blocks.field_196661_l, Blocks.field_150391_bh);
        func_240522_a_(END_STONES).func_240532_a_(Blocks.field_150377_bs);
        func_240522_a_(ENDERMAN_PLACE_ON_BLACKLIST);
        func_240522_a_(FENCE_GATES).addTags(FENCE_GATES_WOODEN);
        func_240522_a_(FENCE_GATES_WOODEN).func_240534_a_(Blocks.field_180390_bo, Blocks.field_180391_bp, Blocks.field_180392_bq, Blocks.field_180386_br, Blocks.field_180387_bt, Blocks.field_180385_bs, Blocks.field_235354_mM_, Blocks.field_235355_mN_);
        func_240522_a_(FENCES).addTags(FENCES_NETHER_BRICK, FENCES_WOODEN);
        func_240522_a_(FENCES_NETHER_BRICK).func_240532_a_(Blocks.field_150386_bk);
        func_240522_a_(FENCES_WOODEN).func_240534_a_(Blocks.field_180407_aO, Blocks.field_180408_aP, Blocks.field_180404_aQ, Blocks.field_180403_aR, Blocks.field_180405_aT, Blocks.field_180406_aS,  Blocks.field_235350_mI_, Blocks.field_235351_mJ_);
        func_240522_a_(GLASS).addTags(GLASS_COLORLESS, STAINED_GLASS);
        func_240522_a_(GLASS_COLORLESS).func_240532_a_(Blocks.field_150359_w);
        addColored(func_240522_a_(STAINED_GLASS)::func_240532_a_, GLASS, "{color}_stained_glass");
        func_240522_a_(GLASS_PANES).addTags(GLASS_PANES_COLORLESS, STAINED_GLASS_PANES);
        func_240522_a_(GLASS_PANES_COLORLESS).func_240532_a_(Blocks.field_150410_aZ);
        addColored(func_240522_a_(STAINED_GLASS_PANES)::func_240532_a_, GLASS_PANES, "{color}_stained_glass_pane");
        func_240522_a_(GRAVEL).func_240532_a_(Blocks.field_150351_n);
        func_240522_a_(NETHERRACK).func_240532_a_(Blocks.field_150424_aL);
        func_240522_a_(OBSIDIAN).func_240532_a_(Blocks.field_150343_Z);
        func_240522_a_(ORES).addTags(ORES_COAL, ORES_DIAMOND, ORES_EMERALD, ORES_GOLD, ORES_IRON, ORES_LAPIS, ORES_REDSTONE, ORES_QUARTZ, ORES_NETHERITE_SCRAP);
        func_240522_a_(ORES_COAL).func_240532_a_(Blocks.field_150365_q);
        func_240522_a_(ORES_DIAMOND).func_240532_a_(Blocks.field_150482_ag);
        func_240522_a_(ORES_EMERALD).func_240532_a_(Blocks.field_150412_bA);
        func_240522_a_(ORES_GOLD).func_240531_a_(BlockTags.field_232866_P_);
        func_240522_a_(ORES_IRON).func_240532_a_(Blocks.field_150366_p);
        func_240522_a_(ORES_LAPIS).func_240532_a_(Blocks.field_150369_x);
        func_240522_a_(ORES_QUARTZ).func_240532_a_(Blocks.field_196766_fg);
        func_240522_a_(ORES_REDSTONE).func_240532_a_(Blocks.field_150450_ax);
        func_240522_a_(ORES_NETHERITE_SCRAP).func_240532_a_(Blocks.field_235398_nh_);
        func_240522_a_(SAND).addTags(SAND_COLORLESS, SAND_RED);
        func_240522_a_(SAND_COLORLESS).func_240532_a_(Blocks.field_150354_m);
        func_240522_a_(SAND_RED).func_240532_a_(Blocks.field_196611_F);
        func_240522_a_(SANDSTONE).func_240534_a_(Blocks.field_150322_A, Blocks.field_196585_ak, Blocks.field_196583_aj, Blocks.field_196580_bH, Blocks.field_180395_cM, Blocks.field_196799_hB, Blocks.field_196798_hA, Blocks.field_196582_bJ);
        func_240522_a_(STONE).func_240534_a_(Blocks.field_196656_g, Blocks.field_196654_e, Blocks.field_196650_c, Blocks.field_196686_dc, Blocks.field_150348_b, Blocks.field_196657_h, Blocks.field_196655_f, Blocks.field_196652_d);
        func_240522_a_(STORAGE_BLOCKS).addTags(STORAGE_BLOCKS_COAL, STORAGE_BLOCKS_DIAMOND, STORAGE_BLOCKS_EMERALD, STORAGE_BLOCKS_GOLD, STORAGE_BLOCKS_IRON, STORAGE_BLOCKS_LAPIS, STORAGE_BLOCKS_QUARTZ, STORAGE_BLOCKS_REDSTONE, STORAGE_BLOCKS_NETHERITE);
        func_240522_a_(STORAGE_BLOCKS_COAL).func_240532_a_(Blocks.field_150402_ci);
        func_240522_a_(STORAGE_BLOCKS_DIAMOND).func_240532_a_(Blocks.field_150484_ah);
        func_240522_a_(STORAGE_BLOCKS_EMERALD).func_240532_a_(Blocks.field_150475_bE);
        func_240522_a_(STORAGE_BLOCKS_GOLD).func_240532_a_(Blocks.field_150340_R);
        func_240522_a_(STORAGE_BLOCKS_IRON).func_240532_a_(Blocks.field_150339_S);
        func_240522_a_(STORAGE_BLOCKS_LAPIS).func_240532_a_(Blocks.field_150368_y);
        func_240522_a_(STORAGE_BLOCKS_QUARTZ).func_240532_a_(Blocks.field_150371_ca);
        func_240522_a_(STORAGE_BLOCKS_REDSTONE).func_240532_a_(Blocks.field_150451_bX);
        func_240522_a_(STORAGE_BLOCKS_NETHERITE).func_240532_a_(Blocks.field_235397_ng_);
    }

    private void addColored(Consumer<Block> consumer, ITag.INamedTag<Block> group, String pattern)
    {
        String prefix = group.func_230234_a_().func_110623_a().toUpperCase(Locale.ENGLISH) + '_';
        for (DyeColor color  : DyeColor.values())
        {
            ResourceLocation key = new ResourceLocation("minecraft", pattern.replace("{color}",  color.func_176762_d()));
            ITag.INamedTag<Block> tag = getForgeTag(prefix + color.func_176762_d());
            Block block = ForgeRegistries.BLOCKS.getValue(key);
            if (block == null || block  == Blocks.field_150350_a)
                throw new IllegalStateException("Unknown vanilla block: " + key.toString());
            func_240522_a_(tag).func_240532_a_(block);
            consumer.accept(block);
        }
    }

    @SuppressWarnings("unchecked")
    private ITag.INamedTag<Block> getForgeTag(String name)
    {
        try
        {
            name = name.toUpperCase(Locale.ENGLISH);
            return (ITag.INamedTag<Block>)Tags.Blocks.class.getDeclaredField(name).get(null);
        }
        catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e)
        {
            throw new IllegalStateException(Tags.Blocks.class.getName() + " is missing tag name: " + name);
        }
    }

    @Override
    public String func_200397_b()
    {
        return "Forge Block Tags";
    }
}
