package models;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class PathSequence {
    public final String path;

    private final String[] parts;
    private int index;

    public PathSequence(final String path) {
        this.path = path;

        parts = path.split(Pattern.quote(File.separator));
        index = 0;
    }

    public boolean hasNext() {
        return index < parts.length;
    }

    public String peek() {
        if (!hasNext()) return null;

        return parts[index];
    }

    public String next() {
        if (!hasNext()) return null;

        return parts[index++];
    }

    public void reset() {
        index = 0;
    }

    public static boolean validate(final String path) {
        try {
            final Path temp = Path.of(path);
        } catch (InvalidPathException e) {
            return false;
        }

        return true;
    }
}
