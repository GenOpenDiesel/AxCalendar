package com.artillexstudios.axcalendar.commands.subcommands;

import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axcalendar.AxCalendar;
import com.artillexstudios.axcalendar.utils.CalendarUtils;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public enum ResetDaily {
    INSTANCE;

    public void execute(@NotNull CommandSender sender) {
        int today = CalendarUtils.getDayOfMonth();
        AxCalendar.getThreadedQueue().submit(() -> AxCalendar.getDatabase().resetDay(today));
        sender.sendMessage(StringUtils.formatToString(AxCalendar.CONFIG.getString("prefix") + " &#33FF33Zresetowano nagrody dla wszystkich graczy z dnia: &f" + today));
    }
}
