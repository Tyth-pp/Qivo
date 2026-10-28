package com.qivo.service;

import java.util.Map;

public interface StatisticsService {
    Map<String, Object> dashboard();

    Map<String, Object> workbench();

    Map<String, Object> sales(String type);

    Map<String, Object> userStats();

    Map<String, Object> revenue(String type);

    Map<String, Object> orderStats();

    Map<String, Object> productTop10();
}
