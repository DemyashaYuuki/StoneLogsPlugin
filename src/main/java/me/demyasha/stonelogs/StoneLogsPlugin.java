package me.demyasha.stonelogs;

import me.demyasha.stonelogs.mining.CustomLogMiningListener;
import me.demyasha.stonelogs.mining.CustomLogMiningManager;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.plugin.java.JavaPlugin;

public final class StoneLogsPlugin extends JavaPlugin {

    private CustomLogMiningManager miningManager;

    @Override
    public void onEnable() {
        this.miningManager = new CustomLogMiningManager(this);
        getServer().getPluginManager().registerEvents(new CustomLogMiningListener(this, miningManager), this);
        getLogger().info("StoneLogs enabled.");
    }

    @Override
    public void onDisable() {
        if (this.miningManager != null) {
            this.miningManager.shutdown();
        }
        getLogger().info("StoneLogs disabled.");
    }

    public boolean isTrackedLog(Material material) {
        return Tag.LOGS.isTagged(material);
    }
}
