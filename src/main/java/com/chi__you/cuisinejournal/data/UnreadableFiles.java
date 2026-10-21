package com.chi__you.cuisinejournal.data;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import java.nio.file.*;
import java.io.IOException;

/** A recovery copy must succeed before a broken user file can ever be overwritten. */
public final class UnreadableFiles {
    private UnreadableFiles() {}
    public static boolean preserve(Path file) {
        try {
            Path backup = file.resolveSibling(file.getFileName()+".unreadable-"+java.util.UUID.randomUUID()+".bak");
            Files.copy(file,backup);
            CuisineJournalLog.LOGGER.error("Unreadable configuration preserved at {}; fix the original and reload",backup);
            return true;
        } catch (IOException | RuntimeException failure) {
            CuisineJournalLog.LOGGER.error("Cannot preserve {}; edits are read-only until a successful reload",file,failure);
            return false;
        }
    }
}
