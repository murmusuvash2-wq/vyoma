package com.vyoma.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Durable project domain model.
 *
 * A project is the parent record. Its blueprint is a versioned child record
 * linked by projectId, so a blueprint can never be an orphaned standalone item.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val projectId: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val status: String = "draft",
    val activeBlueprintVersion: Int = 1,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "blueprints",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index(value = ["projectId", "version"], unique = true)]
)
data class BlueprintEntity(
    @PrimaryKey val blueprintId: String = UUID.randomUUID().toString(),
    val projectId: String,
    val version: Int = 1,
    val goal: String,
    val screens: String,
    val data: String,
    val integrations: String,
    val permissions: String,
    val edgeCases: String,
    val acceptanceTests: String,
    val approvalStatus: String = "draft",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

data class ProjectWithBlueprints(
    @Embedded val project: ProjectEntity,
    @Relation(parentColumn = "projectId", entityColumn = "projectId")
    val blueprints: List<BlueprintEntity>
)

@Dao
interface ProjectDao {
    @Transaction
    @Query("SELECT * FROM projects ORDER BY updatedAtEpochMs DESC")
    fun observeProjects(): Flow<List<ProjectWithBlueprints>>

    @Transaction
    @Query("SELECT * FROM projects WHERE projectId = :projectId LIMIT 1")
    fun observeProject(projectId: String): Flow<ProjectWithBlueprints?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBlueprint(blueprint: BlueprintEntity)

    @Query("UPDATE projects SET name = :name, description = :description, status = :status, activeBlueprintVersion = :blueprintVersion, updatedAtEpochMs = :updatedAt WHERE projectId = :projectId")
    suspend fun updateProjectSummary(
        projectId: String,
        name: String,
        description: String,
        status: String,
        blueprintVersion: Int,
        updatedAt: Long
    )

    @Query("UPDATE blueprints SET goal = :goal, screens = :screens, data = :data, integrations = :integrations, permissions = :permissions, edgeCases = :edgeCases, acceptanceTests = :acceptanceTests, approvalStatus = :approvalStatus, updatedAtEpochMs = :updatedAt WHERE projectId = :projectId AND version = :version")
    suspend fun updateBlueprint(
        projectId: String,
        version: Int,
        goal: String,
        screens: String,
        data: String,
        integrations: String,
        permissions: String,
        edgeCases: String,
        acceptanceTests: String,
        approvalStatus: String,
        updatedAt: Long
    )

    @Transaction
    suspend fun createProjectWithBlueprint(project: ProjectEntity, blueprint: BlueprintEntity) {
        require(project.projectId == blueprint.projectId) {
            "Project and blueprint must share the same projectId."
        }
        insertProject(project)
        insertBlueprint(blueprint)
    }

    @Transaction
    suspend fun saveBlueprintForProject(
        projectId: String,
        blueprint: BlueprintEntity,
        projectName: String,
        projectDescription: String,
        status: String
    ) {
        require(blueprint.projectId == projectId) {
            "Blueprint belongs to a different project."
        }
        val now = System.currentTimeMillis()
        insertBlueprint(blueprint)
        updateProjectSummary(
            projectId = projectId,
            name = projectName,
            description = projectDescription,
            status = status,
            blueprintVersion = blueprint.version,
            updatedAt = now
        )
    }
}

@Database(
    entities = [ProjectEntity::class, BlueprintEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VyomaDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var instance: VyomaDatabase? = null

        fun getInstance(context: Context): VyomaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VyomaDatabase::class.java,
                    "vyoma-projects.db"
                ).build().also { instance = it }
            }
    }
}

/** Small domain boundary so UI code does not depend on Room implementation details. */
class ProjectRepository(context: Context) {
    private val dao = VyomaDatabase.getInstance(context).projectDao()

    fun observeProjects(): Flow<List<ProjectWithBlueprints>> = dao.observeProjects()

    fun observeProject(projectId: String): Flow<ProjectWithBlueprints?> =
        dao.observeProject(projectId)

    suspend fun createProject(
        name: String,
        goal: String,
        screens: String = "Home",
        data: String = "Not specified yet",
        integrations: String = "None specified",
        permissions: String = "No special permissions specified",
        edgeCases: String = "Handle empty input and recoverable errors",
        acceptanceTests: String = "App opens and the primary flow works"
    ): String {
        require(name.isNotBlank()) { "Project name cannot be blank." }
        require(goal.isNotBlank()) { "Project goal cannot be blank." }
        val projectId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val project = ProjectEntity(
            projectId = projectId,
            name = name.trim(),
            description = goal.trim(),
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        val blueprint = BlueprintEntity(
            projectId = projectId,
            goal = goal.trim(),
            screens = screens,
            data = data,
            integrations = integrations,
            permissions = permissions,
            edgeCases = edgeCases,
            acceptanceTests = acceptanceTests,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        dao.createProjectWithBlueprint(project, blueprint)
        return projectId
    }

    suspend fun saveBlueprintVersion(
        project: ProjectWithBlueprints,
        goal: String,
        screens: String,
        data: String,
        integrations: String,
        permissions: String,
        edgeCases: String,
        acceptanceTests: String
    ) {
        require(goal.isNotBlank()) { "Project goal cannot be blank." }
        val current = project.blueprints.maxByOrNull { it.version }
            ?: error("This project has no blueprint to update.")
        val now = System.currentTimeMillis()
        val next = current.copy(
            blueprintId = UUID.randomUUID().toString(),
            version = current.version + 1,
            goal = goal.trim(),
            screens = screens.trim(),
            data = data.trim(),
            integrations = integrations.trim(),
            permissions = permissions.trim(),
            edgeCases = edgeCases.trim(),
            acceptanceTests = acceptanceTests.trim(),
            approvalStatus = "draft",
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        dao.saveBlueprintForProject(
            projectId = project.project.projectId,
            blueprint = next,
            projectName = project.project.name,
            projectDescription = goal.trim(),
            status = "draft"
        )
    }
}
