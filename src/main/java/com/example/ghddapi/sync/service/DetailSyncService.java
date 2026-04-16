package com.example.ghddapi.sync.service;

import com.example.ghddapi.sync.dto.DetailSyncRequest;
import com.example.ghddapi.util.R;

public interface DetailSyncService {
    R<String> syncDetailData(DetailSyncRequest request);
}
