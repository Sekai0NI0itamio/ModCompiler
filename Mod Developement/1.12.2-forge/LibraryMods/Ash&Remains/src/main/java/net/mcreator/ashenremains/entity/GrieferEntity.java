package net.mcreator.ashenremains.entity;

import net.mcreator.ashenremains.init.AshenremainsModEntities;
import net.mcreator.ashenremains.procedures.GrieferBlastProcedure;
import net.mcreator.ashenremains.procedures.GrieferDeathProcedure;
import net.mcreator.ashenremains.procedures.GriefersAreCowardsProcedure;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PlayMessages.SpawnEntity;
import net.minecraftforge.registries.ForgeRegistries;

public class GrieferEntity extends Monster {
   public GrieferEntity(SpawnEntity packet, Level world) {
      this((EntityType<GrieferEntity>)AshenremainsModEntities.GRIEFER.get(), world);
   }

   public GrieferEntity(EntityType<GrieferEntity> type, Level world) {
      super(type, world);
      this.m_274367_(0.6F);
      this.f_21364_ = 6;
      this.m_21557_(false);
   }

   public Packet<ClientGamePacketListener> m_5654_() {
      return NetworkHooks.getEntitySpawningPacket(this);
   }

   protected void m_8099_() {
      super.m_8099_();
      this.f_21345_.m_25352_(1, new PanicGoal(this, 1.0));
      this.f_21345_.m_25352_(2, new AvoidEntityGoal(this, Player.class, 8.0F, 1.0, 0.8));
      this.f_21345_.m_25352_(3, new WaterAvoidingRandomStrollGoal(this, 0.6));
      this.f_21345_.m_25352_(4, new RandomLookAroundGoal(this));
   }

   public MobType m_6336_() {
      return MobType.f_21640_;
   }

   public SoundEvent m_7975_(DamageSource ds) {
      return (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.moss.hit"));
   }

   public SoundEvent m_5592_() {
      return (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("block.fire.extinguish"));
   }

   public void m_8038_(ServerLevel serverWorld, LightningBolt lightningBolt) {
      super.m_8038_(serverWorld, lightningBolt);
      GrieferDeathProcedure.execute(this.m_9236_(), this.m_20185_(), this.m_20186_(), this.m_20189_());
   }

   public boolean m_6469_(DamageSource damagesource, float amount) {
      double x = this.m_20185_();
      double y = this.m_20186_();
      double z = this.m_20189_();
      Level world = this.m_9236_();
      Entity sourceentity = damagesource.m_7639_();
      Entity immediatesourceentity = damagesource.m_7640_();
      GriefersAreCowardsProcedure.execute(world, x, y, z, this);
      return damagesource.m_276093_(DamageTypes.f_268631_) ? false : super.m_6469_(damagesource, amount);
   }

   public boolean m_5825_() {
      return true;
   }

   public void m_6667_(DamageSource source) {
      super.m_6667_(source);
      GrieferDeathProcedure.execute(this.m_9236_(), this.m_20185_(), this.m_20186_(), this.m_20189_());
   }

   public void m_6075_() {
      super.m_6075_();
      GrieferBlastProcedure.execute(this.m_9236_(), this.m_20185_(), this.m_20186_(), this.m_20189_(), this);
   }

   public static void init() {
   }

   public static Builder createAttributes() {
      Builder builder = Mob.m_21552_();
      builder = builder.m_22268_(Attributes.f_22279_, 0.4);
      builder = builder.m_22268_(Attributes.f_22276_, 16.0);
      builder = builder.m_22268_(Attributes.f_22284_, 0.5);
      builder = builder.m_22268_(Attributes.f_22281_, 3.0);
      return builder.m_22268_(Attributes.f_22277_, 16.0);
   }
}
