package com.example.ghddapi.sync.controller;

import com.example.ghddapi.sync.dto.DataSyncRequest;
import com.example.ghddapi.sync.service.DataSyncService;
import com.example.ghddapi.util.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@Tag(name = "数据同步", description = "数据同步接口")
public class DataSyncController {

    private final DataSyncService dataSyncService;

    @PostMapping("/data")
    @Operation(summary = "同步数据", description = "调用外部接口获取数据并保存到指定数据库表")
    public R<String> syncData(@RequestBody DataSyncRequest request) {
        return dataSyncService.syncData(request);
    }
}
