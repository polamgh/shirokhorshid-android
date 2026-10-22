package com.psiphon3.psiphonlibrary.internal;

/**
 * Runtime secret decoder — payloads are XOR-obfuscated at build time.
 * Class and method names are obfuscated by R8 in release builds.
 */
public final class Sd {
    private Sd() {
    }

    /** Latin-1 string literal(s) from the generated EmbeddedValues back to encoded bytes. */
    public static byte[] b(String... chunks) {
        StringBuilder joined = new StringBuilder();
        for (String chunk : chunks) {
            joined.append(chunk);
        }
        byte[] out = new byte[joined.length()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) joined.charAt(i);
        }
        return out;
    }

    public static String d(int seed, byte[] enc) {
        if (enc == null || enc.length == 0) {
            return "";
        }
        char[] out = new char[enc.length];
        for (int i = 0; i < enc.length; i++) {
            int k = (seed ^ (i * 31) ^ (i >>> 3)) & 0xFF;
            out[i] = (char) ((enc[i] & 0xFF) ^ k);
        }
        return new String(out);
    }

    public static String[] da(int baseSeed, byte[][] parts) {
        if (parts == null || parts.length == 0) {
            return new String[0];
        }
        String[] out = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = d(baseSeed ^ (i * 17), parts[i]);
        }
        return out;
    }
}
