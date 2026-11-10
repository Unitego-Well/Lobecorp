package org.unitego.lobecorp.world.encounter.midnightcleanup;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.unitego.lobecorp.world.encounter.IEncounterDefinition;
import org.unitego.lobecorp.world.horde.HordeInstance;
import org.unitego.lobecorp.world.horde.HordeSavedData;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityDimensions;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;
import org.unitego.lobecorp.registry.entity.skill.SweeperSkills;
import net.minecraft.world.entity.EntitySpawnReason;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;
import org.unitego.lobecorp.registry.tag.LcEntityTypeTags;
import org.unitego.lobecorp.util.entity.combat.CombatUtil;
import org.unitego.lobecorp.util.visual.TimelineUtil;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.world.entity.combat.CombatData;
import org.unitego.lobecorp.world.entity.ordeal.indigo.IIndigoOrdeal;
import org.unitego.lobecorp.world.entity.ordeal.indigo.SweeperAi;
import org.unitego.lobecorp.world.horde.IHordeProfile;
import org.unitego.lobecorp.world.horde.HordeRules;
import org.unitego.lobecorp.world.horde.HordeTargetContext;
import org.unitego.lobecorp.world.horde.HordeSpawnContext;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;
import net.neoforged.neoforge.event.EventHooks;

@SuppressWarnings("resource")
public class MidnightCleanupEncounter implements IHordeProfile, IEncounterDefinition {
	/// 当地尾波通过不足五秒时，停止新增参战者并进入收尾表现。
	public static final int SPAWN_CUTOFF_TICKS = 5 * 20;

	public static final Identifier ID = Lobecorp.id("midnight_cleanup");
	public static final String FACTION = "horde_indigo";
	/// 模板覆盖完整线路，总时长包含最后一段浪潮通过的时间。
	public static final int TEMPLATE_TICKS = 3 * 60 * 20, OPENING_TICKS = 14 * 20;
	/// 倒计时仍提前十秒；完整入场提前十五秒，恢复完成后浪潮抵达。
	public static final int ARRIVAL_WARNING_TICKS = 10 * 20;
	public static final int OPENING_WARNING_TICKS = 15 * 20;
	public static final double TEMPLATE_WIDTH = 500, TEMPLATE_LENGTH = 1000;

	/// 完整开幕共用的游戏刻节点与基准视野，恢复在276刻完成。
	public static final int IMPACT_END = 36;
	public static final int FIRST_CENTER_END = 68;
	public static final int CENTER_AIM_END = 148, CENTER_END = 276;
	public static final int TEXT_READY = 188, DISSOLVE_START = 212;
	public static final int RECOVERY_START = 220;
	public static final float BASE_FOV = 68;
	/// 曲线键保持原顺序，外部只能采样，不能修改控制点。
	private static final float[][] YAW = {{0, 2}, {24, 5}, {48, -8}, {72, 8}, {96, -3}, {132, 0}, {220, 0}};
	private static final float[][] PITCH = {{0, 24}, {24, 18}, {48, -7}, {72, -9}, {96, -5}, {132, 0}, {220, 0}};
	private static final float[][] ROLL = {{0, 0}, {48, -1.72F}, {72, 3.15F}, {96, -2.58F}, {124, 3.44F},
			{160, 1.72F}, {196, 1.03F}, {228, 0.46F}, {252, 0.11F}, {276, 0}};
	private static final float[][] FOV = {{0, 62}, {28, 64}, {56, 74}, {88, 79}, {120, 58}, {CENTER_AIM_END, 34},
			{176, 30}, {204, 38}, {236, 56}, {260, 65}, {276, BASE_FOV}};
	private static final float[][] SIDE = {{0, 0}, {26, 0.06F}, {52, 0.22F}, {84, 0.72F}, {116, 1.14F},
			{144, 0.98F}, {176, 0.74F}, {208, 0.42F}, {236, 0.20F}, {256, 0.05F}, {276, 0}};
	private static final float[][] HEIGHT = {{0, -0.53F}, {26, -0.35F}, {52, -0.07F}, {84, 0.14F},
			{116, 0.25F}, {144, 0.17F}, {176, 0.09F}, {208, 0.04F}, {236, 0.02F}, {256, 0.01F}, {276, 0}};
	private static final float[][] FORWARD = {{0, 0.34F}, {26, 0.22F}, {52, -0.08F}, {84, -0.62F},
			{116, -1.18F}, {144, -1.58F}, {176, -1.20F}, {208, -0.64F}, {236, -0.26F}, {256, -0.06F}, {276, 0}};
	/// 黑边加宽节点，单位为游戏刻。
	public static final int LETTERBOX_GROW_START = 120;
	public static final int LETTERBOX_GROW_END = 176;
	/// 黑边高度占画面高度的比例。
	public static final float LETTERBOX_INITIAL_HEIGHT = 0.10F;
	public static final float LETTERBOX_FINAL_HEIGHT = 0.21F;
	public static final int LETTERBOX_ENTER_TICKS = 32;
	/// 迟到字幕的走廊宽度倍率。
	public static final double SUBTITLE_WIDTH_FACTOR = 1.5;
	/// 开幕临时锁速修饰符标识。
	public static final Identifier SPEED_ID = Lobecorp.id("midnight_cleanup/opening_speed");
	/// 补播与闭幕横幅持续刻数，不接管镜头。
	public static final int CLOSING_TICKS = 50;
	/// 演出字形基础颜色，透明度由原时间轴驱动。
	public static final int CHAPTER_COLOR = 0x8E85CF;
	/// 演出字形基础颜色，透明度由原时间轴驱动。
	public static final int NAME_COLOR = 0xD4C8FF;
	/// 演出字形基础颜色，透明度由原时间轴驱动。
	public static final int SUBTITLE_COLOR = 0xAEA2C5;
	/// 镜头与输入接管优先级。
	public static final int CONTROL_PRIORITY = 200;
	/// 相机位移判零阈值，单位为格。
	public static final double CAMERA_OFFSET_EPSILON = 0.001;
	/// 相机碰撞退让距离，单位为格。
	public static final double CAMERA_COLLISION_MARGIN = 0.15;
	/// 配置视野有效范围的上界。
	public static final int MAXIMUM_VALID_FOV = 179;
	/// 字幕入退场节点按游戏刻计，布局比例相对于屏幕尺寸。
	public static final int BANNER_ENTER_TICKS = 5;
	public static final int BANNER_DISSOLVE_TICKS = 18;
	public static final float BANNER_TITLE_Y = 0.67F;
	public static final int BANNER_MAX_GAP = 24;
	public static final float BANNER_CHAPTER_SCALE = 0.9F;
	public static final float BANNER_NAME_SCALE = 2.6F;
	public static final float BANNER_SUBTITLE_SCALE = 0.85F;
	public static final float TITLE_MAX_WIDTH = 0.72F;
	public static final float SUBTITLE_MAX_WIDTH = 0.84F;
	public static final float BANNER_LINE_ALPHA_THRESHOLD = 0.01F;
	public static final int BANNER_LINE_HALF_WIDTH = 8;
	public static final float BANNER_LINE_GAP_FACTOR = 0.60F;
	public static final int BANNER_LINE_COLOR = 0x7770BD;
	public static final int AMBIENCE_ENTER_END = 124;
	public static final int AMBIENCE_LEAVE_START = 212;
	public static final float AMBIENCE_OVERLAY_OPACITY = 0.10F;
	public static final int AMBIENCE_OVERLAY_COLOR = 0x301020;
	public static final float AMBIENCE_EDGE_HEIGHT = 0.18F;
	public static final int AMBIENCE_EDGE_ALPHA = 60;
	public static final int CHAPTER_ENTER_START = 10;
	public static final int NAME_ENTER_START = 128;
	public static final int SUBTITLE_ENTER_START = 172;
	public static final int TEXT_RISE_START = 208;
	public static final float TITLE_Y = 0.44F;
	public static final int FULL_MAX_GAP = 28;
	public static final float TEXT_REFERENCE_HEIGHT = 220F;
	public static final int CHAPTER_LEAVE_START = 88;
	public static final int NAME_LEAVE_END = 236;
	public static final int FULL_NAME_SCALE = 3;
	public static final float FULL_SUBTITLE_SCALE = 0.9F;
	public static final int SUBTITLE_LEAVE_START = 208;
	public static final float TEXT_GAP_HEIGHT = 0.10F;
	public static final int CHAPTER_ENTER_END = 22;
	public static final int NAME_ENTER_END = 140;
	public static final int TEXT_RISE_END = 240;
	public static final int TEXT_RISE_DISTANCE = 3;
	public static final float TEXT_MIN_SCALE = 0.75F;
	public static final float TEXT_MAX_SCALE = 1.3F;
	public static final int CHAPTER_LEAVE_END = 112;
	public static final int SUBTITLE_LEAVE_END = 228;
	public static final int BANNER_LINE_HEIGHT = 1;
	/// 黑边及入场黑幕使用原有黑色。
	public static final int LETTERBOX_COLOR = 0xFF000000;
	public static final int BLACKOUT_COLOR = 0;
	/// 聚合音效保持原有音高。
	public static final int SOUND_PITCH = 1;
	/// 倒计时精灵资源及字形布局，位置数组只允许按索引读取。
	public static final Identifier DIGITS = hudTexture(false, "digits");
	public static final Identifier TEXT_BOX = hudTexture(false, "text_box");
	public static final Identifier TIMER = hudTexture(false, "timer");
	public static final Identifier NEW_DIGITS = hudTexture(true, "digits");
	public static final Identifier NEW_TIMER = hudTexture(true, "timer_box");
	/// 精灵按冒号、0至9排列，格宽7、间隔1，纹理总宽87。
	public static final int GLYPH_WIDTH = 7, GLYPH_STEP = 8, SHEET_WIDTH = 87;
	private static final int[] DEFAULT_X = {20, 27, 34, 37, 44};
	private static final int[] NEW_X = {12, 22, 30, 38, 48};
	/// 两位分钟的显示上限；超过时保持99:59，不回绕成00:00。
	public static final int MAX_SECONDS = 99 * 60 + 59;

	/// 冒号按游戏刻闪烁，前半周期显示。
	public static final int HUD_COLON_PERIOD_TICKS = 20;
	public static final int HUD_COLON_VISIBLE_TICKS = 10;
	public static final int HUD_NEW_WIDTH = 67;
	public static final int HUD_NEW_HEIGHT = 48;
	public static final int HUD_TEXT_BOX_X = 12;
	public static final int HUD_TEXT_BOX_Y = 21;
	public static final int HUD_TEXT_BOX_WIDTH = 17;
	public static final int HUD_NEW_GLYPH_HEIGHT = 8;
	public static final int HUD_COLON_U_OFFSET = 2;
	public static final int HUD_NEW_GLYPH_Y = 20;
	public static final int HUD_COLON_WIDTH = 3;
	public static final int HUD_DEFAULT_WIDTH = 61;
	public static final int HUD_DEFAULT_HEIGHT = 31;
	public static final int HUD_TEXT_BOX_HEIGHT = 10;
	public static final int HUD_DEFAULT_GLYPH_HEIGHT = 11;
	public static final int HUD_DEFAULT_GLYPH_Y = 7;
	/// 持续氛围进退场与距离响应。
	public static final double ATMOSPHERE_ENTER_TICKS = 20;
	public static final double ATMOSPHERE_LEAVE_TICKS = 30;
	public static final float SKY_MINIMUM_COVERAGE = 0.025F;
	public static final int SKY_NEAR_DISTANCE = 24;
	public static final int SKY_FAR_DISTANCE = 512;
	public static final int SKY_LATERAL_FADE = 128;
	public static final int SKY_RESPONSE_TICKS = 8;
	public static final float SKY_APPROACH_COVERAGE = 0.975F;
	public static final int ATMOSPHERE_MAX_ELAPSED_TICKS = 5;
	public static final String ATMOSPHERE_PROVIDER = "lobecorp_midnight_cleanup";
	public static final String ATMOSPHERE_PATH = "atmosphere";
	/// 深夜清扫CRT表达式的客户端资源位置，正文由资源包提供。
	public static final Identifier ATMOSPHERE_EXPRESSION_RESOURCE = Lobecorp.id("shaders/encounter/midnight_cleanup/atmosphere.glsl");
	private static final String HUD_TEXTURE_ROOT = "textures/gui/sprites/";
	private static final String HUD_NEW_TEXTURE_STYLE = "newhud";
	private static final String HUD_DEFAULT_TEXTURE_STYLE = "hud";
	private static final String HUD_TEXTURE_DIRECTORY = "/event/midnight_cleanup/";
	private static final String HUD_TEXTURE_EXTENSION = ".png";
	/// 天空最大不透明度。
	public static final double MAXIMUM_OPACITY = 0.92;
	/// 事件云色与雾色。
	public static final int CLOUD_TINT = 0x64182A;
	/// 云色混合系数。
	public static final float CLOUD_BLEND = 0.60f;
	public static final float FOG_RED = 0.19F, FOG_GREEN = 0.027F, FOG_BLUE = 0.055F;
	/// 普通与光影路径使用同一天空着色器资源。
	public static final Identifier SKY_SHADER = Lobecorp.id("core/horde_red_sky");
	/// 刀光原有每批位置数倍率，单轮组数倍率在此基础上叠加。
	public static final int SLASH_POSITION_MULTIPLIER = 2;
	/// 每组在代表点周围三个行列间距内独立选点；失败不退回重复中心。
	public static final int PARTICLE_SCATTER_CELLS = 3, PARTICLE_SCATTER_ATTEMPTS = 6;
	public static final int SOUND_BATCH_MULTIPLIER = 3;
	public static final int SOUND_FREQUENCY_MULTIPLIER = 10;
	/// 每次在最近事件内取声源，区域半径与最大可听距离均以格为单位。
	public static final double SOUND_REGION_RADIUS = 32;
	public static final double MAX_SOUND_RANGE = 256;
	private static final float[] VOICE_GAIN = {0.55F, 0.65F, 0.55F, 0.5F, 0.6F, 0.55F, 0.75F};
	private static final String[] SOUND_TYPES = {"move", "attack", "clear_start", "clear_loop", "clear_end", "jump", "land"};
	/// 既有音效事件资源前缀。
	private static final String SOUND_PREFIX = "event.midnight_cleanup.";
	/// 震动包络的逐刻响应系数。
	public static final float RUMBLE_RESPONSE = 0.25F;
	/// 声音实例启动后的失效回收等待刻数。
	public static final int VOICE_IDLE_TICKS = 10;
	/// 以下声音时间参数沿用原有频率时间轴。
	public static final int LANDING_DELAY_MIN = 29;
	public static final int JUMP_INTERVAL_MIN = 240;
	public static final int ATTACK_INTERVAL_MIN = 30;
	public static final int MOVE_INTERVAL_MIN = 7;
	public static final int CLEANUP_LOOPS_MIN = 3;
	public static final int CLEANUP_START_DELAY = 20;
	public static final int CLEANUP_INTERVAL_MIN = 160;
	public static final int CLEANUP_LOOP_INTERVAL_MIN = 8;
	public static final int CLEANUP_END_DELAY = 28;
	/// 代表声源点的最小间距平方，单位为格平方。
	public static final int AUDIO_POINT_SEPARATION_SQUARED = 16;
	public static final int SOUND_POINT_ATTEMPTS = 8;
	/// 刀光频率在原粒子时间轴内采样，不复制权威配置倍率。
	public static final int SLASH_INTERVAL_MIN = 1;
	public static final int SLASH_GROUPS_MIN = 4;
	public static final int SLASH_PARTICLES_MIN = 1;
	public static final int SLASH_STYLE_COUNT = 3;
	public static final float SLASH_SCALE_MIN = 0.7f;
	/// 刀光出生点相对地面的高度，单位为格。
	public static final double SLASH_HEIGHT = 1.7;
	public static final int DECREASED_PARTICLE_DIVISOR = 3;
	public static final int LANDING_DELAY_MAX = 33;
	public static final int JUMP_INTERVAL_MAX = 400;
	public static final int ATTACK_INTERVAL_MAX = 70;
	public static final int MOVE_INTERVAL_MAX = 13;
	public static final int CLEANUP_LOOPS_MAX = 6;
	public static final int CLEANUP_INTERVAL_MAX = 240;
	public static final int CLEANUP_LOOP_INTERVAL_MAX = 16;
	public static final int SLASH_INTERVAL_VARIATION = 4;
	public static final int SLASH_GROUPS_VARIATION = 7;
	public static final int SLASH_PARTICLES_VARIATION = 3;
	public static final float SLASH_SCALE_VARIATION = 0.5f;
	/// 沿浪潮线路的声源探测偏移，单位为格。
	public static final int AUDIO_SAMPLE_NEAR = 8;
	public static final int AUDIO_SAMPLE_MIDDLE = 32;
	public static final int AUDIO_SAMPLE_FAR = 64;
	public static final int AUDIO_SAMPLE_DISTANT = 128;
	/// 刀光内部检测的行列半径。
	public static final int SLASH_INTERIOR_PROBE_RADIUS = 1;
	/// 浪潮虚拟清道夫的模型与动画资源标识。
	public static final Identifier SWEEPER_MODEL = Lobecorp.id("entity/sweeper_a");
	public static final String RUN_ANIMATION = "run";
	public static final Identifier SWEEPER_TEXTURE = Lobecorp.id("textures/entity/sweeper_a.png");
	/// 走廊端点判定阈值，单位为格。
	public static final double CORRIDOR_END_EPSILON = 0.001;

	private final Map<MinecraftServer, MidnightCleanupOpening> presentations = new WeakHashMap<>();

	public static float viewYaw(float age) {
		return TimelineUtil.sample(YAW, age);
	}

	public static float viewPitch(float age) {
		return TimelineUtil.sample(PITCH, age);
	}

	public static float viewRoll(float age) {
		return TimelineUtil.sample(ROLL, age);
	}

	public static float viewFov(float age) {
		return TimelineUtil.sample(FOV, age);
	}

	public static float viewSide(float age) {
		return TimelineUtil.sample(SIDE, age);
	}

	public static float viewHeight(float age) {
		return TimelineUtil.sample(HEIGHT, age);
	}

	public static float viewForward(float age) {
		return TimelineUtil.sample(FORWARD, age);
	}

	public static int hudDigitX(boolean modern, int index) {
		return (modern ? NEW_X : DEFAULT_X)[index];
	}

	private static Identifier hudTexture(boolean modern, String name) {
		return Lobecorp.id(HUD_TEXTURE_ROOT + (modern ? HUD_NEW_TEXTURE_STYLE : HUD_DEFAULT_TEXTURE_STYLE)
				+ HUD_TEXTURE_DIRECTORY + name + HUD_TEXTURE_EXTENSION);
	}

	public static float voiceGain(int index) {
		return VOICE_GAIN[index];
	}

	public static Identifier soundId(int index) {
		return Lobecorp.id(SOUND_PREFIX + SOUND_TYPES[index]);
	}

	@Override
	public IHordeProfile hordeProfile() {
		return this;
	}

	@Override
	public int spawnCutoffTicks() {
		return SPAWN_CUTOFF_TICKS;
	}

	@Override
	public boolean clearsDebris(HordeInstance instance) {
		return instance.presentationEnabled();
	}

	@Override
	public void start(MinecraftServer server, HordeInstance instance) {
		presentations.computeIfAbsent(server, _ -> new MidnightCleanupOpening()).start(server, instance);
	}

	@Override
	public void tick(MinecraftServer server, HordeSavedData data) {
		presentations.computeIfAbsent(server, _ -> new MidnightCleanupOpening()).tick(server, data);
	}

	@Override
	public void finish(MinecraftServer server, HordeInstance instance) {
		var presentation = presentations.get(server);
		if (presentation != null) {
			presentation.finish(server, instance);
		}
	}

	@Override
	public void stopped(MinecraftServer server, HordeInstance instance) {
		finish(server, instance);
	}

	@Override
	public void skip(ServerPlayer player, UUID instance) {
		var presentation = presentations.get(player.level().getServer());
		if (presentation != null) {
			presentation.skip(player, instance);
		}
	}

	@Override
	public void clear(MinecraftServer server) {
		var presentation = presentations.remove(server);
		if (presentation != null) {
			presentation.clear();
		}
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public String faction() {
		return FACTION;
	}

	@Override
	public HordeRules defaults() {
		return HordeRules.configured();
	}

	@Override
	public double movementSpeed() {
		return SweeperAi.runningGroundSpeed();
	}

	@Override
	public boolean acceptsTarget(HordeTargetContext context, LivingEntity target) {
		if (!CombatUtil.canTargetInLevel(context.level(), target) || target instanceof EntityCorpse<?>) {
			return false;
		}
		if (CombatUtil.isIndiscriminate(target)) {
			return true;
		}
		if (target instanceof IIndigoOrdeal || target.is(LcEntityTypeTags.ORDEAL_INDIGO)) {
			return false;
		}
		var affiliation = CombatData.get(context.level().getServer()).affiliation(target.getUUID());
		return affiliation == null || !affiliation.faction().equals(context.faction());
	}

	@Override
	public Mob createParticipant(ServerLevel level, HordeSpawnContext context) {
		var sweeper = OrdealEntityTypes.SWEEPER.get().create(level, EntitySpawnReason.COMMAND);
		if (sweeper == null) {
			throw new IllegalStateException("清道夫实体类型无法创建");
		}
		sweeper.setPos(context.position());
		EventHooks.finalizeMobSpawn(sweeper, level, level.getCurrentDifficultyAt(sweeper.blockPosition()), EntitySpawnReason.COMMAND, null);
		sweeper.setHealth(sweeper.getMaxHealth());
		return sweeper;
	}

	@Override
	public HordeVisualDefinition visuals() {
		return new HordeVisualDefinition(SWEEPER_MODEL,
				SWEEPER_MODEL, RUN_ANIMATION,
				SWEEPER_TEXTURE);
	}

	@Override
	public EntityDimensions dimensions() {
		return OrdealEntityTypes.SWEEPER.get().getDimensions();
	}

	@Override
	public boolean startsCombat(IEntitySkill<?> skill) {
		return skill == SweeperSkills.ATTACK.get() || skill == SweeperSkills.LEAP.get();
	}
}
