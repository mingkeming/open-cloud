package com.opencloud.base.provider.service.impl;

import com.opencloud.base.provider.service.BaseActionService;
import com.opencloud.common.model.PageParams;
import com.opencloud.common.test.BaseTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author: liuyadu
 * @date: 2019/3/12 11:15
 * @description:
 */
public class BaseResourceOperationServiceImplTest extends BaseTest {
    @Autowired
    private BaseActionService baseResourceOperationService;

    @Test
    public void findListPage() throws Exception {
        baseResourceOperationService.findListPage(new PageParams());
    }

    @Test
    public void findListByMenuId() throws Exception {
    }

    @Test
    public void getOperation() throws Exception {
    }

    @Test
    public void isExist() throws Exception {
    }

    @Test
    public void addOperation() throws Exception {
    }

    @Test
    public void updateOperation() throws Exception {
    }

    @Test
    public void removeOperation() throws Exception {
    }

}
