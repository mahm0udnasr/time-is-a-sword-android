package com.mahm0udnasr.sword.data

import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** One JSON format for everything stored (and for backups, §7.5). */
val StoreJson = Json {
    ignoreUnknownKeys = true // a newer/older file never fails to load
    encodeDefaults = true // the file shows every field, like the extension's export
    prettyPrint = false
}

private class JsonSerializer<T>(private val ks: KSerializer<T>, override val defaultValue: T) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        StoreJson.decodeFromString(ks, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable JSON", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Invalid JSON content", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(StoreJson.encodeToString(ks, t).encodeToByteArray())
    }
}

/**
 * A typed JSON file on DataStore (D4), exposed as a [StateFlow] so the
 * accessibility service decides from memory and never reads disk per event.
 *
 * - DataStore writes to a temp file and renames it, so killing the app mid-write
 *   leaves the old file intact.
 * - If the file is unreadable anyway, a copy is kept as `<name>.corrupt-<time>`
 *   and the defaults are used (never crash, never lose the evidence).
 * - Every [update] goes through one [Mutex] (the extension's `chain` promise).
 *
 * Until the file is read, [flow] holds [default]; call [awaitLoaded] before
 * acting on it.
 */
class JsonStore<T>(
    private val file: File,
    ks: KSerializer<T>,
    private val default: T,
    scope: CoroutineScope,
    migrations: List<DataMigration<T>> = emptyList(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val store: DataStore<T> = DataStoreFactory.create(
        serializer = JsonSerializer(ks, default),
        corruptionHandler = ReplaceFileCorruptionHandler { e ->
            keepCorruptCopy(e)
            default
        },
        migrations = migrations,
        scope = scope,
        produceFile = { file },
    )

    private val mutex = Mutex()
    private val state = MutableStateFlow(default)
    private val loaded = CompletableDeferred<Unit>()

    /** Latest value, in memory. */
    val flow: StateFlow<T> = state.asStateFlow()

    /** Shortcut for the latest in-memory value. */
    val value: T get() = state.value

    init {
        scope.launch {
            store.data.collect {
                state.value = it
                loaded.complete(Unit)
            }
        }
    }

    suspend fun awaitLoaded() = loaded.await()

    /** Atomically transform the stored value; returns the new value. */
    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        store.updateData { transform(it) }.also { state.value = it }
    }

    private fun keepCorruptCopy(e: CorruptionException) {
        runCatching {
            if (file.exists()) file.copyTo(File(file.parentFile, "${file.name}.corrupt-${now()}"), overwrite = true)
        }
        runCatching { Log.e(TAG, "Corrupt ${file.name}; defaults used, copy kept", e) }
    }

    private companion object {
        const val TAG = "SwordStore"
    }
}
