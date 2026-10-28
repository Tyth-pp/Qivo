package com.qivo.controller.fore;

import com.qivo.service.IndexService;
import com.qivo.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "前台-首页")
@RestController
@RequestMapping("/api/fore/index")
public class IndexController {

    private final IndexService indexService;

    public IndexController(IndexService indexService) {
        this.indexService = indexService;
    }

    @Operation(summary = "首页数据（轮播图、热门商品、新品、分类导航）")
    @GetMapping("/data")
    public Result<Map<String, Object>> indexData() {
        return Result.success(indexService.indexData());
    }
}
