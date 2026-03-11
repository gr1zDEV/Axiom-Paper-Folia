package com.moulberry.axiom.threading;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface ThreadingBridge {

    void runGlobal(Runnable task);

    void runForWorldChunk(World world, int chunkX, int chunkZ, Runnable task);

    void runForEntity(Entity entity, Runnable task);

    void runForPlayer(Player player, Runnable task);

}
