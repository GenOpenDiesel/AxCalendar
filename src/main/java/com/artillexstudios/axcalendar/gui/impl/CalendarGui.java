package com.artillexstudios.axcalendar.gui.impl;

import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.ContainerUtils;
import com.artillexstudios.axapi.utils.ItemBuilder;
import com.artillexstudios.axapi.utils.StringUtils;
import com.artillexstudios.axcalendar.AxCalendar;
import com.artillexstudios.axcalendar.enums.ClaimStatus;
import com.artillexstudios.axcalendar.gui.GuiFrame;
import com.artillexstudios.axcalendar.gui.data.Day;
import com.artillexstudios.axcalendar.gui.data.MenuManager;
import com.artillexstudios.axcalendar.gui.data.Reward;
import com.artillexstudios.axcalendar.utils.CalendarUtils;
import com.artillexstudios.axcalendar.utils.ItemBuilderUtil;
import com.artillexstudios.axcalendar.utils.RequirementUtils;
import com.artillexstudios.axcalendar.utils.SoundUtils;
import com.artillexstudios.axcalendar.utils.TimeUtils;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

import static com.artillexstudios.axcalendar.AxCalendar.CONFIG;
import static com.artillexstudios.axcalendar.AxCalendar.MENU;
import static com.artillexstudios.axcalendar.AxCalendar.MESSAGEUTILS;

public class CalendarGui extends GuiFrame {
    private static final Set<CalendarGui> openMenus = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    // Obiekt do synchronizacji zapisu plików, aby uniknąć błędów przy wielu graczach naraz
    private static final Object LOCK = new Object();

    private final Gui gui;

    public CalendarGui(Player player) {
        super(MENU, player);

        this.gui = Gui.gui()
                .title(StringUtils.format(AxCalendar.getPlaceholderParser().setPlaceholders(player, MENU.getString("title"))))
                .rows(MENU.getInt("rows", 6))
                .disableAllInteractions()
                .create();

        setGui(gui);
    }

    public void open() {
        CompletableFuture<Void> cf = new CompletableFuture<>();

        // load menu async
        AxCalendar.getThreadedQueue().submit(() -> {
            if (file.getSection("close") != null) {
                super.createItem("close", event -> {
                    Scheduler.get().runAt(player.getLocation(), scheduledTask -> {
                        player.closeInventory();
                    });
                });
            }

            for (Map.Entry<Integer, Day> entry : MenuManager.getDays().entrySet()) {
                int num = entry.getKey();
                Day day = entry.getValue();

                final HashMap<String, String> rp = new HashMap<>();
                rp.put("%day%", "" + day.day());
                rp.put("%time%", TimeUtils.fancyTime(CalendarUtils.getMilisUntilDay(num)));

                switch (getClaimStatus(day)) {
                    case CLAIMED -> {
                        GuiItem guiItem = new GuiItem(ItemBuilderUtil.parse(day.claimed().clone(), rp));
                        guiItem.setAction(event -> {
                            MESSAGEUTILS.sendLang(player, "error.already-claimed", rp);
                            SoundUtils.playSound(player, CONFIG.getString("sounds.failed"));
                        });
                        gui.setItem(day.slot(), guiItem);
                    }

                    case CLAIMABLE -> {
                        GuiItem guiItem = new GuiItem(ItemBuilderUtil.parse(day.claimable().clone(), rp));
                        guiItem.setAction(event -> AxCalendar.getThreadedQueue().submit(() -> {
                            // 1. Podstawowe zabezpieczenie asynchroniczne (sprawdza czy nagroda nadal jest dostępna)
                            if (getClaimStatus(day) != ClaimStatus.CLAIMABLE) return;

                            // --- MODYFIKACJA START: Logika dla 25 dnia (Bonus) ---
// --- MODYFIKACJA START: Logika dla 25 dnia (Bonus) ---
                            if (day.day() == 25) {
                                int claimedCount = 0;
                                int daysToCheck = 0;

                                // Zliczamy odebrane nagrody z dni 1-24
                                for (Day d : MenuManager.getDays().values()) {
                                    if (d.day() < 25) {
                                        daysToCheck++;
                                        // Sprawdzenie w bazie danych
                                        if (AxCalendar.getDatabase().isClaimed(player, d)) {
                                            claimedCount++;
                                        }
                                    }
                                }

                                // Logika tolerancji: Pozwalamy "zapomnieć" o maksymalnie 2 dniach.
                                // Jeśli dni do sprawdzenia jest 24, gracz musi mieć odebrane co najmniej 22 (24 - 2).
                                if (claimedCount < (daysToCheck - 2)) {
                                    MESSAGEUTILS.sendFormatted(player, "&cMusisz odebrać nagrody z co najmniej " + (daysToCheck - 2) + " dni (1-24), aby otworzyć ten prezent!");
                                    SoundUtils.playSound(player, CONFIG.getString("sounds.failed"));
                                    return;
                                }
                            }
                            // --- MODYFIKACJA END ---
                            // --- MODYFIKACJA END ---

                            int maxIP = CONFIG.getInt("max-accounts-per-ip", 3);
                            if (maxIP != -1 && maxIP <= AxCalendar.getDatabase().countIps(player, day)) {
                                MESSAGEUTILS.sendLang(player, "error.too-many-ips", rp);
                                SoundUtils.playSound(player, CONFIG.getString("sounds.failed"));
                                return;
                            }

                            if (!RequirementUtils.canClaim(player)) {
                                MESSAGEUTILS.sendLang(player, "error.requirements-fail", rp);
                                SoundUtils.playSound(player, CONFIG.getString("sounds.failed"));
                                return;
                            }

                            // Zapis statusu odebrania w bazie danych
                            AxCalendar.getDatabase().claim(player, day);

                            // --- LOGOWANIE DO PLIKU START ---
                            logClaimToFile(player, day.day());
                            // --- LOGOWANIE DO PLIKU END ---

                            Scheduler.get().run(scheduledTask -> {
                                SoundUtils.playSound(player, CONFIG.getString("sounds.claimed"));
                                for (Reward reward : day.rewards()) {
                                    for (String command : reward.claimCommands()) {
                                        command = command.replace("%player%", player.getName());
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), AxCalendar.getPlaceholderParser().setPlaceholders(player, command));
                                    }
                                    for (Map<?, ?> map : reward.claimItems()) {
                                        ItemStack it = ItemBuilder.create((Map<Object, Object>) map).get();
                                        ContainerUtils.INSTANCE.addOrDrop(player.getInventory(), List.of(it), player.getLocation());
                                    }
                                    MESSAGEUTILS.sendFormatted(player, CONFIG.getString("prefix") + reward.message(), rp);
                                }
                            });
                            open();
                        }));
                        gui.setItem(day.slot(), guiItem);
                    }

                    case UNCLAIMABLE -> {
                        GuiItem guiItem = new GuiItem(ItemBuilderUtil.parse(day.unclaimable().clone(), rp));
                        guiItem.setAction(event -> {
                            MESSAGEUTILS.sendLang(player, "error.too-early", rp);
                            SoundUtils.playSound(player, CONFIG.getString("sounds.failed"));
                        });
                        gui.setItem(day.slot(), guiItem);
                    }

                    case EXPIRED -> {
                        GuiItem guiItem = new GuiItem(ItemBuilderUtil.parse(day.expired().clone(), rp));
                        guiItem.setAction(event -> {
                            MESSAGEUTILS.sendLang(player, "error.too-late", rp);
                            SoundUtils.playSound(player, CONFIG.getString("sounds.failed"));
                        });
                        gui.setItem(day.slot(), guiItem);
                    }
                }
            }
            cf.complete(null);
        });

        // open when the gui is loaded
        cf.thenRun(() -> {
            if (openMenus.contains(this)) {
                gui.update();
                updateTitle();
                return;
            }

            gui.setCloseGuiAction(e -> openMenus.remove(this));

            Scheduler.get().run(t -> {
                gui.open(player);
                openMenus.add(this);
            });
        });
    }

public ClaimStatus getClaimStatus(Day day) {
        if (AxCalendar.getDatabase().isClaimed(player, day)) return ClaimStatus.CLAIMED;
        if (!CalendarUtils.isSameMonth()) return ClaimStatus.UNCLAIMABLE;
        
        // Specjalna logika dla dnia 25 - dostępny od 25 do końca miesiąca
        if (day.day() == 25) {
            int currentDay = CalendarUtils.getDayOfMonth();
            if (currentDay >= 25) return ClaimStatus.CLAIMABLE;
            return ClaimStatus.UNCLAIMABLE;
        }
        
        if (CalendarUtils.getDayOfMonth() == day.day()) return ClaimStatus.CLAIMABLE;
        boolean lateClaiming = CONFIG.getBoolean("allow-late-claiming", true);
        if (CalendarUtils.getDayOfMonth() > day.day()) return lateClaiming ? ClaimStatus.CLAIMABLE : ClaimStatus.EXPIRED;
        return ClaimStatus.UNCLAIMABLE;
    }

    public static Set<CalendarGui> getOpenMenus() {
        return openMenus;
    }

    public Player getPlayer() {
        return player;
    }

    // Funkcja zapisująca logi do pliku claims.log
    private void logClaimToFile(Player player, int day) {
        synchronized (LOCK) {
            File dataFolder = AxCalendar.getInstance().getDataFolder();
            File logsFile = new File(dataFolder, "claims.log");

            try {
                if (!logsFile.exists()) {
                    logsFile.createNewFile();
                }

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(logsFile, true))) {
                    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
                    String logEntry = String.format("[%s] Gracz %s odebrał nagrodę za dzień %d", time, player.getName(), day);
                    writer.write(logEntry);
                    writer.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Bukkit.getLogger().warning("[AxCalendar] Nie udało się zapisać logu odbioru nagrody!");
            }
        }
    }
}
