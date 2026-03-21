/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.dto.result.common;

import com.alibaba.himarket.dto.converter.OutputConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements OutputConverter<PageResult<T>, Page<T>> {

    private List<T> content;

    private int number;

    private int size;

    private long totalElements;

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public <S> PageResult<T> mapFrom(PageResult<S> source, Function<S, T> mapper) {
        setContent(source.getContent().stream().map(mapper).collect(Collectors.toList()));
        setSize(source.getSize());
        setNumber(source.getNumber());
        setTotalElements(source.getTotalElements());
        return this;
    }

    public <S> PageResult<T> convertFrom(Page<S> source, Function<S, T> mapper) {
        setContent(source.getContent().stream().map(mapper).collect(Collectors.toList()));
        setSize(source.getSize());
        // 由Pageable转换时修正
        setNumber(source.getNumber() + 1);
        setTotalElements(source.getTotalElements());
        return this;
    }

    public static <T> PageResult<T> empty(int pageNumber, int pageSize) {
        return PageResult.<T>builder()
                .content(new ArrayList<>())
                .number(pageNumber)
                .size(pageSize)
                .totalElements(0)
                .build();
    }

    public static <T> PageResult<T> of(List<T> content, int pageNumber, int pageSize, long total) {
        return PageResult.<T>builder()
                .content(content)
                .number(pageNumber)
                .size(pageSize)
                .totalElements(total)
                .build();
    }
}
