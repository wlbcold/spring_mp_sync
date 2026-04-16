package com.example.ghddapi.sync.service.impl;

import com.example.ghddapi.sync.dto.DetailSyncRequest;
import com.example.ghddapi.sync.mapper.DataSyncMapper;
import com.example.ghddapi.sync.service.DetailSyncService;
import com.example.ghddapi.util.R;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetailSyncServiceImpl implements DetailSyncService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DataSyncMapper dataSyncMapper;

    @Override
    public R<String> syncDetailData(DetailSyncRequest request) {
        try {
            // 1. 从源表读取数据
            List<Map<String, Object>> sourceDataList = dataSyncMapper.selectAllData(request.getSourceTableName());
            if (sourceDataList.isEmpty()) {
                return R.ok("源表中没有数据");
            }

            // 2. 获取目标表结构
            List<Map<String, String>> targetColumns = dataSyncMapper.getTableColumns(request.getTargetTableName());
            if (targetColumns.isEmpty()) {
                return R.fail("目标表不存在或表中没有字段: " + request.getTargetTableName());
            }

            // 3. 遍历源表数据，调用详情接口
            int successCount = 0;
            int failCount = 0;
            int interval = request.getIntervalMs() != null ? request.getIntervalMs() : 100;

            for (Map<String, Object> sourceData : sourceDataList) {
                try {
                    // 判断请求方法，构建对应的参数
                    String method = request.getMethod() != null ? request.getMethod().toUpperCase() : "GET";
                    boolean isPostOrPut = "POST".equals(method) || "PUT".equals(method);
                    String bodyType = request.getBodyType() != null ? request.getBodyType().toUpperCase() : "FORM";
                    boolean isJsonBody = "JSON".equals(bodyType);

                    Map<String, String> paramMap;
                    if (isPostOrPut && !isJsonBody) {
                        // POST/PUT 表单参数
                        paramMap = buildFormParamMap(sourceData, request);
                    } else {
                        // GET 查询参数 或 POST/PUT JSON参数
                        paramMap = buildQueryParamMap(sourceData, request);
                    }

                    if (paramMap.isEmpty()) {
                        log.warn("源表数据中参数字段为空，跳过");
                        failCount++;
                        continue;
                    }

                    // 调用详情接口
                    JsonNode detailData = callDetailApi(request, paramMap, sourceData);

                    // 解析详情数据
                    List<Map<String, Object>> parsedDataList = parseDetailDataList(detailData, request.getDataPath());

                    if (parsedDataList != null && !parsedDataList.isEmpty()) {
                        // 遍历并插入每条数据
                        for (Map<String, Object> parsedData : parsedDataList) {
                            if (parsedData != null && !parsedData.isEmpty()) {
                                insertDetailData(request.getTargetTableName(), targetColumns, parsedData, request.getFieldMapping());
                            }
                        }
                        successCount++;
                    } else {
                        log.warn("获取详情数据为空，参数: {}", paramMap);
                        failCount++;
                    }

                    // 间隔一段时间，防止请求过快
                    if (interval > 0) {
                        Thread.sleep(interval);
                    }

                } catch (Exception e) {
                    log.error("处理单条数据详情失败: {}", e.getMessage());
                    failCount++;
                }
            }

            String resultMsg = String.format("详情同步完成，成功: %d 条，失败: %d 条", successCount, failCount);
            log.info(resultMsg);
            return R.ok(resultMsg);

        } catch (Exception e) {
            log.error("详情数据同步失败", e);
            return R.fail("详情数据同步失败: " + e.getMessage());
        }
    }

    private Object getFieldValue(Map<String, Object> data, String fieldName) {
        // 尝试多种方式获取字段值
        // 1. 原始字段名
        if (data.containsKey(fieldName)) {
            return data.get(fieldName);
        }
        // 2. 驼峰转下划线
        String underscoreName = toUnderscoreCase(fieldName);
        if (data.containsKey(underscoreName)) {
            return data.get(underscoreName);
        }
        // 3. 忽略大小写匹配
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(fieldName) ||
                entry.getKey().equalsIgnoreCase(underscoreName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 构建GET请求查询参数字典
     */
    private Map<String, String> buildQueryParamMap(Map<String, Object> sourceData, DetailSyncRequest request) {
        Map<String, String> paramMap = new HashMap<>();

        // 1. 处理主参数字段（sourceFieldName 和 apiQueryFieldName）
        if (request.getSourceFieldName() != null && !request.getSourceFieldName().isEmpty()) {
            String[] sourceFields = request.getSourceFieldName().split(",");
            String[] apiFields = request.getApiQueryFieldName() != null ?
                    request.getApiQueryFieldName().split(",") : sourceFields;

            for (int i = 0; i < sourceFields.length; i++) {
                String sourceField = sourceFields[i].trim();
                String apiField = i < apiFields.length ? apiFields[i].trim() : sourceField;

                Object value = getFieldValue(sourceData, sourceField);
                if (value != null) {
                    paramMap.put(apiField, value.toString());
                }
            }
        }

        // 2. 处理GET查询参数映射
        if (request.getQueryParamMapping() != null && !request.getQueryParamMapping().isEmpty()) {
            for (Map.Entry<String, String> entry : request.getQueryParamMapping().entrySet()) {
                String sourceField = entry.getKey();
                String apiField = entry.getValue();

                Object value = getFieldValue(sourceData, sourceField);
                if (value != null) {
                    paramMap.put(apiField, value.toString());
                }
            }
        }

        // 3. 添加自定义固定参数
        if (request.getCustomParams() != null && !request.getCustomParams().isEmpty()) {
            paramMap.putAll(request.getCustomParams());
        }

        return paramMap;
    }

    /**
     * 构建POST/PUT表单参数字典
     */
    private Map<String, String> buildFormParamMap(Map<String, Object> sourceData, DetailSyncRequest request) {
        Map<String, String> paramMap = new HashMap<>();

        // 1. 处理主参数字段（sourceFieldName 和 apiQueryFieldName）
        if (request.getSourceFieldName() != null && !request.getSourceFieldName().isEmpty()) {
            String[] sourceFields = request.getSourceFieldName().split(",");
            String[] apiFields = request.getApiQueryFieldName() != null ?
                    request.getApiQueryFieldName().split(",") : sourceFields;

            for (int i = 0; i < sourceFields.length; i++) {
                String sourceField = sourceFields[i].trim();
                String apiField = i < apiFields.length ? apiFields[i].trim() : sourceField;

                Object value = getFieldValue(sourceData, sourceField);
                if (value != null) {
                    paramMap.put(apiField, value.toString());
                }
            }
        }

        // 2. 处理POST/PUT表单参数映射
        if (request.getFormParamMapping() != null && !request.getFormParamMapping().isEmpty()) {
            for (Map.Entry<String, String> entry : request.getFormParamMapping().entrySet()) {
                String sourceField = entry.getKey();
                String apiField = entry.getValue();

                Object value = getFieldValue(sourceData, sourceField);
                if (value != null) {
                    paramMap.put(apiField, value.toString());
                }
            }
        }

        // 3. 添加自定义固定参数
        if (request.getCustomParams() != null && !request.getCustomParams().isEmpty()) {
            paramMap.putAll(request.getCustomParams());
        }

        return paramMap;
    }

    private JsonNode callDetailApi(DetailSyncRequest request, Map<String, String> paramMap, Map<String, Object> sourceData) throws Exception {
        String urlTemplate = request.getDetailApiUrlTemplate();
        String method = request.getMethod() != null ? request.getMethod().toUpperCase() : "GET";
        String bodyType = request.getBodyType() != null ? request.getBodyType().toUpperCase() : "FORM";

        HttpHeaders headers = new HttpHeaders();
        if (request.getHeaders() != null) {
            request.getHeaders().forEach(headers::set);
        }

        String url;
        HttpEntity<?> entity;

        // 判断是否为POST/PUT请求且使用JSON body
        boolean isPostOrPut = "POST".equals(method) || "PUT".equals(method);
        boolean isJsonBody = "JSON".equals(bodyType);

        if (isPostOrPut && isJsonBody) {
            // POST/PUT JSON body模式
            url = urlTemplate;
            // 设置Content-Type为application/json
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 构建JSON body
            String bodyJson;
            if (request.getBodyTemplate() != null && !request.getBodyTemplate().isEmpty()) {
                // 使用用户提供的模板，替换所有{key}为对应的参数值
                bodyJson = request.getBodyTemplate();
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    bodyJson = bodyJson.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            } else {
                // 使用默认模板，将所有参数构建成JSON
                StringBuilder jsonBuilder = new StringBuilder("{");
                int i = 0;
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    if (i > 0) jsonBuilder.append(", ");
                    jsonBuilder.append("\"").append(entry.getKey()).append("\": \"").append(entry.getValue()).append("\"");
                    i++;
                }
                jsonBuilder.append("}");
                bodyJson = jsonBuilder.toString();
            }

            entity = new HttpEntity<>(bodyJson, headers);
        } else if (isPostOrPut) {
            // POST/PUT 表单模式
            url = urlTemplate;
            // 设置Content-Type为application/x-www-form-urlencoded
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // 构建表单参数
            MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
            for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                formParams.add(entry.getKey(), entry.getValue());
            }

            entity = new HttpEntity<>(formParams, headers);
        } else {
            // GET请求查询参数模式
            // 判断URL模板类型
            if (urlTemplate.contains("{value}")) {
                // 路径参数模式: /api/detail/{value}，使用第一个参数值
                String firstValue = paramMap.isEmpty() ? "" : paramMap.values().iterator().next();
                url = urlTemplate.replace("{value}", firstValue);
                entity = new HttpEntity<>(headers);
            } else if (urlTemplate.contains("?")) {
                // 查询参数模式: /api/detail?xxx，附加所有参数
                StringBuilder urlBuilder = new StringBuilder(urlTemplate);
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    urlBuilder.append("&").append(entry.getKey()).append("=").append(entry.getValue());
                }
                url = urlBuilder.toString();
                entity = new HttpEntity<>(headers);
            } else {
                // 查询参数模式（无现有参数）: /api/detail
                StringBuilder urlBuilder = new StringBuilder(urlTemplate).append("?");
                int i = 0;
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    if (i > 0) urlBuilder.append("&");
                    urlBuilder.append(entry.getKey()).append("=").append(entry.getValue());
                    i++;
                }
                url = urlBuilder.toString();
                entity = new HttpEntity<>(headers);
            }
        }

        HttpMethod httpMethod = HttpMethod.valueOf(method);
        ResponseEntity<String> response = restTemplate.exchange(url, httpMethod, entity, String.class);
        return objectMapper.readTree(response.getBody());
    }

    private List<Map<String, Object>> parseDetailDataList(JsonNode rootNode, String dataPath) {
        JsonNode dataNode = rootNode;
        if (dataPath != null && !dataPath.isEmpty()) {
            String[] paths = dataPath.split("\\.");
            for (String path : paths) {
                if (dataNode == null || dataNode.isNull()) {
                    return null;
                }
                dataNode = dataNode.get(path);
            }
        }

        if (dataNode == null || dataNode.isNull()) {
            return null;
        }

        List<Map<String, Object>> resultList = new ArrayList<>();

        // 如果返回的是数组，遍历数组中的每个对象
        if (dataNode.isArray()) {
            for (JsonNode item : dataNode) {
                if (item.isObject()) {
                    Map<String, Object> map = jsonNodeToMap(item);
                    if (map != null && !map.isEmpty()) {
                        resultList.add(map);
                    }
                }
            }
        }
        // 如果返回的是单个对象
        else if (dataNode.isObject()) {
            Map<String, Object> map = jsonNodeToMap(dataNode);
            if (map != null && !map.isEmpty()) {
                resultList.add(map);
            }
        }

        return resultList;
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        Map<String, Object> map = new HashMap<>();

        // 处理对象类型
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode value = node.get(fieldName);
                map.put(fieldName, convertJsonNodeToValue(value));
            }
        }
        // 处理数组类型 - 将数组转换为JSON字符串存储
        else if (node.isArray()) {
            map.put("_array_data", node.toString());
        }
        // 处理其他类型（基本类型）
        else {
            map.put("_value", convertJsonNodeToValue(node));
        }

        return map;
    }

    /**
     * 将JsonNode转换为Java对象
     */
    private Object convertJsonNodeToValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        } else if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            return node.numberValue();
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isArray()) {
            // 数组类型转换为JSON字符串
            return node.toString();
        } else if (node.isObject()) {
            // 嵌套对象转换为JSON字符串
            return node.toString();
        } else {
            return node.toString();
        }
    }

    private void insertDetailData(String tableName, List<Map<String, String>> columns,
                                  Map<String, Object> data, Map<String, String> fieldMapping) {
        List<String> columnNames = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // 将data的key转换为驼峰格式，用于匹配
        Map<String, Object> camelCaseData = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            camelCaseData.put(toCamelCase(entry.getKey()), entry.getValue());
        }

        for (Map<String, String> column : columns) {
            String dbColumnName = column.get("COLUMN_NAME");
            String apiFieldName = dbColumnName;

            // 如果有字段映射，使用映射关系
            if (fieldMapping != null && !fieldMapping.isEmpty()) {
                for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(dbColumnName)) {
                        apiFieldName = entry.getKey();
                        break;
                    }
                }
            }

            // 尝试多种方式获取值
            Object value = data.get(apiFieldName);
            if (value == null) {
                value = data.get(toCamelCase(apiFieldName));
            }
            if (value == null) {
                value = camelCaseData.get(toCamelCase(apiFieldName));
            }
            if (value == null) {
                for (Map.Entry<String, Object> dataEntry : data.entrySet()) {
                    if (dataEntry.getKey().equalsIgnoreCase(apiFieldName) ||
                        dataEntry.getKey().equalsIgnoreCase(toCamelCase(apiFieldName))) {
                        value = dataEntry.getValue();
                        break;
                    }
                }
            }

            if (value != null) {
                columnNames.add(dbColumnName);
                values.add(value);
            }
        }

        if (columnNames.isEmpty()) {
            throw new RuntimeException("没有可插入的数据");
        }

        dataSyncMapper.insertData(tableName, columnNames, values);
    }

    /**
     * 将下划线命名转换为驼峰命名
     */
    private String toCamelCase(String underscoreName) {
        if (underscoreName == null || underscoreName.isEmpty()) {
            return underscoreName;
        }

        StringBuilder result = new StringBuilder();
        boolean nextUpperCase = false;

        for (int i = 0; i < underscoreName.length(); i++) {
            char c = underscoreName.charAt(i);
            if (c == '_') {
                nextUpperCase = true;
            } else if (nextUpperCase) {
                result.append(Character.toUpperCase(c));
                nextUpperCase = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    /**
     * 将驼峰命名转换为下划线命名
     */
    private String toUnderscoreCase(String camelCaseName) {
        if (camelCaseName == null || camelCaseName.isEmpty()) {
            return camelCaseName;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCaseName.length(); i++) {
            char c = camelCaseName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
