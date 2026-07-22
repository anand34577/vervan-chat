package com.vervan.chat.store.catalog

import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Verifies a catalogue document against the public key(s) built into the APK.
 *
 * Algorithm is SHA256withECDSA over P-256. Ed25519 would be the more modern choice but
 * `java.security.Signature` only exposes it from API 33, and this app's minSdk is 26 — bundling a
 * crypto library purely for signature verification is a much larger attack surface than using the
 * platform primitive that has been present since before minSdk.
 *
 * **The public key ships only inside the APK and is never published alongside the catalogue**
 *. Serving the key next to the thing it authenticates would make the signature
 * decorative: anyone who could replace the catalogue could replace the key with their own.
 *
 * ### Key rotation
 * [trustedKeys] is a list, and every key in it is tried, so a rotation is a three-release dance
 * with no flag day: ship a release trusting {old, new} -> start signing with new once that release
 * is the minimum supported version -> ship a release trusting {new} only. A single-key field would
 * make rotation impossible without bricking older installs, which is why this is plural from day
 * one rather than "when we need it".
 */
class CatalogSignatureVerifier(private val trustedKeys: List<PublicKey>) {

    /**
     * @param payload the exact catalogue bytes as fetched — verification is over raw bytes, never
     *   over a re-serialised parse, because any normalisation difference would break the signature
     *   for reasons that look like tampering.
     * @return true when any trusted key verifies the signature.
     */
    fun verify(payload: ByteArray, signature: ByteArray): Boolean {
        if (trustedKeys.isEmpty()) {
            // A build with no trusted key must fail closed rather than accept everything. This is
            // the "someone stripped the key constant" case, and it should be loud.
            Log.e(TAG, "No trusted catalogue keys are compiled into this build; rejecting.")
            return false
        }
        return trustedKeys.any { key ->
            try {
                Signature.getInstance(SIGNATURE_ALGORITHM).run {
                    initVerify(key)
                    update(payload)
                    verify(signature)
                }
            } catch (t: Throwable) {
                // A malformed signature throws rather than returning false; that is still just a
                // failed verification against this key, so try the next one.
                Log.w(TAG, "Verification attempt failed: ${t.message}")
                false
            }
        }
    }

    companion object {
        private const val TAG = "CatalogSigVerifier"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

        /**
         * Base64 X.509 SubjectPublicKeyInfo blobs for every currently-trusted signing key.
         *
         * TODO(store): replace with the real P-256 public key before the store ships. Generate the
         * keypair offline and keep the private half off any build machine:
         *
         *   openssl ecparam -name prime256v1 -genkey -noout -out catalog-signing.pem
         *   openssl ec -in catalog-signing.pem -pubout -outform DER | base64 -w0
         *
         * An empty list here means [verify] rejects everything, so a build that forgets this step
         * degrades to "store never syncs" rather than "store trusts anything".
         */
        val EMBEDDED_PUBLIC_KEYS_BASE64: List<String> = emptyList()

        fun fromEmbeddedKeys(
            encodedKeys: List<String> = EMBEDDED_PUBLIC_KEYS_BASE64
        ): CatalogSignatureVerifier {
            val keyFactory = KeyFactory.getInstance("EC")
            val keys = encodedKeys.mapNotNull { encoded ->
                try {
                    keyFactory.generatePublic(
                        X509EncodedKeySpec(Base64.decode(encoded, Base64.DEFAULT))
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "Embedded catalogue key is unusable: ${t.message}")
                    null
                }
            }
            return CatalogSignatureVerifier(keys)
        }
    }
}
