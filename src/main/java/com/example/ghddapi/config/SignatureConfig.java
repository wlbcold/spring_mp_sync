package com.example.ghddapi.config;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
public class SignatureConfig {

    /**
     * 人员名称与签名图片映射配置
     */
    private static final Map<String, String> SIGNATURE_MAPPING = new HashMap<>();

    static {
        //人员签名映射
        SIGNATURE_MAPPING.put("何旦", "template/何旦.png");
        SIGNATURE_MAPPING.put("何德源", "template/何德源.png");
        SIGNATURE_MAPPING.put("何浩翔", "template/何浩翔.png");
        SIGNATURE_MAPPING.put("寿双阳", "template/寿双阳.png");
        SIGNATURE_MAPPING.put("汤烨", "template/汤烨.png");
        SIGNATURE_MAPPING.put("余南", "template/余南.png");
        SIGNATURE_MAPPING.put("赵创超", "template/赵创超.png");
    }

    /**
     * 根据人员名称获取签名图片路径
     */
    public String getSignaturePath(String personName) {
        if (personName == null || personName.trim().isEmpty()) {
            return getDefaultSignaturePath();
        }

        // 精确匹配
        String path = SIGNATURE_MAPPING.get(personName.trim());
        if (path != null) {
            return path;
        }

        // 模糊匹配（包含关系）
        for (Map.Entry<String, String> entry : SIGNATURE_MAPPING.entrySet()) {
            if (personName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return getDefaultSignaturePath();
    }

    /**
     * 获取默认签名图片路径
     */
    public String getDefaultSignaturePath() {
        return "signatures/default_signature.png";
    }

    /**
     * 添加新的签名映射
     */
    public void addSignatureMapping(String personName, String imagePath) {
        SIGNATURE_MAPPING.put(personName, imagePath);
    }

    /**
     * 获取所有配置的人员名称
     */
    public Set<String> getAllPersonNames() {
        return SIGNATURE_MAPPING.keySet();
    }
}