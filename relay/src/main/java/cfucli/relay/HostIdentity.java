package cfucli.relay;

import module java.base;

public record HostIdentity(String sessionId, String password, KeyPair keyPair, byte[] pairingSecret) {

    public byte[] publicKey() {
        return KeyExchange.encode(keyPair.getPublic());
    }

    public static HostIdentity create(String sessionId, String password) {
        return new HostIdentity(sessionId, password, KeyExchange.newKeyPair(), PairingSecret.derive(password, sessionId));
    }
}
