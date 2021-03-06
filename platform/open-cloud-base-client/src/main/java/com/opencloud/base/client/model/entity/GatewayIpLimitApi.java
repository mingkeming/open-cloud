package com.opencloud.base.client.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

/**
 * @author liuyadu
 */
@TableName("gateway_ip_limit_api")
public class GatewayIpLimitApi implements Serializable {

    /**
     * 策略ID
     */
    private Long policyId;

    /**
     * 接口资源ID
     */
    private Long apiId;


    private static final long serialVersionUID = 1L;

    /**
     * 获取策略ID
     *
     * @return policy_id - 策略ID
     */
    public Long getPolicyId() {
        return policyId;
    }

    /**
     * 设置策略ID
     *
     * @param policyId 策略ID
     */
    public void setPolicyId(Long policyId) {
        this.policyId = policyId;
    }

    /**
     * 获取接口资源ID
     *
     * @return api_id - 接口资源ID
     */
    public Long getApiId() {
        return apiId;
    }

    /**
     * 设置接口资源ID
     *
     * @param apiId 接口资源ID
     */
    public void setApiId(Long apiId) {
        this.apiId = apiId;
    }
}
