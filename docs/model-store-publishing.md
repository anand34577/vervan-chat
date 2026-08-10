# Publishing the Vervan Model Store catalogue

The Android app expects the Model Store catalogue to be hosted separately from the app source. A small public GitHub repository with GitHub Pages is a good fit because the catalogue is metadata, not the model weights themselves.

The app downloads model artifacts from the sources listed in the catalogue, normally Hugging Face revisions pinned to immutable commit SHAs. GitHub hosts the catalogue files and signature; it does not need to host multi-gigabyte model weights.

## 1. Create a separate GitHub repository

Create a repository such as `vervan-model-store`. Its layout should keep the `docs` directory itself:

```text
vervan-model-store/
└── docs/
    └── api/
        └── v1/
            ├── latest.json
            ├── catalog.json
            └── catalog.json.sig
```

Copy the `docs/` directory from the sample catalogue into that repository. Do not flatten it into the repository root: the raw GitHub fallback expects `/main/docs/api/v1/latest.json`, while GitHub Pages serves the same files from `/api/v1/latest.json` when Pages uses the `/docs` folder.

## 2. Generate a real signing key

Do not use the private key included with the sample. Generate a new P-256 keypair using OpenSSL, and keep the private key outside all public repositories:

```bash
openssl ecparam -name prime256v1 -genkey -noout -out catalog-signing-private.pem
openssl ec -in catalog-signing-private.pem -pubout -out catalog-signing-public.pem
openssl ec -in catalog-signing-private.pem -pubout -outform DER -out catalog-signing-public.der
```

On PowerShell, print the base64 public key that will be embedded in the app:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("catalog-signing-public.der"))
```

The output is a public verification value. The `catalog-signing-private.pem` file is the secret and must never be committed, uploaded, or placed in `local.properties`.

## 3. Prepare the catalogue

Edit `docs/api/v1/catalog.json` using real values. Every installable variant should have:

- a supported runtime and the correct artifact roles;
- a 64-character lowercase SHA-256 for every artifact;
- a full 40-character Hugging Face commit SHA, never `main`, a branch, or a moving tag;
- correct file sizes, install paths, ABI requirements, and RAM estimates;
- a model card URL and accurate license information;
- a higher `catalogVersion` whenever you publish an update.

The model weights are not trusted merely because they came from a known provider. The app downloads them, hashes the bytes, and compares the result with the signed catalogue entry.

## 4. Sign the exact catalogue bytes

From the sample directory, use the supplied script with your private key. Run it from Git Bash, WSL, or another environment with Bash and OpenSSL:

```bash
./sign-catalog.sh /secure/path/catalog-signing-private.pem
```

This writes `docs/api/v1/catalog.json.sig`. The signature covers the raw bytes of `catalog.json`, so sign again after every edit. Bump `catalogVersion` before signing an update; the script does not safely choose a version for you.

If you prefer PowerShell, the equivalent is:

```powershell
openssl dgst -sha256 -sign C:/secure/catalog-signing-private.pem -out catalog.sig docs/api/v1/catalog.json
[Convert]::ToBase64String([IO.File]::ReadAllBytes("catalog.sig")) | Set-Content -NoNewline docs/api/v1/catalog.json.sig
Remove-Item -LiteralPath catalog.sig
```

## 5. Point `latest.json` at the published files

Replace the placeholders in `docs/api/v1/latest.json` with your GitHub username and repository name:

```json
{
  "catalogVersion": 1,
  "catalogUrl": "https://YOUR_GITHUB_USERNAME.github.io/YOUR_CATALOG_REPO/api/v1/catalog.json",
  "signatureUrl": "https://YOUR_GITHUB_USERNAME.github.io/YOUR_CATALOG_REPO/api/v1/catalog.json.sig",
  "minimumAppVersion": 0
}
```

Keep `catalogVersion` equal to the signed catalogue’s `catalogVersion`. The app enforces compatibility through each variant’s `requirements.minAppVersion`; the pointer’s `minimumAppVersion` is currently informational.

## 6. Enable GitHub Pages

Push the catalogue repository, then open:

`Settings → Pages → Build and deployment → Deploy from a branch → main → /docs`

After GitHub finishes deploying, verify these URLs in a browser:

```text
https://YOUR_GITHUB_USERNAME.github.io/YOUR_CATALOG_REPO/api/v1/latest.json
https://YOUR_GITHUB_USERNAME.github.io/YOUR_CATALOG_REPO/api/v1/catalog.json
https://YOUR_GITHUB_USERNAME.github.io/YOUR_CATALOG_REPO/api/v1/catalog.json.sig
```

The app can also use the raw GitHub fallback:

```text
https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/YOUR_CATALOG_REPO/main/docs/api/v1/latest.json
```

GitHub Pages is still useful as the primary endpoint because it gives you the clean `/api/v1/...` URLs shown in the sample.

## 7. Configure the Android app

In the Android repository’s uncommitted `local.properties`, add the public key and both catalogue endpoints:

```properties
catalog.publicKeys=<BASE64_PUBLIC_KEY>
catalog.endpoints=https://YOUR_GITHUB_USERNAME.github.io/YOUR_CATALOG_REPO/api/v1/latest.json,https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/YOUR_CATALOG_REPO/main/docs/api/v1/latest.json
```

For CI, use the equivalent environment variables:

```text
VERVAN_CATALOG_PUBLIC_KEYS
VERVAN_CATALOG_ENDPOINTS
```

The app derives its HTTPS host allowlist from the configured endpoint hosts. Do not configure a host that you do not control or intentionally trust.

## 8. Add the optional offline bootstrap

If the Store should show the same catalogue before the first network sync, copy the signed catalogue body—not the pointer or signature—to:

```text
app/src/main/assets/store/bootstrap-catalog.json
```

Create the directory if it does not exist. The bootstrap is inside the signed APK, so it is parsed as trusted local content. Keep it in sync with the catalogue you intend to ship.

## 9. Test before publishing a release

Run the unit tests and the release verification task:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:bundleRelease
```

On a real device, check all of the following:

- the Store opens with the bootstrap catalogue when offline;
- Refresh downloads `latest.json`, `catalog.json`, and the detached signature;
- a modified catalogue or signature is rejected;
- an incompatible variant is hidden before downloading gigabytes of data;
- a gated model requires license acknowledgement;
- an interrupted install resumes and a bad SHA-256 does not become installable;
- uninstalling a model does not delete blobs still shared by another installed variant.

Useful Logcat tags are `CatalogRepository`, `CatalogSigVerifier`, `CatalogParser`, and `ModelStoreRepository`.

## Updating the catalogue

For each release of the catalogue:

1. edit `catalog.json`;
2. increase `catalogVersion`;
3. verify every source revision and SHA-256;
4. sign the exact file again;
5. update `latest.json` to the same version;
6. push the three files together; and
7. test the Store on a device before announcing the update.

For key rotation, ship an app release that trusts both the old and new public keys, start signing with the new private key, then remove the old key only after the older app population is no longer expected to receive catalogue updates.

## Never publish these files

Never commit or upload:

- `catalog-signing-private.pem`;
- any password-protected key’s password;
- user Hugging Face tokens;
- private model files that you do not have redistribution rights to publish.
