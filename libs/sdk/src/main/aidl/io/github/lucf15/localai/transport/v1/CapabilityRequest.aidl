package io.github.lucf15.localai.transport.v1;

// Experimental wire contract. No engine, model ID or prompt crosses this boundary.
parcelable CapabilityRequest {
    String capability;
    String language;
    String text;
}
