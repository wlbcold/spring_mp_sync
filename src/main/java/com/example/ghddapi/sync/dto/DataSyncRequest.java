package com.example.ghddapi.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "数据同步请求参数")
public class DataSyncRequest {

    @Schema(description = "数据库表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tableName;

    @Schema(description = "外部接口URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String apiUrl;

    @Schema(description = "请求头信息")
    private Map<String, String> headers;

    @Schema(description = "请求参数")
    private Map<String, Object> params;

    @Schema(description = "请求方法: GET/POST/PUT/DELETE，默认为GET")
    private String method;

    @Schema(description = "数据字段映射(key:外部接口字段名, value:数据库字段名)")
    private Map<String, String> fieldMapping;

    @Schema(description = "响应数据路径，如 data.list 或 data.records")
    private String dataPath;
}
