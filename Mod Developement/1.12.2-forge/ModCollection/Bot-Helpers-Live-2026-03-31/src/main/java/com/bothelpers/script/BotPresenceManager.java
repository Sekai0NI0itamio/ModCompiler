package com.bothelpers.script;

import com.bothelpers.entity.EntityBotHelper;
import com.google.common.base.Charsets;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BotPresenceManager {
    private static final Map<UUID, CompanionPlayer> COMPANIONS = new HashMap<>();

    private BotPresenceManager() {
    }

    public static void tick(EntityBotHelper bot) {
        if (!(bot.world instanceof WorldServer) || bot.isDead) {
            return;
        }

        bot.addTag("player");

        WorldServer world = (WorldServer) bot.world;
        CompanionPlayer companion = COMPANIONS.get(bot.getUniqueID());

        if (companion == null || companion.world != world) {
            if (companion != null) {
                companion.detach();
            }

            companion = new CompanionPlayer(bot, world);
            COMPANIONS.put(bot.getUniqueID(), companion);
        }

        companion.sync(bot);
    }

    public static FakePlayer getActionPlayer(EntityBotHelper bot) {
        if (!(bot.world instanceof WorldServer)) {
            return null;
        }

        tick(bot);
        CompanionPlayer companion = COMPANIONS.get(bot.getUniqueID());
        return companion == null ? null : companion.player;
    }

    public static void release(EntityBotHelper bot) {
        CompanionPlayer companion = COMPANIONS.remove(bot.getUniqueID());
        if (companion != null) {
            companion.detach();
        }
    }

    private static final class CompanionPlayer {
        private final WorldServer world;
        private final FakePlayer player;
        private boolean addedToChunkMap;

        private CompanionPlayer(EntityBotHelper bot, WorldServer world) {
            this.world = world;
            this.player = createPlayer(bot, world);
            this.player.interactionManager.setGameType(GameType.SURVIVAL);
        }

        private void sync(EntityBotHelper bot) {
            this.player.dimension = bot.dimension;
            this.player.copyLocationAndAnglesFrom(bot);
            this.player.setSneaking(false);

            if (!this.addedToChunkMap) {
                this.world.getPlayerChunkMap().addPlayer(this.player);
                this.addedToChunkMap = true;
            } else {
                this.world.getPlayerChunkMap().updateMovingPlayer(this.player);
            }
        }

        private void detach() {
            if (this.addedToChunkMap) {
                this.world.getPlayerChunkMap().removePlayer(this.player);
                this.addedToChunkMap = false;
            }
        }

        private static FakePlayer createPlayer(EntityBotHelper bot, WorldServer world) {
            UUID fakeUuid = UUID.nameUUIDFromBytes(("bothelpers-presence-" + bot.getUniqueID().toString()).getBytes(Charsets.UTF_8));
            String rawName = bot.getName() == null ? "bot" : bot.getName().replace(' ', '_');
            String fakeName = ("[Bot]" + rawName);
            if (fakeName.length() > 16) {
                fakeName = fakeName.substring(0, 16);
            }

            FakePlayer fakePlayer = FakePlayerFactory.get(world, new GameProfile(fakeUuid, fakeName));
            if (fakePlayer.connection == null) {
                new NoOpNetHandlerPlayServer(world, fakePlayer);
            }
            return fakePlayer;
        }
    }

    private static final class NoOpNetHandlerPlayServer extends net.minecraft.network.NetHandlerPlayServer {
        private NoOpNetHandlerPlayServer(WorldServer world, EntityPlayerMP player) {
            super(world.getMinecraftServer(), new NoOpNetworkManager(), player);
        }

        @Override
        public void disconnect(ITextComponent textComponent) {
        }
    }

    private static final class NoOpNetworkManager extends NetworkManager {
        private NoOpNetworkManager() {
            super(EnumPacketDirection.CLIENTBOUND);
        }

        @Override
        public void sendPacket(Packet<?> packetIn) {
        }

        @Override
        public void sendPacket(Packet<?> packetIn, io.netty.util.concurrent.GenericFutureListener<? extends io.netty.util.concurrent.Future<? super Void>> listener, io.netty.util.concurrent.GenericFutureListener<? extends io.netty.util.concurrent.Future<? super Void>>... listeners) {
        }
    }
}
