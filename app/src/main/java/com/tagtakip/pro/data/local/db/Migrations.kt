package com.tagtakip.pro.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2:
 * - earnings: tür değerleri INCOME/EXPENSE'e dönüştürülür, `note` → `description`,
 *   kullanılmayan `platform` sütunu kaldırılır (SQLite DROP COLUMN desteklemediği için tablo yeniden oluşturulur).
 *   v1'de `type` sütunu zaten vardı; bu yüzden düz ALTER TABLE ADD COLUMN type hata verirdi.
 * - trips: `earning_amount` ve `description` sütunları eklenir.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `earnings_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`shift_id` INTEGER, `trip_id` INTEGER, `amount` REAL NOT NULL, " +
                "`type` TEXT NOT NULL DEFAULT 'INCOME', `description` TEXT, `timestamp` INTEGER NOT NULL, " +
                "FOREIGN KEY(`shift_id`) REFERENCES `shifts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                "FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL(
            "INSERT INTO `earnings_new` (`id`, `shift_id`, `trip_id`, `amount`, `type`, `description`, `timestamp`) " +
                "SELECT `id`, `shift_id`, `trip_id`, `amount`, " +
                "CASE WHEN `amount` < 0 THEN 'EXPENSE' ELSE 'INCOME' END, `note`, `timestamp` FROM `earnings`"
        )
        db.execSQL("DROP TABLE `earnings`")
        db.execSQL("ALTER TABLE `earnings_new` RENAME TO `earnings`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_earnings_shift_id` ON `earnings` (`shift_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_earnings_trip_id` ON `earnings` (`trip_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_earnings_timestamp` ON `earnings` (`timestamp`)")

        db.execSQL("ALTER TABLE `trips` ADD COLUMN `earning_amount` REAL")
        db.execSQL("ALTER TABLE `trips` ADD COLUMN `description` TEXT")
    }
}

/**
 * v2 → v3:
 * - vehicle_info: tek kayıtlık araç bilgisi tablosu (VehicleInfoEntity ile birebir aynı şema/varsayılanlar).
 * - trips: yolculuk başlangıç ekran görüntüsü için `screenshotPath` sütunu.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vehicle_info` (" +
                "`id` INTEGER NOT NULL, " +
                "`marka` TEXT NOT NULL DEFAULT '', " +
                "`model` TEXT NOT NULL DEFAULT '', " +
                "`yakitTuru` TEXT NOT NULL DEFAULT 'BENZIN', " +
                "`lt100km` REAL NOT NULL DEFAULT 0.0, " +
                "`litreBasiFiyat` REAL NOT NULL DEFAULT 0.0, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL("ALTER TABLE `trips` ADD COLUMN `screenshotPath` TEXT")
    }
}
