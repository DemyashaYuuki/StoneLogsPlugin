package me.demyasha.stonelogs.mining;

import me.demyasha.stonelogs.StoneLogsPlugin;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CustomLogMiningListener implements Listener {

    private final StoneLogsPlugin plugin;
    private final CustomLogMiningManager miningManager;

    public CustomLogMiningListener(StoneLogsPlugin plugin, CustomLogMiningManager miningManager) {
        this.plugin = plugin;
        this.miningManager = miningManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!plugin.isTrackedLog(block.getType())) {
            return;
        }

        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }

        event.setCancelled(true);
        miningManager.start(player, block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        if (!plugin.isTrackedLog(event.getBlock().getType())) {
            return;
        }

        miningManager.abort(event.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        miningManager.abort(event.getPlayer(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        miningManager.abort(event.getPlayer(), false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        miningManager.abort(event.getPlayer(), false);
    }
}
