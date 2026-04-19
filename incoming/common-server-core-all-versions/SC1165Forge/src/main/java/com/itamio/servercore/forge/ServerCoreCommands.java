package com.itamio.servercore.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;

public final class ServerCoreCommands {
   private ServerCoreCommands() {
   }

   public static void register(CommandDispatcher<CommandSource> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.func_197057_a("tpa")
               .requires(source -> source.func_197022_f() instanceof ServerPlayerEntity))
            .then(
               Commands.func_197056_a("player", EntityArgument.func_197096_c())
                  .executes(ctx -> handleTpa(ctx, EntityArgument.func_197089_d(ctx, "player"), TeleportRequestService.RequestType.TPA))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.func_197057_a("tpahere")
               .requires(source -> source.func_197022_f() instanceof ServerPlayerEntity))
            .then(
               Commands.func_197056_a("player", EntityArgument.func_197096_c())
                  .executes(ctx -> handleTpa(ctx, EntityArgument.func_197089_d(ctx, "player"), TeleportRequestService.RequestType.TPAHERE))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.func_197057_a("tpaccept")
                  .requires(source -> source.func_197022_f() instanceof ServerPlayerEntity))
               .executes(ctx -> handleTpaccept(ctx, null)))
            .then(Commands.func_197056_a("player", StringArgumentType.word()).executes(ctx -> handleTpaccept(ctx, StringArgumentType.getString(ctx, "player"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.func_197057_a("tpacceptall")
               .requires(source -> source.func_197022_f() instanceof ServerPlayerEntity))
            .executes(ServerCoreCommands::handleTpacceptAll)
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.func_197057_a("tpadeny")
                  .requires(source -> source.func_197022_f() instanceof ServerPlayerEntity))
               .executes(ctx -> handleTpadeny(ctx, null)))
            .then(Commands.func_197056_a("player", StringArgumentType.word()).executes(ctx -> handleTpadeny(ctx, StringArgumentType.getString(ctx, "player"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.func_197057_a("tpadenyall")
               .requires(source -> source.func_197022_f() instanceof ServerPlayerEntity))
            .executes(ServerCoreCommands::handleTpadenyAll)
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.func_197057_a("tpacancel")
               .requires(source -> source.func_197022_f() instanceof ServerPlayerEntity))
            .then(
               Commands.func_197056_a("player", StringArgumentType.word()).executes(ctx -> handleTpacancel(ctx, StringArgumentType.getString(ctx, "player")))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.func_197057_a("sethome")
               .requires(source -> source.func_197022_f() instanceof ServerPlayerEntity))
            .then(Commands.func_197056_a("name", StringArgumentType.word()).executes(ctx -> handleSetHome(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.func_197057_a("home")
                  .requires(source -> source.func_197022_f() instanceof ServerPlayerEntity))
               .then(Commands.func_197057_a("list").executes(ServerCoreCommands::handleHomeList)))
            .then(Commands.func_197056_a("name", StringArgumentType.word()).executes(ctx -> handleHomeTeleport(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.func_197057_a("delhome")
               .requires(source -> source.func_197022_f() instanceof ServerPlayerEntity))
            .then(Commands.func_197056_a("name", StringArgumentType.word()).executes(ctx -> handleDelHome(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.func_197057_a("rtp")
                     .requires(source -> source.func_197022_f() instanceof ServerPlayerEntity))
                  .then(Commands.func_197057_a("overworld").executes(ctx -> handleRtp(ctx, "minecraft:overworld"))))
               .then(Commands.func_197057_a("nether").executes(ctx -> handleRtp(ctx, "minecraft:the_nether"))))
            .then(Commands.func_197057_a("end").executes(ctx -> handleRtp(ctx, "minecraft:the_end")))
      );
   }

   private static int handleTpa(CommandContext<CommandSource> ctx, ServerPlayerEntity target, TeleportRequestService.RequestType type) {
      ServerPlayerEntity sender = requirePlayer(ctx);
      if (sender == null) {
         return 0;
      } else if (sender == target) {
         MessageUtil.send(sender, "You cannot teleport to yourself.");
         return 0;
      } else {
         UUID senderUuid = PlayerUtil.getUuid(sender);
         UUID targetUuid = PlayerUtil.getUuid(target);
         if (senderUuid == null || targetUuid == null) {
            MessageUtil.send(sender, "Player data unavailable.");
            return 0;
         } else if (senderUuid.equals(targetUuid)) {
            MessageUtil.send(sender, "You cannot teleport to yourself.");
            return 0;
         } else {
            TeleportRequestService.getInstance()
               .upsertRequest(senderUuid, sender.func_146103_bH().getName(), targetUuid, target.func_146103_bH().getName(), type);
            MessageUtil.send(sender, "Teleport request sent to " + target.func_146103_bH().getName() + ".");
            if (type == TeleportRequestService.RequestType.TPA) {
               MessageUtil.send(
                  target,
                  sender.func_146103_bH().getName()
                     + " wants to teleport to you. Use /tpaccept "
                     + sender.func_146103_bH().getName()
                     + " or /tpadeny "
                     + sender.func_146103_bH().getName()
                     + "."
               );
            } else {
               MessageUtil.send(
                  target,
                  sender.func_146103_bH().getName()
                     + " wants you to teleport to them. Use /tpaccept "
                     + sender.func_146103_bH().getName()
                     + " or /tpadeny "
                     + sender.func_146103_bH().getName()
                     + "."
               );
            }

            return 1;
         }
      }
   }

   private static int handleTpaccept(CommandContext<CommandSource> ctx, String requesterName) {
      ServerPlayerEntity target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         TeleportRequestService service = TeleportRequestService.getInstance();
         TeleportRequestService.TeleportRequest request;
         if (requesterName != null && !requesterName.isEmpty()) {
            request = service.popIncoming(PlayerUtil.getUuid(target), requesterName);
         } else {
            request = service.popAllIncoming(PlayerUtil.getUuid(target)).stream().findFirst().orElse(null);
         }

         if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
         } else {
            return completeTeleport(target, request);
         }
      }
   }

   private static int handleTpacceptAll(CommandContext<CommandSource> ctx) {
      ServerPlayerEntity target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         int success = 0;

         for (TeleportRequestService.TeleportRequest request : TeleportRequestService.getInstance().popAllIncoming(PlayerUtil.getUuid(target))) {
            success += completeTeleport(target, request);
         }

         if (success == 0) {
            MessageUtil.send(target, "No pending teleport requests.");
         }

         return success;
      }
   }

   private static int handleTpadeny(CommandContext<CommandSource> ctx, String requesterName) {
      ServerPlayerEntity target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         TeleportRequestService service = TeleportRequestService.getInstance();
         TeleportRequestService.TeleportRequest request;
         if (requesterName != null && !requesterName.isEmpty()) {
            request = service.popIncoming(PlayerUtil.getUuid(target), requesterName);
         } else {
            request = service.popAllIncoming(PlayerUtil.getUuid(target)).stream().findFirst().orElse(null);
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

   private static int handleTpadenyAll(CommandContext<CommandSource> ctx) {
      ServerPlayerEntity target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         int denied = TeleportRequestService.getInstance().popAllIncoming(PlayerUtil.getUuid(target)).size();
         MessageUtil.send(target, denied == 0 ? "No pending teleport requests." : "Denied " + denied + " requests.");
         return denied;
      }
   }

   private static int handleTpacancel(CommandContext<CommandSource> ctx, String targetNameOrAll) {
      ServerPlayerEntity requester = requirePlayer(ctx);
      if (requester == null) {
         return 0;
      } else {
         int removed = TeleportRequestService.getInstance().cancelOutgoing(PlayerUtil.getUuid(requester), targetNameOrAll);
         MessageUtil.send(requester, removed == 0 ? "No pending outgoing requests." : "Cancelled " + removed + " request(s).");
         return removed;
      }
   }

   private static int completeTeleport(ServerPlayerEntity target, TeleportRequestService.TeleportRequest request) {
      MinecraftServer server = target.func_184102_h();
      if (server == null) {
         MessageUtil.send(target, "Server unavailable.");
         return 0;
      } else {
         ServerPlayerEntity requester = PlayerUtil.getPlayerByUuid(server, request.getRequesterUuid());
         if (requester == null) {
            MessageUtil.send(target, "Requester is offline.");
            return 0;
         } else {
            if (request.getType() == TeleportRequestService.RequestType.TPA) {
               TeleportUtil.teleport(
                  requester,
                  PlayerUtil.getServerWorld(target),
                  PlayerUtil.getX(target),
                  PlayerUtil.getY(target),
                  PlayerUtil.getZ(target),
                  RotationUtil.getYaw(target),
                  RotationUtil.getPitch(target)
               );
               MessageUtil.send(requester, "Teleporting to " + target.func_146103_bH().getName() + ".");
               MessageUtil.send(target, "Accepted teleport request from " + requester.func_146103_bH().getName() + ".");
            } else {
               TeleportUtil.teleport(
                  target,
                  PlayerUtil.getServerWorld(requester),
                  PlayerUtil.getX(requester),
                  PlayerUtil.getY(requester),
                  PlayerUtil.getZ(requester),
                  RotationUtil.getYaw(requester),
                  RotationUtil.getPitch(requester)
               );
               MessageUtil.send(target, "Teleporting to " + requester.func_146103_bH().getName() + ".");
               MessageUtil.send(requester, target.func_146103_bH().getName() + " accepted your request.");
            }

            return 1;
         }
      }
   }

   private static int handleSetHome(CommandContext<CommandSource> ctx, String name) {
      ServerPlayerEntity player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         HomeRecord record = HomeService.getInstance().setHome(((CommandSource)ctx.getSource()).func_197028_i(), player, name);
         if (record == null) {
            MessageUtil.send(player, "Invalid home name.");
            return 0;
         } else {
            MessageUtil.send(player, "Home set: " + record.getName());
            return 1;
         }
      }
   }

   private static int handleHomeList(CommandContext<CommandSource> ctx) {
      ServerPlayerEntity player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         List<HomeRecord> homes = HomeService.getInstance().listHomes(((CommandSource)ctx.getSource()).func_197028_i(), PlayerUtil.getUuid(player));
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

   private static int handleHomeTeleport(CommandContext<CommandSource> ctx, String name) {
      ServerPlayerEntity player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         HomeRecord record = HomeService.getInstance().getHome(((CommandSource)ctx.getSource()).func_197028_i(), PlayerUtil.getUuid(player), name);
         if (record == null) {
            MessageUtil.send(player, "Home not found.");
            return 0;
         } else {
            ServerWorld world = TeleportUtil.resolveWorld(((CommandSource)ctx.getSource()).func_197028_i(), record.getDimension());
            if (world == null) {
               MessageUtil.send(player, "Target dimension is not available.");
               return 0;
            } else {
               TeleportUtil.teleport(player, world, record.getX(), record.getY(), record.getZ(), record.getYaw(), record.getPitch());
               MessageUtil.send(player, "Teleported to home " + record.getName() + ".");
               return 1;
            }
         }
      }
   }

   private static int handleDelHome(CommandContext<CommandSource> ctx, String name) {
      ServerPlayerEntity player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         boolean removed = HomeService.getInstance().deleteHome(((CommandSource)ctx.getSource()).func_197028_i(), PlayerUtil.getUuid(player), name);
         MessageUtil.send(player, removed ? "Home deleted." : "Home not found.");
         return removed ? 1 : 0;
      }
   }

   private static int handleRtp(CommandContext<CommandSource> ctx, String dimensionKey) {
      ServerPlayerEntity player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, dimensionKey);
         MessageUtil.send(player, result.getMessage());
         return result.isSuccess() ? 1 : 0;
      }
   }

   private static ServerPlayerEntity requirePlayer(CommandContext<CommandSource> ctx) {
      if (ctx != null && ctx.getSource() != null) {
         CommandSource source = (CommandSource)ctx.getSource();
         if (source.func_197022_f() instanceof ServerPlayerEntity) {
            return (ServerPlayerEntity)source.func_197022_f();
         } else {
            ServerPlayerEntity player = invokePlayer(source, "getPlayerOrException");
            return player != null ? player : invokePlayer(source, "asPlayer");
         }
      } else {
         return null;
      }
   }

   private static ServerPlayerEntity invokePlayer(CommandSource source, String methodName) {
      try {
         Method method = source.getClass().getMethod(methodName);
         Object result = method.invoke(source);
         return result instanceof ServerPlayerEntity ? (ServerPlayerEntity)result : null;
      } catch (ReflectiveOperationException var4) {
         return null;
      }
   }
}
