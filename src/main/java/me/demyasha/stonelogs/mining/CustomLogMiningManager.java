package me.demyasha.stonelogs.mining;

import me.demyasha.stonelogs.StoneLogsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CustomLogMiningManager {

    private static final double CUSTOM_LOG_HARDNESS = 1.5D;
    private static final int MAX_TARGET_DISTANCE = 6;

    private final StoneLogsPlugin plugin;
    private final Map<UUID, MiningSession> sessions = new HashMap<>();

    public CustomLogMiningManager(StoneLogsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(Player player, Block block) {
        MiningSession existing = sessions.get(player.getUniqueId());
        if (existing != null && existing.matches(block)) {
            return;
        }

        abort(player, true);

        MiningSession session = new MiningSession(player.getUniqueId(), block);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                tick(session);
            }
        }.runTaskTimer(plugin, 1L, 1L);

        session.setTask(task);
        sessions.put(player.getUniqueId(), session);
        player.sendBlockDamage(block.getLocation(), 0.0F, player.getEntityId());
    }

    public void abort(Player player, boolean clearVisual) {
        abort(player.getUniqueId(), clearVisual);
    }

    public void shutdown() {
        for (MiningSession session : sessions.values()) {
            session.cancelTask();
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null) {
                clearVisual(player, session);
            }
        }
        sessions.clear();
    }

    private void abort(UUID playerId, boolean clearVisual) {
        MiningSession removed = sessions.remove(playerId);
        if (removed == null) {
            return;
        }

        removed.cancelTask();
        Player player = Bukkit.getPlayer(playerId);
        if (clearVisual && player != null) {
            clearVisual(player, removed);
        }
    }

    private void tick(MiningSession session) {
        Player player = Bukkit.getPlayer(session.playerId());
        if (player == null || !player.isOnline()) {
            abort(session.playerId(), false);
            return;
        }

        if (player.getGameMode() != GameMode.SURVIVAL) {
            abort(player, true);
            return;
        }

        Block block = session.getBlock();
        if (block == null || block.getType().isAir() || !plugin.isTrackedLog(block.getType())) {
            abort(player, false);
            return;
        }

        Block target = player.getTargetBlockExact(MAX_TARGET_DISTANCE);
        if (target == null || !sameBlock(target, block)) {
            abort(player, true);
            return;
        }

        session.addProgress(calculateProgressPerTick(player));
        float visualProgress = (float) Math.min(1.0D, session.progress());
        player.sendBlockDamage(block.getLocation(), visualProgress, player.getEntityId());

        if (session.progress() < 1.0D) {
            return;
        }

        finishBreaking(player, session, block);
    }

    private void finishBreaking(Player player, MiningSession session, Block block) {
        clearVisual(player, session);
        session.cancelTask();
        sessions.remove(player.getUniqueId());

        if (!player.isOnline() || block.getType().isAir()) {
            return;
        }

        BlockBreakEvent breakEvent = new BlockBreakEvent(block, player);
        breakEvent.setDropItems(true);
        breakEvent.setExpToDrop(0);
        Bukkit.getPluginManager().callEvent(breakEvent);

        if (breakEvent.isCancelled()) {
            player.sendBlockChange(block.getLocation(), block.getBlockData());
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (breakEvent.isDropItems()) {
            block.breakNaturally(mainHand, true);
        } else {
            block.setType(Material.AIR);
        }

        damageHeldItemIfNeeded(player, mainHand);
    }

    private void damageHeldItemIfNeeded(Player player, ItemStack mainHand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (mainHand == null || mainHand.getType() == Material.AIR) {
            return;
        }
        if (mainHand.getType().getMaxDurability() <= 0) {
            return;
        }

        player.damageItemStack(EquipmentSlot.HAND, 1);
    }

    private void clearVisual(Player player, MiningSession session) {
        Block block = session.getBlock();
        if (block != null) {
            player.sendBlockDamage(block.getLocation(), 0.0F, player.getEntityId());
        }
    }

    private double calculateProgressPerTick(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        boolean correctTool = isCorrectTool(heldItem);

        double speed = 1.0D;
        if (correctTool) {
            speed = getAxeSpeed(heldItem.getType());

            int efficiencyLevel = heldItem.getEnchantmentLevel(Enchantment.EFFICIENCY);
            if (efficiencyLevel > 0) {
                speed += 1.0D + (efficiencyLevel * efficiencyLevel);
            }
        }

        PotionEffect haste = player.getPotionEffect(PotionEffectType.HASTE);
        if (haste != null) {
            speed *= 1.0D + (0.2D * (haste.getAmplifier() + 1));
        }

        PotionEffect miningFatigue = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        if (miningFatigue != null) {
            speed *= Math.pow(0.3D, miningFatigue.getAmplifier() + 1);
        }

        if (player.isInWater() && !hasAquaAffinity(player)) {
            speed /= 5.0D;
        }

        if (!player.isOnGround()) {
            speed /= 5.0D;
        }

        double divisor = correctTool ? 30.0D : 100.0D;
        return speed / CUSTOM_LOG_HARDNESS / divisor;
    }

    private boolean hasAquaAffinity(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return helmet != null && helmet.getEnchantmentLevel(Enchantment.AQUA_AFFINITY) > 0;
    }

    private boolean isCorrectTool(ItemStack itemStack) {
        return itemStack != null && Tag.ITEMS_AXES.isTagged(itemStack.getType());
    }

    private double getAxeSpeed(Material material) {
        return switch (material) {
            case WOODEN_AXE -> 2.0D;
            case STONE_AXE -> 4.0D;
            case IRON_AXE -> 6.0D;
            case DIAMOND_AXE -> 8.0D;
            case NETHERITE_AXE -> 9.0D;
            case GOLDEN_AXE -> 12.0D;
            default -> 1.0D;
        };
    }

    private boolean sameBlock(Block a, Block b) {
        return a.getX() == b.getX()
                && a.getY() == b.getY()
                && a.getZ() == b.getZ()
                && a.getWorld().getUID().equals(b.getWorld().getUID());
    }

    private static final class MiningSession {
        private final UUID playerId;
        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;
        private double progress;
        private BukkitTask task;

        private MiningSession(UUID playerId, Block block) {
            this.playerId = playerId;
            this.worldId = block.getWorld().getUID();
            this.x = block.getX();
            this.y = block.getY();
            this.z = block.getZ();
            this.progress = 0.0D;
        }

        public UUID playerId() {
            return playerId;
        }

        public double progress() {
            return progress;
        }

        public void addProgress(double amount) {
            this.progress += amount;
        }

        public void setTask(BukkitTask task) {
            this.task = task;
        }

        public void cancelTask() {
            if (task != null) {
                task.cancel();
            }
        }

        public boolean matches(Block block) {
            return block.getX() == x
                    && block.getY() == y
                    && block.getZ() == z
                    && block.getWorld().getUID().equals(worldId);
        }

        public Block getBlock() {
            World world = Bukkit.getWorld(worldId);
            if (world == null) {
                return null;
            }
            return world.getBlockAt(x, y, z);
        }
    }
}
