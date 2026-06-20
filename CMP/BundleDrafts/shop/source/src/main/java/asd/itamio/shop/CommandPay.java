package asd.itamio.shop;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CommandPay extends CommandBase {

    // Stores pending payments: sender UUID -> {target name, amount, timestamp}
    private static final Map<UUID, PendingPayment> pendingPayments = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_MS = 30000; // 30 seconds to confirm

    private static class PendingPayment {
        final String targetName;
        final double amount;
        final long timestamp;

        PendingPayment(String targetName, double amount) {
            this.targetName = targetName;
            this.amount = amount;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CONFIRM_TIMEOUT_MS;
        }
    }

    @Override
    public String getName() {
        return "pay";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/pay <player> <amount>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("\u00a7cOnly players can use this command."));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        UUID senderUuid = player.getUniqueID();

        // Check for pending confirmation
        PendingPayment pending = pendingPayments.get(senderUuid);
        if (pending != null && !pending.isExpired() && args.length == 0) {
            // This shouldn't happen with 0 args, but handle it
            sender.sendMessage(new TextComponentString("\u00a7cUse /pay <player> <amount> or /pay confirm"));
            return;
        }

        // Check if confirming
        if (args.length == 1 && args[0].equalsIgnoreCase("confirm")) {
            if (pending == null || pending.isExpired()) {
                pendingPayments.remove(senderUuid);
                sender.sendMessage(new TextComponentString("\u00a7cNo pending payment to confirm. Use /pay <player> <amount> first."));
                return;
            }

            // Execute the payment
            EconomyData economy = EconomyData.get(player.getEntityWorld());

            if (!economy.subtractBalance(senderUuid, pending.amount)) {
                sender.sendMessage(new TextComponentString("\u00a7cYou don't have enough money. You have $" + String.format("%.2f", economy.getBalance(senderUuid)) + " but need $" + String.format("%.2f", pending.amount) + "."));
                pendingPayments.remove(senderUuid);
                return;
            }

            // Resolve target UUID
            UUID targetUuid = resolveTargetUuid(server, economy, pending.targetName);
            if (targetUuid == null) {
                // Refund
                economy.addBalance(senderUuid, pending.amount);
                sender.sendMessage(new TextComponentString("\u00a7cCould not find player: " + pending.targetName));
                pendingPayments.remove(senderUuid);
                return;
            }

            economy.addBalance(targetUuid, pending.amount);
            pendingPayments.remove(senderUuid);

            sender.sendMessage(new TextComponentString("\u00a7aPaid $" + String.format("%.2f", pending.amount) + " to " + pending.targetName + "!"));
            sender.sendMessage(new TextComponentString("\u00a77Your balance: $" + String.format("%.2f", economy.getBalance(senderUuid))));

            // Notify target if online
            EntityPlayerMP targetPlayer = server.getPlayerList().getPlayerByUsername(pending.targetName);
            if (targetPlayer != null) {
                targetPlayer.sendMessage(new TextComponentString("\u00a7aYou received $" + String.format("%.2f", pending.amount) + " from " + player.getName() + "!"));
                targetPlayer.sendMessage(new TextComponentString("\u00a77Your balance: $" + String.format("%.2f", economy.getBalance(targetUuid))));
            }

            return;
        }

        // New payment request
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("\u00a7cUsage: /pay <player> <amount>"));
            return;
        }

        String targetName = args[0];
        double amount;

        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(new TextComponentString("\u00a7cInvalid amount: " + args[1]));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(new TextComponentString("\u00a7cAmount must be greater than 0."));
            return;
        }

        // Can't pay yourself unless OP or creative
        if (targetName.equalsIgnoreCase(player.getName())) {
            boolean isOp = player.canUseCommand(2, "pay");
            boolean isCreative = player.isCreative();
            if (!isOp && !isCreative) {
                sender.sendMessage(new TextComponentString("\u00a7cYou can't pay yourself. (OP/Creative only)"));
                return;
            }
        }

        // Validate target exists
        EconomyData economy = EconomyData.get(player.getEntityWorld());
        UUID targetUuid = resolveTargetUuid(server, economy, targetName);
        if (targetUuid == null) {
            sender.sendMessage(new TextComponentString("\u00a7cPlayer not found: " + targetName));
            return;
        }

        // Check balance
        double balance = economy.getBalance(senderUuid);
        if (balance < amount) {
            sender.sendMessage(new TextComponentString("\u00a7cYou don't have enough money. You have $" + String.format("%.2f", balance) + " but need $" + String.format("%.2f", amount) + "."));
            return;
        }

        // Store pending payment
        pendingPayments.put(senderUuid, new PendingPayment(targetName, amount));

        sender.sendMessage(new TextComponentString("\u00a7eConfirm payment of $" + String.format("%.2f", amount) + " to " + targetName + "?"));
        sender.sendMessage(new TextComponentString("\u00a7aType \u00a7f/pay confirm \u00a7ato confirm, or wait 30 seconds to cancel."));
    }

    private UUID resolveTargetUuid(MinecraftServer server, EconomyData economy, String name) {
        // First check online players
        EntityPlayerMP onlinePlayer = server.getPlayerList().getPlayerByUsername(name);
        if (onlinePlayer != null) {
            economy.registerPlayer(name, onlinePlayer.getUniqueID());
            return onlinePlayer.getUniqueID();
        }

        // Check stored name map (for offline players who have played before)
        return economy.getUuidByName(name);
    }
}
