package models;

import com.github.difflib.patch.AbstractDelta;

public record MergeWrapper<T>(WrapperFor wrapperFor, AbstractDelta<T> delta) {
    public enum WrapperFor {
        Modified,
        New
    }
}
