package io.github.lucf15.localai.transport.v1;
import io.github.lucf15.localai.transport.v1.CapabilityRequest;
import io.github.lucf15.localai.transport.v1.ICapabilityCallback;
import io.github.lucf15.localai.transport.v1.ICapabilityOperation;
import io.github.lucf15.localai.transport.v1.CapabilityAvailability;

interface ILocalAiService {
    int getProtocolVersion();
    // Short admission call only. Execution and results are asynchronous.
    ICapabilityOperation submit(in CapabilityRequest request, ICapabilityCallback callback);
    CapabilityAvailability[] getCapabilities();
}
