# Integrating the Kotlin SDK

The complete reference client is [Sample](../apps/sample/src/main/kotlin/io/github/lucf15/localai/reader/ExampleScreen.kt).
It runs in a separate process with a separate signing certificate from Local AI.

## Add the source dependency

Inside this checkout, depend on `project(":libs:sdk")`. It includes the generated AIDL contract and coroutine dependency. No Maven package is published.

To build local library artifacts:

```sh
./gradlew :libs:sdk:assembleRelease
```

The AAR is `libs/sdk/build/outputs/aar/sdk-release.aar`. It contains the Kotlin
client and generated wire types. An external app using a raw AAR must also include
`org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0`. The artifact contains no
model weights or inference runtime.

## Request Android permission

Install Local AI before the client. The SDK manifest declares the custom dangerous
`io.github.lucf15.localai.runner.permission.USE_LOCAL_AI` permission. Request
`LocalAiClient.PERMISSION` with `ActivityResultContracts.RequestPermission` before
collecting a request. `hasAccess()` checks the grant; `accessSettingsIntent()` opens
the client’s Android settings for changes. The manager cannot grant access itself.

Capture the capability, text and language before requesting permission so a
successful callback executes exactly the request the user authorized. Do not
persist a pending request across process death and unexpectedly submit it later.

## Execute and cancel

Construct `TransformRequest(Capability.SUMMARIZE, text, Language.ENGLISH)` and
collect `LocalAiClient(context).transform(request)` from an owned coroutine.
`Ready` reports current availability; `Text.value` is cumulative output and
`Text.complete` indicates completion. Replace displayed text on each update.

Cancel the collecting job to cancel native work. Cleanup can take time: the SDK
keeps the binding alive until a terminal response, manager disconnect or cleanup
timeout. Reject new requests while the previous request is still cleaning up.
Popping an example in Sample clears its Navigation3-scoped ViewModel and cancels
the request. If another request arrives before native cleanup has finished, the
manager rejects it with BUSY; the client can let the user try again.

Errors are `LocalAiException` with a `LocalAiError`, including PERMISSION_DENIED,
MODEL_NOT_INSTALLED, BUSY, MANAGER_UNAVAILABLE, INPUT_TOO_LARGE and TIMED_OUT.
No request is automatically retried after process death.

## Current limits

The current CPU service uses the pinned Qwen3 0.6B INT4 artifact. It accepts
summarize/rewrite/proofread, Italian or English, nonblank source text up to 1,024
UTF-8 bytes and one admitted operation at a time. Output is bounded at 16,384
characters and execution has a 300-second deadline. Token limits can produce an
incomplete answer; semantic quality is experimental and requires review.

The bound service is not a privileged system service or a guarantee of permanent
background availability. Apps need this explicit integration; Local AI does not
inject tools into other installed applications.
