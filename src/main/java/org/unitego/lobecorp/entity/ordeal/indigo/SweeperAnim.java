package org.unitego.lobecorp.entity.ordeal.indigo;

/// 清道夫动画状态。
/// <p>
/// 由服务端写入 synced data，客户端渲染时根据状态播放对应动画。
/// 基础状态（IDLE/MOVE/RUN）按移动速度切换，动作状态由技能/清理驱动。
public enum SweeperAnim {
    /// 待机
    IDLE,
    /// 移动
    MOVE,
    /// 奔跑
    RUN,
    /// 攻击第 1 段
    ATTACK1,
    /// 攻击第 2 段
    ATTACK2,
    /// 攻击第 3 段
    ATTACK3,
    /// 飞扑起跳
    LEAP,
    /// 飞扑落地
    LEAP2,
    /// 清理开始
    CLEAR1,
    /// 清理处理中
    CLEAR2,
    /// 清理结束
    CLEAR3
}
