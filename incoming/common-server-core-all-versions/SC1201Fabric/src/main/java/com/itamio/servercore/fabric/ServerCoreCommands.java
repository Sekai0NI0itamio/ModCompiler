package com.itamio.servercore.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.class_1937;
import net.minecraft.class_2168;
import net.minecraft.class_2170;
import net.minecraft.class_2186;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;

public final class ServerCoreCommands {
   private ServerCoreCommands() {
   }

   public static void register(CommandDispatcher<class_2168> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("tpa").requires(source -> source.method_9228() instanceof class_3222))
            .then(
               class_2170.method_9244("player", class_2186.method_9305())
                  .executes(ctx -> handleTpa(ctx, class_2186.method_9315(ctx, "player"), TeleportRequestService.RequestType.TPA))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("tpahere").requires(source -> source.method_9228() instanceof class_3222))
            .then(
               class_2170.method_9244("player", class_2186.method_9305())
                  .executes(ctx -> handleTpa(ctx, class_2186.method_9315(ctx, "player"), TeleportRequestService.RequestType.TPAHERE))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("tpaccept")
                  .requires(source -> source.method_9228() instanceof class_3222))
               .executes(ctx -> handleTpaccept(ctx, null)))
            .then(class_2170.method_9244("player", StringArgumentType.word()).executes(ctx -> handleTpaccept(ctx, StringArgumentType.getString(ctx, "player"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("tpacceptall").requires(source -> source.method_9228() instanceof class_3222))
            .executes(ServerCoreCommands::handleTpacceptAll)
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("tpadeny")
                  .requires(source -> source.method_9228() instanceof class_3222))
               .executes(ctx -> handleTpadeny(ctx, null)))
            .then(class_2170.method_9244("player", StringArgumentType.word()).executes(ctx -> handleTpadeny(ctx, StringArgumentType.getString(ctx, "player"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("tpadenyall").requires(source -> source.method_9228() instanceof class_3222))
            .executes(ServerCoreCommands::handleTpadenyAll)
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("tpacancel").requires(source -> source.method_9228() instanceof class_3222))
            .then(
               class_2170.method_9244("player", StringArgumentType.word()).executes(ctx -> handleTpacancel(ctx, StringArgumentType.getString(ctx, "player")))
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("sethome").requires(source -> source.method_9228() instanceof class_3222))
            .then(class_2170.method_9244("name", StringArgumentType.word()).executes(ctx -> handleSetHome(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("home")
                  .requires(source -> source.method_9228() instanceof class_3222))
               .then(class_2170.method_9247("list").executes(ServerCoreCommands::handleHomeList)))
            .then(class_2170.method_9244("name", StringArgumentType.word()).executes(ctx -> handleHomeTeleport(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("delhome").requires(source -> source.method_9228() instanceof class_3222))
            .then(class_2170.method_9244("name", StringArgumentType.word()).executes(ctx -> handleDelHome(ctx, StringArgumentType.getString(ctx, "name"))))
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)class_2170.method_9247("rtp")
                     .requires(source -> source.method_9228() instanceof class_3222))
                  .then(class_2170.method_9247("overworld").executes(ctx -> handleRtp(ctx, class_1937.field_25179.method_29177().toString()))))
               .then(class_2170.method_9247("nether").executes(ctx -> handleRtp(ctx, class_1937.field_25180.method_29177().toString()))))
            .then(class_2170.method_9247("end").executes(ctx -> handleRtp(ctx, class_1937.field_25181.method_29177().toString())))
      );
   }

   private static int handleTpa(CommandContext<class_2168> ctx, class_3222 target, TeleportRequestService.RequestType type) {
      class_3222 sender = requirePlayer(ctx);
      if (sender == null) {
         return 0;
      } else if (sender.method_5667().equals(target.method_5667())) {
         MessageUtil.send(sender, "You cannot teleport to yourself.");
         return 0;
      } else {
         TeleportRequestService.getInstance()
            .upsertRequest(sender.method_5667(), sender.method_7334().getName(), target.method_5667(), target.method_7334().getName(), type);
         MessageUtil.send(sender, "Teleport request sent to " + target.method_7334().getName() + ".");
         if (type == TeleportRequestService.RequestType.TPA) {
            MessageUtil.send(
               target,
               sender.method_7334().getName()
                  + " wants to teleport to you. Use /tpaccept "
                  + sender.method_7334().getName()
                  + " or /tpadeny "
                  + sender.method_7334().getName()
                  + "."
            );
         } else {
            MessageUtil.send(
               target,
               sender.method_7334().getName()
                  + " wants you to teleport to them. Use /tpaccept "
                  + sender.method_7334().getName()
                  + " or /tpadeny "
                  + sender.method_7334().getName()
                  + "."
            );
         }

         return 1;
      }
   }

   private static int handleTpaccept(CommandContext<class_2168> ctx, String requesterName) {
      class_3222 target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         TeleportRequestService service = TeleportRequestService.getInstance();
         TeleportRequestService.TeleportRequest request;
         if (requesterName != null && !requesterName.isEmpty()) {
            request = service.popIncoming(target.method_5667(), requesterName);
         } else {
            request = service.popAllIncoming(target.method_5667()).stream().findFirst().orElse(null);
         }

         if (request == null) {
            MessageUtil.send(target, "No pending teleport request found.");
            return 0;
         } else {
            return completeTeleport(target, request);
         }
      }
   }

   private static int handleTpacceptAll(CommandContext<class_2168> ctx) {
      class_3222 target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         int success = 0;

         for (TeleportRequestService.TeleportRequest request : TeleportRequestService.getInstance().popAllIncoming(target.method_5667())) {
            success += completeTeleport(target, request);
         }

         if (success == 0) {
            MessageUtil.send(target, "No pending teleport requests.");
         }

         return success;
      }
   }

   private static int handleTpadeny(CommandContext<class_2168> ctx, String requesterName) {
      class_3222 target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         TeleportRequestService service = TeleportRequestService.getInstance();
         TeleportRequestService.TeleportRequest request;
         if (requesterName != null && !requesterName.isEmpty()) {
            request = service.popIncoming(target.method_5667(), requesterName);
         } else {
            request = service.popAllIncoming(target.method_5667()).stream().findFirst().orElse(null);
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

   private static int handleTpadenyAll(CommandContext<class_2168> ctx) {
      class_3222 target = requirePlayer(ctx);
      if (target == null) {
         return 0;
      } else {
         int denied = TeleportRequestService.getInstance().popAllIncoming(target.method_5667()).size();
         MessageUtil.send(target, denied == 0 ? "No pending teleport requests." : "Denied " + denied + " requests.");
         return denied;
      }
   }

   private static int handleTpacancel(CommandContext<class_2168> ctx, String targetNameOrAll) {
      class_3222 requester = requirePlayer(ctx);
      if (requester == null) {
         return 0;
      } else {
         int removed = TeleportRequestService.getInstance().cancelOutgoing(requester.method_5667(), targetNameOrAll);
         MessageUtil.send(requester, removed == 0 ? "No pending outgoing requests." : "Cancelled " + removed + " request(s).");
         return removed;
      }
   }

   private static int completeTeleport(class_3222 target, TeleportRequestService.TeleportRequest request) {
      MinecraftServer server = target.method_5682();
      if (server == null) {
         MessageUtil.send(target, "Server unavailable.");
         return 0;
      } else {
         class_3222 requester = server.method_3760().method_14602(request.getRequesterUuid());
         if (requester == null) {
            MessageUtil.send(target, "Requester is offline.");
            return 0;
         } else {
            if (request.getType() == TeleportRequestService.RequestType.TPA) {
               TeleportUtil.teleport(
                  requester,
                  target.method_51469(),
                  target.method_23317(),
                  target.method_23318(),
                  target.method_23321(),
                  target.method_36454(),
                  target.method_36455()
               );
               MessageUtil.send(requester, "Teleporting to " + target.method_7334().getName() + ".");
               MessageUtil.send(target, "Accepted teleport request from " + requester.method_7334().getName() + ".");
            } else {
               TeleportUtil.teleport(
                  target,
                  requester.method_51469(),
                  requester.method_23317(),
                  requester.method_23318(),
                  requester.method_23321(),
                  requester.method_36454(),
                  requester.method_36455()
               );
               MessageUtil.send(target, "Teleporting to " + requester.method_7334().getName() + ".");
               MessageUtil.send(requester, target.method_7334().getName() + " accepted your request.");
            }

            return 1;
         }
      }
   }

   private static int handleSetHome(CommandContext<class_2168> ctx, String name) {
      class_3222 player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         HomeRecord record = HomeService.getInstance().setHome(((class_2168)ctx.getSource()).method_9211(), player, name);
         if (record == null) {
            MessageUtil.send(player, "Invalid home name.");
            return 0;
         } else {
            MessageUtil.send(player, "Home set: " + record.getName());
            return 1;
         }
      }
   }

   private static int handleHomeList(CommandContext<class_2168> ctx) {
      class_3222 player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         List<HomeRecord> homes = HomeService.getInstance().listHomes(((class_2168)ctx.getSource()).method_9211(), player.method_5667());
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

   private static int handleHomeTeleport(CommandContext<class_2168> ctx, String name) {
      class_3222 player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         HomeRecord record = HomeService.getInstance().getHome(((class_2168)ctx.getSource()).method_9211(), player.method_5667(), name);
         if (record == null) {
            MessageUtil.send(player, "Home not found.");
            return 0;
         } else {
            class_3218 world = TeleportUtil.resolveWorld(((class_2168)ctx.getSource()).method_9211(), record.getDimension());
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

   private static int handleDelHome(CommandContext<class_2168> ctx, String name) {
      class_3222 player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         boolean removed = HomeService.getInstance().deleteHome(((class_2168)ctx.getSource()).method_9211(), player.method_5667(), name);
         MessageUtil.send(player, removed ? "Home deleted." : "Home not found.");
         return removed ? 1 : 0;
      }
   }

   private static int handleRtp(CommandContext<class_2168> ctx, String dimensionKey) {
      class_3222 player = requirePlayer(ctx);
      if (player == null) {
         return 0;
      } else {
         RandomTeleportService.RtpResult result = RandomTeleportService.getInstance().teleport(player, dimensionKey);
         MessageUtil.send(player, result.getMessage());
         return result.isSuccess() ? 1 : 0;
      }
   }

   private static class_3222 requirePlayer(CommandContext<class_2168> ctx) {
      try {
         return ((class_2168)ctx.getSource()).method_44023();
      } catch (Exception var2) {
         return null;
      }
   }
}
