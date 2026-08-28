package com.jhds.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jhds.entity.PageAlertContent;
import com.jhds.mapper.PageAlertContentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PageAlertContentService {

    @Autowired
    private PageAlertContentMapper pageAlertContentMapper;

    public PageAlertContent getByKey(String alertKey) {
        if (alertKey == null || alertKey.trim().isEmpty()) {
            return null;
        }
        return pageAlertContentMapper.selectOne(new LambdaQueryWrapper<PageAlertContent>()
                .eq(PageAlertContent::getAlertKey, alertKey.trim()));
    }

    public List<PageAlertContent> listEnabled() {
        return pageAlertContentMapper.selectList(new LambdaQueryWrapper<PageAlertContent>()
                .eq(PageAlertContent::getEnabled, 1)
                .orderByAsc(PageAlertContent::getSortOrder)
                .orderByAsc(PageAlertContent::getId));
    }
}
