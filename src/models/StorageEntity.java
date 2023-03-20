package models;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class StorageEntity {
    public static Synchronized<StorageEntity> instance;

    protected boolean isEmpty;

    protected final String path;
    protected String name;
    protected Map<String, StorageEntity> linkedEntities;

    public StorageEntity(final String path) {
        this.path = path;
        refresh();
    }

    public void refresh() {
        final File fileOrDirObj = new File(path);

        isEmpty = !fileOrDirObj.exists();
        if (isEmpty) return;

        name = fileOrDirObj.getName();
        if (fileOrDirObj.isFile())
            linkedEntities = null;
        else {
            if (linkedEntities != null && !linkedEntities.isEmpty())
                linkedEntities.clear();
            linkedEntities = new HashMap<>();

            final File[] filesAndDirs = Objects.requireNonNull(fileOrDirObj.listFiles());
            for (final File fileOrDir : filesAndDirs)
                linkedEntities.put(fileOrDir.getName(), new StorageEntity(fileOrDir.getAbsolutePath()));
        }
    }

    public void printEntity(final int tabs) {
        System.out.println(name);

        if (linkedEntities == null) return;

        final List<StorageEntity> linkedEntities = this.linkedEntities
                .keySet()
                .stream()
                .map((key) -> this.linkedEntities.get(key))
                .sorted(Comparator.comparing((linkedEntity) -> linkedEntity.name))
                .toList();
        for (final StorageEntity linkedEntity : linkedEntities) {
            for (int i = 0; i < tabs; i++) System.out.print("-");
            linkedEntity.printEntity(tabs + 1);
        }
    }

    public StorageEntity findEntity(String path) {
        if (path == null) return this;
        if (!PathSequence.validate(path)) return null;

        if (path.startsWith("/")) path = path.substring(1);
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);

        if (path.isBlank() || path.isEmpty()) return this;

        StorageEntity now = this;
        final PathSequence sequence = new PathSequence(path);
        while (sequence.hasNext()) {
            final String next = sequence.next();

            if ((sequence.hasNext() && now.linkedEntities == null) || !now.linkedEntities.containsKey(next))
                return null;
            now = now.linkedEntities.get(next);
        }

        return now;
    }

    public String getName() {
        return name;
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    public String getPath() {
        return path;
    }

    public Map<String, StorageEntity> getLinkedEntities() {
        return linkedEntities;
    }

    public String getMd5() throws IOException, NoSuchAlgorithmException {
        byte[] data = Files.readAllBytes(Paths.get(path));
        byte[] hash = MessageDigest.getInstance("MD5").digest(data);

        return new BigInteger(1, hash).toString(16);
    }
}
