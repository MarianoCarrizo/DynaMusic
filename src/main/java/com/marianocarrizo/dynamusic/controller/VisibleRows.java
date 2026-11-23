package com.marianocarrizo.dynamusic.controller;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

final class VisibleRows {

    private static final String FLOW_SELECTOR = ".virtual-flow";
    private static final String CELL_SELECTOR = ".list-cell";
    private static final double TOLERANCE = 1;

    private VisibleRows() {
    }

    static RowRange of(ListView<?> list) {
        Node flow = list.lookup(FLOW_SELECTOR);
        if (flow == null) return RowRange.EMPTY;
        Bounds viewport = flow.localToScene(flow.getLayoutBounds());
        int first = Integer.MAX_VALUE;
        int last = -1;
        for (Node node : list.lookupAll(CELL_SELECTOR)) {
            if (!(node instanceof ListCell<?> cell) || cell.isEmpty() || !cell.isVisible()) continue;
            Bounds bounds = cell.localToScene(cell.getLayoutBounds());
            if (bounds.getMinY() < viewport.getMinY() - TOLERANCE) continue;
            if (bounds.getMaxY() > viewport.getMaxY() + TOLERANCE) continue;
            first = Math.min(first, cell.getIndex());
            last = Math.max(last, cell.getIndex());
        }
        return last < 0 ? RowRange.EMPTY : new RowRange(first, last);
    }
}
