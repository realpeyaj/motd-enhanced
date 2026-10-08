package dev.peyaj.motdenhanced.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class SchedulerUtil {

    private SchedulerUtil() {
    }

    public static void runSync(Plugin plugin, Runnable task) {
        try {
            // Check for Folia GlobalRegionScheduler
            Method getGlobalRegionScheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
            Object scheduler = getGlobalRegionScheduler.invoke(Bukkit.getServer());
            Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
            runMethod.invoke(scheduler, plugin, (Consumer<Object>) o -> task.run());
            return;
        } catch (NoSuchMethodException ignored) {
            // Standard Paper / Spigot
        } catch (Throwable ignored) {
        }

        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        try {
            // Check for Folia AsyncScheduler
            Method getAsyncScheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler");
            Object scheduler = getAsyncScheduler.invoke(Bukkit.getServer());
            Method runNowMethod = scheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
            runNowMethod.invoke(scheduler, plugin, (Consumer<Object>) o -> task.run());
            return;
        } catch (NoSuchMethodException ignored) {
            // Standard Paper / Spigot
        } catch (Throwable ignored) {
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
}
