package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import me.lucko.spark.proto.SparkProtos;
import me.lucko.spark.proto.SparkSamplerProtos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.neoforged.neoforge.client.ClientCommandHandler;
import net.neoforged.neoforge.client.ClientCommandSourceStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/// 仅基准客户端使用的 spark 会话；启动回执与保存实件分别形成门禁。
public class PhotonBenchSpark implements AutoCloseable, CommandSource {
	private static final String START = "sparkc profiler start --thread * --not-combined";
	private static final String STOP = "sparkc profiler stop --save-to-file --comment ";
	private static final String CANCEL = "sparkc profiler cancel";
	private static final String SAVED_PATH = "Data has been written to: ";
	private static final int MESSAGE_LIMIT = 64;
	private static final int MESSAGE_LENGTH = 4096;
	/// 保存验证在停止采样后执行，防止意外文件把基准宿主内存耗尽。
	private static final long MAX_PROFILE_BYTES = 256L << 20;
	private static final long COMMAND_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(20);

	private enum State {
		STARTING, RUNNING, STOPPING, COMPLETE, FAILED, CLOSED
	}

	private record Message(long nanoTime, long wallTime, String text) {
	}

	private final Minecraft minecraft;
	private final ClientLevel level;
	private final String runId;
	private final String runName;
	private final ClientCommandSourceStack source;
	private final ArrayBlockingQueue<Message> incoming = new ArrayBlockingQueue<>(MESSAGE_LIMIT);
	private final AtomicBoolean overflow = new AtomicBoolean();
	private final ArrayDeque<Message> messages = new ArrayDeque<>();
	private State state = State.STARTING;
	private String failure = "";
	private String savedPath = "";
	private String sha256 = "";
	private String verification = "not_verified";
	private Path profile;
	private byte[] profileBytes;
	private long startRequestNs;
	private long startRequestMs;
	private long startedAckNs;
	private long startedAckMs;
	private long stopRequestNs;
	private long stopRequestMs;
	private long stoppedAckNs;
	private long saveAckNs;
	private long pathAckNs;
	private long verifiedNs;
	private long cancelRequestNs;
	private long cancelAckNs;
	private long profileStartMs;
	private long profileEndMs;
	private int threadCount;
	private int discardedMessages;
	private boolean ownsSampler;
	private boolean ownershipUnconfirmed;
	private boolean saveComplete;
	private boolean closed;
	private boolean archived;

	private PhotonBenchSpark(Minecraft minecraft, String runId) {
		this.minecraft = minecraft;
		this.runId = runId;
		runName = "photon_bench_" + UUID.randomUUID().toString().replace("-", "");
		var player = minecraft.player;

		if (player == null || minecraft.level == null) {
			throw new IllegalStateException("spark 客户端会话需要已连接的世界与玩家。");
		}

		level = minecraft.level;

		// 独立身份只服务命令发送者去重，不加入世界、不 tick、不发送实体包。
		var identity = new Marker(EntityType.MARKER, minecraft.level);
		source = new ClientCommandSourceStack(this, player.position(), player.getRotationVector(),
				player.permissions(), runName, Component.literal(runName), identity);
	}

	public static PhotonBenchSpark start(Minecraft minecraft, String runId) {
		if (runId == null || !runId.matches("[A-Za-z0-9_.-]{1,160}")) {
			throw new IllegalArgumentException("spark runId 必须是单个英文、数字或点下划线连字符组成的标识。");
		}

		var session = new PhotonBenchSpark(minecraft, runId);
		session.requireClientThread();
		session.startRequestNs = System.nanoTime();
		session.startRequestMs = System.currentTimeMillis();
		session.dispatch(START);
		return session;
	}

	/// 每 tick 调用；启动后返回 true，停止后只有实件验证完成才再次返回 true，失败明确抛出。
	public boolean poll() {
		requireClientThread();

		for (Message message; (message = incoming.poll()) != null; ) {
			if (messages.size() == MESSAGE_LIMIT) {
				messages.removeFirst();
				discardedMessages++;
			}

			messages.addLast(message);
			accept(message);
		}

		if ((state == State.STARTING || state == State.RUNNING || state == State.STOPPING)
				&& (minecraft.player == null || minecraft.level != level)) {
			fail("spark 所属客户端世界已断开或切换，收尾未确认。", true);
		}

		if (overflow.get()) {
			fail("spark 回执队列溢出，无法确认完整生命周期。", true);
		}

		if (state == State.STARTING && System.nanoTime() - startRequestNs > COMMAND_TIMEOUT_NS) {
			fail("spark 启动回执超时。", true);
		} else if (state == State.STOPPING && System.nanoTime() - stopRequestNs > COMMAND_TIMEOUT_NS) {
			fail("spark 保存回执超时。", ownsSampler);
		}

		if (state == State.STOPPING && saveComplete && !savedPath.isBlank()) {
			verifyProfile();
		}

		if (closed && ownsSampler && cancelRequestNs == 0 && stopRequestNs == 0 && !ownershipUnconfirmed) {
			cancelRequestNs = System.nanoTime();
			dispatch(CANCEL);
		}

		if (cancelRequestNs != 0 && cancelAckNs == 0 && System.nanoTime() - cancelRequestNs > COMMAND_TIMEOUT_NS) {
			fail("spark 取消回执超时，收尾未确认。", true);
		}

		if (state == State.FAILED) {
			throw new IllegalStateException(failure);
		}

		return !closed && (state == State.RUNNING || state == State.COMPLETE);
	}

	public void stop() {
		requireClientThread();

		if (stopRequestNs != 0) {
			return;
		}

		if (closed || state != State.RUNNING || !ownsSampler) {
			throw new IllegalStateException("spark 尚未确认本会话启动，不能停止其他采样。");
		}

		stopRequestNs = System.nanoTime();
		stopRequestMs = System.currentTimeMillis();
		state = State.STOPPING;
		dispatch(STOP + runId);
	}

	public boolean complete() {
		return state == State.COMPLETE;
	}

	/// 只有收到本 label 的唯一启动回执后才允许建立测量零点。
	public boolean running() {
		return state == State.RUNNING && ownsSampler && startedAckNs != 0;
	}

	private void accept(Message message) {
		var text = message.text();

		if (text.contains("Profiler is already running!") || text.contains("There isn't an active profiler running.")) {
			ownsSampler = false;
			fail("spark 当前采样状态不属于此会话：" + text, true);
			return;
		}

		if (text.contains("Profiler operation failed unexpectedly") || text.contains("An error occurred whilst saving the data.")) {
			fail("spark 采样或保存失败：" + text, ownsSampler);
			return;
		}

		if (text.contains("Profiler is now running!") && startedAckNs == 0 && stopRequestNs == 0) {
			startedAckNs = message.nanoTime();
			startedAckMs = message.wallTime();
			ownsSampler = true;

			if (state == State.STARTING) {
				state = State.RUNNING;
			}
		}

		if (stopRequestNs != 0 && text.contains("Stopping the profiler & saving results")) {
			stoppedAckNs = message.nanoTime();
			ownsSampler = false;
		}

		if (stopRequestNs != 0 && text.contains("Profiler stopped & save complete!")) {
			saveComplete = true;
			saveAckNs = message.nanoTime();
			ownsSampler = false;
		}

		int pathStart = text.indexOf(SAVED_PATH);

		if (stopRequestNs != 0 && pathStart >= 0) {
			savedPath = text.substring(pathStart + SAVED_PATH.length()).trim();
			pathAckNs = message.nanoTime();
		}

		if (cancelRequestNs != 0 && text.contains("Profiler has been cancelled.")) {
			cancelAckNs = message.nanoTime();
			ownsSampler = false;

			if (failure.isBlank()) {
				state = State.CLOSED;
			}
		}
	}

	private void verifyProfile() {
		try {
			var gameDirectory = minecraft.gameDirectory.toPath().toRealPath();
			var directory = gameDirectory.resolve("config/spark").toRealPath();
			var candidate = Path.of(savedPath);
			candidate = (candidate.isAbsolute() ? candidate : gameDirectory.resolve(candidate)).normalize();

			if (!directory.startsWith(gameDirectory) || !candidate.startsWith(directory)
					|| !candidate.getFileName().toString().matches("profile-.*\\.sparkprofile")) {
				throw new IOException("spark 返回了当前实例输出目录之外的路径。");
			}

			candidate = candidate.toRealPath();

			if (!candidate.getParent().equals(directory) || !Files.isRegularFile(candidate)) {
				throw new IOException("spark 保存路径不是当前实例的普通报告文件。");
			}

			long size = Files.size(candidate);

			if (size <= 0 || size > MAX_PROFILE_BYTES) {
				throw new IOException("spark 报告为空或超过读取预算：" + size);
			}

			var bytes = Files.readAllBytes(candidate);

			if (bytes.length != size) {
				throw new IOException("spark 报告读取期间长度变化。");
			}

			var data = SparkSamplerProtos.SamplerData.parseFrom(bytes);
			var metadata = data.getMetadata();
			boolean sampled = data.getThreadsList().stream()
					.anyMatch(thread -> thread.getTimesList().stream().anyMatch(time -> Double.isFinite(time) && time > 0));

			if (!data.hasMetadata() || !sampled || data.getThreadsCount() == 0
					|| !runId.equals(metadata.getComment()) || !runName.equals(metadata.getCreator().getName())
					|| metadata.getPlatformMetadata().getType() != SparkProtos.PlatformMetadata.Type.CLIENT
					|| metadata.getStartTime() < startRequestMs || metadata.getStartTime() > startedAckMs
					|| metadata.getEndTime() < stopRequestMs) {
				throw new IOException("spark protobuf 的样本、身份、客户端来源或时间范围不匹配。");
			}

			profile = candidate;
			profileBytes = bytes;
			sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			profileStartMs = metadata.getStartTime();
			profileEndMs = metadata.getEndTime();
			threadCount = data.getThreadsCount();
			verification = "save_ack_file_sha256_protobuf_identity_client_time_samples";
			verifiedNs = System.nanoTime();
			state = State.COMPLETE;
		} catch (IOException | RuntimeException | NoSuchAlgorithmException invalid) {
			fail("spark 实件验证失败：" + invalid.getMessage(), false);
		}
	}

	@SuppressWarnings("ConstantValue") // NeoForge 的 dispatcher 在客户端命令注册前实际为 null。
	private void dispatch(String command) {
		try {
			var dispatcher = ClientCommandHandler.getDispatcher();

			if (dispatcher == null || dispatcher.getRoot().getChild("sparkc") == null) {
				throw new IllegalStateException("当前客户端没有已注册的 sparkc 命令。");
			}

			dispatcher.execute(command, source);
		} catch (Exception problem) {
			fail("spark 命令派发失败：" + problem.getMessage(), true);
		}
	}

	private void fail(String message, boolean uncertainOwnership) {
		if (failure.isBlank()) {
			failure = message;
		}

		ownershipUnconfirmed |= uncertainOwnership;
		state = State.FAILED;
	}

	private void requireClientThread() {
		if (!minecraft.isSameThread()) {
			throw new IllegalStateException("spark 会话只能由所属客户端线程推进。");
		}
	}

	@Override
	public void sendSystemMessage(Component message) {
		var text = message.getString();

		if (text.length() > MESSAGE_LENGTH) {
			overflow.set(true);
			text = text.substring(0, MESSAGE_LENGTH);
		}

		if (!incoming.offer(new Message(System.nanoTime(), System.currentTimeMillis(), text))) {
			overflow.set(true);
		}
	}

	@Override
	public boolean acceptsSuccess() {
		return true;
	}

	@Override
	public boolean acceptsFailure() {
		return true;
	}

	@Override
	public boolean shouldInformAdmins() {
		return false;
	}

	public JsonObject snapshot() {
		var result = new JsonObject();
		result.addProperty("state", state.name());
		result.addProperty("run_id", runId);
		result.addProperty("sender_name", runName);
		result.addProperty("failure", failure);
		result.addProperty("start_request_ns", startRequestNs);
		result.addProperty("start_request_ms", startRequestMs);
		result.addProperty("started_ack_ns", startedAckNs);
		result.addProperty("started_ack_ms", startedAckMs);
		result.addProperty("stop_request_ns", stopRequestNs);
		result.addProperty("stop_request_ms", stopRequestMs);
		result.addProperty("stopped_ack_ns", stoppedAckNs);
		result.addProperty("save_ack_ns", saveAckNs);
		result.addProperty("path_ack_ns", pathAckNs);
		result.addProperty("verified_ns", verifiedNs);
		result.addProperty("cancel_request_ns", cancelRequestNs);
		result.addProperty("cancel_ack_ns", cancelAckNs);
		result.addProperty("owns_sampler", ownsSampler);
		result.addProperty("cleanup_unconfirmed", ownershipUnconfirmed || ownsSampler || state == State.STARTING);
		result.addProperty("profile_path", profile == null ? savedPath : profile.toString());
		result.addProperty("profile_bytes", profileBytes == null ? 0 : profileBytes.length);
		result.addProperty("profile_sha256", sha256);
		result.addProperty("verification_level", verification);
		result.addProperty("profile_start_ms", profileStartMs);
		result.addProperty("profile_export_ms", profileEndMs);
		result.addProperty("profile_threads", threadCount);
		result.addProperty("archived", archived);
		result.addProperty("closed", closed);
		result.addProperty("discarded_messages", discardedMessages);
		result.addProperty("message_overflow", overflow.get());
		var rows = new JsonArray();

		for (var message : messages) {
			var row = new JsonObject();
			row.addProperty("nano_time", message.nanoTime());
			row.addProperty("wall_time_ms", message.wallTime());
			row.addProperty("text", message.text());
			rows.add(row);
		}

		result.add("messages", rows);
		return result;
	}

	public void archive(BenchClientContext context) throws IOException {
		requireClientThread();

		if (!complete() || profileBytes == null) {
			throw new IllegalStateException("spark 采样实件未验证完成，不能归档为成功。");
		}

		if (!archived) {
			context.artifacts().write(runName + ".sparkprofile", "application/x-spark-sampler", profileBytes);
			archived = true;
		}

		context.artifacts().write(runName + "-spark.json", "application/json", snapshot().toString());
	}

	@Override
	public void close() {
		requireClientThread();
		closed = true;

		// 推进已经到达的回执；正常停止路径不得再发送取消。
		try {
			poll();
		} catch (IllegalStateException _) {
			if (ownsSampler && cancelRequestNs == 0 && stopRequestNs == 0 && !ownershipUnconfirmed) {
				cancelRequestNs = System.nanoTime();
				dispatch(CANCEL);
			}
		}
	}
}

