package com.github.lyd.gateway.producer.configuration;

import com.github.lyd.base.client.constants.BaseConstants;
import com.github.lyd.common.autoconfigure.GatewayProperties;
import com.github.lyd.common.constants.GlobalConstants;
import com.github.lyd.common.exception.OpenAccessDeniedHandler;
import com.github.lyd.common.exception.OpenAuthenticationEntryPoint;
import com.github.lyd.common.model.ResultBody;
import com.github.lyd.common.security.OpenHelper;
import com.github.lyd.common.utils.WebUtils;
import com.github.lyd.gateway.producer.filter.GrantAccessMetadataSource;
import com.github.lyd.gateway.producer.filter.SignatureFilter;
import com.github.lyd.gateway.producer.locator.GrantAccessLocator;
import com.github.lyd.gateway.producer.service.feign.SystemAppApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.access.vote.RoleVoter;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.web.client.RestTemplate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 资源服务配置
 *
 * @author: liuyadu
 * @date: 2018/10/23 10:31
 * @description:
 */
@Slf4j
@Configuration
@EnableResourceServer
public class ResourceServerConfiguration extends ResourceServerConfigurerAdapter {
    @Autowired
    private ResourceServerProperties properties;
    @Autowired
    private GrantAccessLocator accessLocator;
    @Autowired
    private GatewayProperties gatewayProperties;
    @Autowired
    private SystemAppApi systemAppApi;
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
        resources.tokenServices(OpenHelper.buildRemoteTokenServices(properties));
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        // 增加签名过滤器
        http.addFilterAfter(new SignatureFilter(systemAppApi, gatewayProperties), AbstractPreAuthenticatedProcessingFilter.class);
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .and()
                .authorizeRequests()
                // 放行自定义Oauth2登录
                .antMatchers("/**/login/**",
                        "/**/logout/**",
                        "/**/oauth/token/**",
                        "/**/oauth/check_token/**").permitAll()
                // 只有拥有actuator权限可执行远程端点
                .requestMatchers(EndpointRequest.toAnyEndpoint()).hasAnyAuthority(GlobalConstants.AUTHORITY_ACTUATOR)
                // 自定义FilterSecurityInterceptor
                .withObjectPostProcessor(new ObjectPostProcessor<FilterSecurityInterceptor>() {
                    @Override
                    public <O extends FilterSecurityInterceptor> O postProcess(
                            O fsi) {
                        //追加到默认投票器链,支持默认表达式方式
                        if (fsi.getAccessDecisionManager() instanceof AffirmativeBased) {
                            AffirmativeBased affirmativeBased = (AffirmativeBased) fsi.getAccessDecisionManager();
                            affirmativeBased.getDecisionVoters().addAll(decisionVoters());
                        }
                        // 设置权限配置并引用默认的权限配置
                        fsi.setSecurityMetadataSource(grantAccessMetadataSource(fsi.getSecurityMetadataSource()));
                        return fsi;
                    }
                })
                .anyRequest().authenticated()
                // SSO退出
                .and().logout().logoutSuccessHandler(new SsoLogoutSuccessHandler(gatewayProperties.getServerAddr() + "/auth/logout", restTemplate))
                .and()
                //认证鉴权错误处理,为了统一异常处理。每个资源服务器都应该加上。
                .exceptionHandling()
                .accessDeniedHandler(new OpenAccessDeniedHandler())
                .authenticationEntryPoint(new OpenAuthenticationEntryPoint())
                .and()
                .csrf().disable();
    }

    static class SsoLogoutSuccessHandler implements LogoutSuccessHandler {
        private String defaultTargetUrl;
        private RestTemplate restTemplate;

        public SsoLogoutSuccessHandler(String defaultTargetUrl, RestTemplate restTemplate) {
            this.defaultTargetUrl = defaultTargetUrl;
            this.restTemplate = restTemplate;
        }

        @Override
        public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
            try {
                restTemplate.getForEntity(defaultTargetUrl, String.class);
            } catch (Exception e) {
                log.error("sso logout error:", e);
            }
            WebUtils.writeJson(response, ResultBody.success("退出成功", null));
        }
    }

    /**
     * 自定义投票器
     */
    private List<AccessDecisionVoter<? extends Object>> decisionVoters() {
        List<AccessDecisionVoter<? extends Object>> decisionVoters = new ArrayList();
        //默认角色投票器,默认前缀为ROLE_
        RoleVoter roleVoter = new RoleVoter();
        //用户权限投票器,修改前缀为USER_
        RoleVoter userVoter = new RoleVoter();
        userVoter.setRolePrefix(BaseConstants.AUTHORITY_PREFIX_USER);
        //应用权限投票器,修改前缀为APP_
        RoleVoter appVoter = new RoleVoter();
        appVoter.setRolePrefix(BaseConstants.AUTHORITY_PREFIX_APP);
        decisionVoters.add(roleVoter);
        decisionVoters.add(userVoter);
        decisionVoters.add(appVoter);
        return decisionVoters;
    }

    /**
     * 自定义权限配置
     *
     * @param filterInvocationSecurityMetadataSource
     * @return
     */
    public GrantAccessMetadataSource grantAccessMetadataSource(FilterInvocationSecurityMetadataSource filterInvocationSecurityMetadataSource) {
        GrantAccessMetadataSource securityMetadataSource = new GrantAccessMetadataSource(accessLocator, gatewayProperties, filterInvocationSecurityMetadataSource);
        return securityMetadataSource;
    }
}

