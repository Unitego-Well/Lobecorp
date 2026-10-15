package org.unitego.lobecorp.conductor.config;

/// 指挥家运行规则与业务参数的集中定义。
public interface ConductorRules {
	/// 指挥家世界目录的客户端同步间隔，单位为 tick。
	int DIRECTORY_SYNC_INTERVAL_TICKS = 20;
	/// 指挥家镜头观察位置的客户端同步间隔，单位为 tick。
	int VIEW_SYNC_INTERVAL_TICKS = 5;
	/// 镜头停止同步后释放观察区块票据的等待时间，单位为 tick。
	int VIEW_TIMEOUT_TICKS = 40;
	/// 未配置跟随范围属性时的目标搜索距离，单位为方块。
	double TARGET_SEARCH_RANGE = 32.0D;
	/// 队友共享攻击目标的距离，单位为方块。
	double TEAM_AGGRO_SHARE_RANGE = 16.0D;
	/// 巡逻模式允许活动的半径，单位为方块。
	double PATROL_RADIUS = 20.0D;
	/// 警戒模式允许活动的半径，单位为方块。
	double GUARD_RADIUS = 10.0D;
	/// 普通实体到达移动目的地的距离阈值，单位为方块。
	double ARRIVAL_DISTANCE = 0.35D;
	/// 末影龙到达移动目的地的距离阈值，单位为方块。
	double DRAGON_ARRIVAL_DISTANCE = 4.0D;
	/// 接近目的地时开始减弱避让的距离，单位为方块。
	double FINAL_APPROACH_DISTANCE = 1.5D;
	/// 编队成员之间额外保留的间距，单位为方块。
	double FORMATION_GAP = 0.75D;
	/// 编队方向归一化时使用的平方长度误差阈值。
	double FORMATION_DIRECTION_EPSILON = 1.0E-8D;
	/// 无法取得实体宽度时使用的编队宽度，单位为方块。
	double FORMATION_DEFAULT_WIDTH = 1.0D;
	/// 实体避让检测额外扩展的距离，单位为方块。
	double ENTITY_AVOIDANCE_MARGIN = 0.25D;
	/// 实体避让施加的水平速度调整倍率。
	double ENTITY_AVOIDANCE_STRENGTH = 0.08D;
	/// 指挥家移动指令使用的寻路速度倍率。
	double MOVEMENT_SPEED = 1.0D;
	/// 使用实体自然移动速度的特殊配置值。
	double NATURAL_MOVEMENT_SPEED = -1.0D;
	/// 实体避让提前检查的距离，单位为方块。
	double ENTITY_AVOIDANCE_LOOKAHEAD = 2.0D;
	/// 尝试侧向避让时检查的方向数量。
	int ENTITY_AVOIDANCE_SIDES = 2;
	/// 移动重新规划之间的等待时长，单位为 tick。
	int MOVEMENT_RETRY_TICKS = 10;
	/// 分段寻路的最大目标距离，单位为方块。
	int PATH_SEGMENT_RANGE = 64;
	/// 认定路径取得进展的最小距离，单位为方块。
	double PATH_MIN_PROGRESS = 0.5D;
	/// 移动没有进展时判定停滞的等待时长，单位为 tick。
	int MOVEMENT_STALL_TICKS = 40;
	/// 移动停滞后允许重新规划的最大次数。
	int MOVEMENT_MAX_REPLANS = 3;
	/// 更新移动进度时要求的最小位移，单位为方块。
	double MOVEMENT_PROGRESS_DISTANCE = 0.1D;
	/// 到达目的地时允许的高度误差，单位为方块。
	double ARRIVAL_HEIGHT = 0.5D;
	/// 搜索编队站位可通行位置的范围，单位为方块。
	int SLOT_SEARCH_RADIUS = 4;
	/// 检测移动路径安全性时的采样间距，单位为方块。
	double PATH_SAMPLE_DISTANCE = 0.25D;
	/// 地面支撑检测向下扩展的深度，单位为方块。
	double SUPPORT_PROBE_DEPTH = 0.1D;
	/// 对指定位置进行普通远程攻击的间隔，单位为 tick。
	int POINT_ATTACK_INTERVAL_TICKS = 40;
	/// 弓箭攻击的拉弓时长，单位为 tick。
	int BOW_DRAW_TICKS = 20;
	/// 弩装填完成后的基础等待时长，单位为 tick。
	int CROSSBOW_READY_DELAY_TICKS = 20;
	/// 弩装填完成后随机追加等待的最大时长，单位为 tick。
	int CROSSBOW_READY_RANDOM_TICKS = 20;
	/// 旋风人风弹攻击的蓄力时长，单位为 tick。
	int BREEZE_CHARGE_TICKS = 15;
	/// 旋风人风弹攻击后的恢复时长，单位为 tick。
	int BREEZE_RECOVER_TICKS = 4;
	/// 旋风人风弹攻击的冷却时长，单位为 tick。
	int BREEZE_SHOOT_COOLDOWN_TICKS = 10;
	/// 旋风人风弹发射的速度参数。
	float BREEZE_PROJECTILE_SPEED = 0.7F;
	/// 旋风人风弹的基础散布参数。
	float BREEZE_UNCERTAINTY_BASE = 5.0F;
	/// 每级难度减少的旋风人风弹散布参数。
	float BREEZE_UNCERTAINTY_PER_DIFFICULTY = 4.0F;
	/// 旋风人风弹发射音效的音量。
	float BREEZE_SHOOT_VOLUME = 1.5F;
	/// 普通远程攻击指向指定位置时的发射速度。
	float POINT_ATTACK_POWER = 1.0F;
	/// 弩攻击指向指定位置时的发射速度。
	float CROSSBOW_POINT_ATTACK_POWER = 1.6F;
	/// 普通远程攻击的基础散布参数。
	float POINT_ATTACK_UNCERTAINTY_BASE = 14.0F;
	/// 每级难度减少的普通远程攻击散布参数。
	float POINT_ATTACK_UNCERTAINTY_PER_DIFFICULTY = 4.0F;
	/// 手动能力请求保留的最长时间，单位为 tick。
	int MANUAL_ABILITY_REQUEST_TICKS = 10;
	/// 监守者声波的最大水平距离，单位为方块。
	double WARDEN_SONIC_BOOM_HORIZONTAL_RANGE = 15.0D;
	/// 监守者声波允许的最大高度差，单位为方块。
	double WARDEN_SONIC_BOOM_VERTICAL_RANGE = 20.0D;
	/// 监守者声波预览判断框的半宽，单位为方块。
	double WARDEN_SONIC_BOOM_PREVIEW_HALF_WIDTH = 0.75D;
	/// 监守者声波对有效目标造成的伤害。
	double WARDEN_SONIC_BOOM_DAMAGE = 10.0D;
	/// 监守者声波的前摇时长，单位为 tick。
	int WARDEN_SONIC_WINDUP_TICKS = 34;
	/// 监守者声波完整动作的持续时长，单位为 tick。
	int WARDEN_SONIC_DURATION_TICKS = 60;
	/// 监守者声波粒子超出命中终点的延伸数量。
	int WARDEN_SONIC_PARTICLE_EXTENSION = 7;
	/// 触发监守者声波表现的原版实体事件编号。
	byte WARDEN_SONIC_EVENT = 62;
	/// 监守者声波音效的音量。
	float WARDEN_SONIC_VOLUME = 3.0F;
	/// 监守者声波音效的音调。
	float WARDEN_SONIC_PITCH = 1.0F;
	/// 监守者声波施加的水平击退强度。
	double WARDEN_SONIC_KNOCKBACK_HORIZONTAL = 2.5D;
	/// 监守者声波施加的垂直击退强度。
	double WARDEN_SONIC_KNOCKBACK_VERTICAL = 0.5D;
	/// 自动分配队伍颜色时使用的色相分段数量。
	int COLOR_HUE_STEPS = 360;
	/// 自动分配队伍颜色时的饱和度。
	float COLOR_SATURATION = 0.75F;
	/// 自动分配队伍颜色时的亮度。
	float COLOR_BRIGHTNESS = 0.95F;
}
