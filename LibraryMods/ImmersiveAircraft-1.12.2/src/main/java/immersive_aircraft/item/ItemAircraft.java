package immersive_aircraft.item;

import immersive_aircraft.Main;
import immersive_aircraft.entity.AbstractAircraftEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Item form of an aircraft. Right-click on a block to spawn the corresponding
 * entity. Mirrors the role of {@code AircraftItem} and {@code VehicleItem} in
 * the 1.20.1 source.
 */
public class ItemAircraft extends Item {
    private final Class<? extends Entity> entityClass;

    public ItemAircraft(String name, Class<? extends Entity> entityClass) {
        this.entityClass = entityClass;
        this.setUnlocalizedName(name);
        this.setMaxStackSize(1);
        this.setRegistryName(Main.MODID, name);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        // The block-placing form is more useful - fall through to that path.
        return new ActionResult<>(EnumActionResult.PASS, playerIn.getHeldItem(handIn));
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos,
                                      EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) {
            return EnumActionResult.SUCCESS;
        }
        try {
            Entity aircraft = entityClass.getConstructor(World.class).newInstance(worldIn);
            double x = pos.getX() + 0.5d;
            double y = pos.getY() + 1.0d;
            double z = pos.getZ() + 0.5d;
            // Use a slightly forward offset so the player isn't standing inside
            Vec3d look = player.getLookVec();
            aircraft.setPosition(x + look.x * 1.5d, y, z + look.z * 1.5d);
            aircraft.rotationYaw = player.rotationYaw;
            if (aircraft instanceof AbstractAircraftEntity) {
                ((AbstractAircraftEntity) aircraft).setEngineTarget(0.0f);
            }
            worldIn.spawnEntity(aircraft);
            if (!player.capabilities.isCreativeMode) {
                player.getHeldItem(hand).shrink(1);
            }
            return EnumActionResult.SUCCESS;
        } catch (Exception ex) {
            Main.LOGGER.error("Failed to spawn aircraft", ex);
            return EnumActionResult.FAIL;
        }
    }
}
