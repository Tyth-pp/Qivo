package com.qivo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页统一返回封装
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> implements Serializable {

    private List<T> records;
    private long total;
    private long pageNum;
    private long pageSize;

    public static <T> PageVO<T> of(List<T> records, long total, int pageNum, int pageSize) {
        return new PageVO<>(records, total, pageNum, pageSize);
    }
}
