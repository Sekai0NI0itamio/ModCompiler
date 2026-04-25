package net.minecraft.client.resources.model;

import com.mojang.math.OctahedralGroup;
import com.mojang.math.Quadrant;
import com.mojang.math.Transformation;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.core.BlockMath;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

@OnlyIn(Dist.CLIENT)
public enum BlockModelRotation implements ModelState {
    X0_Y0(Quadrant.R0, Quadrant.R0),
    X0_Y90(Quadrant.R0, Quadrant.R90),
    X0_Y180(Quadrant.R0, Quadrant.R180),
    X0_Y270(Quadrant.R0, Quadrant.R270),
    X90_Y0(Quadrant.R90, Quadrant.R0),
    X90_Y90(Quadrant.R90, Quadrant.R90),
    X90_Y180(Quadrant.R90, Quadrant.R180),
    X90_Y270(Quadrant.R90, Quadrant.R270),
    X180_Y0(Quadrant.R180, Quadrant.R0),
    X180_Y90(Quadrant.R180, Quadrant.R90),
    X180_Y180(Quadrant.R180, Quadrant.R180),
    X180_Y270(Quadrant.R180, Quadrant.R270),
    X270_Y0(Quadrant.R270, Quadrant.R0),
    X270_Y90(Quadrant.R270, Quadrant.R90),
    X270_Y180(Quadrant.R270, Quadrant.R180),
    X270_Y270(Quadrant.R270, Quadrant.R270);

    private static final BlockModelRotation[][] XY_TABLE = Util.make(
        new BlockModelRotation[Quadrant.values().length][Quadrant.values().length], p_389566_ -> {
            for (BlockModelRotation blockmodelrotation : values()) {
                p_389566_[blockmodelrotation.xRotation.ordinal()][blockmodelrotation.yRotation.ordinal()] = blockmodelrotation;
            }
        }
    );
    private final Quadrant xRotation;
    private final Quadrant yRotation;
    final Transformation transformation;
    private final OctahedralGroup actualRotation;
    final Map<Direction, Matrix4fc> faceMapping = new EnumMap<>(Direction.class);
    final Map<Direction, Matrix4fc> inverseFaceMapping = new EnumMap<>(Direction.class);
    private final BlockModelRotation.WithUvLock withUvLock = new BlockModelRotation.WithUvLock(this);

    private BlockModelRotation(final Quadrant p_393753_, final Quadrant p_393435_) {
        this.xRotation = p_393753_;
        this.yRotation = p_393435_;
        this.actualRotation = OctahedralGroup.fromXYAngles(p_393753_, p_393435_);
        if (this.actualRotation != OctahedralGroup.IDENTITY) {
            this.transformation = new Transformation(new Matrix4f(this.actualRotation.transformation()));
        } else {
            this.transformation = Transformation.identity();
        }

        for (Direction direction : Direction.values()) {
            Matrix4fc matrix4fc = BlockMath.getFaceTransformation(this.transformation, direction).getMatrix();
            this.faceMapping.put(direction, matrix4fc);
            this.inverseFaceMapping.put(direction, matrix4fc.invertAffine(new Matrix4f()));
        }
    }

    @Override
    public Transformation transformation() {
        return this.transformation;
    }

    public static BlockModelRotation by(Quadrant p_397325_, Quadrant p_391656_) {
        return XY_TABLE[p_397325_.ordinal()][p_391656_.ordinal()];
    }

    public OctahedralGroup actualRotation() {
        return this.actualRotation;
    }

    public ModelState withUvLock() {
        return this.withUvLock;
    }

    @OnlyIn(Dist.CLIENT)
    record WithUvLock(BlockModelRotation parent) implements ModelState {
        @Override
        public Transformation transformation() {
            return this.parent.transformation;
        }

        @Override
        public Matrix4fc faceTransformation(Direction p_392706_) {
            return this.parent.faceMapping.getOrDefault(p_392706_, NO_TRANSFORM);
        }

        @Override
        public Matrix4fc inverseFaceTransformation(Direction p_391398_) {
            return this.parent.inverseFaceMapping.getOrDefault(p_391398_, NO_TRANSFORM);
        }
    }
}
