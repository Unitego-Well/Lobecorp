package org.unitego.lobecorp.entity.ordeal.indigo;

/// 清道夫变种 a/b/c/d
public enum SweeperVariant {
    A, B, C, D;

    /// 资源文件命名段（小写），用于拼接 sweeper_&lt;variant&gt; 的模型/动画/纹理路径
    public String resourceName() {
        return name().toLowerCase();
    }
}
