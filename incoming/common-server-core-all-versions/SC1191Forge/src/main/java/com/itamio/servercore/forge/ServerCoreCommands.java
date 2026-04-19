package com.itamio.servercore.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class ServerCoreCommands {
   private ServerCoreCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("tpa").requires(source -> source.m_81373_() instanceof ServerPlayer))
            .then(
               Commands.m_82129_("player", EntityArgument.m_91466_())
                  .executes(ctx -> handleTpa(ctx, EntityArgument.m_91474_(ctx, "player"), TeleportRequestService.RequestType.TPA))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("tpahere").requires(source -> source.m_81373_() instanceof ServerPlayer))
            .then(
               Commands.m_82129_("player", EntityArgument.m_91466_())
                  .executes(ctx -> handleTpa(ctx, EntityArgument.m_91474_(ctx, "player"), TeleportRequestService.RequestType.TPAHERE))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("tpaccept")
                  .requires(source -> source.m_81373_() instanceof ServerPlayer))
               .executes(ctx -> handleTpaccept(ctx, null)))
            .then(Commands.m_82129_("player", StringArgumentType.word()).executes(ctx -> handleTpaccept(ctx, StringArgumentType.getString(ctx, "player"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("tpacceptall").requires(source -> source.m_81373_() instanceof ServerPlayer))
            .executes(ServerCoreCommands::handleTpacceptAll)
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("tpadeny")
                  .requires(source -> source.m_81373_() instanceof ServerPlayer))
               .executes(ctx -> handleTpadeny(ctx, null)))
            .then(Commands.m_82129_("player", StringArgumentType.word()).executes(ctx -> handleTpadeny(ctx, StringArgumentType.getString(ctx, "player"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("tpadenyall").requires(source -> source.m_81373_() instanceof ServerPlayer))
            .executes(ServerCoreCommands::handleTpadenyAll)
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("tpacancel").requires(source -> source.m_81373_() instanceof ServerPlayer))
            .then(Commands.m_82129_("player", StringArgumentType.word()).executes(ctx -> handleTpacancel(ctx, StringArgumentType.getString(ctx, "player"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("sethome").requires(source -> source.m_81373_() instanceof ServerPlayer))
            .then(Commands.m_82129_("name", StringArgumentType.word()).executes(ctx -> handleSetHome(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("home")
                  .requires(source -> source.m_81373_() instanceof ServerPlayer))
               .then(Commands.m_82127_("list").executes(ServerCoreCommands::handleHomeList)))
            .then(Commands.m_82129_("name", StringArgumentType.word()).executes(ctx -> handleHomeTeleport(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("delhome").requires(source -> source.m_81373_() instanceof ServerPlayer))
            .then(Commands.m_82129_("name", StringArgumentType.word()).executes(ctx -> handleDelHome(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.m_82127_("rtp")
                     .requires(source -> source.m_81373_() instanceof ServerPlayer))
                  .then(Commands.m_82127_("overworld").executes(ctx -> handleRtp(ctx, Level.f_46428_.m_135782_().toString()))))
               .then(Commands.m_82127_("nether").executes(ctx -> handleRtp(ctx, Level.f_46429_.m_135782_().toString()))))
            .then(Commands.m_82127_("end").executes(ctx -> handleRtp(ctx, Level.f_46430_.m_135782_().toString())))
      );
   }

   private static int handleTpa(CommandContext<CommandSourceStack> ctx, ServerPlayer target, TeleportRequestService.RequestType type) {
      ServerPlayer sender = requirePlayer(ctx);
      if (sender == null) {
         return 0;
      } else if (sender.m_20148_().equals(target.m_20148_())) {
         MessageUtil.send(sender, "You cannot teleport to yourself.");
         return 0;
      } else {
         TeleportRequestService.getInstance()
            .upsertRequest(sender.m_20148_(), sender.m_36316_().getName(), target.m_20148_(), target.m_36316_().getName(), type);
         MessageUtil.send(sender, "Teleport request sent to " + target.m_36316_().getName() + ".");
         if (type == TeleportRequestService.RequestType.TPA) {
            MessageUtil.send(
               target,
               sender.m_36316_().getName()
                  + " wants to teleport to you. Use /tpaccept "
                  + sender.m_36316_().getName()
                  + " or /tpadeny "
                  + sender.m_36316_().getName()
                  + "."
            );
         } else {
            MessageUtil.send(
               target,
               sender.m_36316_().getName()
                  + " wants you to teleport to them. Use /tpaccept "
                  + sender.m_36316_().getName()
                  + " or /tpadeny "
                  + sender.m_36316_().getName()
                  + "."
            );
         }

         return 1;
      }
   }

   private static int handleTpaccept(CommandContext<CommandSourceStack> ctx, String requesterName) {
      ServerPlayer target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         TeleportRequestService service = TeleportRequestService.getInstance();
         TeleportRequestService.TeleportRequest request;
         if (requesterName != null && !requesterName.isEmpty()) {
            request = service.popIncoming(target.m_20148_(), requesterName);
         } else {
            request = service.popAllIncoming(target.m_20148_()).stream().findFirst().orElse(null);
         }

         if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
         } else {
            return completeTeleport(target, request);
         }
      }
   }

   private static int handleTpacceptAll(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         int success = 0;

         for (TeleportRequestService.TeleportRequest request : TeleportRequestService.getInstance().popAllIncoming(target.m_20148_())) {
            success += completeTeleport(target, request);
         }

         if (success == 0) {
            MessageUtil.send(target, "No pending teleport requests.");
         }

         return success;
      }
   }

   private static int handleTpadeny(CommandContext<CommandSourceStack> ctx, String requesterName) {
      ServerPlayer target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         TeleportRequestService service = TeleportRequestService.getInstance();
         TeleportRequestService.TeleportRequest request;
         if (requesterName != null && !requesterName.isEmpty()) {
            request = service.popIncoming(target.m_20148_(), requesterName);
         } else {
            request = service.popAllIncoming(target.m_20148_()).stream().findFirst().orElse(null);
         }

         if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
         } else {
            MessageUtil.send(target, "Teleport request denied.");
            return 1;
         }
      }
   }

   private static int handleTpadenyAll(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         int denied = TeleportRequestService.getInstance().popAllIncoming(target.m_20148_()).size();
         MessageUtil.send(target, denied == 0 ? "No pending teleport requests." : "Denied " + denied + " requests.");
         return denied;
      }
   }

   private static int handleTpacancel(CommandContext<CommandSourceStack> ctx, String targetNameOrAll) {
      ServerPlayer requester = requirePlayer(ctx);
      if (requester == null) {
         return 0;
      } else {
         int removed = TeleportRequestService.getInstance().cancelOutgoing(requester.m_20148_(), targetNameOrAll);
         MessageUtil.send(requester, removed == 0 ? "No pending outgoing requests." : "Cancelled " + removed + " request(s).");
         return removed;
      }
   }

   private static int completeTeleport(ServerPlayer target, TeleportRequestService.TeleportRequest request) {
      MinecraftServer server = target.m_20194_();
      if (server == null) {
         MessageUtil.send(target, "Server unavailable.");
         return 0;
      } else {
         ServerPlayer requester = server.m_6846_().m_11259_(request.getRequesterUuid());
         if (requester == null) {
            MessageUtil.send(target, "Requester is offline.");
            return 0;
         } else {
            ServerLevel targetLevel = target.m_9236_();
            ServerLevel requesterLevel = requester.m_9236_();
            if (request.getType() == TeleportRequestService.RequestType.TPA) {
               TeleportUtil.teleport(requester, targetLevel, target.m_20185_(), target.m_20186_(), target.m_20189_(), target.m_146908_(), target.m_146909_());
               MessageUtil.send(requester, "Teleporting to " + target.m_36316_().getName() + ".");
               MessageUtil.send(target, "Accepted teleport request from " + requester.m_36316_().getName() + ".");
            } else {
               TeleportUtil.teleport(
                  target, requesterLevel, requester.m_20185_(), requester.m_20186_(), requester.m_20189_(), requester.m_146908_(), requester.m_146909_()
               );
               MessageUtil.send(target, "Teleporting to " + requester.m_36316_().getName() + ".");
               MessageUtil.send(requester, target.m_36316_().getName() + " accepted your request.");
            }

            return 1;
         }
      }
   }

   private static int handleSetHome(CommandContext<CommandSourceStack> ctx, String name) {
      ServerPlayer player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         HomeRecord record = HomeService.getInstance().setHome(((CommandSourceStack)ctx.getSource()).m_81377_(), player, name);
         if (record == null) {
            MessageUtil.send(player, "Invalid home name.");
            return 0;
         } else {
            MessageUtil.send(player, "Home set: " + record.getName());
            return 1;
         }
      }
   }

   private static int handleHomeList(CommandContext<CommandSourceStack> ctx) {
      ServerPlayer player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         List<HomeRecord> homes = HomeService.getInstance().listHomes(((CommandSourceStack)ctx.getSource()).m_81377_(), player.m_20148_());
         if (homes.isEmpty()) {
            MessageUtil.send(player, "You have no homes.");
            return 0;
         } else {
            StringBuilder builder = new StringBuilder("Homes: ");

            for (int i = 0; i < homes.size(); i++) {
               if (i > 0) {
                  builder.append(", ");
               }

               builder.append(homes.get(i).getName());
            }

            MessageUtil.send(player, builder.toString());
            return homes.size();
         }
      }
   }

   private static int handleHomeTeleport(CommandContext<CommandSourceStack> ctx, String name) {
      ServerPlayer player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         HomeRecord record = HomeService.getInstance().getHome(((CommandSourceStack)ctx.getSource()).m_81377_(), player.m_20148_(), name);
         if (record == null) {
            MessageUtil.send(player, "Home not found.");
            return 0;
         } else {
            ServerLevel level = TeleportUtil.resolveLevel(((CommandSourceStack)ctx.getSource()).m_81377_(), record.getDimension());
            if (level == null) {
               MessageUtil.send(player, "Target dimension is not available.");
               return 0;
            } else {
               TeleportUtil.teleport(player, level, record.getX(), record.getY(), record.getZ(), record.getYaw(), record.getPitch());
               MessageUtil.send(player, "Teleported to home " + record.getName() + ".");
               return 1;
            }
         }
      }
   }

   private static int handleDelHome(CommandContext<CommandSourceStack> ctx, String name) {
      ServerPlayer player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         boolean removed = HomeService.getInstance().deleteHome(((CommandSourceStack)ctx.getSource()).m_81377_(), player.m_20148_(), name);
         MessageUtil.send(player, removed ? "Home deleted." : "Home not found.");
         return removed ? 1 : 0;
      }
   }

   private static int handleRtp(CommandContext<CommandSourceStack> ctx, String dimensionKey) {
      ServerPlayer player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, dimensionKey);
         MessageUtil.send(player, result.getMessage());
         return result.isSuccess() ? 1 : 0;
      }
   }

   private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx) {
      try {
         return ((CommandSourceStack)ctx.getSource()).m_81375_();
      } catch (CommandSyntaxException var2) {
         return null;
      }
   }
}
