package com.example.ghddapi.sync.controller;

import com.example.ghddapi.sync.dto.DetailSyncRequest;
import com.example.ghddapi.sync.service.DetailSyncService;
import com.example.ghddapi.util.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@Tag(name = "数据同步", description = "数据同步接口")
public class DetailSyncController {

    private final DetailSyncService detailSyncService;

    @PostMapping("/detail")
    @Operation(summary = "同步详情数据", description = "从源表读取字段值，调用详情接口获取数据并保存到目标表")
    public R<String> syncDetailData(@RequestBody DetailSyncRequest request) {
        return detailSyncService.syncDetailData(request);
    }
}
