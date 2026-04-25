/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderState.TextureState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderType.State;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.NonNullLazy;
import net.minecraftforge.common.util.NonNullSupplier;
import org.lwjgl.opengl.GL11;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
public enum ForgeRenderTypes
{
    ITEM_LAYERED_SOLID(()-> getItemLayeredSolid(AtlasTexture.field_110575_b)),
    ITEM_LAYERED_CUTOUT(()-> getItemLayeredCutout(AtlasTexture.field_110575_b)),
    ITEM_LAYERED_CUTOUT_MIPPED(()-> getItemLayeredCutoutMipped(AtlasTexture.field_110575_b)),
    ITEM_LAYERED_TRANSLUCENT(()-> getItemLayeredTranslucent(AtlasTexture.field_110575_b)),
    ITEM_UNSORTED_TRANSLUCENT(()-> getUnsortedTranslucent(AtlasTexture.field_110575_b)),
    ITEM_UNLIT_TRANSLUCENT(()-> getUnlitTranslucent(AtlasTexture.field_110575_b)),
    ITEM_UNSORTED_UNLIT_TRANSLUCENT(()-> getUnlitTranslucent(AtlasTexture.field_110575_b, false));

    public static boolean enableTextTextureLinearFiltering = false;

    /**
     * @return A RenderType fit for multi-layer solid item rendering.
     */
    public static RenderType getItemLayeredSolid(ResourceLocation textureLocation)
    {
        return Internal.layeredItemSolid(textureLocation);
    }

    /**
     * @return A RenderType fit for multi-layer cutout item item rendering.
     */
    public static RenderType getItemLayeredCutout(ResourceLocation textureLocation)
    {
        return Internal.layeredItemCutout(textureLocation);
    }

    /**
     * @return A RenderType fit for multi-layer cutout-mipped item rendering.
     */
    public static RenderType getItemLayeredCutoutMipped(ResourceLocation textureLocation)
    {
        return Internal.layeredItemCutoutMipped(textureLocation);
    }

    /**
     * @return A RenderType fit for multi-layer translucent item rendering.
     */
    public static RenderType getItemLayeredTranslucent(ResourceLocation textureLocation)
    {
        return Internal.layeredItemTranslucent(textureLocation);
    }

    /**
     * @return A RenderType fit for translucent item/entity rendering, but with depth sorting disabled.
     */
    public static RenderType getUnsortedTranslucent(ResourceLocation textureLocation)
    {
        return Internal.unsortedTranslucent(textureLocation);
    }

    /**
     * @return A RenderType fit for translucent item/entity rendering, but with diffuse lighting disabled
     * so that fullbright quads look correct.
     */
    public static RenderType getUnlitTranslucent(ResourceLocation textureLocation)
    {
        return getUnlitTranslucent(textureLocation, true);
    }

    /**
     * @return A RenderType fit for translucent item/entity rendering, but with diffuse lighting disabled
     * so that fullbright quads look correct.
     * @param sortingEnabled If false, depth sorting will not be performed.
     */
    public static RenderType getUnlitTranslucent(ResourceLocation textureLocation, boolean sortingEnabled)
    {
        return Internal.unlitTranslucent(textureLocation, sortingEnabled);
    }

    /**
     * @return Same as {@link RenderType#getEntityCutout(ResourceLocation)}, but with mipmapping enabled.
     */
    public static RenderType getEntityCutoutMipped(ResourceLocation textureLocation)
    {
        return Internal.layeredItemCutoutMipped(textureLocation);
    }

    /**
     * @return Replacement of {@link RenderType#getText(ResourceLocation)}, but with optional linear texture filtering.
     */
    public static RenderType getText(ResourceLocation locationIn)
    {
        return Internal.getText(locationIn);
    }

    /**
     * @return Replacement of {@link RenderType#getTextSeeThrough(ResourceLocation)}, but with optional linear texture filtering.
     */
    public static RenderType getTextSeeThrough(ResourceLocation locationIn)
    {
        return Internal.getTextSeeThrough(locationIn);
    }

    // ----------------------------------------
    //  Implementation details below this line
    // ----------------------------------------

    private final NonNullSupplier<RenderType> renderTypeSupplier;

    ForgeRenderTypes(NonNullSupplier<RenderType> renderTypeSupplier)
    {
        // Wrap in a Lazy<> to avoid running the supplier more than once.
        this.renderTypeSupplier = NonNullLazy.of(renderTypeSupplier);
    }

    public RenderType get()
    {
        return renderTypeSupplier.get();
    }

    private static class Internal extends RenderType
    {
        private Internal(String name, VertexFormat fmt, int glMode, int size, boolean doCrumbling, boolean depthSorting, Runnable onEnable, Runnable onDisable)
        {
            super(name, fmt, glMode, size, doCrumbling, depthSorting, onEnable, onDisable);
            throw new IllegalStateException("This class must not be instantiated");
        }

        public static RenderType unsortedTranslucent(ResourceLocation textureLocation)
        {
            final boolean sortingEnabled = false;
            State renderState = State.func_228694_a_()
                    .func_228724_a_(new TextureState(textureLocation, false, false))
                    .func_228726_a_(field_228515_g_)
                    .func_228716_a_(field_228532_x_)
                    .func_228713_a_(field_228517_i_)
                    .func_228714_a_(field_228491_A_)
                    .func_228719_a_(field_228528_t_)
                    .func_228722_a_(field_228530_v_)
                    .func_228728_a_(true);
            return func_228633_a_("forge_entity_unsorted_translucent", DefaultVertexFormats.field_227849_i_, GL11.GL_QUADS, 256, true, sortingEnabled, renderState);
        }

        public static RenderType unlitTranslucent(ResourceLocation textureLocation, boolean sortingEnabled)
        {
            State renderState = State.func_228694_a_()
                    .func_228724_a_(new TextureState(textureLocation, false, false))
                    .func_228726_a_(field_228515_g_)
                    .func_228713_a_(field_228517_i_)
                    .func_228714_a_(field_228491_A_)
                    .func_228719_a_(field_228528_t_)
                    .func_228722_a_(field_228530_v_)
                    .func_228728_a_(true);
            return func_228633_a_("forge_entity_unlit_translucent", DefaultVertexFormats.field_227849_i_, GL11.GL_QUADS, 256, true, sortingEnabled, renderState);
        }

        public static RenderType layeredItemSolid(ResourceLocation locationIn) {
            RenderType.State rendertype$state = RenderType.State.func_228694_a_()
                    .func_228724_a_(new RenderState.TextureState(locationIn, false, false))
                    .func_228726_a_(field_228510_b_)
                    .func_228716_a_(field_228532_x_)
                    .func_228719_a_(field_228528_t_)
                    .func_228722_a_(field_228530_v_)
                    .func_228728_a_(true);
            return func_228633_a_("forge_item_entity_solid", DefaultVertexFormats.field_227849_i_, 7, 256, true, false, rendertype$state);
        }

        public static RenderType layeredItemCutout(ResourceLocation locationIn) {
            RenderType.State rendertype$state = RenderType.State.func_228694_a_()
                    .func_228724_a_(new RenderState.TextureState(locationIn, false, false))
                    .func_228726_a_(field_228510_b_)
                    .func_228716_a_(field_228532_x_)
                    .func_228713_a_(field_228517_i_)
                    .func_228719_a_(field_228528_t_)
                    .func_228722_a_(field_228530_v_)
                    .func_228728_a_(true);
            return func_228633_a_("forge_item_entity_cutout", DefaultVertexFormats.field_227849_i_, 7, 256, true, false, rendertype$state);
        }

        public static RenderType layeredItemCutoutMipped(ResourceLocation locationIn) {
            RenderType.State rendertype$state = RenderType.State.func_228694_a_()
                    .func_228724_a_(new RenderState.TextureState(locationIn, false, true))
                    .func_228726_a_(field_228510_b_)
                    .func_228716_a_(field_228532_x_)
                    .func_228713_a_(field_228517_i_)
                    .func_228719_a_(field_228528_t_)
                    .func_228722_a_(field_228530_v_)
                    .func_228728_a_(true);
            return func_228633_a_("forge_item_entity_cutout_mipped", DefaultVertexFormats.field_227849_i_, 7, 256, true, false, rendertype$state);
        }

        public static RenderType layeredItemTranslucent(ResourceLocation locationIn) {
            RenderType.State rendertype$state = RenderType.State.func_228694_a_()
                    .func_228724_a_(new RenderState.TextureState(locationIn, false, false))
                    .func_228726_a_(field_228515_g_)
                    .func_228716_a_(field_228532_x_)
                    .func_228713_a_(field_228517_i_)
                    .func_228719_a_(field_228528_t_)
                    .func_228722_a_(field_228530_v_)
                    .func_228728_a_(true);
            return func_228633_a_("forge_item_entity_translucent_cull", DefaultVertexFormats.field_227849_i_, 7, 256, true, true, rendertype$state);
        }

        public static RenderType getText(ResourceLocation locationIn) {
            RenderType.State rendertype$state = RenderType.State.func_228694_a_()
                    .func_228724_a_(new CustomizableTextureState(locationIn, () -> ForgeRenderTypes.enableTextTextureLinearFiltering, () -> false))
                    .func_228713_a_(field_228517_i_)
                    .func_228726_a_(field_228515_g_)
                    .func_228719_a_(field_228528_t_)
                    .func_228728_a_(false);
            return func_228633_a_("forge_text", DefaultVertexFormats.field_227852_q_, 7, 256, false, true, rendertype$state);
        }

        public static RenderType getTextSeeThrough(ResourceLocation locationIn) {
            RenderType.State rendertype$state = RenderType.State.func_228694_a_()
                    .func_228724_a_(new CustomizableTextureState(locationIn, () -> ForgeRenderTypes.enableTextTextureLinearFiltering, () -> false))
                    .func_228713_a_(field_228517_i_)
                    .func_228726_a_(field_228515_g_)
                    .func_228719_a_(field_228528_t_)
                    .func_228715_a_(field_228492_B_)
                    .func_228727_a_(field_228496_F_)
                    .func_228728_a_(false);
            return func_228633_a_("forge_text_see_through", DefaultVertexFormats.field_227852_q_, 7, 256, false, true, rendertype$state);
        }
    }

    private static class CustomizableTextureState extends TextureState
    {
        private CustomizableTextureState(ResourceLocation resLoc, Supplier<Boolean> blur, Supplier<Boolean> mipmap)
        {
            super(resLoc, blur.get(), mipmap.get());
            this.field_228507_Q_ = () -> {
                this.field_228603_R_ = blur.get();
                this.field_228604_S_ = mipmap.get();
                RenderSystem.enableTexture();
                TextureManager texturemanager = Minecraft.func_71410_x().func_110434_K();
                texturemanager.func_110577_a(resLoc);
                texturemanager.func_229267_b_(resLoc).func_174937_a(this.field_228603_R_, this.field_228604_S_);
            };
        }
    }
}
