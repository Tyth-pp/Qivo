package com.qivo.vo;

import com.qivo.entity.Permission;
import java.util.List;
import java.util.Map;

public record RolePreviewVO(Long version, long memberCount,
        List<Permission> added, List<Permission> removed,
        Map<String, Boolean> capabilities, List<String> warnings) {}
