package dev.aurafarm;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Сохранение ауры и челюсти в файл config/aurafarm.properties.
 * Ключ "uuid" это аура, ключ "jaw.uuid" это уровень челюсти.
 * Файл общий для всех миров на этом компьютере или сервере.
 */
public final class AuraStorage {
    private static final String JAW_PREFIX = "jaw.";

    private AuraStorage() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("aurafarm.properties");
    }

    public static void load() {
        Path path = file();
        if (!Files.exists(path)) {
            return;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("[aurafarm] Не удалось прочитать ауру: " + e);
            return;
        }
        for (String key : props.stringPropertyNames()) {
            try {
                int value = Integer.parseInt(props.getProperty(key).trim());
                if (key.startsWith(JAW_PREFIX)) {
                    AuraManager.setJawRaw(UUID.fromString(key.substring(JAW_PREFIX.length())), value);
                } else {
                    AuraManager.setRaw(UUID.fromString(key), value);
                }
            } catch (IllegalArgumentException ignored) {
                // битая строка в файле, пропускаем
            }
        }
    }

    public static void save() {
        Properties props = new Properties();
        for (Map.Entry<UUID, Integer> entry : AuraManager.snapshot().entrySet()) {
            props.setProperty(entry.getKey().toString(), Integer.toString(entry.getValue()));
        }
        for (Map.Entry<UUID, Integer> entry : AuraManager.snapshotJaw().entrySet()) {
            props.setProperty(JAW_PREFIX + entry.getKey(), Integer.toString(entry.getValue()));
        }
        try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            props.store(writer, "Aura Farm");
        } catch (IOException e) {
            System.err.println("[aurafarm] Не удалось сохранить ауру: " + e);
        }
    }
}
