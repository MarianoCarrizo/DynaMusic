package com.marianocarrizo.dynamusic.tags;

import java.io.IOException;

final class TagLimitException extends IOException {

    TagLimitException(String message) {
        super(message);
    }
}
