// projectedCards 的真实面序：-X、+X、-Y、+Y、-Z、+Z。
// 六向源帧缓存 24 顶点，固定输出 12 个槽；超远实例的额外两片在 GPU 退化为零面积。
int hordeCardVertex(int vertex, vec3 point, mat2 yaw, vec3 center) {
    vec3 relative = point;
    bool single = FrameLod.y >= 0.0 && dot(relative, relative) > FrameLod.y * FrameLod.y;
    vec3 observer = -relative;
    observer.xz = transpose(yaw) * observer.xz;
    observer -= center;
    int axis = vertex / 4;
    if (single) {
        vec3 magnitude = abs(observer);
        axis = magnitude.x >= magnitude.y && magnitude.x >= magnitude.z ? 0
        : magnitude.y >= magnitude.z ? 1 : 2;
    }
    int face = axis * 2 + (observer[axis] >= 0.0 ? 1 : 0);
    int corner = single && vertex >= 4 ? 0 : vertex % 4;
    return face * 4 + corner;
}
