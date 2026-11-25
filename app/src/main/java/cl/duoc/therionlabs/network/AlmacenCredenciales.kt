package cl.duoc.therionlabs.network

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AlmacenCredenciales {
    @Synchronized
    private fun llave(): SecretKey {
        val almacen = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (almacen.getKey("sesiones_therionlabs", null) as? SecretKey)?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            "sesiones_therionlabs",
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
            }
            .generateKey()
    }

    fun cifrar(texto: String): String {
        val cifrador =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, llave()) }
        val datos = cifrador.doFinal(texto.toByteArray(Charsets.UTF_8))
        return "v1:" +
            Base64.encodeToString(cifrador.iv, Base64.NO_WRAP) +
            ":" +
            Base64.encodeToString(datos, Base64.NO_WRAP)
    }

    fun leer(texto: String?): String? = runCatching {
        if (texto == null) return null
        if (!texto.startsWith("v1:")) return texto
        val partes = texto.split(':')
        val cifrador =
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    Cipher.DECRYPT_MODE,
                    llave(),
                    GCMParameterSpec(128, Base64.decode(partes[1], Base64.NO_WRAP)),
                )
            }
        String(cifrador.doFinal(Base64.decode(partes[2], Base64.NO_WRAP)), Charsets.UTF_8)
    }
        .getOrNull()
}
