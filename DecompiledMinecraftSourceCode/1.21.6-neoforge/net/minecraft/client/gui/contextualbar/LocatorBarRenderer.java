package net.minecraft.client.gui.contextualbar;

import java.util.UUID;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.WaypointStyle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LocatorBarRenderer implements ContextualBarRenderer {
    private static final ResourceLocation LOCATOR_BAR_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/locator_bar_background");
    private static final ResourceLocation LOCATOR_BAR_ARROW_UP = ResourceLocation.withDefaultNamespace("hud/locator_bar_arrow_up");
    private static final ResourceLocation LOCATOR_BAR_ARROW_DOWN = ResourceLocation.withDefaultNamespace("hud/locator_bar_arrow_down");
    private static final int DOT_SIZE = 9;
    private static final int VISIBLE_DEGREE_RANGE = 60;
    private static final int ARROW_WIDTH = 7;
    private static final int ARROW_HEIGHT = 5;
    private static final int ARROW_LEFT = 1;
    private static final int ARROW_PADDING = 1;
    private final Minecraft minecraft;

    public LocatorBarRenderer(Minecraft p_409053_) {
        this.minecraft = p_409053_;
    }

    @Override
    public void renderBackground(GuiGraphics p_410535_, DeltaTracker p_408703_) {
        p_410535_.blitSprite(RenderPipelines.GUI_TEXTURED, LOCATOR_BAR_BACKGROUND, this.left(this.minecraft.getWindow()), this.top(this.minecraft.getWindow()), 182, 5);
    }

    @Override
    public void render(GuiGraphics p_410559_, DeltaTracker p_405979_) {
        int i = this.top(this.minecraft.getWindow());
        Level level = this.minecraft.cameraEntity.level();
        this.minecraft
            .player
            .connection
            .getWaypointManager()
            .forEachWaypoint(
                this.minecraft.cameraEntity,
                p_407174_ -> {
                    if (!p_407174_.id().left().map(p_409131_ -> p_409131_.equals(this.minecraft.cameraEntity.getUUID())).orElse(false)) {
                        double d0 = p_407174_.yawAngleToCamera(level, this.minecraft.gameRenderer.getMainCamera());
                        if (!(d0 <= -61.0) && !(d0 > 60.0)) {
                            int j = Mth.ceil((p_410559_.guiWidth() - 9) / 2.0F);
                            Waypoint.Icon waypoint$icon = p_407174_.icon();
                            WaypointStyle waypointstyle = this.minecraft.getWaypointStyles().get(waypoint$icon.style);
                            float f = Mth.sqrt((float)p_407174_.distanceSquared(this.minecraft.cameraEntity));
                            ResourceLocation resourcelocation = waypointstyle.sprite(f);
                            int k = waypoint$icon.color
                                .orElseGet(
                                    () -> p_407174_.id()
                                        .map(
                                            p_406769_ -> ARGB.setBrightness(ARGB.color(255, p_406769_.hashCode()), 0.9F),
                                            p_407645_ -> ARGB.setBrightness(ARGB.color(255, p_407645_.hashCode()), 0.9F)
                                        )
                                );
                            int l = (int)(d0 * 173.0 / 2.0 / 60.0);
                            p_410559_.blitSprite(RenderPipelines.GUI_TEXTURED, resourcelocation, j + l, i - 2, 9, 9, k);
                            TrackedWaypoint.PitchDirection trackedwaypoint$pitchdirection = p_407174_.pitchDirectionToCamera(level, this.minecraft.gameRenderer);
                            if (trackedwaypoint$pitchdirection != TrackedWaypoint.PitchDirection.NONE) {
                                int i1;
                                ResourceLocation resourcelocation1;
                                if (trackedwaypoint$pitchdirection == TrackedWaypoint.PitchDirection.DOWN) {
                                    i1 = 6;
                                    resourcelocation1 = LOCATOR_BAR_ARROW_DOWN;
                                } else {
                                    i1 = -6;
                                    resourcelocation1 = LOCATOR_BAR_ARROW_UP;
                                }

                                p_410559_.blitSprite(RenderPipelines.GUI_TEXTURED, resourcelocation1, j + l + 1, i + i1, 7, 5);
                            }
                        }
                    }
                }
            );
    }
}
