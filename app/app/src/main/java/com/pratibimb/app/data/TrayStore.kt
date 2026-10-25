package com.pratibimb.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.pratibimb.app.engine.DecisionJson
import com.pratibimb.app.ui.HeldTrayItem
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Held notifications on disk, so a held item survives the app being killed. The file is
// encrypted with an AES-GCM key that lives in the Android Keystore and never leaves it; the
// entry is removed as soon as an item is released. The original tap action (a PendingIntent)
// cannot be stored, so a restored item opens its app instead.
object TrayStore {
    private const val FILE_NAME = "held_tray.bin"
    private const val KEY_ALIAS = "pratibimb_tray"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    @Synchronized
    fun save(context: Context, items: List<HeldTrayItem>) {
        val file = File(context.filesDir, FILE_NAME)
        if (items.isEmpty()) {
            file.delete()
            return
        }
        try {
            val json = JSONArray(items.map { toJson(it) }).toString()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            file.writeBytes(byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(json.toByteArray()))
        } catch (e: GeneralSecurityException) {
            NotificationLog.add("TrayStore", "Could not save the tray: ${e.message}")
        } catch (e: IOException) {
            NotificationLog.add("TrayStore", "Could not save the tray: ${e.message}")
        }
    }

    // An unreadable file (key reset, corruption) is deleted rather than retried forever.
    fun load(context: Context): List<HeldTrayItem> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val bytes = file.readBytes()
            val ivSize = bytes[0].toInt()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 1, ivSize))
            val json = String(cipher.doFinal(bytes, 1 + ivSize, bytes.size - 1 - ivSize))
            val array = JSONArray(json)
            (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        } catch (e: GeneralSecurityException) {
            discard(file, e)
        } catch (e: IOException) {
            discard(file, e)
        } catch (e: JSONException) {
            discard(file, e)
        } catch (e: RuntimeException) {
            discard(file, e)
        }
    }

    private fun discard(file: File, e: Exception): List<HeldTrayItem> {
        NotificationLog.add("TrayStore", "Saved tray unreadable, discarded: ${e.message}")
        file.delete()
        return emptyList()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun toJson(item: HeldTrayItem): JSONObject =
        JSONObject()
            .put("id", item.id)
            .put("app", item.app)
            .put("title", item.title)
            .put("text", item.text)
            .put("sender", item.sender ?: JSONObject.NULL)
            .put("packageName", item.packageName)
            .put("heldReason", item.heldReason)
            .put("timeAgo", item.timeAgo)
            .put("minutesUntilRelease", item.minutesUntilRelease)
            .put("decision", DecisionJson.encode(item.decision))
            .put("senderHash", item.senderHash ?: JSONObject.NULL)
            .put("heldAtMillis", item.heldAtMillis)
            .put("flag", item.flag ?: JSONObject.NULL)

    private fun fromJson(json: JSONObject): HeldTrayItem =
        HeldTrayItem(
            id = json.getString("id"),
            app = json.getString("app"),
            title = json.getString("title"),
            text = json.getString("text"),
            sender = json.optStringOrNull("sender"),
            packageName = json.getString("packageName"),
            heldReason = json.getString("heldReason"),
            timeAgo = json.getString("timeAgo"),
            minutesUntilRelease = json.getInt("minutesUntilRelease"),
            decision = DecisionJson.decode(json.getString("decision")),
            contentIntent = null,
            senderHash = json.optStringOrNull("senderHash"),
            heldAtMillis = json.getLong("heldAtMillis"),
            // Files saved before flags existed have no key.
            flag = if (json.has("flag")) json.optStringOrNull("flag") else null,
        )

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}
