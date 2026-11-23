package com.marianocarrizo.dynamusic.controller;

record RowRange(int first, int last) {

    static final RowRange EMPTY = new RowRange(-1, -1);

    boolean contains(int index) {
        return index >= first && index <= last;
    }

    int size() {
        return last - first + 1;
    }
}
