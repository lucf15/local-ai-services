# Architecture

Local AI is a normal Android application exposing a permission-protected bound
service. Sample is an independent client. Kotlin requests describe a capability,
source text and result language; native handles and prompts stay in the manager.

The service captures the caller UID and current signing identity at admission.
The dangerous USE_LOCAL_AI permission protects binding, while operations are
scoped to their admitting caller. Shared UIDs are rejected. A single native
inference lease prevents concurrent engine use. Input/output limits, deadlines,
callback backpressure, client death and cancellation have explicit handling.

The runtime shares no conversation between requests. The native adapter owns a
dedicated executor and waits for native termination before closing resources.
The SDK keeps its binding during cancellation cleanup and never silently replays
requests after a disconnect. Normal service requests persist no transcript.

WorkManager owns downloads independently of screen visibility. Artifacts are
pinned by repository revision, size and SHA-256; verification precedes atomic
activation in app-private storage. The manager exposes the full download catalog and distinguishes research
candidates from the Qwen3 configuration currently consumed by connected apps.

Compose state uses stable readonly interfaces with private property-level
snapshot state and atomic related updates. UI publication happens on Main;
installation and native work happen off Main. Repository and SDK streams retain
Flow where their asynchronous contracts require it.

Local AI manages models and access; Sample owns the example/editor/result flows.
Both use Material3 with separate layouts, native dynamic colors and shared basic
controls. Navigation3 owns serializable destination keys and a saved back stack.
Each entry has a ViewModel store; Sample editors retain their own source/language
through SavedStateHandle and cancel work when their entry is removed.
The platform owns the runtime permission dialog and adaptive launcher mask.

`EvaluationController`, synthetic cases and the offline reporting tool support
development work. They are separate from the manager’s visible product flow.
No model/configuration has passed a complete human quality review; installation
and successful inference do not establish semantic quality or phone performance.

## Modules

- `apps/local-ai`: manager UI, WorkManager downloads and service composition.
- `apps/sample`: separate client APK and example screens.
- `libs/sdk`: client Flow, binding and generated AIDL contract. It depends on neither runtime nor UI.
- `libs/runtime`: model metadata, installation, task execution and native adapter.
- `libs/ui`: shared theme, components, icons and launch windows.

The former standalone IPC fixture apps have been removed. The sample generates
its own local debug key and remains independently signed from the manager.
`data/` contains the pinned catalog, task definitions and synthetic evaluation cases.
