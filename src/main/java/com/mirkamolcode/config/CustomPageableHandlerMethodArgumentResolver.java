package com.mirkamolcode.config;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CustomPageableHandlerMethodArgumentResolver extends PageableHandlerMethodArgumentResolver {

    public CustomPageableHandlerMethodArgumentResolver(CustomSortHandlerMethodArgumentResolver sortResolver) {
        super(sortResolver);
    }

    @Override
    public Pageable resolveArgument(MethodParameter methodParameter,
                                    ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest,
                                    WebDataBinderFactory binderFactory) {
        String unpagedParam = webRequest.getParameter("unpaged");
        String sizeParam = webRequest.getParameter("size");
        boolean isUnpaged = "true".equalsIgnoreCase(unpagedParam) || "-1".equals(sizeParam);

        String[] sortParams = webRequest.getParameterValues("sort");
        Sort sort = Sort.unsorted();
        if (sortParams != null && sortParams.length > 0) {
            sort = CustomSortHandlerMethodArgumentResolver.parseSort(sortParams);
        }

        if (isUnpaged) {
            return sort.isSorted() ? Pageable.unpaged(sort) : Pageable.unpaged();
        }

        Pageable pageable = super.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory);
        if (sort.isSorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        }
        return pageable;
    }
}
