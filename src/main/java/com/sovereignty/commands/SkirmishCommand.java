package com.sovereignty.commands;

import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.Province;
import com.sovereignty.skirmish.SkirmishManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles the {@code /skirmish} command and its subcommands.
 *
 * <h3>Subcommands</h3>
 * <ul>
 *   <li>{@code declare} — Declare a border skirmish on the chunk you are looking at.</li>
 *   <li>{@code status}  — Check if a skirmish is active in your current chunk.</li>
 * </ul>
 */
public final class SkirmishCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final List<String> SUBCOMMANDS = List.of("declare", "status");

    private final SkirmishManager skirmishManager;
    private final ProvinceManager provinceManager;

    public SkirmishCommand(SkirmishManager skirmishManager, ProvinceManager provinceManager) {
        this.skirmishManager = skirmishManager;
        this.provinceManager = provinceManager;
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
            case "declare" -> handleDeclare(player);
            case "status" -> handleStatus(player);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private boolean handleDeclare(Player player) {
        ChunkPosition pos = playerChunk(player);

        // Find the attacker's province — they must own one
        Optional<Province> attackerProvince = findPlayerProvince(player);
        if (attackerProvince.isEmpty()) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ You must own a province to declare a skirmish.</red>"));
            return true;
        }

        // Delegate to the SkirmishManager which does full validation
        skirmishManager.declareSkirmish(player, pos, attackerProvince.get());
        return true;
    }

    private boolean handleStatus(Player player) {
        ChunkPosition pos = playerChunk(player);
        boolean active = skirmishManager.isSkirmishActive(pos);

        if (active) {
            player.sendMessage(MINI.deserialize(
                    "<red>⚔ A skirmish is <bold>ACTIVE</bold> in this chunk! "
                            + "PvP and protections are disabled.</red>"));
        } else {
            player.sendMessage(MINI.deserialize(
                    "<green>✔ No active skirmish in this chunk.</green>"));
        }
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(MINI.deserialize(
                "\n<red><bold>═══ Skirmish Commands ═══</bold></red>\n"
                        + "<white>/skirmish declare</white> <gray>— Declare a border skirmish on this chunk</gray>\n"
                        + "<white>/skirmish status</white> <gray>— Check for active skirmishes here</gray>\n"
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

    private ChunkPosition playerChunk(Player player) {
        return new ChunkPosition(
                player.getWorld().getName(),
                player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4);
    }

    private Optional<Province> findPlayerProvince(Player player) {
        ChunkPosition pos = playerChunk(player);
        Optional<Province> atChunk = provinceManager.getProvinceAt(pos);
        if (atChunk.isPresent() && atChunk.get().getOwnerUuid().equals(player.getUniqueId())) {
            return atChunk;
        }
        return Optional.empty();
    }
}
