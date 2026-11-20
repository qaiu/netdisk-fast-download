package cn.qaiu.util;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorSpecies;

import static jdk.incubator.vector.VectorOperators.*;

final class VectorBase64 {
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final int WIDTH = SPECIES.length();
    private static final int PACKED = WIDTH / 4 * 3;
    private static final VectorMask<Byte> PACKED_MASK = SPECIES.indexInRange(0, PACKED);
    // Each four-byte lane holds one quartet. Pack or expand its three binary bytes.
    private static final VectorShuffle<Byte> PACK = VectorShuffle.fromOp(SPECIES,
            i -> i < PACKED ? i / 3 * 4 + i % 3 : 0);
    private static final VectorShuffle<Byte> EXPAND = VectorShuffle.fromOp(SPECIES,
            i -> i / 4 * 3 + Math.min(i % 4, 2));

    // Each low-nibble entry is a bit set of allowed high nibbles.
    private static final class Alphabet {
        final ByteVector lowMasks;
        final ByteVector highMasks;
        final ByteVector offsets;
        final byte last;
        final ByteVector encodeTable;

        Alphabet(String letters) {
            byte[] low = new byte[Math.max(WIDTH, 16)];
            byte[] high = new byte[low.length];
            byte[] offset = new byte[low.length];
            byte[] chars = new byte[WIDTH];
            if (WIDTH >= 64) {
                for (int i = 0; i < 64; i++) {
                    chars[i] = (byte) letters.charAt(i);
                }
            }
            encodeTable = ByteVector.fromArray(SPECIES, chars, 0);
            last = (byte) letters.charAt(63);
            for (int i = 0; i < letters.length(); i++) {
                int ch = letters.charAt(i);
                int bucket = ch >>> 4;
                byte bit = (byte) (1 << bucket);
                high[bucket] = bit;
                low[ch & 15] |= bit;
                if (ch != last) {
                    offset[bucket] = (byte) (i - ch);
                }
            }
            lowMasks = ByteVector.fromArray(SPECIES, low, 0);
            highMasks = ByteVector.fromArray(SPECIES, high, 0);
            offsets = ByteVector.fromArray(SPECIES, offset, 0);
        }
    }

    private static final Alphabet BASIC = new Alphabet(Base64Encoder.BASIC);
    private static final Alphabet URL = new Alphabet(Base64Encoder.URL);

    private VectorBase64() {}

    static boolean available() {
        if (WIDTH < 16) {
            return false;
        }
        // Incubator APIs can change between JDK releases. Link both kernels before enabling them.
        byte[] plain = new byte[PACKED];
        byte[] encoded = new byte[WIDTH];
        byte[] decoded = new byte[PACKED];
        plain[0] = (byte) 0xfb;
        encode(plain, encoded, true);
        decode(encoded, WIDTH, decoded, true);
        return encoded[0] == '-' && java.util.Arrays.equals(plain, decoded);
    }

    static int decode(byte[] input, int groupEnd, byte[] output, boolean url) {
        Alphabet alphabet = url ? URL : BASIC;
        int limit = SPECIES.loopBound(groupEnd);
        int dst = 0;
        for (int src = 0; src < limit; src += WIDTH) {
            ByteVector ascii = ByteVector.fromArray(SPECIES, input, src);
            ByteVector high = ascii.lanewise(LSHR, 4);
            ByteVector low = ascii.and((byte) 15);
            // The low-nibble table records which high nibbles form legal characters.
            if (high.selectFrom(alphabet.highMasks).and(low.selectFrom(alphabet.lowMasks)).eq((byte) 0).anyTrue()) {
                throw new IllegalArgumentException("Invalid Base64 character");
            }
            ByteVector values = ascii.add(high.selectFrom(alphabet.offsets)).blend((byte) 63, ascii.eq(alphabet.last));
            // [a,b,c,d] -> [(a<<2)|(b>>4), (b<<4)|(c>>2), (c<<6)|d, unused].
            IntVector w = values.reinterpretAsInts();
            IntVector bytes = w.lanewise(LSHL, 2).and(0xfc).or(w.lanewise(LSHR, 12).and(3))
                    .or(w.lanewise(LSHL, 4).and(0xf000)).or(w.lanewise(LSHR, 10).and(0x0f00))
                    .or(w.lanewise(LSHL, 6).and(0xc00000)).or(w.lanewise(LSHR, 8).and(0x3f0000));
            bytes.reinterpretAsBytes().rearrange(PACK).intoArray(output, dst, PACKED_MASK);
            dst += PACKED;
        }
        return limit;
    }

    static int encode(byte[] input, byte[] output, boolean url) {
        int limit = input.length / PACKED * PACKED;
        int dst = 0;
        for (int src = 0; src < limit; src += PACKED) {
            IntVector w = ByteVector.fromArray(SPECIES, input, src, PACKED_MASK)
                    .rearrange(EXPAND).reinterpretAsInts();
            // Undo the quartet packing, extracting four six-bit indices per lane.
            IntVector indices = w.lanewise(LSHR, 2).and(63)
                    .or(w.lanewise(LSHL, 12).and(0x3000)).or(w.lanewise(LSHR, 4).and(0x0f00))
                    .or(w.lanewise(LSHL, 10).and(0x3c0000)).or(w.lanewise(LSHR, 6).and(0x030000))
                    .or(w.lanewise(LSHL, 8).and(0x3f000000));
            ByteVector v = indices.reinterpretAsBytes();
            if (WIDTH >= 64) {
                v.selectFrom((url ? URL : BASIC).encodeTable).intoArray(output, dst);
            } else {
                v.add((byte) 'A').blend(v.add((byte) ('a' - 26)), v.compare(GE, (byte) 26))
                    .blend(v.add((byte) ('0' - 52)), v.compare(GE, (byte) 52))
                    .blend((byte) (url ? '-' : '+'), v.eq((byte) 62))
                    .blend((byte) (url ? '_' : '/'), v.eq((byte) 63)).intoArray(output, dst);
            }
            dst += WIDTH;
        }
        return limit;
    }
}
