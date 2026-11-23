package com.marianocarrizo.dynamusic.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AudioQualityTest {

    private static final String SEPARATOR = " · ";

    @TempDir Path dir;

    private Optional<AudioQuality> read(String name, byte[] bytes) throws IOException {
        Path file = Files.write(dir.resolve(name), bytes);
        return AudioQualityReader.read(file.toFile());
    }

    private static byte[] oggPage(byte[] data) {
        byte[] header = AudioFixtures.concat("OggS".getBytes(StandardCharsets.US_ASCII), new byte[22],
                new byte[] {1, (byte) data.length});
        return AudioFixtures.concat(header, data);
    }

    private static byte[] vorbisIdentification(int rate, int nominalBitrate) {
        ByteBuffer body = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        body.put((byte) 1).put("vorbis".getBytes(StandardCharsets.US_ASCII)).putInt(0).put((byte) 2).putInt(rate)
                .putInt(0).putInt(nominalBitrate).putInt(0);
        return body.array();
    }

    private static byte[] opusHead() {
        ByteBuffer body = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
        body.put("OpusHead".getBytes(StandardCharsets.US_ASCII)).put((byte) 1).put((byte) 2).putShort((short) 312)
                .putInt(44100).putShort((short) 0).put((byte) 0);
        return body.array();
    }

    private static byte[] audioSampleEntry(String type, int bits, int rate) {
        ByteBuffer entry = ByteBuffer.allocate(36).order(ByteOrder.BIG_ENDIAN);
        entry.putInt(36).put(type.getBytes(StandardCharsets.ISO_8859_1)).put(new byte[8]).put(new byte[8])
                .putShort((short) 2).putShort((short) bits).putShort((short) 0).putShort((short) 0)
                .putShort((short) rate).putShort((short) 0);
        return entry.array();
    }

    private static byte[] m4aWith(byte[] sampleEntry) {
        byte[] stsd = AudioFixtures.atom("stsd", new byte[4], new byte[] {0, 0, 0, 1}, sampleEntry);
        byte[] moov = AudioFixtures.atom("moov",
                AudioFixtures.atom("trak", AudioFixtures.atom("mdia", AudioFixtures.atom("minf",
                        AudioFixtures.atom("stbl", stsd)))));
        return AudioFixtures.concat(AudioFixtures.atom("ftyp", new byte[8]), AudioFixtures.atom("mdat", new byte[500]),
                moov);
    }

    @Test
    void flacShowsDepthAndRate() throws IOException {
        AudioQuality quality = read("a.flac", AudioFixtures.flacHeader(96000, 24)).orElseThrow();

        assertEquals("FLAC" + SEPARATOR + "24-bit" + SEPARATOR + "96 kHz", quality.label());
        assertTrue(quality.isHiRes());
    }

    @Test
    void cdQualityIsNotHiRes() throws IOException {
        AudioQuality quality = read("a.wav", AudioFixtures.wav(16, 44100, 2, false)).orElseThrow();

        assertEquals("WAV" + SEPARATOR + "16-bit" + SEPARATOR + "44.1 kHz", quality.label());
        assertFalse(quality.isHiRes());
    }

    @Test
    void floatWavAndAiffAreDescribed() throws IOException {
        assertEquals("WAV" + SEPARATOR + "32-bit float" + SEPARATOR + "48 kHz",
                read("a.wav", AudioFixtures.wav(32, 48000, 2, true)).orElseThrow().label());
        assertEquals("AIFF" + SEPARATOR + "24-bit" + SEPARATOR + "48 kHz",
                read("a.aiff", AudioFixtures.aiff(24, 48000)).orElseThrow().label());
    }

    @Test
    void hiResMeansMoreThanSixteenBitsOrMoreThanFortyEightKilohertz() {
        assertTrue(new AudioQuality("X", 24, false, 44100, 0).isHiRes());
        assertTrue(new AudioQuality("X", 16, false, 96000, 0).isHiRes());
        assertFalse(new AudioQuality("X", 16, false, 48000, 0).isHiRes());
        assertFalse(AudioQuality.ofFormat("MP3").isHiRes());
    }

    @Test
    void mp3ShowsTheBitrateOfAConstantBitrateStream() throws IOException {
        byte[] frame = {(byte) 0xFF, (byte) 0xFB, (byte) 0xE0, 0, 0, 0, 0, 0};

        assertEquals("MP3" + SEPARATOR + "320 kbps", read("a.mp3", frame).orElseThrow().label());
    }

    @Test
    void mp3SkipsAnId3TagBeforeTheFirstFrame() throws IOException {
        byte[] tag = AudioFixtures.concat("ID3".getBytes(StandardCharsets.US_ASCII), new byte[] {3, 0, 0},
                new byte[] {0, 0, 0, 20}, new byte[20]);
        byte[] frame = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0, 0, 0, 0, 0};

        assertEquals("MP3" + SEPARATOR + "128 kbps", read("a.mp3", AudioFixtures.concat(tag, frame)).orElseThrow().label());
    }

    @Test
    void mp3VariableBitrateOmitsTheBitrate() throws IOException {
        byte[] frame = AudioFixtures.concat(new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0},
                new byte[32], "Xing".getBytes(StandardCharsets.US_ASCII), new byte[40]);

        assertEquals("MP3", read("a.mp3", frame).orElseThrow().label());
    }

    @Test
    void vorbisShowsRateAndNominalBitrate() throws IOException {
        byte[] file = oggPage(vorbisIdentification(44100, 192000));

        assertEquals("OGG" + SEPARATOR + "44.1 kHz" + SEPARATOR + "192 kbps", read("a.ogg", file).orElseThrow().label());
    }

    @Test
    void vorbisWithoutANominalBitrateOmitsIt() throws IOException {
        assertEquals("OGG" + SEPARATOR + "48 kHz", read("a.ogg", oggPage(vorbisIdentification(48000, 0))).orElseThrow().label());
    }

    @Test
    void opusAlwaysDecodesAtFortyEightKilohertz() throws IOException {
        assertEquals("OPUS" + SEPARATOR + "48 kHz", read("a.opus", oggPage(opusHead())).orElseThrow().label());
    }

    @Test
    void m4aShowsTheRateAndAlacShowsTheDepthToo() throws IOException {
        AudioQuality aac = read("a.m4a", m4aWith(audioSampleEntry("mp4a", 16, 48000))).orElseThrow();
        AudioQuality alac = read("b.m4a", m4aWith(audioSampleEntry("alac", 24, 48000))).orElseThrow();

        assertEquals("M4A" + SEPARATOR + "48 kHz", aac.label());
        assertEquals("ALAC" + SEPARATOR + "24-bit" + SEPARATOR + "48 kHz", alac.label());
        assertTrue(alac.isHiRes());
    }

    @Test
    void formatsWithoutAReaderShowOnlyTheirName() throws IOException {
        assertEquals("WMA", read("a.wma", new byte[64]).orElseThrow().label());
        assertEquals("AAC", read("a.aac", new byte[64]).orElseThrow().label());
    }

    @Test
    void unknownPartsAreOmittedWhenHeadersAreBroken() throws IOException {
        assertEquals("FLAC", read("a.flac", new byte[100]).orElseThrow().label());
        assertEquals("WAV", read("a.wav", new byte[100]).orElseThrow().label());
        assertEquals("OGG", read("a.ogg", new byte[100]).orElseThrow().label());
        assertEquals("MP3", read("a.mp3", new byte[100]).orElseThrow().label());
        assertEquals("M4A", read("a.m4a", new byte[100]).orElseThrow().label());
    }

    @Test
    void unsupportedAndMissingFilesGiveNothingOrJustTheFormat() throws IOException {
        assertTrue(read("notes.txt", new byte[10]).isEmpty());
        assertEquals("FLAC", AudioQualityReader.read(dir.resolve("missing.flac").toFile()).orElseThrow().label());
    }

    @Test
    void randomBytesNeverThrowForAnyFormat() throws IOException {
        Random random = new Random(3);
        for (String extension : AudioFormat.allExtensions()) {
            byte[] noise = new byte[3000];
            random.nextBytes(noise);
            assertTrue(read("noise." + extension, noise).isPresent());
            assertTrue(read("short." + extension, Arrays.copyOf(noise, 5)).isPresent());
            assertTrue(read("empty." + extension, new byte[0]).isPresent());
        }
    }

    @Test
    void kilohertzTrimsTrailingZeros() {
        assertEquals("44.1 kHz", AudioQuality.kilohertz(44100));
        assertEquals("96 kHz", AudioQuality.kilohertz(96000));
        assertEquals("22.05 kHz", AudioQuality.kilohertz(22050));
        assertEquals("88.2 kHz", AudioQuality.kilohertz(88200));
    }
}
