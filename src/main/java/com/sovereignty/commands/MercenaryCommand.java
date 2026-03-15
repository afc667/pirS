package com.sovereignty.commands;

import com.sovereignty.mercenary.MercenaryManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the {@code /mercenary} command and its subcommands.
 *
 * <h3>Subcommands</h3>
 * <ul>
 *   <li>{@code on}      — Toggle mercenary availability on.</li>
 *   <li>{@code off}     — Toggle mercenary availability off.</li>
 *   <li>{@code accept}  — Accept a pending hire contract.</li>
 *   <li>{@code decline} — Decline a pending hire contract.</li>
 * </ul>
 */
public final class MercenaryCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final List<String> SUBCOMMANDS = List.of("on", "off", "accept", "decline");

    private final MercenaryManager mercenaryManager;

    public MercenaryCommand(MercenaryManager mercenaryManager) {
        this.mercenaryManager = mercenaryManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MINI.deserialize("<red>This command can only be used by players.</red>"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "on" -> {
                if (mercenaryManager.isMercenary(player.getUniqueId())) {
                    player.sendMessage(MINI.deserialize(
                            "<gray>You are already in mercenary mode.</gray>"));
                } else {
                    mercenaryManager.toggleMercenary(player);
                }
                yield true;
            }
            case "off" -> {
                if (!mercenaryManager.isMercenary(player.getUniqueId())) {
                    player.sendMessage(MINI.deserialize(
                            "<gray>You are not in mercenary mode.</gray>"));
                } else {
                    mercenaryManager.toggleMercenary(player);
                }
                yield true;
            }
            case "accept" -> {
                mercenaryManager.acceptContract(player);
                yield true;
            }
            case "decline" -> {
                mercenaryManager.declineContract(player);
                yield true;
            }
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private void sendUsage(Player player) {
        boolean isMerc = mercenaryManager.isMercenary(player.getUniqueId());
        player.sendMessage(MINI.deserialize(
                "\n<gold><bold>═══ Mercenary Commands ═══</bold></gold>\n"
                        + "<white>/mercenary on</white> <gray>— Enable mercenary mode (available for hire)</gray>\n"
                        + "<white>/mercenary off</white> <gray>— Disable mercenary mode</gray>\n"
                        + "<white>/mercenary accept</white> <gray>— Accept a pending contract</gray>\n"
                        + "<white>/mercenary decline</white> <gray>— Decline a pending contract</gray>\n"
                        + "\n<gray>Status: " + (isMerc ? "<green>Available" : "<red>Unavailable") + "</gray>\n"
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
