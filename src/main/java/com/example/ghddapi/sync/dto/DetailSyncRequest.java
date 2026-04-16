package com.example.ghddapi.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "详情同步请求参数")
public class DetailSyncRequest {

    @Schema(description = "源数据库表名（已同步的列表数据表）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceTableName;

    @Schema(description = "目标数据库表名（详情数据保存表）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetTableName;

    @Schema(description = "源表字段名（用于查询详情的字段，如id），支持多个字段用逗号分隔")
    private String sourceFieldName;

    @Schema(description = "API查询参数字段名（如果与sourceFieldName不同，如indexId），支持多个字段用逗号分隔，与sourceFieldName一一对应")
    private String apiQueryFieldName;

    @Schema(description = "GET请求查询参数映射，key为源表字段名，value为API查询参数字段名")
    private Map<String, String> queryParamMapping;

    @Schema(description = "POST/PUT表单参数映射，key为源表字段名，value为表单字段名")
    private Map<String, String> formParamMapping;

    @Schema(description = "自定义固定参数，会随每个请求一起发送")
    private Map<String, String> customParams;

    @Schema(description = "详情接口URL模板，使用{value}作为占位符，如: https://api.example.com/detail/{value}", requiredMode = Schema.RequiredMode.REQUIRED)
    private String detailApiUrlTemplate;

    @Schema(description = "请求头信息")
    private Map<String, String> headers;

    @Schema(description = "请求方法: GET/POST/PUT/DELETE，默认为GET")
    private String method;

    @Schema(description = "请求体类型: FORM/JSON，POST/PUT请求时使用，默认为FORM")
    private String bodyType;

    @Schema(description = "POST/PUT请求时的JSON请求体模板，使用{value}作为占位符，如: {\"indexId\": \"{value}\", \"type\": \"1\"}")
    private String bodyTemplate;

    @Schema(description = "响应数据路径，如 data 或 data.detail，留空则使用根节点")
    private String dataPath;

    @Schema(description = "数据字段映射(key:外部接口字段名, value:数据库字段名)")
    private Map<String, String> fieldMapping;

    @Schema(description = "是否批量查询，批量时sourceFieldName对应多个值用逗号分隔")
    private Boolean batchQuery;

    @Schema(description = "每次查询的间隔毫秒数，防止请求过快，默认100")
    private Integer intervalMs;
}
