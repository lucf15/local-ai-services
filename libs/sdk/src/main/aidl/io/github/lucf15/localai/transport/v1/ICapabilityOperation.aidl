package io.github.lucf15.localai.transport.v1;
interface ICapabilityOperation {
    void cancel();
    // Protocol 2: one unacknowledged progress snapshot per operation.
    void acknowledge(int sequence);
}
