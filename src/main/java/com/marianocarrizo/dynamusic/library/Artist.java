package com.marianocarrizo.dynamusic.library;

import java.util.List;

public record Artist(String name, List<Album> albums) {
}
