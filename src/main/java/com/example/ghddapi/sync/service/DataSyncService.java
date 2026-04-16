package com.example.ghddapi.sync.service;

import com.example.ghddapi.sync.dto.DataSyncRequest;
import com.example.ghddapi.util.R;

public interface DataSyncService {
    R<String> syncData(DataSyncRequest request);
}
