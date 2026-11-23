package com.marianocarrizo.dynamusic.tags;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TagAccumulator {

    static final int FRONT_COVER = 3;
    private static final String ARTIST_SEPARATOR = ", ";
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(\\d{1,4})");
    private static final Pattern LEADING_YEAR = Pattern.compile("^\\s*(\\d{4})");

    private final boolean wantPicture;
    private String title = "";
    private String artist = "";
    private String album = "";
    private String year = "";
    private int trackNumber;
    private boolean hasPicture;
    private int pictureType = -1;
    private byte[] picture;

    TagAccumulator(boolean wantPicture) {
        this.wantPicture = wantPicture;
    }

    boolean wantsPicture() {
        return wantPicture;
    }

    void title(String value) {
        if (title.isEmpty()) title = TrackTags.clean(value);
    }

    void artist(String value) {
        String cleaned = TrackTags.clean(value);
        if (cleaned.isEmpty()) return;
        artist = artist.isEmpty() ? cleaned : artist + ARTIST_SEPARATOR + cleaned;
    }

    void album(String value) {
        if (album.isEmpty()) album = TrackTags.clean(value);
    }

    void track(String value) {
        if (trackNumber > 0 || value == null) return;
        Matcher matcher = LEADING_NUMBER.matcher(value);
        if (matcher.find()) trackNumber = Integer.parseInt(matcher.group(1));
    }

    void track(int value) {
        if (trackNumber == 0 && value > 0) trackNumber = value;
    }

    void year(String value) {
        if (!year.isEmpty() || value == null) return;
        Matcher matcher = LEADING_YEAR.matcher(value);
        if (matcher.find()) year = matcher.group(1);
    }

    void markPicture() {
        hasPicture = true;
    }

    void offerPicture(int type, byte[] data) {
        hasPicture = true;
        if (!wantPicture || data == null || data.length == 0 || data.length > BoundedReader.MAX_PICTURE_BYTES) return;
        boolean better = picture == null || (type == FRONT_COVER && pictureType != FRONT_COVER);
        if (!better) return;
        picture = data;
        pictureType = type;
    }

    TrackTags tags() {
        return new TrackTags(title, artist, album, trackNumber, year, hasPicture);
    }

    Optional<byte[]> picture() {
        return Optional.ofNullable(picture);
    }
}
