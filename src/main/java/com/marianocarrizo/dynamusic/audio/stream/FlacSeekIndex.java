package com.marianocarrizo.dynamusic.audio.stream;

import java.util.ArrayList;
import java.util.List;
import org.jflac.metadata.SeekPoint;
import org.jflac.metadata.SeekTable;

final class FlacSeekIndex {

    private static final long PLACEHOLDER_SAMPLE = -1L;

    private final FlacSeekPoint origin;
    private final List<FlacSeekPoint> points;

    FlacSeekIndex(long firstFrameOffset, List<FlacSeekPoint> relativePoints) {
        this.origin = new FlacSeekPoint(0, firstFrameOffset);
        this.points = absolute(firstFrameOffset, relativePoints);
    }

    static FlacSeekIndex fromTable(SeekTable table, long firstFrameOffset) {
        List<FlacSeekPoint> relative = new ArrayList<>();
        if (table != null) {
            for (int i = 0; i < table.numberOfPoints(); i++) {
                SeekPoint point = table.getSeekPoint(i);
                relative.add(new FlacSeekPoint(point.getSampleNumber(), point.getStreamOffset()));
            }
        }
        return new FlacSeekIndex(firstFrameOffset, relative);
    }

    boolean hasPoints() {
        return !points.isEmpty();
    }

    FlacSeekPoint locate(long targetSample) {
        FlacSeekPoint best = origin;
        for (FlacSeekPoint point : points) {
            if (point.sample() > targetSample) break;
            best = point;
        }
        return best;
    }

    private static List<FlacSeekPoint> absolute(long firstFrameOffset, List<FlacSeekPoint> relativePoints) {
        List<FlacSeekPoint> result = new ArrayList<>();
        for (FlacSeekPoint point : relativePoints) {
            if (point.sample() == PLACEHOLDER_SAMPLE || point.sample() < 0) continue;
            result.add(new FlacSeekPoint(point.sample(), firstFrameOffset + point.offset()));
        }
        result.sort((a, b) -> Long.compare(a.sample(), b.sample()));
        return List.copyOf(result);
    }
}
