/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.common.util;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.mojang.authlib.GameProfile;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketDirection;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.stats.Stat;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.Set;
import java.util.UUID;

//Preliminary, simple Fake Player class
public class FakePlayer extends ServerPlayerEntity
{
    public FakePlayer(ServerWorld world, GameProfile name)
    {
        super(world.func_73046_m(), world, name, new PlayerInteractionManager(world));
        this.field_71135_a = new FakePlayerNetHandler(world.func_73046_m(), this);
    }

    @Override public Vector3d func_213303_ch(){ return new Vector3d(0, 0, 0); }
    @Override public BlockPos func_233580_cy_(){ return BlockPos.field_177992_a; }
    @Override public void func_146105_b(ITextComponent chatComponent, boolean actionBar){}
    @Override public void func_145747_a(ITextComponent component, UUID senderUUID) {}
    @Override public void func_71064_a(Stat par1StatBase, int par2){}
    //@Override public void openGui(Object mod, int modGuiId, World world, int x, int y, int z){}
    @Override public boolean func_180431_b(DamageSource source){ return true; }
    @Override public boolean func_96122_a(PlayerEntity player){ return false; }
    @Override public void func_70645_a(DamageSource source){ return; }
    @Override public void func_70071_h_(){ return; }
    @Override public void func_147100_a(CClientSettingsPacket pkt){ return; }
    @Override @Nullable public MinecraftServer func_184102_h() { return ServerLifecycleHooks.getCurrentServer(); }

    @ParametersAreNonnullByDefault
    private static class FakePlayerNetHandler extends ServerPlayNetHandler {
        private static final NetworkManager DUMMY_NETWORK_MANAGER = new NetworkManager(PacketDirection.CLIENTBOUND);

        public FakePlayerNetHandler(MinecraftServer server, ServerPlayerEntity player) {
            super(server, DUMMY_NETWORK_MANAGER, player);
        }

        @Override public void func_73660_a() { }
        @Override public void func_184342_d() { }
        @Override public void func_194028_b(ITextComponent message) { }
        @Override public void func_147358_a(CInputPacket packet) { }
        @Override public void func_184338_a(CMoveVehiclePacket packet) { }
        @Override public void func_184339_a(CConfirmTeleportPacket packet) { }
        @Override public void func_191984_a(CMarkRecipeSeenPacket packet) { }
        @Override public void func_241831_a(CUpdateRecipeBookStatusPacket packet) { }
        @Override public void func_194027_a(CSeenAdvancementsPacket packet) { }
        @Override public void func_195518_a(CTabCompletePacket packet) { }
        @Override public void func_210153_a(CUpdateCommandBlockPacket packet) { }
        @Override public void func_210158_a(CUpdateMinecartCommandBlockPacket packet) { }
        @Override public void func_210152_a(CPickItemPacket packet) { }
        @Override public void func_210155_a(CRenameItemPacket packet) { }
        @Override public void func_210154_a(CUpdateBeaconPacket packet) { }
        @Override public void func_210157_a(CUpdateStructureBlockPacket packet) { }
        @Override public void func_217262_a(CUpdateJigsawBlockPacket packet) { }
        @Override public void func_230549_a_(CJigsawBlockGeneratePacket packet) { }
        @Override public void func_210159_a(CSelectTradePacket packet) { }
        @Override public void func_210156_a(CEditBookPacket packet) { }
        @Override public void func_211526_a(CQueryEntityNBTPacket packet) { }
        @Override public void func_211525_a(CQueryTileEntityNBTPacket packet) { }
        @Override public void func_147347_a(CPlayerPacket packet) { }
        @Override public void func_147364_a(double x, double y, double z, float yaw, float pitch) { }
        @Override public void func_175089_a(double x, double y, double z, float yaw, float pitch, Set<SPlayerPositionLookPacket.Flags> flags) { }
        @Override public void func_147345_a(CPlayerDiggingPacket packet) { }
        @Override public void func_184337_a(CPlayerTryUseItemOnBlockPacket packet) { }
        @Override public void func_147346_a(CPlayerTryUseItemPacket packet) { }
        @Override public void func_175088_a(CSpectatePacket packet) { }
        @Override public void func_175086_a(CResourcePackStatusPacket packet) { }
        @Override public void func_184340_a(CSteerBoatPacket packet) { }
        @Override public void func_147231_a(ITextComponent message) { }
        @Override public void func_147359_a(IPacket<?> packet) { }
        @Override public void func_211148_a(IPacket<?> packet, @Nullable GenericFutureListener<? extends Future<? super Void>> p_211148_2_) { }
        @Override public void func_147355_a(CHeldItemChangePacket packet) { }
        @Override public void func_147354_a(CChatMessagePacket packet) { }
        @Override public void func_175087_a(CAnimateHandPacket packet) { }
        @Override public void func_147357_a(CEntityActionPacket packet) { }
        @Override public void func_147340_a(CUseEntityPacket packet) { }
        @Override public void func_147342_a(CClientStatusPacket packet) { }
        @Override public void func_147356_a(CCloseWindowPacket packet) { }
        @Override public void func_147351_a(CClickWindowPacket packet) { }
        @Override public void func_194308_a(CPlaceRecipePacket packet) { }
        @Override public void func_147338_a(CEnchantItemPacket packet) { }
        @Override public void func_147344_a(CCreativeInventoryActionPacket packet) { }
        @Override public void func_147339_a(CConfirmTransactionPacket packet) { }
        @Override public void func_147343_a(CUpdateSignPacket packet) { }
        @Override public void func_147353_a(CKeepAlivePacket packet) { }
        @Override public void func_147348_a(CPlayerAbilitiesPacket packet) { }
        @Override public void func_147352_a(CClientSettingsPacket packet) { }
        @Override public void func_147349_a(CCustomPayloadPacket packet) { }
        @Override public void func_217263_a(CSetDifficultyPacket packet) { }
        @Override public void func_217261_a(CLockDifficultyPacket packet) { }
    }
}
