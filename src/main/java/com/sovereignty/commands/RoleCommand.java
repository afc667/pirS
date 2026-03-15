package com.sovereignty.commands;

import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.Province;
import com.sovereignty.models.enums.CouncilRole;
import com.sovereignty.roles.RoleManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles the {@code /role} command and its subcommands.
 *
 * <h3>Subcommands</h3>
 * <ul>
 *   <li>{@code assign <player> <marshal|chancellor|steward>} — Assign a council role.</li>
 *   <li>{@code revoke <player>}                              — Remove a player's role.</li>
 *   <li>{@code info}                                         — View your province's council roles.</li>
 * </ul>
 */
public final class RoleCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final List<String> SUBCOMMANDS = List.of("assign", "revoke", "info");
    private static final List<String> ROLE_NAMES = List.of("marshal", "chancellor", "steward");

    private final RoleManager roleManager;
    private final ProvinceManager provinceManager;

    public RoleCommand(RoleManager roleManager, ProvinceManager provinceManager) {
        this.roleManager = roleManager;
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
            case "assign" -> handleAssign(player, args);
            case "revoke" -> handleRevoke(player, args);
            case "info" -> handleInfo(player);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private boolean handleAssign(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(MINI.deserialize(
                    "<red>Usage: /role assign <player> <marshal|chancellor|steward></red>"));
            return true;
        }

        Optional<Province> province = findPlayerProvince(player);
        if (province.isEmpty()) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ You must be in your own province to assign roles.</red>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ Player <white>" + args[1] + "</white> is not online.</red>"));
            return true;
        }

        CouncilRole role;
        try {
            role = CouncilRole.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ Invalid role. Choose: marshal, chancellor, or steward.</red>"));
            return true;
        }

        boolean success = roleManager.assignRole(
                target.getUniqueId(), province.get().getId(), role);
        if (success) {
            player.sendMessage(MINI.deserialize(
                    "<green>✔ <white>" + target.getName() + "</white> assigned as <white>"
                            + role.name() + "</white>.</green>"));
            target.sendMessage(MINI.deserialize(
                    "<gold>👑 You have been assigned the <white>"
                            + role.name() + "</white> role!</gold>"));
        } else {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ Could not assign role — the slot may already be taken.</red>"));
        }
        return true;
    }

    private boolean handleRevoke(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MINI.deserialize(
                    "<red>Usage: /role revoke <player></red>"));
            return true;
        }

        Optional<Province> province = findPlayerProvince(player);
        if (province.isEmpty()) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ You must be in your own province to revoke roles.</red>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ Player <white>" + args[1] + "</white> is not online.</red>"));
            return true;
        }

        roleManager.revokeRole(target.getUniqueId(), province.get().getId());
        player.sendMessage(MINI.deserialize(
                "<green>✔ <white>" + target.getName() + "</white>'s council role has been revoked.</green>"));
        target.sendMessage(MINI.deserialize(
                "<gray>Your council role has been revoked.</gray>"));
        return true;
    }

    private boolean handleInfo(Player player) {
        Optional<Province> province = findPlayerProvince(player);
        if (province.isEmpty()) {
            player.sendMessage(MINI.deserialize(
                    "<red>✖ Stand in a province to view its council.</red>"));
            return true;
        }

        long id = province.get().getId();
        UUID marshal = roleManager.getMarshal(id);
        UUID chancellor = roleManager.getChancellor(id);
        UUID steward = roleManager.getSteward(id);

        player.sendMessage(MINI.deserialize(
                "\n<gradient:#FFD700:#FFA500><bold>═══ Council Roles ═══</bold></gradient>\n"
                        + "<white>Marshal:</white> <gray>" + nameOrEmpty(marshal) + "</gray>\n"
                        + "<white>Chancellor:</white> <gray>" + nameOrEmpty(chancellor) + "</gray>\n"
                        + "<white>Steward:</white> <gray>" + nameOrEmpty(steward) + "</gray>\n"
        ));
        return true;
    }

    private void sendUsage(Player player) {
        player.sendMessage(MINI.deserialize(
                "\n<gradient:#FFD700:#FFA500><bold>═══ Role Commands ═══</bold></gradient>\n"
                        + "<white>/role assign <player> <role></white> <gray>— Assign a council role</gray>\n"
                        + "<white>/role revoke <player></white> <gray>— Revoke a player's role</gray>\n"
                        + "<white>/role info</white> <gray>— View current council assignments</gray>\n"
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && ("assign".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0]))) {
            return null; // Return null for default player name completion
        }
        if (args.length == 3 && "assign".equalsIgnoreCase(args[0])) {
            return ROLE_NAMES.stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private Optional<Province> findPlayerProvince(Player player) {
        ChunkPosition pos = new ChunkPosition(
                player.getWorld().getName(),
                player.getLocation().getBlockX() >> 4,
                player.getLocation().getBlockZ() >> 4);
        Optional<Province> atChunk = provinceManager.getProvinceAt(pos);
        if (atChunk.isPresent() && atChunk.get().getOwnerUuid().equals(player.getUniqueId())) {
            return atChunk;
        }
        return Optional.empty();
    }

    private String nameOrEmpty(UUID uuid) {
        if (uuid == null) return "(vacant)";
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? p.getName() : "(offline)";
    }
}
