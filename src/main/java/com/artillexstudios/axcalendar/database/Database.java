package com.artillexstudios.axcalendar.database;

import com.artillexstudios.axcalendar.gui.data.Day;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface Database {

    String getType();

    void setup();

    void claim(@NotNull Player player, Day day);

    boolean isClaimed(@NotNull Player player, Day day);

    int countIps(@NotNull Player player, Day day);

    void reset(@NotNull OfflinePlayer player);

    void resetDay(int day);

    void disable();
}
