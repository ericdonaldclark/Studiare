package net.ericclark.studiare.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateSetDao {
    @Query("SELECT * FROM template_sets WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getAllActiveTemplateSets(): Flow<List<TemplateSet>>

    @Query("SELECT * FROM template_sets WHERE isPendingSync = 1")
    fun getPendingSyncTemplateSets(): List<TemplateSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(templateSet: TemplateSet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateAll(templateSets: List<TemplateSet>)

    @Query("UPDATE template_sets SET isDeleted = 1, isPendingSync = 1 WHERE id = :id")
    fun softDelete(id: String)

    @Query("DELETE FROM template_sets WHERE id = :id")
    fun hardDelete(id: String)

    @Query("DELETE FROM template_sets WHERE isDeleted = 1")
    fun purgeDeletedTemplateSets()
}
