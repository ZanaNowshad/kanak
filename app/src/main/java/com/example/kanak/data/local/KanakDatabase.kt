package com.example.kanak.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProductEntity::class,
        CustomerEntity::class,
        SupplierEntity::class,
        PurchaseEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        StockAdjustmentEntity::class,
        SyncQueueEntity::class,
        StockLedgerEntity::class,
        DeviceStockAllowanceEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class KanakDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun customerDao(): CustomerDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun stockLedgerDao(): StockLedgerDao
    abstract fun deviceStockAllowanceDao(): DeviceStockAllowanceDao

    companion object {
        @Volatile
        private var INSTANCE: KanakDatabase? = null

        fun getInstance(context: Context): KanakDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KanakDatabase::class.java,
                    "kanak_local_cache.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
