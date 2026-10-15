import {randomUUID} from "node:crypto";

export const LASER_CONFIG = Object.freeze({
    name: "白芯粉紫变量激光",
    controller: {name: "laser_controls", type: "lobecorp_animation_controller"},
    animation: {name: "laser", duration: 100, looping: true},
    variables: {
        length: {type: "NUMBER", default: 12, minimum: 0.01, maximum: 64, unit: "block"},
        width: {type: "NUMBER", default: 0.8, minimum: 0.01, maximum: 8, unit: "full_diameter_block"},
        intensity: {type: "NUMBER", default: 1, minimum: 0, maximum: 1, unit: "alpha_multiplier"},
        enabled: {type: "BOOLEAN", default: true, minimum: 0, maximum: 1, unit: null},
    },
    beam: {
        type: "beam_emitter", endpoint: [0, 0, -1], duration: 100, looping: true,
        startDelay: 0, emitRate: 0, raycast: "NONE", light: 15,
    },
    material: {
        type: "texture", texture: "photon:textures/particle/laser.png", discardThreshold: 0.001,
        depthTest: true, depthMask: false, cull: false,
        blend: {enabled: true, function: "ADD", sourceColor: "SRC_ALPHA", destinationColor: "ONE"},
    },
    layers: [
        {name: "laser_core", widthMultiplier: 0.22, color: 0xfffff4ff | 0, order: 2},
        {name: "laser_body", widthMultiplier: 1, color: 0xffff48dc | 0, order: 1},
        {name: "laser_halo", widthMultiplier: 2.6, color: 0x70ad36ff | 0, order: 0},
    ],
});

const definitionVersion = 6;
const animationId = randomUUID();
const parameterIds = Object.fromEntries(Object.keys(LASER_CONFIG.variables).map(name => [name, randomUUID()]));
const errors = {
    invalidState: "变量激光需要包含对象列表的 FX 工程状态。",
    missingObject: "变量激光对象不存在、重名或类型不符：",
    invalidUuid: "变量激光对象 UUID 格式不合法：",
    missingSample: "变量激光缺少注册类型、已有参数路径或匹配的原生函数／材质：",
};

function laserObjects(state) {
    if (state?.kind !== "fx" || !Array.isArray(state.objects)) {
        throw new Error(errors.invalidState);
    }

    const find = (name, type) => {
        const objects = state.objects.filter(object => object.name === name);
        if (objects.length !== 1 || objects[0].type !== type) {
            throw new Error(errors.missingObject + name);
        }

        const object = objects[0];
        uuidInts(object.objectId);
        return object;
    };
    const beams = LASER_CONFIG.layers.map(layer => find(layer.name, LASER_CONFIG.beam.type));
    find(LASER_CONFIG.controller.name, LASER_CONFIG.controller.type);
    return beams;
}

function uuidInts(uuid) {
    if (typeof uuid !== "string" || !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(uuid)) {
        throw new Error(errors.invalidUuid + uuid);
    }

    const compact = uuid.replaceAll("-", "");
    return Array.from({length: 4}, (_, index) => Number.parseInt(compact.slice(index * 8, (index + 1) * 8), 16) | 0);
}

function snbtFloat(value) {
    return String(value) + "f";
}

function singleChannel(value) {
    const point = ["0f", snbtFloat(value)];
    return "[" + [...point, ...point, ...point, ...point].join(",") + "]";
}

function scaleProperty() {
    const base = [1, 1, LASER_CONFIG.variables.length.default];
    const channels = base.map(value => "[" + singleChannel(value) + "]");
    return '{type:"scale",base:[' + base.map(snbtFloat).join(",") + "],channels:[" + channels.join(",")
        + "],rangeMin:0f,rangeMax:" + snbtFloat(LASER_CONFIG.variables.length.maximum) + ",exprClips:[[],[],[]]}";
}

function widthProperty(layer) {
    const width = LASER_CONFIG.variables.width.default * layer.widthMultiplier / 2;
    return '{type:"config",base:[' + snbtFloat(width) + "],channels:[[" + singleChannel(width)
        + ']],rangeMin:0f,rangeMax:1f,exprClips:[[]],path:"width",valueType:"NUMBER_FUNCTION",'
        + 'label:"BeamConfig.width",curveClips:[[]]}';
}

function colorProperty(layer) {
    return '{type:"config",path:"color",valueType:"COLOR",label:"BeamConfig.color",stops:[{tick:0f,argb:'
        + layer.color + '}],gradientClips:[]}';
}

function animationTrack(beam, layer) {
    const properties = [scaleProperty(), widthProperty(layer), colorProperty(layer)];
    return '{type:"animation",data:{target:[I;' + uuidInts(beam.objectId).join(",")
        + '],name:"",mute:0b,lock:0b,clips:[],properties:[' + properties.join(",") + "]}}";
}

function parameterDefinition(name, variable) {
    const initial = typeof variable.default === "boolean" ? Number(variable.default) : variable.default;
    return "{id:" + JSON.stringify(parameterIds[name]) + ",name:" + JSON.stringify(name)
        + ",type:" + JSON.stringify(variable.type) + ",defaults:[" + snbtFloat(initial)
        + "],minimum:" + snbtFloat(variable.minimum) + ",maximum:" + snbtFloat(variable.maximum) + "}";
}

function parameterBinding(beam, property, parameter, component, blend, scale = 1, scaleParameter = "") {
    return "{target:" + JSON.stringify(beam.objectId) + ",property:" + JSON.stringify(property)
        + ",blend:" + JSON.stringify(blend) + ',source:"PARAMETER",parameter:'
        + JSON.stringify(parameterIds[parameter]) + ",component:" + component
        + ",scale:" + snbtFloat(scale) + ',offset:0f,scaleParameter:'
        + JSON.stringify(scaleParameter) + ',offsetParameter:""}';
}

function beamBindings(beam, layer) {
    return [
        parameterBinding(beam, "transform.scale", "length", 2, "OVERRIDE"),
        parameterBinding(beam, "config.width", "width", -1, "OVERRIDE", layer.widthMultiplier / 2),
        // enabled 只切换可见性；实例继续模拟，停止与销毁仍由原生生命周期接口负责。
        parameterBinding(beam, "config.color", "enabled", 3, "MULTIPLY", 1, parameterIds.intensity),
    ];
}

export function buildLaserDefinitionSnbt(state) {
    const beams = laserObjects(state);
    const parameters = Object.entries(LASER_CONFIG.variables).map(([name, variable]) => parameterDefinition(name, variable));
    const tracks = beams.map((beam, index) => animationTrack(beam, LASER_CONFIG.layers[index]));
    const bindings = beams.flatMap((beam, index) => beamBindings(beam, LASER_CONFIG.layers[index]));
    return "{version:" + definitionVersion + ",defaultAnimation:" + JSON.stringify(animationId)
        + ",animations:[{id:" + JSON.stringify(animationId) + ",name:" + JSON.stringify(LASER_CONFIG.animation.name)
        + ",duration:" + LASER_CONFIG.animation.duration + "d,autoDuration:0b,autoEnableModules:0b,loop:"
        + Number(LASER_CONFIG.animation.looping) + 'b,priority:0,completion:"HOLD",durationParameter:"",parameters:['
        + parameters.join(",") + "],tracks:[" + tracks.join(",") + "],bindings:[" + bindings.join(",")
        + "],markers:[]}],rules:[]}";
}

export function buildLaserOperations(capabilities, state) {
    const beams = laserObjects(state);
    const descriptor = capabilities?.emitterTypes?.find(type => type.type === LASER_CONFIG.beam.type);
    if (!descriptor || !["width", "color"].every(path => descriptor.runtimeBindings?.some(binding => binding.path === path))) {
        throw new Error(errors.missingSample + LASER_CONFIG.beam.type);
    }

    return beams.flatMap((beam, index) => layerOperations(beam, LASER_CONFIG.layers[index]));
}

function parameterValue(beam, path) {
    let value = beam.parameters;
    for (const segment of path.slice(1).split("/")) {
        if (value === null || typeof value !== "object" || !Object.hasOwn(value, segment)) {
            throw new Error(errors.missingSample + path);
        }

        value = value[segment];
    }
    return value;
}

function layerOperations(beam, layer) {
    const materialPath = "/config/renderer/materials/payload/0";
    const material = parameterValue(beam, materialPath + "/material");
    if (parameterValue(beam, "/config/width/type") !== "constant"
        || parameterValue(beam, "/config/color/type") !== "color"
        || material.type !== LASER_CONFIG.material.type) {
        throw new Error(errors.missingSample + layer.name);
    }

    const values = [
        ["/config/end", LASER_CONFIG.beam.endpoint],
        ["/transform/localScale", [1, 1, LASER_CONFIG.variables.length.default]],
        ["/config/duration", LASER_CONFIG.beam.duration],
        ["/config/looping", Number(LASER_CONFIG.beam.looping)],
        ["/config/startDelay", LASER_CONFIG.beam.startDelay],
        ["/config/width/data/number", LASER_CONFIG.variables.width.default * layer.widthMultiplier / 2],
        ["/config/color/data/number", layer.color],
        ["/config/emitRate/data/number", LASER_CONFIG.beam.emitRate],
        ["/config/raycast", LASER_CONFIG.beam.raycast],
        ["/config/lights/_enable", 1],
        ["/config/lights/skyLight/data/number", LASER_CONFIG.beam.light],
        ["/config/lights/blockLight/data/number", LASER_CONFIG.beam.light],
        ["/config/renderer/orderInLayer", layer.order],
        [materialPath + "/material/data/texture", LASER_CONFIG.material.texture],
        [materialPath + "/material/data/discardThreshold", LASER_CONFIG.material.discardThreshold],
        [materialPath + "/depthTest", Number(LASER_CONFIG.material.depthTest)],
        [materialPath + "/depthMask", Number(LASER_CONFIG.material.depthMask)],
        [materialPath + "/cull", Number(LASER_CONFIG.material.cull)],
        [materialPath + "/blendMode/enableBlend", Number(LASER_CONFIG.material.blend.enabled)],
        [materialPath + "/blendMode/blendFunc", LASER_CONFIG.material.blend.function],
        [materialPath + "/blendMode/srcColorFactor", LASER_CONFIG.material.blend.sourceColor],
        [materialPath + "/blendMode/dstColorFactor", LASER_CONFIG.material.blend.destinationColor],
    ];
    return values.map(([path, value]) => {
        parameterValue(beam, path);
        return {op: "set_parameter", objectId: beam.objectId, path, value: structuredClone(value)};
    });
}

