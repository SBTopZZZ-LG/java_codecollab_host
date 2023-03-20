package utils;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.*;
import models.MergeWrapper;

import java.io.IOException;
import java.util.*;

public class SourceMergeUtils {
    protected static final String CHANGE1 = "CHANGE 1", CHANGE2 = "CHANGE 2";

    public static String mergeV1(final List<String> original, final List<String> localModified, final List<String> hostModified) throws IOException, PatchFailedException {
        final Patch<String> oldToNew = DiffUtils.diff(original, hostModified);
        final Patch<String> oldToModified = DiffUtils.diff(original, localModified);

        final List<Object[]> conflicts = new ArrayList<>();
        final List<AbstractDelta<String>> merged = mergeV1_mergePatches(oldToNew, oldToModified, conflicts);

        final Patch<String> mergedPatch = mergeV1_mergedPatchesToPatch(merged);
        conflicts.forEach(conflict -> mergedPatch.addDelta(new InsertDelta<>(
                new Chunk<>(Integer.parseInt(conflict[0].toString()), new ArrayList<>()),
                new Chunk<>(Integer.parseInt(conflict[0].toString()), conflict[1].toString().split("\n"))
        )));
        mergedPatch.getDeltas().sort(Comparator.comparing(delta -> delta.getSource().getPosition()));

        return String.join("\n", mergedPatch.applyTo(original));
    }

    private static int mergeV1_chooseDelta(final AbstractDelta<String> delta1, final AbstractDelta<String> delta2) {
        /*
         * Change + Change => Conflict (as long as the changed content is not equal)
         * Change + Delete => Change
         * Change + Insert => Conflict
         *
         * Delete + Change => Change
         * Delete + Delete => Delete
         * Delete + Insert => Insert
         *
         * Insert + Change => Conflict
         * Insert + Delete => Insert
         * Insert + Insert => Insert
         * */

        if (delta1 instanceof ChangeDelta<String>) {
            if (delta2 instanceof DeleteDelta<String>)
                return 0;
            else
                return -1;
        } else if (delta1 instanceof DeleteDelta<String>)
            return 1;
        else if (!(delta2 instanceof ChangeDelta<String>))
            return 1;
        else
            return -1;
    }
    private static List<AbstractDelta<String>> mergeV1_mergePatches(final Patch<String> oldToNew, final Patch<String> oldToModified, final List<Object[]> outConflicts) {
        return new ArrayList<MergeWrapper<String>>() {{
            addAll(oldToNew.getDeltas().stream()
                    .map((patch -> new MergeWrapper<>(MergeWrapper.WrapperFor.New, patch)))
                    .toList());
            addAll(oldToModified.getDeltas().stream()
                    .map((patch -> new MergeWrapper<>(MergeWrapper.WrapperFor.Modified, patch)))
                    .toList());

            sort(Comparator.comparing((patch) -> patch.delta().getSource().getPosition()));

            final PriorityQueue<Integer> removalIndices = new PriorityQueue<>((x, y) -> y - x);
            for (int i = 0; i < size() - 1; i++) {
                if (get(i).delta().getSource().getPosition() == get(i + 1).delta().getSource().getPosition()) {
                    final int retained = mergeV1_chooseDelta(get(i).delta(), get(i + 1).delta());
                    if (retained == -1) {
                        removalIndices.add(i);
                        removalIndices.add(i + 1);

                        if (outConflicts != null) {
                            final StringBuilder builder = new StringBuilder();
                            builder.append("<<<<<<< " + CHANGE1 + "\n");

                            AbstractDelta<String> modifiedDelta, newDelta;
                            if (get(i).wrapperFor() == MergeWrapper.WrapperFor.Modified) {
                                modifiedDelta = get(i).delta();
                                newDelta = get(i + 1).delta();
                            } else {
                                modifiedDelta = get(i + 1).delta();
                                newDelta = get(i).delta();
                            }

                            if (modifiedDelta instanceof DeleteDelta<String>)
                                builder.append(String.join("\n", modifiedDelta.getSource().getLines().stream().map((line) -> "[-] " + line).toList()));
                            else
                                builder.append(String.join("\n", modifiedDelta.getTarget().getLines().stream().map((line) -> "[+] " + line).toList()));

                            builder.append("\n=======\n");

                            if (newDelta instanceof DeleteDelta<String>)
                                builder.append(String.join("\n", newDelta.getSource().getLines().stream().map((line) -> "[-] " + line).toList()));
                            else
                                builder.append(String.join("\n", newDelta.getTarget().getLines().stream().map((line) -> "[+] " + line).toList()));

                            builder.append("\n>>>>>>> " + CHANGE2);

                            outConflicts.add(new Object[]{get(i).delta().getSource().getPosition(), builder.toString()});
                        }
                    } else {
                        if (retained == 0)
                            removalIndices.add(i + 1);
                        else
                            removalIndices.add(i);
                    }
                }
            }

            while (!removalIndices.isEmpty()) {
                final int index = removalIndices.poll();
                if (index == -1)
                    continue;
                remove(index);
            }
        }}.stream()
                .map(MergeWrapper::delta)
                .toList();
    }
    private static Patch<String> mergeV1_mergedPatchesToPatch(final List<AbstractDelta<String>> mergedPatches) {
        final Patch<String> patch = new Patch<>();

        mergedPatches.stream()
                .filter(Objects::nonNull)
                .forEach(patch::addDelta);

        return patch;
    }
}
