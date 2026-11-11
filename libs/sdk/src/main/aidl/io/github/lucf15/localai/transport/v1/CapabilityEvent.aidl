package io.github.lucf15.localai.transport.v1;

// Text is a cumulative snapshot, not a token delta. Terminal events include final text.
parcelable CapabilityEvent {
    const int PROGRESS = 0;
    const int COMPLETED = 1;
    const int CANCELLED = 2;
    const int FAILED = 3;
    int sequence;
    int status;
    String text;
    String errorCode;
}
