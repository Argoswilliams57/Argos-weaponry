package com.argos.argos_weaponry;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs the content packs bundled inside the mod JAR into the Point Blank
 * folder of the game directory.
 *
 * <p>Content packs are read from {@code assets/argos_weaponry/content_packs/}.
 * Every directory is copied recursively and every ZIP archive is extracted.
 * Both keep their original name, so a pack called {@code My Pack} ends up in
 * {@code <game directory>/pointblank/My Pack/}.</p>
 */
public final class ContentPackInstaller {

    private static final String CONTENT_PACK_RESOURCE =
            "assets/" + Constants.MOD_ID + "/content_packs";

    private static final String POINT_BLANK_FOLDER = "pointblank";

    private static final String ZIP_EXTENSION = ".zip";

    private ContentPackInstaller() {
    }

    /**
     * Installs every bundled content pack.
     *
     * <p>This is called from the mod constructor, which runs early enough for
     * Vic's Point Blank to discover the packs while it initialises.</p>
     *
     * <p>Failures are logged instead of thrown so that a broken pack can never
     * prevent the game from starting.</p>
     */
    public static void install() {

        try {

            installPacks();

        } catch (Exception e) {

            Constants.LOG.error(
                    "[PackLoader] Unexpected error while installing content packs.",
                    e
            );
        }
    }

    private static void installPacks() {

        if (!Config.isModEnabled()) {
            Constants.LOG.info(
                    "[PackLoader] Argos Weaponry is disabled; skipping content pack installation."
            );
            return;
        }

        boolean overwriteExisting = Config.shouldOverwriteExisting();

        Path destinationRoot = FMLPaths.GAMEDIR.get()
                .resolve(POINT_BLANK_FOLDER)
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(destinationRoot);
        } catch (IOException e) {
            Constants.LOG.error(
                    "[PackLoader] Could not create the Point Blank folder at {}.",
                    destinationRoot,
                    e
            );
            return;
        }

        Path contentPackRoot = findBundledContentPacks();

        if (contentPackRoot == null) {
            Constants.LOG.info(
                    "[PackLoader] No bundled content packs were found."
            );
            return;
        }

        List<Path> contentPacks;

        try (Stream<Path> paths = Files.list(contentPackRoot)) {

            contentPacks = paths
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

        } catch (IOException e) {

            Constants.LOG.error(
                    "[PackLoader] Could not read the bundled content packs.",
                    e
            );
            return;
        }

        int installedPacks = 0;

        for (Path contentPack : contentPacks) {

            try {

                if (Files.isDirectory(contentPack)) {

                    installDirectoryPack(
                            contentPack,
                            destinationRoot,
                            overwriteExisting
                    );

                    installedPacks++;

                } else if (Files.isRegularFile(contentPack)
                        && isZipFile(contentPack)) {

                    installZipPack(
                            contentPack,
                            destinationRoot,
                            overwriteExisting
                    );

                    installedPacks++;

                } else {

                    Constants.LOG.info(
                            "[PackLoader] Ignoring unsupported content pack: {}",
                            contentPack.getFileName()
                    );
                }

            } catch (Exception e) {

                Constants.LOG.error(
                        "[PackLoader] Failed to install content pack '{}'.",
                        contentPack.getFileName(),
                        e
                );
            }
        }

        Constants.LOG.info(
                "[PackLoader] Content pack installation complete ({} pack(s) processed).",
                installedPacks
        );
    }

    /*
     * ============================================================
     * BUNDLED RESOURCE LOOKUP
     * ============================================================
     */

    /**
     * Locates the bundled content pack directory inside the mod file.
     *
     * <p>{@code IModFile#findResource} resolves both a plain game directory
     * layout (development environment) and the union filesystem of a mod JAR,
     * so the same code works in both cases.</p>
     *
     * @return the content pack directory, or {@code null} if it does not exist
     */
    private static Path findBundledContentPacks() {

        ModList modList = ModList.get();

        if (modList == null) {
            return null;
        }

        var modFileInfo = modList.getModFileById(Constants.MOD_ID);

        if (modFileInfo == null || modFileInfo.getFile() == null) {
            return null;
        }

        Path contentPackRoot = modFileInfo.getFile()
                .findResource(CONTENT_PACK_RESOURCE);

        if (contentPackRoot == null || !Files.exists(contentPackRoot)) {
            return null;
        }

        return contentPackRoot;
    }

    /*
     * ============================================================
     * DIRECTORY CONTENT PACKS
     * ============================================================
     */

    private static void installDirectoryPack(
            Path sourceFolder,
            Path destinationRoot,
            boolean overwriteExisting) throws IOException {

        String packName = sourceFolder.getFileName().toString();

        Path destination = destinationRoot.resolve(packName).normalize();

        Files.createDirectories(destination);

        copyDirectory(sourceFolder, destination, overwriteExisting);

        Constants.LOG.info(
                "[PackLoader] Installed content pack folder: {}",
                packName
        );
    }

    private static void copyDirectory(
            Path source,
            Path destination,
            boolean overwriteExisting) throws IOException {

        try (Stream<Path> paths = Files.walk(source)) {

            for (Path path : paths.toList()) {

                Path relativePath = source.relativize(path);

                Path target = resolveInsideDestination(
                        destination,
                        relativePath.toString()
                );

                if (Files.isDirectory(path)) {

                    Files.createDirectories(target);

                } else {

                    copyFile(path, target, overwriteExisting);
                }
            }
        }
    }


    /*
     * ============================================================
     * ZIP CONTENT PACKS
     * ============================================================
     */

    private static void installZipPack(
            Path sourceZip,
            Path destinationRoot,
            boolean overwriteExisting) throws IOException {

        String fileName = sourceZip.getFileName().toString();

        String packName = fileName.substring(
                0,
                fileName.length() - ZIP_EXTENSION.length()
        );

        Path destination = resolveInsideDestination(
                destinationRoot,
                packName
        );

        Files.createDirectories(destination);

        try (InputStream input = Files.newInputStream(sourceZip);
             ZipInputStream zipInput = new ZipInputStream(input)) {

            ZipEntry entry;

            while ((entry = zipInput.getNextEntry()) != null) {

                /*
                 * Normalise the separator first: a ZIP entry must never be
                 * allowed to escape the destination folder (ZIP-slip).
                 */
                String entryName = entry.getName().replace('\\', '/');

                Path target = resolveInsideDestination(destination, entryName);

                if (entry.isDirectory() || entryName.endsWith("/")) {

                    Files.createDirectories(target);

                    continue;
                }

                if (Files.isDirectory(target)) {

                    Constants.LOG.warn(
                            "[PackLoader] Skipping ZIP entry '{}' because a folder already exists at that location.",
                            entry.getName()
                    );

                    continue;
                }

                if (Files.exists(target) && !overwriteExisting) {

                    logKeepingExistingFile(target);

                    continue;
                }

                Path parent = target.getParent();

                if (parent != null) {
                    Files.createDirectories(parent);
                }

                if (overwriteExisting) {

                    Files.copy(
                            zipInput,
                            target,
                            StandardCopyOption.REPLACE_EXISTING
                    );

                } else {

                    Files.copy(zipInput, target);
                }
            }
        }

        Constants.LOG.info(
                "[PackLoader] Installed content pack archive: {}",
                fileName
        );
    }


    /*
     * ============================================================
     * FILE HANDLING HELPERS
     * ============================================================
     */

    private static void copyFile(
            Path source,
            Path target,
            boolean overwriteExisting) throws IOException {

        if (Files.isDirectory(target)) {

            Constants.LOG.warn(
                    "[PackLoader] Skipping file '{}' because a folder already exists at that location.",
                    target
            );

            return;
        }

        if (Files.exists(target) && !overwriteExisting) {

            logKeepingExistingFile(target);

            return;
        }

        Path parent = target.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (overwriteExisting) {

            Files.copy(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } else {

            Files.copy(source, target);
        }
    }

    /**
     * Resolves a content pack entry name against the destination folder and
     * rejects anything that would end up outside of it.
     *
     * <p>Absolute paths, Windows drive letters and {@code ..} segments are all
     * caught by the normalisation plus the prefix check.</p>
     */
    private static Path resolveInsideDestination(
            Path destination,
            String entryName) throws IOException {

        Path target = destination.resolve(entryName).normalize();

        if (!target.startsWith(destination)) {

            throw new IOException(
                    "Blocked content pack entry outside the destination folder: "
                            + entryName
            );
        }

        return target;
    }

    private static void logKeepingExistingFile(Path target) {

        Constants.LOG.debug(
                "[PackLoader] Keeping existing file: {}",
                target
        );
    }

    private static boolean isZipFile(Path path) {

        return path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT)
                .endsWith(ZIP_EXTENSION);
    }
}

