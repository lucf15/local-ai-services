# Binder contract

The Android SDK AIDL compiler, invoked by AGP, generates structured parcelables,
stubs and proxies from `libs/sdk/src/main/aidl`. Generated Java stays in
build outputs. Callers do not implement parcel serialization themselves.

`checkBinderApi` uses the SDK compiler’s `--checkapi=compatible` against the frozen
`libs/sdk/api/v1` baseline. `dumpBinderApi` generates a reviewable API dump.
Do not replace the baseline to conceal a compatibility failure. This uses the
SDK compiler directly, not Android platform/Soong Stable AIDL release management.

Protocol 2 appends capability discovery and progress acknowledgement. The real
service admits one operation; progress is cumulative and coalesced, with at most
one unacknowledged progress callback. A terminal success contains the full text.
Cancellation completes after native cleanup. A dead callback cancels its operation.

An operation is scoped to the UID and current signing identity captured at
admission. The SDK has typed errors, input validation, binding lifecycle ownership
and no automatic request replay. Android’s custom dangerous permission protects
the exported service before a client can bind.

The client SDK and wire contract live in one module. Both the manager and client
use the generated classes, and the sample is independently signed. Android tests
exercise real service access boundaries; opt-in probes cover native cancellation,
backpressure and manager death.
