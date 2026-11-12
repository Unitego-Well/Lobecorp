package org.unitego.lobecorp.client.bench;

import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import me.lucko.spark.proto.SparkSamplerProtos;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.neoforged.neoforge.client.ClientCommandHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// 正式遭遇专用本地采样会话；完成回执、新文件和非空线程数据共同构成保存门禁。
public class HordeEncounterSpark {
    public static final String LOCAL_PATH = "artifacts/spark/horde-encounter.sparkprofile";
    private final Path directory = Path.of("config/spark").toAbsolutePath().normalize();
    private Set<Path> before = Set.of();
    private final Set<GuiMessage> previousMessages = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean stopped;
    private boolean acknowledged;
    private boolean saved;

    public void start(BenchClientContext context) throws IOException {
        before = profiles();
        if (!ClientCommandHandler.runCommand("sparkc profiler start --timeout 600")) {
            throw new IllegalStateException("Spark client profiler command is unavailable");
        }
    }

    public void stop(BenchClientContext context) {
        if (stopped) {
            return;
        }
        previousMessages.addAll(messages(context));
        if (!ClientCommandHandler.runCommand("sparkc profiler stop --save-to-file")) {
            throw new IllegalStateException("Spark local stop command is unavailable");
        }
        stopped = true;
    }

    public boolean capture(BenchClientContext context) throws IOException {
        if (saved || !stopped) {
            return saved;
        }
        for (var message : messages(context)) {
            if (!previousMessages.contains(message)
                    && message.content().getString().contains("Profiler stopped & save complete!")) {
                acknowledged = true;
            }
        }
        if (!acknowledged) {
            return false;
        }
        var fresh = profiles().stream().filter(path -> !before.contains(path)).toList();
        if (fresh.isEmpty()) {
            return false;
        }
        if (fresh.size() != 1) {
            throw new IllegalStateException("Spark 本轮新增采样文件不唯一：" + fresh);
        }
        var bytes = Files.readAllBytes(fresh.getFirst());
        var data = SparkSamplerProtos.SamplerData.parseFrom(bytes);
        boolean sampled = data.getThreadsList().stream()
                .anyMatch(thread -> thread.getTimesList().stream()
                        .anyMatch(time -> Double.isFinite(time) && time > 0));
        if (!data.hasMetadata() || data.getThreadsCount() == 0 || !sampled) {
            throw new IllegalStateException("Spark 本地采样缺少元数据、线程或有效非零采样：" + fresh.getFirst());
        }
        var target = context.resultDirectory().resolve(LOCAL_PATH);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        context.artifacts().register(target, "application/octet-stream");
        saved = true;
        return true;
    }

    public boolean saved() {
        return saved;
    }

    public void requireSaved() {
        if (!saved) {
            throw new IllegalStateException("Spark 本地导出30秒内未完成回执、新文件与SamplerData验证");
        }
    }

    private Set<Path> profiles() throws IOException {
        if (!Files.exists(directory)) {
            return Set.of();
        }
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().startsWith("profile")
                    && path.getFileName().toString().endsWith(".sparkprofile"))
                    .collect(Collectors.toSet());
        }
    }

    private List<GuiMessage> messages(BenchClientContext context) {
        try {
            var field = ChatComponent.class.getDeclaredField("allMessages");
            field.setAccessible(true);
            return ((List<?>) field.get(context.minecraft().gui.getChat())).stream()
                    .filter(GuiMessage.class::isInstance).map(GuiMessage.class::cast).toList();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法读取Spark本地保存回执", exception);
        }
    }
}
