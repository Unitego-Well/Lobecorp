// 先保持16格距离档，再在同档优先分割边缘；原距离额度与选中数量不变。
const float HORDE_LOD_EDGE_NEAR_SQUARED = 16384.0;

uint hordeLodKey(float squared, float edge) {
    uint band = uint(clamp(floor(sqrt(squared) / 16.0), 0.0, 7.0));
    uint priority = edge > 0.5 ? 0u : 0x10000000u;
    uint distance = uint(clamp(squared, 0.0, 16384.0) * 4096.0);
    return (band << 29u) | priority | distance;
}

float hordeLodDistance(uint key) {
    return float(key & 0x0fffffffu) / 4096.0;
}

// 最终排序表是唯一额度来源；距离域使用整数编码，避免再次开方产生边界差异。
bool hordeLodAccepted(uint key, uint identity, int rank, int count) {
    if (key == 0xffffffffu || identity == 0xffffffffu || identity >= uint(count)) {
        return false;
    }
    if ((key & 0x0fffffffu) >= 0x04000000u) {
        return false;
    }
    uint band = key >> 29u;
    int allowance = 128 - int(band) * 16;
    return rank >= 0 && rank < 128 && rank < allowance;
}
