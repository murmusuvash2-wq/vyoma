package com.vyoma.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small durable local store for the prototype. This deliberately stays on-device:
 * no account, network, or cloud sync is implied.
 */
internal object ProjectStore {
    private const val PREFS = "vyoma_project_store"
    private const val APPROVED = "approved_blueprints_v1"
    private const val PROJECTS = "local_projects_v1"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadBlueprints(context: Context): List<ApprovedBlueprint> = runCatching {
        val array = JSONArray(prefs(context).getString(APPROVED, "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    ApprovedBlueprint(
                        name = item.optString("name"),
                        goal = item.optString("goal"),
                        screens = item.optString("screens"),
                        data = item.optString("data"),
                        integrations = item.optString("integrations"),
                        permissions = item.optString("permissions"),
                        edgeCases = item.optString("edgeCases"),
                        acceptanceTests = item.optString("acceptanceTests"),
                        mcpTools = item.optString("mcpTools", "GitHub MCP, SQLite/Room DB, Jetpack Navigation")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun saveBlueprints(context: Context, blueprints: List<ApprovedBlueprint>) {
        val array = JSONArray()
        blueprints.forEach { blueprint ->
            array.put(
                JSONObject()
                    .put("name", blueprint.name)
                    .put("goal", blueprint.goal)
                    .put("screens", blueprint.screens)
                    .put("data", blueprint.data)
                    .put("integrations", blueprint.integrations)
                    .put("permissions", blueprint.permissions)
                    .put("edgeCases", blueprint.edgeCases)
                    .put("acceptanceTests", blueprint.acceptanceTests)
                    .put("mcpTools", blueprint.mcpTools)
            )
        }
        prefs(context).edit().putString(APPROVED, array.toString()).apply()
    }

    fun loadLocalProjects(context: Context): List<String> = runCatching {
        val array = JSONArray(prefs(context).getString(PROJECTS, "[]"))
        buildList {
            for (i in 0 until array.length()) add(array.optString(i))
        }
    }.getOrDefault(emptyList())

    fun saveLocalProjects(context: Context, projects: List<String>) {
        val array = JSONArray()
        projects.forEach(array::put)
        prefs(context).edit().putString(PROJECTS, array.toString()).apply()
    }
}
