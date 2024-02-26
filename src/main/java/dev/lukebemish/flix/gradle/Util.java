package dev.lukebemish.flix.gradle;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class Util {
    public static String wordsToCamelCase(String input) {
        var words = input.split("\\W+");
        var result = new StringBuilder();
        for (var word : words) {
            result.append(word.substring(0, 1).toUpperCase());
            result.append(word.substring(1).toLowerCase());
        }
        return result.toString();
    }
}
