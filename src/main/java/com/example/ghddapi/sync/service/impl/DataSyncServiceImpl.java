package com.example.ghddapi.sync.service.impl;

import com.example.ghddapi.sync.dto.DataSyncRequest;
import com.example.ghddapi.sync.mapper.DataSyncMapper;
import com.example.ghddapi.sync.service.DataSyncService;
import com.example.ghddapi.util.R;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncServiceImpl implements DataSyncService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DataSyncMapper dataSyncMapper;

    @Override
    public R<String> syncData(DataSyncRequest request) {
        try {
            // 1. 调用外部接口获取数据
            JsonNode responseData = callExternalApi(request);

            // 2. 解析响应数据
            List<Map<String, Object>> dataList = parseResponseData(responseData, request.getDataPath());

            if (dataList.isEmpty()) {
                return R.ok("未获取到数据");
            }

            // 3. 获取表结构信息
            List<Map<String, String>> tableColumns = dataSyncMapper.getTableColumns(request.getTableName());
            if (tableColumns.isEmpty()) {
                return R.fail("表不存在或表中没有字段: " + request.getTableName());
            }

            // 4. 构建并执行插入SQL
            int successCount = 0;
            int failCount = 0;

            for (Map<String, Object> dataItem : dataList) {
                try {
                    insertData(request.getTableName(), tableColumns, dataItem, request.getFieldMapping());
                    successCount++;
                } catch (Exception e) {
                    log.error("插入数据失败: {}", e.getMessage());
                    failCount++;
                }
            }

            String resultMsg = String.format("同步完成，成功: %d 条，失败: %d 条", successCount, failCount);
            log.info(resultMsg);
            return R.ok(resultMsg);

        } catch (Exception e) {
            log.error("数据同步失败", e);
            return R.fail("数据同步失败: " + e.getMessage());
        }
    }

    private JsonNode callExternalApi(DataSyncRequest request) throws Exception {
        String method = request.getMethod() != null ? request.getMethod().toUpperCase() : "GET";
        HttpHeaders headers = new HttpHeaders();

        // 设置请求头
        if (request.getHeaders() != null) {
            request.getHeaders().forEach(headers::set);
        }

        HttpEntity<?> entity = new HttpEntity<>(headers);

        String url = request.getApiUrl();

        if ("GET".equals(method)) {
            // GET请求：参数拼接到URL
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
            if (request.getParams() != null) {
                request.getParams().forEach((key, value) -> {
                    if (value != null) {
                        builder.queryParam(key, value.toString());
                    }
                });
            }
            url = builder.toUriString();
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } else {
            // POST/PUT请求：参数放body
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> postEntity = new HttpEntity<>(
                    request.getParams() != null ? request.getParams() : new HashMap<>(), headers);
            HttpMethod httpMethod = "PUT".equals(method) ? HttpMethod.PUT : HttpMethod.POST;
            ResponseEntity<String> response = restTemplate.exchange(
                    url, httpMethod, postEntity, String.class);
            return objectMapper.readTree(response.getBody());
        }
    }

    private List<Map<String, Object>> parseResponseData(JsonNode rootNode, String dataPath) {
        List<Map<String, Object>> result = new ArrayList<>();

        JsonNode dataNode = rootNode;
        if (dataPath != null && !dataPath.isEmpty()) {
            String[] paths = dataPath.split("\\.");
            for (String path : paths) {
                if (dataNode.isArray()) {
                    break;
                }
                dataNode = dataNode.get(path);
                if (dataNode == null) {
                    return result;
                }
            }
        }

        if (dataNode.isArray()) {
            for (JsonNode item : dataNode) {
                result.add(jsonNodeToMap(item));
            }
        } else {
            result.add(jsonNodeToMap(dataNode));
        }

        return result;
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> fieldNames = node.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode value = node.get(fieldName);
            if (value.isTextual()) {
                map.put(fieldName, value.asText());
            } else if (value.isNumber()) {
                map.put(fieldName, value.numberValue());
            } else if (value.isBoolean()) {
                map.put(fieldName, value.asBoolean());
            } else if (value.isNull()) {
                map.put(fieldName, null);
            } else {
                map.put(fieldName, value.toString());
            }
        }
        return map;
    }

    private void insertData(String tableName, List<Map<String, String>> columns,
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
                // 查找哪个API字段映射到这个数据库字段
                for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(dbColumnName)) {
                        apiFieldName = entry.getKey();
                        break;
                    }
                }
            }

            // 尝试多种方式获取值
            // 1. 原始字段名匹配
            Object value = data.get(apiFieldName);
            // 2. 驼峰字段名匹配（数据库下划线转驼峰）
            if (value == null) {
                value = data.get(toCamelCase(apiFieldName));
            }
            // 3. 从驼峰转换后的数据中查找
            if (value == null) {
                value = camelCaseData.get(toCamelCase(apiFieldName));
            }
            // 4. 忽略大小写匹配
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
     * 例如: user_name -> userName, create_time -> createTime
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
}
