package com.moulberry.axiom.threading;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PaperThreadingBridge implements ThreadingBridge {

    private final Plugin plugin;

    public PaperThreadingBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        Bukkit.getScheduler().runTask(this.plugin, task);
    }

    @Override
    public void runForWorldChunk(World world, int chunkX, int chunkZ, Runnable task) {
        Bukkit.getScheduler().runTask(this.plugin, task);
    }

    @Override
    public void runForEntity(Entity entity, Runnable task) {
        Bukkit.getScheduler().runTask(this.plugin, task);
    }

    @Override
    public void runForPlayer(Player player, Runnable task) {
        Bukkit.getScheduler().runTask(this.plugin, task);
    }

}
