package com.artillexstudios.axcalendar.utils;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

import static com.artillexstudios.axcalendar.AxCalendar.CONFIG;

public class RequirementUtils implements Listener {

    private static final HashMap<UUID, Long> sessionStart = new HashMap<>();
    private static final HashMap<UUID, Long> dailyStoredTime = new HashMap<>();
    private static int currentDay = -1;

    public static void setup() {
        currentDay = CalendarUtils.getDayOfMonth();
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            sessionStart.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        checkDayReset();
        sessionStart.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (sessionStart.containsKey(uuid)) {
            long sessionTime = System.currentTimeMillis() - sessionStart.get(uuid);
            dailyStoredTime.put(uuid, dailyStoredTime.getOrDefault(uuid, 0L) + sessionTime);
            sessionStart.remove(uuid);
        }
    }

    private static void checkDayReset() {
        int today = CalendarUtils.getDayOfMonth();
        if (today != currentDay) {
            dailyStoredTime.clear();
            
            long now = System.currentTimeMillis();
            for (UUID uuid : sessionStart.keySet()) {
                sessionStart.put(uuid, now);
            }
            
            currentDay = today;
        }
    }

    private static long getDailyPlaytimeMillis(Player player) {
        checkDayReset();
        UUID uuid = player.getUniqueId();
        
        long stored = dailyStoredTime.getOrDefault(uuid, 0L);
        long currentSession = 0;
        
        if (sessionStart.containsKey(uuid)) {
            currentSession = System.currentTimeMillis() - sessionStart.get(uuid);
        }
        
        return stored + currentSession;
    }

    public static boolean canClaim(@NotNull Player player) {
        if (CONFIG.getStringList("claim-requirements") == null || CONFIG.getStringList("claim-requirements").isEmpty()) return true;

        boolean canClaim = false;

        for (String str : CONFIG.getStringList("claim-requirements")) {
            final String[] ar = str.split(" ");

            switch (ar[0]) {
                case "[PLAYTIME]": {
                    if ((player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 60 / 20) >= Integer.parseInt(ar[1])) {
                        canClaim = true;
                    }
                    break;
                }
                
                case "[DAILY_PLAYTIME]": {
                    long playedMinutes = getDailyPlaytimeMillis(player) / 1000 / 60;
                    if (playedMinutes >= Integer.parseInt(ar[1])) {
                        canClaim = true;
                    }
                    break;
                }

                case "[PERMISSION]": {
                    if (player.hasPermission(ar[1])) {
                        canClaim = true;
                    }
                    break;
                }
            }
        }

        return canClaim;
    }
}
