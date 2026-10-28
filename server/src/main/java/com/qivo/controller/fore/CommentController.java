package com.qivo.controller.fore;

import com.qivo.constant.SecurityConstant;
import com.qivo.entity.ProductComment;
import com.qivo.result.Result;
import com.qivo.service.CommentService;
import com.qivo.vo.PageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@Tag(name = "前台-评价")
@RestController
@RequestMapping("/api/fore/comment")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "发表评价")
    @PostMapping("/add")
    public Result<Void> add(HttpServletRequest request, @RequestBody ProductComment comment) {
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        comment.setUserId(userId);
        commentService.add(comment);
        return Result.success();
    }

    @Operation(summary = "追加评价")
    @PostMapping("/append")
    public Result<Void> append(
            HttpServletRequest request, @RequestBody Map<String, Object> params) {
        Long userId = (Long) request.getAttribute(SecurityConstant.REQUEST_USER_ID);
        Long commentId = Long.valueOf(params.get("commentId").toString());
        String appendContent = (String) params.get("appendContent");
        commentService.append(userId, commentId, appendContent);
        return Result.success();
    }

    @Operation(summary = "商品评价列表")
    @GetMapping("/list/{productId}")
    public Result<PageVO<ProductComment>> list(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(commentService.pageByProductId(productId, page, pageSize));
    }
}
