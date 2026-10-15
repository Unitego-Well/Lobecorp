export const STAR_TRAIL_CONFIG = {
    durationTicks: 100,
    looping: 1,
    lightLevel: 15,
    renderer: {
        layer: "Translucent",
        mode: "Billboard",
        sourceBlend: "SRC_ALPHA",
        destinationBlend: "ONE",
        blendFunction: "ADD",
        discardThreshold: 0.01,
        cull: 0,
        depthTest: 1,
        depthMask: 0,
    },
    ribbon: {
        name: "star_trail_ribbon",
        type: "trail_emitter",
        lifetimeTicks: 18,
        widthBlocks: 0.65,
        minVertexDistanceBlocks: 0.045,
        texture: "photon:textures/particle/circle.png",
        uvMode: "Stretch",
        alphaPoints: [[0, 0], [0.2, 0.06], [0.65, 0.3], [1, 0.45]],
        rgbPoints: [[0, 0.7, 0.15, 0.9], [0.45, 0.95, 0.07, 0.9], [1, 1, 0.2, 0.85]],
    },
    stars: {
        type: "particle_emitter",
        simulationSpace: "World",
        emissionMode: "Exacting",
        maxParticles: 128,
        distanceIntervalBlocks: 0,
        tiles: [1, 7],
        uvMode: "WholeSheet",
        frameOverTime: 0,
        cycle: 1,
        shapeType: "cone",
        shapeAngleDegrees: 30,
        alphaPoints: [[0, 0], [0.1, 1], [0.65, 0.8], [1, 0]],
        rgbPoints: [[0, 1, 1, 1], [1, 1, 1, 1]],
        channels: [
            {
                name: "star_trail_pink",
                texture: "lobecorp:textures/particle/the_queen_of_hatred/particle.png",
                sizeBlocks: 0.24,
                lifetimeTicks: 22,
                emissionPerTick: 0.22,
                startSpeed: 0.12,
                radiusBlocks: 0.09,
                frame: 0,
                rollDegrees: 20,
            },
            {
                name: "star_trail_yellow",
                texture: "lobecorp:textures/particle/the_queen_of_hatred/particle2.png",
                sizeBlocks: 0.14,
                lifetimeTicks: 18,
                emissionPerTick: 0.5,
                startSpeed: 0.18,
                radiusBlocks: 0.2,
                frame: 2,
                rollDegrees: 0,
            },
            {
                name: "star_trail_blue",
                texture: "lobecorp:textures/particle/the_queen_of_hatred/particle3.png",
                sizeBlocks: 0.16,
                lifetimeTicks: 20,
                emissionPerTick: 0.35,
                startSpeed: 0.15,
                radiusBlocks: 0.14,
                frame: 2,
                rollDegrees: 30,
            },
        ],
    },
};

const ERRORS = {
    invalidState: "星星拖尾需要暂停的 FX 工程状态。",
    missingType: "当前 Photon 未注册发射器类型：",
    missingObject: "工程必须恰有一个同名发射器：",
    wrongType: "发射器类型不符：",
    missingPath: "原生参数不存在，请先启用对应模块并重新读取状态：",
    wrongFunction: "原生函数类型不符：",
    wrongShape: "原生发射形状不符：",
    wrongMaterial: "星星拖尾需要一个原生 texture 材质槽：",
    invalidClone: "参数克隆函数必须返回独立副本。",
    tooManyOperations: "星星拖尾操作超过当前 MCP 批次限制。",
};

export function buildStarTrailOperations(capabilities, state, helpers = {}) {
    if (state?.kind !== "fx" || state.playing !== false || !Array.isArray(state.objects)) {
        throw new Error(ERRORS.invalidState);
    }

    const config = STAR_TRAIL_CONFIG;
    for (const type of [config.ribbon.type, config.stars.type]) {
        if (!capabilities?.emitterTypes?.some(entry => entry.type === type)) {
            throw new Error(ERRORS.missingType + type);
        }
    }

    const clone = typeof helpers.clone === "function" ? helpers.clone : structuredClone;
    const ribbon = findEmitter(state, config.ribbon.name, config.ribbon.type);
    const stars = config.stars.channels.map(channel => ({
        channel,
        object: findEmitter(state, channel.name, config.stars.type),
    }));
    const operations = [];
    const set = (object, path, value) => setExisting(operations, object, path, value, clone);

    // 禁用模块只序列化 _enable；先提交启用批次，再读取状态调用本函数。
    for (const {object} of stars) {
        for (const path of ["/config/uvAnimation/_enable", "/config/colorOverLifetime/_enable"]) {
            if (readExisting(object.parameters, path) !== 1) {
                set(object, path, 1);
            }
        }
    }

    if (operations.length > 0) {
        return operations;
    }

    configureRibbon(ribbon, set);
    for (const {object, channel} of stars) {
        configureStars(object, channel, set);
    }

    if (operations.length > capabilities.maxOperations) {
        throw new Error(ERRORS.tooManyOperations);
    }

    return operations;
}

function findEmitter(state, name, type) {
    const matches = state.objects.filter(object => object.name === name);
    if (matches.length !== 1) {
        throw new Error(ERRORS.missingObject + name);
    }

    const object = matches[0];
    if (object.type !== type || typeof object.objectId !== "string") {
        throw new Error(ERRORS.wrongType + name);
    }

    return object;
}

function configureRibbon(object, set) {
    const config = STAR_TRAIL_CONFIG;
    const ribbon = config.ribbon;
    set(object, "/config/duration", config.durationTicks);
    set(object, "/config/looping", config.looping);
    set(object, "/config/time", ribbon.lifetimeTicks);
    set(object, "/config/minVertexDistance", ribbon.minVertexDistanceBlocks);
    set(object, "/config/smoothInterpolation", 1);
    set(object, "/config/uvMode", ribbon.uvMode);
    setNumber(object, "/config/widthOverTrail", ribbon.widthBlocks, set);
    setGradient(object, "/config/colorOverTrail", ribbon.alphaPoints, ribbon.rgbPoints, set);
    configureLight(object, set);
    configureMaterial(object, ribbon.texture, set);
}

function configureStars(object, channel, set) {
    const config = STAR_TRAIL_CONFIG;
    const stars = config.stars;
    set(object, "/config/duration", config.durationTicks);
    set(object, "/config/looping", config.looping);
    set(object, "/config/simulationSpace", stars.simulationSpace);
    set(object, "/config/maxParticles", stars.maxParticles);
    set(object, "/config/renderer/renderMode", config.renderer.mode);
    set(object, "/config/emission/emissionMode", stars.emissionMode);
    setNumber(object, "/config/startLifetime", channel.lifetimeTicks, set);
    setNumber(object, "/config/startSpeed", channel.startSpeed, set);
    setNumber(object, "/config/emission/emissionRate", channel.emissionPerTick, set);
    setNumber(object, "/config/emission/distanceRate", stars.distanceIntervalBlocks, set);

    for (let axis = 0; axis < 3; axis++) {
        setNumber(object, "/config/startSize/" + axis, channel.sizeBlocks, set);
    }

    setNumber(object, "/config/startRotation/2", channel.rollDegrees, set);
    const shapePath = "/config/shape/shape";
    if (readExisting(object.parameters, shapePath + "/type") !== stars.shapeType) {
        throw new Error(ERRORS.wrongShape + object.name);
    }

    set(object, shapePath + "/data/radius", channel.radiusBlocks);
    set(object, shapePath + "/data/angle", stars.shapeAngleDegrees);
    set(object, "/config/uvAnimation/tiles", stars.tiles);
    set(object, "/config/uvAnimation/animation", stars.uvMode);
    set(object, "/config/uvAnimation/cycle", stars.cycle);
    setNumber(object, "/config/uvAnimation/startFrame", channel.frame, set);
    setNumber(object, "/config/uvAnimation/frameOverTime", stars.frameOverTime, set);
    setGradient(object, "/config/colorOverLifetime/color", stars.alphaPoints, stars.rgbPoints, set);
    configureLight(object, set);
    configureMaterial(object, channel.texture, set);
}

function configureLight(object, set) {
    set(object, "/config/lights/_enable", 1);
    setNumber(object, "/config/lights/skyLight", STAR_TRAIL_CONFIG.lightLevel, set);
    setNumber(object, "/config/lights/blockLight", STAR_TRAIL_CONFIG.lightLevel, set);
}

function configureMaterial(object, texture, set) {
    const renderer = STAR_TRAIL_CONFIG.renderer;
    const materials = readExisting(object.parameters, "/config/renderer/materials/payload");
    if (!Array.isArray(materials) || materials.length !== 1 || materials[0].material?.type !== "texture") {
        throw new Error(ERRORS.wrongMaterial + object.name);
    }

    const path = "/config/renderer/materials/payload/0";
    set(object, "/config/renderer/layer", renderer.layer);
    set(object, path + "/material/data/texture", texture);
    set(object, path + "/material/data/discardThreshold", renderer.discardThreshold);
    set(object, path + "/cull", renderer.cull);
    set(object, path + "/depthTest", renderer.depthTest);
    set(object, path + "/depthMask", renderer.depthMask);
    set(object, path + "/blendMode/enableBlend", 1);
    set(object, path + "/blendMode/srcColorFactor", renderer.sourceBlend);
    set(object, path + "/blendMode/dstColorFactor", renderer.destinationBlend);
    set(object, path + "/blendMode/blendFunc", renderer.blendFunction);
}

function setNumber(object, path, value, set) {
    if (readExisting(object.parameters, path + "/type") !== "constant") {
        throw new Error(ERRORS.wrongFunction + object.name + path);
    }

    set(object, path + "/data/number", value);
}

function setGradient(object, path, alphaPoints, rgbPoints, set) {
    if (readExisting(object.parameters, path + "/type") !== "gradient") {
        throw new Error(ERRORS.wrongFunction + object.name + path);
    }

    set(object, path + "/data/gradientColor/a", alphaPoints);
    set(object, path + "/data/gradientColor/rgb", rgbPoints);
}

function setExisting(operations, object, path, value, clone) {
    const existing = readExisting(object.parameters, path);
    if (JSON.stringify(existing) === JSON.stringify(value)) {
        return;
    }

    const copied = clone(value);
    if (value !== null && typeof value === "object" && copied === value) {
        throw new Error(ERRORS.invalidClone);
    }

    operations.push({op: "set_parameter", objectId: object.objectId, path, value: copied});
}

function readExisting(parameters, path) {
    let value = parameters;
    for (const segment of path.slice(1).split("/")) {
        const key = segment.replace(/~1/g, "/").replace(/~0/g, "~");
        if (value === null || typeof value !== "object" || !Object.hasOwn(value, key)) {
            throw new Error(ERRORS.missingPath + path);
        }

        value = value[key];
    }

    return value;
}
