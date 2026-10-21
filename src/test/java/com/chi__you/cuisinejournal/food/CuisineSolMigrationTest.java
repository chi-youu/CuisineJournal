package com.chi__you.cuisinejournal.food;

import net.minecraft.nbt.CompoundTag;

/** Tests import selection and backup isolation without initializing item registries. */
public final class CuisineSolMigrationTest {
    private CuisineSolMigrationTest() {
    }

    public static void main(String[] args) {
        CompoundTag top = new CompoundTag();
        CompoundTag legacy = new CompoundTag();
        legacy.putString("id0", "minecraft:bread");
        legacy.putInt("ticks0", 4000);
        CompoundTag itemData = new CompoundTag();
        itemData.putString("variant", "original");
        legacy.put("data0", itemData);
        top.put("sol_food_data", legacy);
        CompoundTag persistent = new CompoundTag();

        CuisineSolMigration.capture(top, persistent);
        check(CuisineSolMigration.pendingLegacy(persistent).getInt("ticks0") == 4000,
                "top-level SOL data is selected");
        legacy.putInt("ticks0", 1);
        itemData.putString("variant", "changed");
        check(persistent.getCompound(CuisineSolMigration.BACKUP).getInt("ticks0") == 4000,
                "backup is not an alias");
        check(persistent.getCompound(CuisineSolMigration.BACKUP).getCompound("data0")
                .getString("variant").equals("original"), "nested NBT is copied");
        persistent.put(CuisineFoodState.ROOT, new CompoundTag());
        CuisineSolMigration.capture(top, persistent);
        check(persistent.getCompound(CuisineSolMigration.BACKUP).getInt("ticks0") == 4000,
                "existing backup is retained");

        persistent.remove(CuisineFoodState.ROOT);
        CuisineSolMigration.capture(top, persistent);
        check(persistent.getCompound(CuisineSolMigration.BACKUP).getInt("ticks0") == 1,
                "not-yet-imported legacy state stays current");
        legacy.putInt("ticks0", 4000);
        persistent.put(CuisineSolMigration.BACKUP, new CompoundTag());
        CuisineSolMigration.capture(top, persistent);
        check(persistent.getCompound(CuisineSolMigration.BACKUP).getInt("ticks0") == 4000,
                "empty backup can be recovered");

        persistent.putBoolean(CuisineSolMigration.IMPORTED, true);
        check(CuisineSolMigration.pendingLegacy(persistent) == null,
                "import marker prevents replay even if new state is absent");
        persistent.remove(CuisineSolMigration.IMPORTED);
        persistent.put(CuisineFoodState.ROOT, new CompoundTag());
        check(CuisineSolMigration.pendingLegacy(persistent) == null, "new empty state wins");
        persistent.putString(CuisineFoodState.ROOT, "unknown-format");
        check(CuisineSolMigration.pendingLegacy(persistent) == null, "malformed new state is not overwritten");

        CompoundTag olderPersistent = new CompoundTag();
        olderPersistent.put("sol_food_data", legacy.copy());
        check(CuisineSolMigration.pendingLegacy(olderPersistent) != null, "ForgeData fallback works");
        check(CuisineSolMigration.pendingLegacy(new CompoundTag()) == null, "new players have nothing to import");
        persistent.putBoolean(CuisineSolMigration.IMPORTED, true);
        CompoundTag replacement = new CompoundTag();
        CuisineSolMigration.copyHistory(persistent, replacement);
        check(replacement.getBoolean(CuisineSolMigration.IMPORTED), "clone retains replay guard");
        persistent.getCompound(CuisineSolMigration.BACKUP).putInt("ticks0", 5);
        check(replacement.getCompound(CuisineSolMigration.BACKUP).getInt("ticks0") == 4000,
                "clone backup is an independent copy");
        check(CuisineSolMigration.pendingLegacy(replacement) == null, "clone does not reimport old food");
        CompoundTag fallbackClone = new CompoundTag();
        CuisineSolMigration.copyHistory(olderPersistent, fallbackClone);
        check(fallbackClone.getCompound(CuisineSolMigration.BACKUP).getInt("ticks0")
                == olderPersistent.getCompound("sol_food_data").getInt("ticks0"),
                "clone retains ForgeData legacy fallback");
        olderPersistent.getCompound("sol_food_data").putInt("ticks0", 7);
        check(fallbackClone.getCompound(CuisineSolMigration.BACKUP).getInt("ticks0") != 7,
                "fallback clone backup is an independent copy");
        CompoundTag emptyBackup = new CompoundTag();
        emptyBackup.put(CuisineSolMigration.BACKUP, new CompoundTag());
        emptyBackup.put("sol_food_data", legacy.copy());
        check(CuisineSolMigration.pendingLegacy(emptyBackup).getInt("ticks0") == 4000,
                "empty backup does not hide ForgeData fallback");
        CompoundTag emptyBackupClone = new CompoundTag();
        CuisineSolMigration.copyHistory(emptyBackup, emptyBackupClone);
        check(emptyBackupClone.getCompound(CuisineSolMigration.BACKUP).getInt("ticks0") == 4000,
                "empty backup clone uses ForgeData fallback");
        System.out.println("CuisineSolMigrationTest: 18 checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
