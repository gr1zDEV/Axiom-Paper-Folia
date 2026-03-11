package com.moulberry.axiom.threading;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class FoliaThreadingBridge implements ThreadingBridge {

    private final Plugin plugin;

    public FoliaThreadingBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(this.plugin, t -> task.run());
    }

    @Override
    public void runForWorldChunk(World world, int chunkX, int chunkZ, Runnable task) {
        Bukkit.getRegionScheduler().run(this.plugin, world, chunkX, chunkZ, t -> task.run());
    }

    @Override
    public void runForEntity(Entity entity, Runnable task) {
        entity.getScheduler().run(this.plugin, t -> task.run(), null);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        player.getScheduler().run(this.plugin, t -> task.run(), null);
    }

}
