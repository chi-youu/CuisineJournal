package com.chi__you.cuisinejournal.data;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Reads and writes one compressed NBT file per player, under the world's data
 * directory.
 *
 * <p>Per-player files rather than one big blob: a thousand-dish modpack with a
 * hundred players should never be re-serialised in full because one person
 * cooked a stew, and a corrupted profile can only ever cost one player
 * their history.</p>
 *
 * <p>Storing under the world directory is what keeps profiles per-world:
 * a singleplayer world and a server can never merge Cooking Levels.</p>
 */
public final class ProfileStorage {
    private static final String DIRECTORY = "cuisinejournal/players";

    private final Path root;

    public ProfileStorage(Path worldDataDirectory) {
        this.root = worldDataDirectory.resolve(DIRECTORY);
    }

    public Path root() {
        return root;
    }

    public CulinaryProfile load(UUID playerId) {
        Path file = fileFor(playerId);
        if (!Files.exists(file)) {
            return new CulinaryProfile(playerId);
        }
        try (InputStream input = Files.newInputStream(file)) {
            CompoundTag tag = NbtIo.readCompressed(input);
            int version=tag.getInt("schemaVersion");
            CulinaryProfile profile=CulinaryProfile.load(playerId, ProfileMigrations.migrate(tag));
            if(version<com.chi__you.cuisinejournal.Constants.PROFILE_SCHEMA_VERSION)profile.markDirty();
            return profile;
        } catch (IOException | RuntimeException failure) {
            // A profile we cannot read is set aside rather than overwritten, so
            // the player restarts empty but their history is still recoverable.
            CuisineJournalLog.LOGGER.error("Could not read the culinary profile for {}; "
                    + "the file has been kept for recovery.", playerId, failure);
            quarantine(file);
            return new CulinaryProfile(playerId);
        }
    }

    public boolean save(CulinaryProfile profile) {
        if(profile.schemaVersion()>com.chi__you.cuisinejournal.Constants.PROFILE_SCHEMA_VERSION){
            CuisineJournalLog.LOGGER.error("Refusing to downgrade newer profile {} (schema {})",profile.playerId(),profile.schemaVersion());
            return false;
        }
        Path file = fileFor(profile.playerId());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(root);
            try (OutputStream output = Files.newOutputStream(temporary)) {
                NbtIo.writeCompressed(profile.save(), output);
            }
            // Write-then-move so a crash mid-save cannot truncate a real profile.
            try(var channel=java.nio.channels.FileChannel.open(temporary,java.nio.file.StandardOpenOption.WRITE)){channel.force(true);}
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            profile.clearDirty();
            return true;
        } catch (IOException | RuntimeException failure) {
            CuisineJournalLog.LOGGER.error("Could not write the culinary profile for {}",
                    profile.playerId(), failure);
            return false;
        } finally {
            try{if(Files.isRegularFile(temporary))Files.deleteIfExists(temporary);}catch(IOException failure){CuisineJournalLog.LOGGER.warn("Could not remove profile temporary file {}",temporary,failure);}
        }
    }

    public boolean delete(UUID playerId) {
        try {
            return Files.deleteIfExists(fileFor(playerId));
        } catch (IOException failure) {
            CuisineJournalLog.LOGGER.error("Could not delete the culinary profile for {}",
                    playerId, failure);
            return false;
        }
    }
    public boolean preserveUnsaved(CulinaryProfile profile){
        if(profile.schemaVersion()>com.chi__you.cuisinejournal.Constants.PROFILE_SCHEMA_VERSION)return false;
        Path recovery=root.resolve(profile.playerId()+".unsaved-"+UUID.randomUUID()+".dat");
        try {
            Files.createDirectories(root);
            try(OutputStream output=Files.newOutputStream(recovery)){NbtIo.writeCompressed(profile.save(),output);}
            CuisineJournalLog.LOGGER.error("Normal profile save failed; unsaved progress preserved for manual recovery at {}",recovery);
            return true;
        }catch(IOException|RuntimeException failure){CuisineJournalLog.LOGGER.error("Cannot preserve unsaved profile {}",profile.playerId(),failure);return false;}
    }

    private Path fileFor(UUID playerId) {
        return root.resolve(playerId + ".dat");
    }

    private static void quarantine(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + UUID.randomUUID()));
        } catch (IOException failure) {
            CuisineJournalLog.LOGGER.error("Could not set the unreadable profile aside", failure);
        }
    }
}
