package com.argos.argos_weaponry;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {

    private static final String CONFIG_FILE_NAME =
            Constants.MOD_ID + "-common.toml";

    private static final ForgeConfigSpec.Builder BUILDER =
            new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_MOD =
            BUILDER
                    .comment(
                            "Enables or disables the entire Argos Weaponry mod's functionality.",
                            "When disabled, the bundled content packs are not installed."
                    )
                    .define("enableMod", true);

    public static final ForgeConfigSpec.BooleanValue OVERWRITE_EXISTING =
            BUILDER
                    .comment(
                            "When enabled, existing files are overwritten during content pack installation.",
                            "When disabled, existing files are preserved and files that already exist are skipped."
                    )
                    .define("overwriteExisting", true);

    public static final ForgeConfigSpec SPEC =
            BUILDER.build();

    private Config() {
    }

    /**
     * Returns whether the mod is enabled.
     *
     * <p>The content pack installer runs from the mod constructor, which is
     * before Forge loads the config files. Reading the spec at that point
     * would throw, so the value is read straight from the config file and the
     * declared default is used when the file does not exist yet.</p>
     */
    public static boolean isModEnabled() {

        if (SPEC.isLoaded()) {
            return ENABLE_MOD.get();
        }

        return readBooleanFromConfigFile(
                "enableMod",
                ENABLE_MOD.getDefault()
        );
    }

    /**
     * Returns whether existing files may be overwritten during installation.
     *
     * @see #isModEnabled() for why the config file is read directly
     */
    public static boolean shouldOverwriteExisting() {

        if (SPEC.isLoaded()) {
            return OVERWRITE_EXISTING.get();
        }

        return readBooleanFromConfigFile(
                "overwriteExisting",
                OVERWRITE_EXISTING.getDefault()
        );
    }

    /**
     * Reads a root level boolean from the common config file.
     *
     * <p>Only the simple {@code key = true} / {@code key = false} layout that
     * Forge writes is supported, which is exactly what this mod defines.</p>
     */
    private static boolean readBooleanFromConfigFile(
            String key,
            boolean defaultValue) {

        Path configFile = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);

        if (!Files.isRegularFile(configFile)) {
            return defaultValue;
        }

        try (BufferedReader reader = Files.newBufferedReader(
                configFile,
                StandardCharsets.UTF_8)) {

            String line;

            while ((line = reader.readLine()) != null) {

                int commentIndex = line.indexOf('#');

                if (commentIndex >= 0) {
                    line = line.substring(0, commentIndex);
                }

                int separatorIndex = line.indexOf('=');

                if (separatorIndex < 0) {
                    continue;
                }

                String name = line.substring(0, separatorIndex).trim();

                if (!name.equals(key)) {
                    continue;
                }

                String value = line.substring(separatorIndex + 1).trim();

                if ("true".equalsIgnoreCase(value)) {
                    return true;
                }

                if ("false".equalsIgnoreCase(value)) {
                    return false;
                }

                return defaultValue;
            }

        } catch (IOException e) {

            Constants.LOG.warn(
                    "[PackLoader] Could not read '{}' from {}. Using the default value {}.",
                    key,
                    configFile,
                    defaultValue,
                    e
            );
        }

        return defaultValue;
    }
}
