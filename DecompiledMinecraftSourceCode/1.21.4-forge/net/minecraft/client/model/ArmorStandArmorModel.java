package net.minecraft.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ArmorStandArmorModel extends HumanoidModel<ArmorStandRenderState> {
    public ArmorStandArmorModel(ModelPart p_170346_) {
        super(p_170346_);
    }

    public static LayerDefinition createBodyLayer(CubeDeformation p_170348_) {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(p_170348_, 0.0F);
        PartDefinition partdefinition = meshdefinition.getRoot();
        PartDefinition partdefinition1 = partdefinition.addOrReplaceChild(
            "head",
            CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, p_170348_),
            PartPose.offset(0.0F, 1.0F, 0.0F)
        );
        partdefinition1.addOrReplaceChild(
            "hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, p_170348_.extend(0.5F)), PartPose.ZERO
        );
        partdefinition.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, p_170348_.extend(-0.1F)),
            PartPose.offset(-1.9F, 11.0F, 0.0F)
        );
        partdefinition.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, p_170348_.extend(-0.1F)),
            PartPose.offset(1.9F, 11.0F, 0.0F)
        );
        return LayerDefinition.create(meshdefinition, 64, 32);
    }

    public void setupAnim(ArmorStandRenderState p_368790_) {
        super.setupAnim(p_368790_);
        this.head.xRot = (float) (Math.PI / 180.0) * p_368790_.headPose.x();
        this.head.yRot = (float) (Math.PI / 180.0) * p_368790_.headPose.y();
        this.head.zRot = (float) (Math.PI / 180.0) * p_368790_.headPose.z();
        this.body.xRot = (float) (Math.PI / 180.0) * p_368790_.bodyPose.x();
        this.body.yRot = (float) (Math.PI / 180.0) * p_368790_.bodyPose.y();
        this.body.zRot = (float) (Math.PI / 180.0) * p_368790_.bodyPose.z();
        this.leftArm.xRot = (float) (Math.PI / 180.0) * p_368790_.leftArmPose.x();
        this.leftArm.yRot = (float) (Math.PI / 180.0) * p_368790_.leftArmPose.y();
        this.leftArm.zRot = (float) (Math.PI / 180.0) * p_368790_.leftArmPose.z();
        this.rightArm.xRot = (float) (Math.PI / 180.0) * p_368790_.rightArmPose.x();
        this.rightArm.yRot = (float) (Math.PI / 180.0) * p_368790_.rightArmPose.y();
        this.rightArm.zRot = (float) (Math.PI / 180.0) * p_368790_.rightArmPose.z();
        this.leftLeg.xRot = (float) (Math.PI / 180.0) * p_368790_.leftLegPose.x();
        this.leftLeg.yRot = (float) (Math.PI / 180.0) * p_368790_.leftLegPose.y();
        this.leftLeg.zRot = (float) (Math.PI / 180.0) * p_368790_.leftLegPose.z();
        this.rightLeg.xRot = (float) (Math.PI / 180.0) * p_368790_.rightLegPose.x();
        this.rightLeg.yRot = (float) (Math.PI / 180.0) * p_368790_.rightLegPose.y();
        this.rightLeg.zRot = (float) (Math.PI / 180.0) * p_368790_.rightLegPose.z();
    }
}
