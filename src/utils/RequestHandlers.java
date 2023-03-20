package utils;

import com.github.difflib.patch.PatchFailedException;
import models.ProtocolModel;
import models.SocketWrapper;
import models.StorageEntity;
import org.apache.commons.text.StringEscapeUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class RequestHandlers {
    public static final Pattern postNameParamPattern = Pattern.compile("^[^\"'\\\\/]{6,16}$", Pattern.CASE_INSENSITIVE);
    public static final Pattern postChatMessageParamPattern = Pattern.compile("^[a-z0-9]+(, *[a-z0-9]+)*$", Pattern.CASE_INSENSITIVE);

    public interface GetRequestHandler {
        boolean handler(final String requestId, final String params, final TCPServer.Options options);
    }

    public static boolean getName(final String requestId, final String params, final TCPServer.Options options) {
        if (!requestId.equalsIgnoreCase("name")) return false;

        final ProtocolModel.Options options1 = ((ProtocolModel.Options) options);
        final SocketWrapper wrapper = options1.getClientSocketWrapper();

        if (wrapper == null) {
            options.acknowledgeWithError("Server error");
            return true;
        }

        options.getSocketWriteStream().println(wrapper.getId());
        options.getSocketWriteStream().println(wrapper.getName());

        return true;
    }

    public static boolean getClients(final String requestId, final String params, final TCPServer.Options options) {
        if (!requestId.equalsIgnoreCase("clients")) return false;

        final List<SocketWrapper> clients = ((ProtocolModel.Options) options).getClientSocketWrappers();
        options.getSocketWriteStream().println(clients.size());

        clients.forEach(wrapper -> options.getSocketWriteStream().println("\"" + wrapper.getName() + "\" " + wrapper.getId()));
        options.getSocketWriteStream().println("DONE");

        return true;
    }

    public static boolean getList(final String requestId, final String params, final TCPServer.Options options) {
        if (!requestId.equalsIgnoreCase("list")) return false;

        return (boolean) StorageEntity.instance.get(instance -> {
            final StorageEntity entity = instance.findEntity(params);

            if (entity == null || entity.isEmpty()) {
                options.acknowledgeWithError("Invalid path");
                return true;
            }

            options.getSocketWriteStream().println(entity.getName());

            if (entity.getLinkedEntities() == null) {
                options.getSocketWriteStream().println("DONE");
                return true;
            }

            options.getSocketWriteStream().println(entity.getLinkedEntities().size());

            for (final String fileName : entity.getLinkedEntities().keySet())
                options.getSocketWriteStream().println("\"" + fileName + "\" (" + (entity.getLinkedEntities().get(fileName).getLinkedEntities() == null ? "file" : "folder") + ")");
            options.getSocketWriteStream().println("DONE");

            return true;
        });
    }

    public static boolean getBinary(final String requestId, final String params, final TCPServer.Options options) {
        if (!requestId.equalsIgnoreCase("binary")) return false;

        return (boolean) StorageEntity.instance.get(instance -> {
            final StorageEntity entity = instance.findEntity(params);

            if (entity == null || entity.isEmpty()) {
                options.acknowledgeWithError("Invalid path");
                return true;
            }

            final String absolutePath = entity.getPath();
            final File file = new File(absolutePath);
            if (!file.exists() || !file.isFile()) {
                options.acknowledgeWithError("Invalid path");
                return true;
            }

            try {
                final BufferedInputStream reader = new BufferedInputStream(new FileInputStream(file));
                while (reader.available() > 0)
                    options.getSocketWriteStream().println(StringEscapeUtils.escapeJava(new String(reader.readNBytes(reader.available()))));
            } catch (IOException e) {
                e.printStackTrace();
                options.acknowledgeWithError("Internal error");
                return true;
            }

            options.getSocketWriteStream().println("DONE");

            return true;
        });
    }

    public static boolean getMd5(final String requestId, final String params, final TCPServer.Options options) {
        if (!requestId.equalsIgnoreCase("md5")) return false;

        return (boolean) StorageEntity.instance.get(instance -> {
            final StorageEntity entity = instance.findEntity(params);
            if (entity == null || entity.isEmpty()) {
                options.acknowledgeWithError("Invalid path");
                return true;
            }

            final String absolutePath = entity.getPath();
            final File file = new File(absolutePath);
            if (!file.exists() || !file.isFile()) {
                options.acknowledgeWithError("Invalid path");
                return true;
            }

            try {
                options.getSocketWriteStream().println(entity.getMd5());
            } catch (IOException | NoSuchAlgorithmException e) {
                options.acknowledgeWithError("Internal error");
                return true;
            }

            options.getSocketWriteStream().println("DONE");

            return true;
        });
    }

    public interface PostRequestHandler {
        boolean handler(final String requestId, final String params, final List<String> payloads, final TCPServer.Options options);
    }

    public static boolean postName(final String requestId, final String params, final List<String> payloads, final TCPServer.Options options) {
        if (!requestId.equalsIgnoreCase("name")) return false;

        try {
            final ProtocolModel.Options options1 = ((ProtocolModel.Options) options);
            final SocketWrapper wrapper = options1.getClientSocketWrapper();

            if (wrapper == null) {
                options.acknowledgeWithError("Server error");
                return true;
            }

            if (!postNameParamPattern.matcher(params).matches()) {
                options.acknowledgeWithError("Invalid name");
                return true;
            }

            wrapper.setName(params);
        } catch (Exception e) {
            e.printStackTrace();

            options.acknowledgeWithError("Server error");
        }

        return true;
    }

    public static boolean postChatMessage(final String requestId, final String params, final List<String> payloads, final TCPServer.Options options) {
        if (!requestId.equalsIgnoreCase("chatMsg")) return false;

        final ProtocolModel.Options options1 = (ProtocolModel.Options) options;
        final List<SocketWrapper> wrappers = options1.getClientSocketWrappers();
        final List<SocketWrapper> targets = new ArrayList<>();

        if (params == null)
            targets.addAll(wrappers);
        else {
            if (!postChatMessageParamPattern.matcher(params).matches()) {
                options.acknowledgeWithError("Invalid parameters!");
                return true;
            }

            Arrays.stream(params.split(","))
                    .forEach(id -> targets.add(wrappers.stream()
                            .filter(wrapper1 -> wrapper1.getId().equalsIgnoreCase(id))
                            .findFirst().orElse(null)));
            targets.removeIf(Objects::isNull);
        }

        final String message = payloads.stream().reduce("", (acc, line) -> acc + StringEscapeUtils.unescapeJava(line));
        targets.forEach(target -> {
            if (target != options1.getClientSocketWrapper())
                target.getMessageQueue().get(messageQueue -> messageQueue.add(
                        "POST chatMsg \"" + options1.getClientSocketWrapper().getId() + "\"\n"
                                + message));
        });

        return true;
    }

    public static boolean postVerifyMd5(final String requestId, final String params, final List<String> payloads, final TCPServer.Options options) {
        if (!requestId.equalsIgnoreCase("verifyMd5")) return false;

        if (params == null) {
            options.acknowledgeWithError("Path required");
            return true;
        }
        if (payloads.isEmpty()) {
            options.acknowledgeWithError("Md5 required");
            return true;
        }

        final String localMd5 = payloads.get(0);

        return (boolean) StorageEntity.instance.get(instance -> {
            try {
                final StorageEntity entity = instance.findEntity(params);
                if (entity == null) {
                    options.acknowledgeWithError("File not found");
                    return true;
                }

                final String remoteMd5 = entity.getMd5();
                options.getSocketWriteStream().println(localMd5.equals(remoteMd5));
            } catch (Exception e) {
                e.printStackTrace();
            }

            return true;
        });
    }

    public static boolean postBinary(final String requestId, final String params, final List<String> payloads, final TCPServer.Options options) {
        if (!requestId.equalsIgnoreCase("binary")) return false;

        return (boolean) StorageEntity.instance.get(instance -> {
            try {
                final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(instance.findEntity(params).getPath()));
                for (final String payload : payloads)
                    bos.write(StringEscapeUtils.unescapeJava(payload).getBytes(StandardCharsets.UTF_8));
                bos.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            return true;
        });
    }

    public static boolean postUpdatedBinary(final String requestId, final String params, final List<String> payloads, final TCPServer.Options options) {
        if (!requestId.equalsIgnoreCase("updatedBinary")) return false;

        return (boolean) StorageEntity.instance.get(instance -> {
            try {
                final StorageEntity entity = instance.findEntity(params);

                final String localHash = payloads.get(0);
                final String remoteHash = entity == null ? null : entity.getMd5();

                if (entity == null || localHash.equals(remoteHash)) {
                    final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(entity == null ? (instance.getPath() + "/" + params) : entity.getPath()));
                    for (int i = 1; i < payloads.size(); i++)
                        if (payloads.get(i).equalsIgnoreCase("-----split-----"))
                            break;
                        else
                            bos.write(StringEscapeUtils.unescapeJava(payloads.get(i)).getBytes(StandardCharsets.UTF_8));
                    bos.close();
                } else {
                    options.acknowledgeWithError("Hash mismatch");

                    final AtomicReference<Integer> splitIndex = new AtomicReference<>(0);
                    final List<String> localModified = new ArrayList<>() {{
                        final AtomicReference<String> temp = new AtomicReference<>("");

                        for (int i = 1; i < payloads.size(); i++)
                            if (payloads.get(i).equalsIgnoreCase("-----split-----")) {
                                splitIndex.set(i + 1);
                                break;
                            } else
                                temp.set(temp.get() + StringEscapeUtils.unescapeJava(payloads.get(i)));

                        temp.get().lines().forEach(this::add);
                    }};
                    final List<String> original = new ArrayList<>() {{
                        final AtomicReference<String> temp = new AtomicReference<>("");

                        for (int i = splitIndex.get(); i < payloads.size(); i++)
                            temp.set(temp.get() + StringEscapeUtils.unescapeJava(payloads.get(i)));

                        temp.get().lines().forEach(this::add);
                    }};
                    final List<String> hostModified = Files.readAllLines(Path.of(entity.getPath()));

                    final String merged = SourceMergeUtils.mergeV1(original, localModified, hostModified);
                    options.getSocketWriteStream().println(StringEscapeUtils.escapeJava(merged));

                    options.getSocketWriteStream().println("DONE");

                    final BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(entity.getPath()));
                    bos.write(merged.getBytes(StandardCharsets.UTF_8));
                    bos.close();
                }
            } catch (IOException | NoSuchAlgorithmException | PatchFailedException e) {
                e.printStackTrace();
            }

            return true;
        });
    }
}
