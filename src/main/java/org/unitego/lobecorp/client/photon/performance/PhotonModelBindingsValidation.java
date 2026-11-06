package org.unitego.lobecorp.client.photon.performance;

import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.model.PhotonModelBindings;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Anchor;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Bone;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Morph;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Stretch;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Transform;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/// 依赖真实 FML 的绑定回归；只由显式开发采样在性能窗口结束后调用。
public class PhotonModelBindingsValidation {
    public static final String REQUEST_ENV = "LOBECORP_LASER_PROFILE_BINDINGS_CHECK";

    public static String verify() {
        partialAndClear();
        continuousEvaluation();
        morphAndIsolation();
        return "bindings_validation_passed_3";
    }

    private static PhotonModelDefinition definition() {
        return new PhotonModelDefinition(new JsonObject(), 1, 1, 2, List.of(), List.of(),
                List.of(new Part("main", "", Vector3.ZERO, Transform.IDENTITY, new Stretch(false, 2, 0, 1),
                        Anchor.NONE, 1, false, true, false)), Map.of(),
                List.of(new Bone("root", "", Transform.IDENTITY)), Map.of(),
                List.of(new Morph("pulse", Map.of(), 0.25f)), List.of());
    }

    private static void partialAndClear() {
        var bindings = new PhotonModelBindings(definition());
        var owner = new PhotonModelOutput();
        require(bindings.partOverrides(owner, 0).isEmpty() && bindings.boneOverrides(owner, 0).isEmpty()
                && bindings.morphWeights().isEmpty(), "未覆盖分量必须为空");
        var position = requireNonNull(bindings.slot("parts.main.position"));
        position.setRaw(new NumberFunction3(2, 3, 4));
        requireNonNull(bindings.slot("bones.root.rotation")).setRaw(new NumberFunction3(0, 90, 0));
        var saved = bindings.partOverrides(owner, 0);
        var part = saved.get("main");
        require(new Vector3(2, 3, 4).equals(part.translation()) && part.rotation() == null && part.scale() == null,
                "部分 TRS 覆盖改变");
        var rotation = requireNonNull(bindings.boneOverrides(owner, 0).get("root").rotation());
        var expected = new Quaternionf().rotationXYZ(0, (float) Math.PI / 2, 0);
        require(Math.abs(expected.y - rotation.y()) < 0.000001f && Math.abs(expected.w - rotation.w()) < 0.000001f,
                "欧拉角转四元数改变");
        bindings.clear();
        require(position == bindings.slot("parts.main.position") && bindings.partOverrides(owner, 1).isEmpty()
                && bindings.boneOverrides(owner, 1).isEmpty(), "清除覆盖或槽身份改变");
        require(new Vector3(2, 3, 4).equals(saved.get("main").translation()), "后续清除修改旧快照");
        boolean immutable = false;
        try {
            saved.clear();
        } catch (UnsupportedOperationException expectedFailure) {
            immutable = true;
        }
        require(immutable, "覆盖结果失去不可变性");
    }

    private static void continuousEvaluation() {
        var bindings = new PhotonModelBindings(definition());
        var owner = new PhotonModelOutput();
        var calls = new ArrayList<String>();
        for (var component : List.of("position", "rotation", "scale")) {
            requireNonNull(bindings.slot("parts.main." + component)).setRaw(new NumberFunction3(0, 0, 0) {
                @Override
                public Vector3f get(float progress, Supplier<Float> random) {
                    calls.add(component);
                    return new Vector3f(progress, 0, 0);
                }
            });
        }
        var first = bindings.partOverrides(owner, 0.25f);
        var second = bindings.partOverrides(owner, 0.75f);
        require(calls.equals(List.of("position", "rotation", "scale", "position", "rotation", "scale")),
                "逐帧求值次数或分量顺序改变");
        require(requireNonNull(first.get("main").translation()).x() == 0.25f
                        && requireNonNull(second.get("main").translation()).x() == 0.75f && first != second,
                "相同函数对象的连续时间求值或旧帧改变");
    }

    private static void morphAndIsolation() {
        var definition = definition();
        var first = new PhotonModelBindings(definition);
        var second = new PhotonModelBindings(definition);
        requireNonNull(first.slot("morphs.pulse.weight")).setRaw(0.5f);
        var saved = first.morphWeights();
        requireNonNull(first.slot("morphs.pulse.weight")).setRaw(0.75f);
        require(saved.equals(Map.of("pulse", 0.5f)) && first.morphWeights().equals(Map.of("pulse", 0.75f))
                && second.morphWeights().isEmpty(), "Morph 快照或实例隔离改变");
        first.clear();
        require(first.morphWeights().isEmpty() && saved.equals(Map.of("pulse", 0.5f)), "Morph 清除修改旧快照");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
