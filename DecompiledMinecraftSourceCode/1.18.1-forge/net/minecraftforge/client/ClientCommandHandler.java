/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.command.CommandHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;

public class ClientCommandHandler
{
    private static final Logger LOGGER = LogManager.getLogger();
    private static CommandDispatcher<CommandSourceStack> commands = null;

    public static void init()
    {
        MinecraftForge.EVENT_BUS.addListener(ClientCommandHandler::handleClientPlayerLogin);
    }

    private static void handleClientPlayerLogin(ClientPlayerNetworkEvent.LoggedInEvent event)
    {
        // some custom server implementations do not send ClientboundCommandsPacket, provide a fallback
        var suggestionDispatcher = mergeServerCommands(new CommandDispatcher<>());
        if (event.getConnection().m_129538_() instanceof ClientPacketListener listener)
        {
            // Must set this, so that suggestions for client-only commands work, if server never sends commands packet
            listener.f_104899_ = suggestionDispatcher;
        }
    }

    /*
     * For internal use
     *
     * Merges command dispatcher use for suggestions to the command dispatcher used for client commands so they can be sent to the server, and vice versa so client commands appear
     * with server commands in suggestions
     */
    public static CommandDispatcher<SharedSuggestionProvider> mergeServerCommands(CommandDispatcher<SharedSuggestionProvider> serverCommands)
    {
        CommandDispatcher<CommandSourceStack> commandsTemp = new CommandDispatcher<>();
        MinecraftForge.EVENT_BUS.post(new RegisterClientCommandsEvent(commandsTemp));

        // Copies the client commands into another RootCommandNode so that redirects can't be used with server commands
        commands = new CommandDispatcher<>();
        copy(commandsTemp.getRoot(), commands.getRoot());

        // Copies the server commands into another RootCommandNode so that redirects can't be used with client commands
        RootCommandNode<SharedSuggestionProvider> serverCommandsRoot = serverCommands.getRoot();
        CommandDispatcher<SharedSuggestionProvider> newServerCommands = new CommandDispatcher<>();
        copy(serverCommandsRoot, newServerCommands.getRoot());

        // Copies the server side commands into a temporary server side commands root node to be used later without the client commands
        RootCommandNode<SharedSuggestionProvider> serverCommandsCopy = new RootCommandNode<>();
        CommandHelper.mergeCommandNode(newServerCommands.getRoot(), serverCommandsCopy, new IdentityHashMap<>(),
                Minecraft.m_91087_().m_91403_().m_105137_(), (context) -> 0, (suggestions) -> null);

        // Copies the client side commands into the server side commands to be used for suggestions
        CommandHelper.mergeCommandNode(commands.getRoot(), newServerCommands.getRoot(), new IdentityHashMap<>(), getSource(), (context) -> 0, (suggestions) -> {
            SuggestionProvider<SharedSuggestionProvider> suggestionProvider = SuggestionProviders
                    .m_121664_((SuggestionProvider<SharedSuggestionProvider>) (SuggestionProvider<?>) suggestions);
            if (suggestionProvider == SuggestionProviders.f_121641_)
            {
                suggestionProvider = (context, builder) -> {
                    ClientCommandSourceStack source = getSource();
                    StringReader reader = new StringReader(context.getInput());
                    if (reader.canRead() && reader.peek() == '/')
                    {
                        reader.skip();
                    }

                    ParseResults<CommandSourceStack> parse = commands.parse(reader, source);
                    return commands.getCompletionSuggestions(parse);
                };
            }
            return suggestionProvider;
        });

        // Copies the server side commands into the client side commands so that they can be sent to the server as a chat message
        CommandHelper.mergeCommandNode(serverCommandsCopy, commands.getRoot(), new IdentityHashMap<>(), Minecraft.m_91087_().m_91403_().m_105137_(),
                (context) -> {
                    Minecraft.m_91087_().f_91074_.m_108739_((context.getInput().startsWith("/") ? "" : "/") + context.getInput());
                    return 0;
                }, (suggestions) -> null);
        return newServerCommands;
    }

    /**
     * @return The command dispatcher for client side commands
     */
    public static CommandDispatcher<CommandSourceStack> getDispatcher()
    {
        return commands;
    }

    /**
     * @return A {@link ClientCommandSourceStack} for the player in the current client
     */
    public static ClientCommandSourceStack getSource()
    {
        LocalPlayer player = Minecraft.m_91087_().f_91074_;
        return new ClientCommandSourceStack(player, player.m_20182_(), player.m_20155_(), player.m_8088_(),
                player.m_7755_().getString(), player.m_5446_(), player);
    }

    /**
     *
     * Creates a deep copy of the sourceNode while keeping the redirects referring to the old command tree
     *
     * @param sourceNode
     *            the original
     * @param resultNode
     *            the result
     */
    private static <S> void copy(CommandNode<S> sourceNode, CommandNode<S> resultNode)
    {
        Map<CommandNode<S>, CommandNode<S>> newNodes = new IdentityHashMap<>();
        newNodes.put(sourceNode, resultNode);
        for (CommandNode<S> child : sourceNode.getChildren())
        {
            CommandNode<S> copy = newNodes.computeIfAbsent(child, innerChild ->
            {
                ArgumentBuilder<S, ?> builder = innerChild.createBuilder();
                CommandNode<S> innerCopy = builder.build();
                copy(innerChild, innerCopy);
                return innerCopy;
            });
            resultNode.addChild(copy);
        }
    }

    /**
     * Always try to execute the cached parsing of client message as a command. Requires that the execute field of the commands to be set to send to server so that they aren't
     * treated as client command's that do nothing.
     *
     * {@link net.minecraft.commands.Commands#performCommand(CommandSourceStack, String)} for reference
     *
     * @param sendMessage
     *            the chat message
     * @return false leaves the message to be sent to the server, true means it should be caught before {@link LocalPlayer#chat(String)}
     */
    public static boolean sendMessage(String sendMessage)
    {
        StringReader reader = new StringReader(sendMessage);

        if (!reader.canRead() || reader.read() != '/')
        {
            return false;
        }

        ClientCommandSourceStack source = getSource();

        try
        {
            commands.execute(reader, source);
        }
        catch (CommandRuntimeException execution)// Probably thrown by the command
        {
            Minecraft.m_91087_().f_91074_.m_6352_(new TextComponent("").m_7220_(execution.m_79226_()).m_130940_(ChatFormatting.RED), Util.f_137441_);
        }
        catch (CommandSyntaxException syntax)// Usually thrown by the CommandDispatcher
        {
            if (syntax.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand() || syntax.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument())
            {
                // in case of unknown command, let the server try and handle it
                return false;
            }
            Minecraft.m_91087_().f_91074_.m_6352_(
                    new TextComponent("").m_7220_(ComponentUtils.m_130729_(syntax.getRawMessage())).m_130940_(ChatFormatting.RED), Util.f_137441_);
            if (syntax.getInput() != null && syntax.getCursor() >= 0)
            {
                int position = Math.min(syntax.getInput().length(), syntax.getCursor());
                MutableComponent details = new TextComponent("")
                        .m_130940_(ChatFormatting.GRAY)
                        .m_130938_((style) -> style
                                .m_131142_(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, reader.getString())));
                if (position > 10)
                {
                    details.m_130946_("...");
                }
                details.m_130946_(syntax.getInput().substring(Math.max(0, position - 10), position));
                if (position < syntax.getInput().length())
                {
                    details.m_7220_(new TextComponent(syntax.getInput().substring(position)).m_130944_(ChatFormatting.RED, ChatFormatting.UNDERLINE));
                }
                details.m_7220_(new TranslatableComponent("command.context.here").m_130944_(ChatFormatting.RED, ChatFormatting.ITALIC));
                Minecraft.m_91087_().f_91074_.m_6352_(new TextComponent("").m_7220_(details).m_130940_(ChatFormatting.RED), Util.f_137441_);
            }
        }
        catch (Exception generic)// Probably thrown by the command
        {
            TextComponent message = new TextComponent(generic.getMessage() == null ? generic.getClass().getName() : generic.getMessage());
            Minecraft.m_91087_().f_91074_.m_6352_(new TranslatableComponent("command.failed")
                    .m_130940_(ChatFormatting.RED)
                    .m_130938_((style) -> style
                            .m_131144_(new HoverEvent(HoverEvent.Action.f_130831_, message))),
                    Util.f_137441_);
            LOGGER.error("Error executing client command \"{}\"", sendMessage, generic);
        }
        return true;
    }
}
