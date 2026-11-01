package dev.aurafarm;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/** Хранит ауру и уровень челюсти игроков в памяти. Файл с сохранением обслуживает AuraStorage. */
public final class AuraManager {
    /** Последнее изменение ауры, чтобы HUD мог показать "+5" на пару секунд. */
    public record Flash(int delta, String reason, long untilMillis) {}

    private static final Map<UUID, Integer> AURA = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> JAW = new ConcurrentHashMap<>();
    private static final Map<UUID, Flash> FLASH = new ConcurrentHashMap<>();

    /** Названия рангов от худшего (0) до лучшего (15). Общие для ауры и челюсти. */
    private static final String[] RANK_NAMES = {
        "Giga-Subhuman", "Sub <0", "Sub 0", "Sub 1", "Sub 2", "Sub 3", "Sub 5",
        "LTN", "MTN", "HTN", "Chadlite", "Chad", "True Adam",
        "God-Tier", "Alienmaxxing", "The Ultimate Halo"
    };

    /** Минимальная аура для входа в ранг с таким номером (у ранга 0 нижней границы нет). */
    private static final int[] AURA_MIN = {
        Integer.MIN_VALUE, -1999, -999, -499, -299, -199, -99,
        100, 300, 700, 1500, 3000, 5000,
        8000, 12000, 20000
    };

    /** Минимальный уровень челюсти для входа в ранг. True Adam начинается с 75. */
    private static final int[] JAW_MIN = {
        Integer.MIN_VALUE, -29, -19, -11, -7, -4, -1,
        3, 8, 15, 25, 40, 75,
        110, 160, 250
    };

    private AuraManager() {}

    // ----- аура -----

    public static int get(UUID playerId) {
        return AURA.getOrDefault(playerId, 0);
    }

    /** Для загрузки из файла: выставляет значение без всплывающих сообщений. */
    public static void setRaw(UUID playerId, int value) {
        AURA.put(playerId, value);
    }

    /** Копия всех значений ауры для сохранения в файл. */
    public static Map<UUID, Integer> snapshot() {
        return new HashMap<>(AURA);
    }

    public static Flash getFlash(UUID playerId) {
        Flash flash = FLASH.get(playerId);
        if (flash == null || flash.untilMillis() < System.currentTimeMillis()) {
            return null;
        }
        return flash;
    }

    /** Меняет ауру. Изменение показывает HUD (см. AuraHud). */
    public static int add(ServerPlayer player, int delta, String reason) {
        int value = AURA.merge(player.getUUID(), delta, Integer::sum);
        FLASH.put(player.getUUID(), new Flash(delta, reason, System.currentTimeMillis() + 3000));
        return value;
    }

    // ----- линия челюсти -----

    public static int getJaw(UUID playerId) {
        return JAW.getOrDefault(playerId, 0);
    }

    public static int addJaw(UUID playerId, int delta) {
        return JAW.merge(playerId, delta, Integer::sum);
    }

    public static void setJawRaw(UUID playerId, int value) {
        JAW.put(playerId, value);
    }

    public static Map<UUID, Integer> snapshotJaw() {
        return new HashMap<>(JAW);
    }

    // ----- ранги -----

    private static int indexFor(int value, int[] mins) {
        for (int i = mins.length - 1; i > 0; i--) {
            if (value >= mins[i]) {
                return i;
            }
        }
        return 0;
    }

    /** Номер ранга по ауре: от 0 (Giga-Subhuman) до 15 (The Ultimate Halo). */
    public static int rankIndex(int aura) {
        return indexFor(aura, AURA_MIN);
    }

    public static String rank(int aura) {
        return RANK_NAMES[rankIndex(aura)];
    }

    /** Номер ранга по уровню челюсти (те же 16 рангов, другие пороги). */
    public static int jawRankIndex(int jaw) {
        return indexFor(jaw, JAW_MIN);
    }

    public static String jawRank(int jaw) {
        return RANK_NAMES[jawRankIndex(jaw)];
    }

    /**
     * Положение в таблице гипергамии от 1 до 10: среднее между рангом ауры и рангом челюсти.
     * 1-6: малое или нулевое внимание, 7-10: полное внимание.
     */
    public static int position(int auraIndex, int jawIndex) {
        double combined = (auraIndex + jawIndex) / 2.0;
        int position = 1 + (int) Math.round(combined * 9.0 / 14.0);
        return Math.max(1, Math.min(10, position));
    }
}
