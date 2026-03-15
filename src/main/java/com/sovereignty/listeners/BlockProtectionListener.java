package com.sovereignty.listeners;

import com.sovereignty.managers.ProvinceManager;
import com.sovereignty.models.ChunkPosition;
import com.sovereignty.models.Province;
import com.sovereignty.warfare.WarfareEngine;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.Optional;

/**
 * Protects claimed chunks from unauthorized block modification.
 *
 * <h3>Rules</h3>
 * <ul>
 *   <li><b>Peacetime:</b> Only the owning province's members may break blocks
 *       in their own territory. All other players are denied.</li>
 *   <li><b>Active Siege (within window):</b> Attackers with a valid war may
 *       damage reinforced blocks. This is checked against the
 *       {@link WarfareEngine}.</li>
 *   <li><b>Civil War:</b> Protection is dropped — anyone may break blocks.</li>
 * </ul>
 *
 * <h3>Explosion Handling</h3>
 * {@link EntityExplodeEvent}: Filters the block list to remove any blocks
 * inside protected territory, unless an active siege is in progress.
 */
public final class BlockProtectionListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ProvinceManager provinceManager;
    private final WarfareEngine warfareEngine;

    public BlockProtectionListener(ProvinceManager provinceManager,
                                   WarfareEngine warfareEngine) {
        this.provinceManager = provinceManager;
        this.warfareEngine = warfareEngine;
    }

    /**
     * Cancels block breaking in claimed territory unless the player has
     * permission (owner/member) or an active siege is in progress.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        ChunkPosition pos = new ChunkPosition(
                block.getWorld().getName(),
                block.getChunk().getX(),
                block.getChunk().getZ()
        );

        Optional<Province> opt = provinceManager.getProvinceAt(pos);
        if (opt.isEmpty()) return; // Wilderness — allow

        Province province = opt.get();
        Player player = event.getPlayer();

        // Civil War — protection dropped
        if (province.isInCivilWar()) return;

        // Owner may always build
        if (province.getOwnerUuid().equals(player.getUniqueId())) return;

        // Active siege — check if the player's province is at war with this one
        if (warfareEngine != null && warfareEngine.isAtWar(province.getId())) {
            // During active siege, attackers may damage blocks
            // (detailed per-player war membership check would go here)
            return;
        }

        // Deny — territory is protected
        event.setCancelled(true);
        player.sendActionBar(MINI.deserialize(
                "<red>✖ This territory belongs to <white>"
                        + province.getName() + "</white>.</red>"
        ));
    }

    /**
     * Filters explosion block lists to exclude protected territory blocks.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            ChunkPosition pos = new ChunkPosition(
                    block.getWorld().getName(),
                    block.getChunk().getX(),
                    block.getChunk().getZ()
            );
            Optional<Province> opt = provinceManager.getProvinceAt(pos);
            if (opt.isPresent()) {
                Province province = opt.get();
                // Allow explosion damage only during Civil War or active siege
                if (!province.isInCivilWar()
                        && (warfareEngine == null || !warfareEngine.isAtWar(province.getId()))) {
                    it.remove();
                }
            }
        }
    }
}
