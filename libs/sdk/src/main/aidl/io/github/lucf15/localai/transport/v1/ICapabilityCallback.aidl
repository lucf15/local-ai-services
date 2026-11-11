package io.github.lucf15.localai.transport.v1;
import io.github.lucf15.localai.transport.v1.CapabilityEvent;
oneway interface ICapabilityCallback {
    void onEvent(in CapabilityEvent event);
}
