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

package net.minecraftforge.common.util;

import com.mojang.authlib.GameProfile;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQuery;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fmllegacy.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Set;
import java.util.UUID;

//Preliminary, simple Fake Player class
public class FakePlayer extends ServerPlayer
{
    public FakePlayer(ServerLevel world, GameProfile name)
    {
        super(world.m_142572_(), world, name);
        this.f_8906_ = new FakePlayerNetHandler(world.m_142572_(), this);
    }

    @Override public Vec3 m_20182_(){ return new Vec3(0, 0, 0); }
    @Override public BlockPos m_142538_(){ return BlockPos.f_121853_; }
    @Override public void m_5661_(Component chatComponent, boolean actionBar){}
    @Override public void m_6352_(Component component, UUID senderUUID) {}
    @Override public void m_6278_(Stat par1StatBase, int par2){}
    //@Override public void openGui(Object mod, int modGuiId, World world, int x, int y, int z){}
    @Override public boolean m_6673_(DamageSource source){ return true; }
    @Override public boolean m_7099_(Player player){ return false; }
    @Override public void m_6667_(DamageSource source){ return; }
    @Override public void m_8119_(){ return; }
    @Override public void m_9156_(ServerboundClientInformationPacket pkt){ return; }
    @Override @Nullable public MinecraftServer m_20194_() { return ServerLifecycleHooks.getCurrentServer(); }

    @ParametersAreNonnullByDefault
    private static class FakePlayerNetHandler extends ServerGamePacketListenerImpl {
        private static final Connection DUMMY_CONNECTION = new Connection(PacketFlow.CLIENTBOUND);

        public FakePlayerNetHandler(MinecraftServer server, ServerPlayer player) {
            super(server, DUMMY_CONNECTION, player);
        }

        @Override public void m_9933_() { }
        @Override public void m_9953_() { }
        @Override public void m_9942_(Component message) { }
        @Override public void m_5918_(ServerboundPlayerInputPacket packet) { }
        @Override public void m_5659_(ServerboundMoveVehiclePacket packet) { }
        @Override public void m_7376_(ServerboundAcceptTeleportationPacket packet) { }
        @Override public void m_7411_(ServerboundRecipeBookSeenRecipePacket packet) { }
        @Override public void m_7982_(ServerboundRecipeBookChangeSettingsPacket packet) { }
        @Override public void m_6947_(ServerboundSeenAdvancementsPacket packet) { }
        @Override public void m_7741_(ServerboundCommandSuggestionPacket packet) { }
        @Override public void m_7192_(ServerboundSetCommandBlockPacket packet) { }
        @Override public void m_6629_(ServerboundSetCommandMinecartPacket packet) { }
        @Override public void m_7965_(ServerboundPickItemPacket packet) { }
        @Override public void m_5591_(ServerboundRenameItemPacket packet) { }
        @Override public void m_5712_(ServerboundSetBeaconPacket packet) { }
        @Override public void m_7424_(ServerboundSetStructureBlockPacket packet) { }
        @Override public void m_8019_(ServerboundSetJigsawBlockPacket packet) { }
        @Override public void m_6449_(ServerboundJigsawGeneratePacket packet) { }
        @Override public void m_6321_(ServerboundSelectTradePacket packet) { }
        @Override public void m_6829_(ServerboundEditBookPacket packet) { }
        @Override public void m_7548_(ServerboundEntityTagQuery packet) { }
        @Override public void m_6780_(ServerboundBlockEntityTagQuery packet) { }
        @Override public void m_7185_(ServerboundMovePlayerPacket packet) { }
        @Override public void m_9774_(double x, double y, double z, float yaw, float pitch) { }
        @Override public void m_9780_(double x, double y, double z, float yaw, float pitch, Set<ClientboundPlayerPositionPacket.RelativeArgument> flags) { }
        @Override public void m_7502_(ServerboundPlayerActionPacket packet) { }
        @Override public void m_6371_(ServerboundUseItemOnPacket packet) { }
        @Override public void m_5760_(ServerboundUseItemPacket packet) { }
        @Override public void m_6936_(ServerboundTeleportToEntityPacket packet) { }
        @Override public void m_7529_(ServerboundResourcePackPacket packet) { }
        @Override public void m_5938_(ServerboundPaddleBoatPacket packet) { }
        @Override public void m_7026_(Component message) { }
        @Override public void m_141995_(Packet<?> packet) { }
        @Override public void m_9831_(Packet<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> listener) { }
        @Override public void m_7798_(ServerboundSetCarriedItemPacket packet) { }
        @Override public void m_7388_(ServerboundChatPacket packet) { }
        @Override public void m_7953_(ServerboundSwingPacket packet) { }
        @Override public void m_5681_(ServerboundPlayerCommandPacket packet) { }
        @Override public void m_6946_(ServerboundInteractPacket packet) { }
        @Override public void m_6272_(ServerboundClientCommandPacket packet) { }
        @Override public void m_7951_(ServerboundContainerClosePacket packet) { }
        @Override public void m_5914_(ServerboundContainerClickPacket packet) { }
        @Override public void m_7191_(ServerboundPlaceRecipePacket packet) { }
        @Override public void m_6557_(ServerboundContainerButtonClickPacket packet) { }
        @Override public void m_5964_(ServerboundSetCreativeModeSlotPacket packet) { }
        @Override public void m_5527_(ServerboundSignUpdatePacket packet) { }
        @Override public void m_5683_(ServerboundKeepAlivePacket packet) { }
        @Override public void m_6828_(ServerboundPlayerAbilitiesPacket packet) { }
        @Override public void m_5617_(ServerboundClientInformationPacket packet) { }
        @Override public void m_7423_(ServerboundCustomPayloadPacket packet) { }
        @Override public void m_7477_(ServerboundChangeDifficultyPacket packet) { }
        @Override public void m_7728_(ServerboundLockDifficultyPacket packet) { }
    }
}
