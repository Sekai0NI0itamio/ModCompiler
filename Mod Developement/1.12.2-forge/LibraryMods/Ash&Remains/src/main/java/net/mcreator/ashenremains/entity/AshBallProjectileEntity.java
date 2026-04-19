package net.mcreator.ashenremains.entity;

import net.mcreator.ashenremains.init.AshenremainsModEntities;
import net.mcreator.ashenremains.init.AshenremainsModItems;
import net.mcreator.ashenremains.procedures.AshBallBlindnessChanceProcedure;
import net.mcreator.ashenremains.procedures.AshBallProjectileHitsBlockProcedure;
import net.mcreator.ashenremains.procedures.AshBallTrailProcedure;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PlayMessages.SpawnEntity;
import net.minecraftforge.registries.ForgeRegistries;

@OnlyIn(
   value = Dist.CLIENT,
   _interface = ItemSupplier.class
)
public class AshBallProjectileEntity extends AbstractArrow implements ItemSupplier {
   public static final ItemStack PROJECTILE_ITEM = new ItemStack((ItemLike)AshenremainsModItems.ASH_BALL.get());

   public AshBallProjectileEntity(SpawnEntity packet, Level world) {
      super((EntityType)AshenremainsModEntities.ASH_BALL_PROJECTILE.get(), world);
   }

   public AshBallProjectileEntity(EntityType<? extends AshBallProjectileEntity> type, Level world) {
      super(type, world);
   }

   public AshBallProjectileEntity(EntityType<? extends AshBallProjectileEntity> type, double x, double y, double z, Level world) {
      super(type, x, y, z, world);
   }

   public AshBallProjectileEntity(EntityType<? extends AshBallProjectileEntity> type, LivingEntity entity, Level world) {
      super(type, entity, world);
   }

   public Packet<ClientGamePacketListener> m_5654_() {
      return NetworkHooks.getEntitySpawningPacket(this);
   }

   @OnlyIn(Dist.CLIENT)
   public ItemStack m_7846_() {
      return PROJECTILE_ITEM;
   }

   protected ItemStack m_7941_() {
      return PROJECTILE_ITEM;
   }

   protected void m_7761_(LivingEntity entity) {
      super.m_7761_(entity);
      entity.m_21317_(entity.m_21234_() - 1);
   }

   public void m_6123_(Player entity) {
      super.m_6123_(entity);
      AshBallBlindnessChanceProcedure.execute(this.m_9236_(), this.m_20185_(), this.m_20186_(), this.m_20189_(), entity, this.m_19749_());
   }

   public void m_5790_(EntityHitResult entityHitResult) {
      super.m_5790_(entityHitResult);
      AshBallBlindnessChanceProcedure.execute(this.m_9236_(), this.m_20185_(), this.m_20186_(), this.m_20189_(), entityHitResult.m_82443_(), this.m_19749_());
   }

   public void m_8060_(BlockHitResult blockHitResult) {
      super.m_8060_(blockHitResult);
      AshBallProjectileHitsBlockProcedure.execute(
         this.m_9236_(), blockHitResult.m_82425_().m_123341_(), blockHitResult.m_82425_().m_123342_(), blockHitResult.m_82425_().m_123343_()
      );
   }

   public void m_8119_() {
      super.m_8119_();
      AshBallTrailProcedure.execute(this.m_9236_(), this.m_20185_(), this.m_20186_(), this.m_20189_());
      if (this.f_36703_) {
         this.m_146870_();
      }
   }

   public static AshBallProjectileEntity shoot(Level world, LivingEntity entity, RandomSource source) {
      return shoot(world, entity, source, 0.5F, 1.0, 0);
   }

   public static AshBallProjectileEntity shoot(Level world, LivingEntity entity, RandomSource random, float power, double damage, int knockback) {
      AshBallProjectileEntity entityarrow = new AshBallProjectileEntity(
         (EntityType<? extends AshBallProjectileEntity>)AshenremainsModEntities.ASH_BALL_PROJECTILE.get(), entity, world
      );
      entityarrow.m_6686_(entity.m_20252_(1.0F).f_82479_, entity.m_20252_(1.0F).f_82480_, entity.m_20252_(1.0F).f_82481_, power * 2.0F, 0.0F);
      entityarrow.m_20225_(true);
      entityarrow.m_36762_(false);
      entityarrow.m_36781_(damage);
      entityarrow.m_36735_(knockback);
      world.m_7967_(entityarrow);
      world.m_6263_(
         null,
         entity.m_20185_(),
         entity.m_20186_(),
         entity.m_20189_(),
         (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.snowball.throw")),
         SoundSource.PLAYERS,
         1.0F,
         1.0F / (random.m_188501_() * 0.5F + 1.0F) + power / 2.0F
      );
      return entityarrow;
   }

   public static AshBallProjectileEntity shoot(LivingEntity entity, LivingEntity target) {
      AshBallProjectileEntity entityarrow = new AshBallProjectileEntity(
         (EntityType<? extends AshBallProjectileEntity>)AshenremainsModEntities.ASH_BALL_PROJECTILE.get(), entity, entity.m_9236_()
      );
      double dx = target.m_20185_() - entity.m_20185_();
      double dy = target.m_20186_() + target.m_20192_() - 1.1;
      double dz = target.m_20189_() - entity.m_20189_();
      entityarrow.m_6686_(dx, dy - entityarrow.m_20186_() + Math.hypot(dx, dz) * 0.2F, dz, 1.0F, 12.0F);
      entityarrow.m_20225_(true);
      entityarrow.m_36781_(1.0);
      entityarrow.m_36735_(0);
      entityarrow.m_36762_(false);
      entity.m_9236_().m_7967_(entityarrow);
      entity.m_9236_()
         .m_6263_(
            null,
            entity.m_20185_(),
            entity.m_20186_(),
            entity.m_20189_(),
            (SoundEvent)ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.snowball.throw")),
            SoundSource.PLAYERS,
            1.0F,
            1.0F / (RandomSource.m_216327_().m_188501_() * 0.5F + 1.0F)
         );
      return entityarrow;
   }
}
