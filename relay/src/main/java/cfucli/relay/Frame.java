package cfucli.relay;

import module java.base;

/** A record rather than @Data because the DataHelper processor rejects both byte[] and enum
 *  fields, which is every field here. Immutable, built once by the codec, never mutated. */
public record Frame(FrameType type, long seq, byte[] payload, Direction direction) {

    public Frame {
        if (payload == null) payload = new byte[0];
    }

    public static Frame of(FrameType type, long seq, byte[] payload, Direction direction) {
        return new Frame(type, seq, payload, direction);
    }

    public static Frame text(FrameType type, long seq, String text, Direction direction) {
        return new Frame(type, seq, text.getBytes(StandardCharsets.UTF_8), direction);
    }

    public String asText() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    public int size() {
        return payload.length;
    }

    @Override
    public String toString() {
        return type + " seq=" + seq + " " + direction.wire + " " + payload.length + "B";
    }
}
