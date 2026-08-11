# Firebase

## Configuration

In order to make this module only know about Firebase, the plugin `com.google.gms.google-services` has been disabled from the `app` module.

To be able to change the values set to `google_app_id` in the file `build.gradle.kts` of this module, you should enable the plugin `com.google.gms.google-services` again, copy the file `google-services.json` to the folder `/app/src/main`, build the project, and check the generated file `app/build/generated/res/google-services/<buildtype>/values/values.xml` to import the generated values into the `build.gradle.kts` files.

## API key (do not commit)

`google_api_key` is injected at build time — it must **not** live in `src/main/res`.

Set one of:

- `local.properties` (gitignored):

```properties
firebase.apiKey=AIza...
```

- or CI / shell env: `ARCANA_FIREBASE_API_KEY`

Restrict the key in Google Cloud Console to Android apps (`ru.celesteai.arcana` + release SHA-1).
If a key was committed to git, rotate it and delete the old one.
