package net.minecraft.client.renderer.block.model;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.math.MatrixUtil;
import com.mojang.math.Quadrant;
import com.mojang.math.Transformation;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@OnlyIn(Dist.CLIENT)
public class FaceBakery {
    public static final int VERTEX_INT_SIZE = 8;
    public static final int VERTEX_COUNT = 4;
    private static final int COLOR_INDEX = 3;
    public static final int UV_INDEX = 4;
    private static final Vector3fc NO_RESCALE = new Vector3f(1.0F, 1.0F, 1.0F);
    private static final Vector3fc BLOCK_MIDDLE = new Vector3f(0.5F, 0.5F, 0.5F);

    @VisibleForTesting
    static BlockElementFace.UVs defaultFaceUV(Vector3fc p_393926_, Vector3fc p_395023_, Direction p_393336_) {
        return switch (p_393336_) {
            case DOWN -> new BlockElementFace.UVs(p_393926_.x(), 16.0F - p_395023_.z(), p_395023_.x(), 16.0F - p_393926_.z());
            case UP -> new BlockElementFace.UVs(p_393926_.x(), p_393926_.z(), p_395023_.x(), p_395023_.z());
            case NORTH -> new BlockElementFace.UVs(16.0F - p_395023_.x(), 16.0F - p_395023_.y(), 16.0F - p_393926_.x(), 16.0F - p_393926_.y());
            case SOUTH -> new BlockElementFace.UVs(p_393926_.x(), 16.0F - p_395023_.y(), p_395023_.x(), 16.0F - p_393926_.y());
            case WEST -> new BlockElementFace.UVs(p_393926_.z(), 16.0F - p_395023_.y(), p_395023_.z(), 16.0F - p_393926_.y());
            case EAST -> new BlockElementFace.UVs(16.0F - p_395023_.z(), 16.0F - p_395023_.y(), 16.0F - p_393926_.z(), 16.0F - p_393926_.y());
        };
    }

    public static BakedQuad bakeQuad(
        Vector3fc p_393181_,
        Vector3fc p_395026_,
        BlockElementFace p_111603_,
        TextureAtlasSprite p_111604_,
        Direction p_111605_,
        ModelState p_111606_,
        @Nullable BlockElementRotation p_111607_,
        boolean p_111608_,
        int p_364904_
    ) {
        BlockElementFace.UVs blockelementface$uvs = p_111603_.uvs();
        if (blockelementface$uvs == null) {
            blockelementface$uvs = defaultFaceUV(p_393181_, p_395026_, p_111605_);
        }

        blockelementface$uvs = shrinkUVs(p_111604_, blockelementface$uvs);
        Matrix4fc matrix4fc = p_111606_.inverseFaceTransformation(p_111605_);
        int[] aint = makeVertices(
            blockelementface$uvs, p_111603_.rotation(), matrix4fc, p_111604_, p_111605_, setupShape(p_393181_, p_395026_), p_111606_.transformation(), p_111607_
        );
        Direction direction = calculateFacing(aint);
        if (p_111607_ == null) {
            recalculateWinding(aint, direction);
        }

        var data = p_111603_.data();
        var quad = new BakedQuad(aint, p_111603_.tintIndex(), direction, p_111604_, p_111608_, p_364904_, data.ambientOcclusion());
        if (!net.minecraftforge.client.model.ForgeFaceData.DEFAULT.equals(data)) {
           quad = net.minecraftforge.client.model.QuadTransformers.applyingLightmap(data.blockLight(), data.skyLight())
               .andThen(net.minecraftforge.client.model.QuadTransformers.applyingColor(data.color()))
               .process(quad);
        }
        return quad;
    }

    private static BlockElementFace.UVs shrinkUVs(TextureAtlasSprite p_397002_, BlockElementFace.UVs p_394057_) {
        float f = p_394057_.minU();
        float f1 = p_394057_.minV();
        float f2 = p_394057_.maxU();
        float f3 = p_394057_.maxV();
        float f4 = p_397002_.uvShrinkRatio();
        float f5 = (f + f + f2 + f2) / 4.0F;
        float f6 = (f1 + f1 + f3 + f3) / 4.0F;
        return new BlockElementFace.UVs(Mth.lerp(f4, f, f5), Mth.lerp(f4, f1, f6), Mth.lerp(f4, f2, f5), Mth.lerp(f4, f3, f6));
    }

    private static int[] makeVertices(
        BlockElementFace.UVs p_391814_,
        Quadrant p_393719_,
        Matrix4fc p_397140_,
        TextureAtlasSprite p_111575_,
        Direction p_111576_,
        float[] p_111577_,
        Transformation p_111578_,
        @Nullable BlockElementRotation p_111579_
    ) {
        FaceInfo faceinfo = FaceInfo.fromFacing(p_111576_);
        int[] aint = new int[32];

        for (int i = 0; i < 4; i++) {
            bakeVertex(aint, i, faceinfo, p_391814_, p_393719_, p_397140_, p_111577_, p_111575_, p_111578_, p_111579_);
        }

        return aint;
    }

    private static float[] setupShape(Vector3fc p_393902_, Vector3fc p_394068_) {
        float[] afloat = new float[Direction.values().length];
        afloat[FaceInfo.Constants.MIN_X] = p_393902_.x() / 16.0F;
        afloat[FaceInfo.Constants.MIN_Y] = p_393902_.y() / 16.0F;
        afloat[FaceInfo.Constants.MIN_Z] = p_393902_.z() / 16.0F;
        afloat[FaceInfo.Constants.MAX_X] = p_394068_.x() / 16.0F;
        afloat[FaceInfo.Constants.MAX_Y] = p_394068_.y() / 16.0F;
        afloat[FaceInfo.Constants.MAX_Z] = p_394068_.z() / 16.0F;
        return afloat;
    }

    private static void bakeVertex(
        int[] p_111621_,
        int p_111622_,
        FaceInfo p_394082_,
        BlockElementFace.UVs p_395065_,
        Quadrant p_392208_,
        Matrix4fc p_396750_,
        float[] p_111625_,
        TextureAtlasSprite p_111626_,
        Transformation p_111627_,
        @Nullable BlockElementRotation p_111628_
    ) {
        FaceInfo.VertexInfo faceinfo$vertexinfo = p_394082_.getVertexInfo(p_111622_);
        Vector3f vector3f = new Vector3f(
            p_111625_[faceinfo$vertexinfo.xFace], p_111625_[faceinfo$vertexinfo.yFace], p_111625_[faceinfo$vertexinfo.zFace]
        );
        applyElementRotation(vector3f, p_111628_);
        applyModelRotation(vector3f, p_111627_);
        float f = BlockElementFace.getU(p_395065_, p_392208_, p_111622_);
        float f1 = BlockElementFace.getV(p_395065_, p_392208_, p_111622_);
        float f2;
        float f3;
        if (MatrixUtil.isIdentity(p_396750_)) {
            f3 = f;
            f2 = f1;
        } else {
            Vector3f vector3f1 = p_396750_.transformPosition(new Vector3f(cornerToCenter(f), cornerToCenter(f1), 0.0F));
            f3 = centerToCorner(vector3f1.x);
            f2 = centerToCorner(vector3f1.y);
        }

        fillVertex(p_111621_, p_111622_, vector3f, p_111626_, f3, f2);
    }

    private static float cornerToCenter(float p_393791_) {
        return p_393791_ - 0.5F;
    }

    private static float centerToCorner(float p_392923_) {
        return p_392923_ + 0.5F;
    }

    private static void fillVertex(int[] p_111615_, int p_111616_, Vector3f p_254291_, TextureAtlasSprite p_111618_, float p_393253_, float p_396335_) {
        int i = p_111616_ * 8;
        p_111615_[i] = Float.floatToRawIntBits(p_254291_.x());
        p_111615_[i + 1] = Float.floatToRawIntBits(p_254291_.y());
        p_111615_[i + 2] = Float.floatToRawIntBits(p_254291_.z());
        p_111615_[i + 3] = -1;
        p_111615_[i + 4] = Float.floatToRawIntBits(p_111618_.getU(p_393253_));
        p_111615_[i + 4 + 1] = Float.floatToRawIntBits(p_111618_.getV(p_396335_));
    }

    private static void applyElementRotation(Vector3f p_254412_, @Nullable BlockElementRotation p_254150_) {
        if (p_254150_ != null) {
            Vector3fc vector3fc = p_254150_.axis().getPositive().getUnitVec3f();
            Matrix4fc matrix4fc = new Matrix4f().rotation(p_254150_.angle() * (float) (Math.PI / 180.0), vector3fc);
            Vector3fc vector3fc1 = p_254150_.rescale() ? computeRescale(p_254150_) : NO_RESCALE;
            rotateVertexBy(p_254412_, p_254150_.origin(), matrix4fc, vector3fc1);
        }
    }

    private static Vector3fc computeRescale(BlockElementRotation p_405947_) {
        if (p_405947_.angle() == 0.0F) {
            return NO_RESCALE;
        } else {
            float f = Math.abs(p_405947_.angle());
            float f1 = 1.0F / Mth.cos(f * (float) (Math.PI / 180.0));

            return switch (p_405947_.axis()) {
                case X -> new Vector3f(1.0F, f1, f1);
                case Y -> new Vector3f(f1, 1.0F, f1);
                case Z -> new Vector3f(f1, f1, 1.0F);
            };
        }
    }

    private static void applyModelRotation(Vector3f p_254561_, Transformation p_253793_) {
        if (p_253793_ != Transformation.identity()) {
            rotateVertexBy(p_254561_, BLOCK_MIDDLE, p_253793_.getMatrix(), NO_RESCALE);
        }
    }

    private static void rotateVertexBy(Vector3f p_393378_, Vector3fc p_396712_, Matrix4fc p_394901_, Vector3fc p_395897_) {
        p_393378_.sub(p_396712_);
        p_394901_.transformPosition(p_393378_);
        p_393378_.mul(p_395897_);
        p_393378_.add(p_396712_);
    }

    private static Direction calculateFacing(int[] p_111613_) {
        Vector3f vector3f = vectorFromData(p_111613_, 0);
        Vector3f vector3f1 = vectorFromData(p_111613_, 8);
        Vector3f vector3f2 = vectorFromData(p_111613_, 16);
        Vector3f vector3f3 = new Vector3f(vector3f).sub(vector3f1);
        Vector3f vector3f4 = new Vector3f(vector3f2).sub(vector3f1);
        Vector3f vector3f5 = new Vector3f(vector3f4).cross(vector3f3).normalize();
        if (!vector3f5.isFinite()) {
            return Direction.UP;
        } else {
            Direction direction = null;
            float f = 0.0F;

            for (Direction direction1 : Direction.values()) {
                float f1 = vector3f5.dot(direction1.getUnitVec3f());
                if (f1 >= 0.0F && f1 > f) {
                    f = f1;
                    direction = direction1;
                }
            }

            return direction == null ? Direction.UP : direction;
        }
    }

    private static float xFromData(int[] p_393171_, int p_394995_) {
        return Float.intBitsToFloat(p_393171_[p_394995_]);
    }

    private static float yFromData(int[] p_396587_, int p_391994_) {
        return Float.intBitsToFloat(p_396587_[p_391994_ + 1]);
    }

    private static float zFromData(int[] p_392671_, int p_392811_) {
        return Float.intBitsToFloat(p_392671_[p_392811_ + 2]);
    }

    private static Vector3f vectorFromData(int[] p_391584_, int p_394245_) {
        return new Vector3f(xFromData(p_391584_, p_394245_), yFromData(p_391584_, p_394245_), zFromData(p_391584_, p_394245_));
    }

    private static void recalculateWinding(int[] p_111631_, Direction p_111632_) {
        int[] aint = new int[p_111631_.length];
        System.arraycopy(p_111631_, 0, aint, 0, p_111631_.length);
        float[] afloat = new float[Direction.values().length];
        afloat[FaceInfo.Constants.MIN_X] = 999.0F;
        afloat[FaceInfo.Constants.MIN_Y] = 999.0F;
        afloat[FaceInfo.Constants.MIN_Z] = 999.0F;
        afloat[FaceInfo.Constants.MAX_X] = -999.0F;
        afloat[FaceInfo.Constants.MAX_Y] = -999.0F;
        afloat[FaceInfo.Constants.MAX_Z] = -999.0F;

        for (int i = 0; i < 4; i++) {
            int j = 8 * i;
            float f = xFromData(aint, j);
            float f1 = yFromData(aint, j);
            float f2 = zFromData(aint, j);
            if (f < afloat[FaceInfo.Constants.MIN_X]) {
                afloat[FaceInfo.Constants.MIN_X] = f;
            }

            if (f1 < afloat[FaceInfo.Constants.MIN_Y]) {
                afloat[FaceInfo.Constants.MIN_Y] = f1;
            }

            if (f2 < afloat[FaceInfo.Constants.MIN_Z]) {
                afloat[FaceInfo.Constants.MIN_Z] = f2;
            }

            if (f > afloat[FaceInfo.Constants.MAX_X]) {
                afloat[FaceInfo.Constants.MAX_X] = f;
            }

            if (f1 > afloat[FaceInfo.Constants.MAX_Y]) {
                afloat[FaceInfo.Constants.MAX_Y] = f1;
            }

            if (f2 > afloat[FaceInfo.Constants.MAX_Z]) {
                afloat[FaceInfo.Constants.MAX_Z] = f2;
            }
        }

        FaceInfo faceinfo = FaceInfo.fromFacing(p_111632_);

        for (int i1 = 0; i1 < 4; i1++) {
            int j1 = 8 * i1;
            FaceInfo.VertexInfo faceinfo$vertexinfo = faceinfo.getVertexInfo(i1);
            float f8 = afloat[faceinfo$vertexinfo.xFace];
            float f3 = afloat[faceinfo$vertexinfo.yFace];
            float f4 = afloat[faceinfo$vertexinfo.zFace];
            p_111631_[j1] = Float.floatToRawIntBits(f8);
            p_111631_[j1 + 1] = Float.floatToRawIntBits(f3);
            p_111631_[j1 + 2] = Float.floatToRawIntBits(f4);

            for (int k = 0; k < 4; k++) {
                int l = 8 * k;
                float f5 = xFromData(aint, l);
                float f6 = yFromData(aint, l);
                float f7 = zFromData(aint, l);
                if (Mth.equal(f8, f5) && Mth.equal(f3, f6) && Mth.equal(f4, f7)) {
                    p_111631_[j1 + 4] = aint[l + 4];
                    p_111631_[j1 + 4 + 1] = aint[l + 4 + 1];
                }
            }
        }
    }

    public static void extractPositions(int[] p_394217_, Consumer<Vector3f> p_393107_) {
        for (int i = 0; i < 4; i++) {
            p_393107_.accept(vectorFromData(p_394217_, 8 * i));
        }
    }
}
