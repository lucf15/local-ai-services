package io.github.lucf15.localai.transport.v1;
parcelable CapabilityAvailability {
    String capability;
    String[] languages;
    int maxInputUtf8Bytes;
    boolean installed;
    boolean experimental = true;
}
