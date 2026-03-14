import {Card, Form, Input, Switch, Divider, message} from 'antd'
import {useMemo} from 'react'
import {Portal, ThirdPartyAuthConfig, AuthenticationType, CasConfig, OidcConfig, OAuth2Config, LdapConfig} from '@/types'
import {portalApi} from '@/lib/api'
import {ThirdPartyAuthManager} from './ThirdPartyAuthManager'

interface PortalSecurityProps {
    portal: Portal
    onRefresh?: () => void
}

export function PortalSecurity({portal, onRefresh}: PortalSecurityProps) {
    const [form] = Form.useForm()

    const handleSave = async () => {
        try {
            const values = await form.validateFields()
            
            await portalApi.updatePortal(portal.portalId, {
                name: portal.name,
                description: portal.description,
                portalSettingConfig: {
                    ...portal.portalSettingConfig,
                    builtinAuthEnabled: values.builtinAuthEnabled,
                    autoApproveDevelopers: values.autoApproveDevelopers,
                    autoApproveSubscriptions: values.autoApproveSubscriptions,
                    frontendRedirectUrl: values.frontendRedirectUrl?.trim() || undefined,
                },
                portalDomainConfig: portal.portalDomainConfig,
                portalUiConfig: portal.portalUiConfig,
            })

            message.success('安全设置保存成功')
            onRefresh?.()
        } catch (error) {
            message.error('保存安全设置失败')
        }
    }

    const handleSettingUpdate = () => {
        // 立即更新配置
        handleSave()
    }

    // 第三方认证配置保存函数
    const handleSaveThirdPartyAuth = async (configs: ThirdPartyAuthConfig[]) => {
        try {
            const currentValues = form.getFieldsValue([
                'builtinAuthEnabled',
                'autoApproveDevelopers',
                'autoApproveSubscriptions',
                'frontendRedirectUrl'
            ])
            const frontendRedirectUrl = (currentValues.frontendRedirectUrl as string | undefined)?.trim() || undefined

            // 分离OIDC和OAuth2配置，去掉type字段
            const oidcConfigs = configs
                .filter(config => config.type === AuthenticationType.OIDC)
                .map(config => {
                    const { type, ...oidcConfig } = config as (OidcConfig & { type: AuthenticationType.OIDC })
                    return oidcConfig
                })

            const casConfigs = configs
                .filter(config => config.type === AuthenticationType.CAS)
                .map(config => {
                    const { type, ...casConfig } = config as (CasConfig & { type: AuthenticationType.CAS })
                    return casConfig
                })

            const oauth2Configs = configs
                .filter(config => config.type === AuthenticationType.OAUTH2)
                .map(config => {
                    const { type, ...oauth2Config } = config as (OAuth2Config & { type: AuthenticationType.OAUTH2 })
                    return oauth2Config
                })

            const ldapConfigs = configs
                .filter(config => config.type === AuthenticationType.LDAP)
                .map(config => {
                    const { type, ...ldapConfig } = config as (LdapConfig & { type: AuthenticationType.LDAP })
                    return ldapConfig
                })
            
            const updateData = {
                ...portal,
                portalSettingConfig: {
                    ...portal.portalSettingConfig,
                    builtinAuthEnabled: currentValues.builtinAuthEnabled,
                    autoApproveDevelopers: currentValues.autoApproveDevelopers,
                    autoApproveSubscriptions: currentValues.autoApproveSubscriptions,
                    frontendRedirectUrl: frontendRedirectUrl,
                    // 直接保存分离的配置数组
                    oidcConfigs: oidcConfigs,
                    casConfigs: casConfigs,
                    ldapConfigs: ldapConfigs,
                    oauth2Configs: oauth2Configs
                }
            }
            
            await portalApi.updatePortal(portal.portalId, updateData)
            
            onRefresh?.()
        } catch (error) {
            throw error
        }
    }

    // 合并OIDC和OAuth2配置用于统一显示
    const thirdPartyAuthConfigs = useMemo((): ThirdPartyAuthConfig[] => {
        const configs: ThirdPartyAuthConfig[] = []
        
        // 添加OIDC配置
        if (portal.portalSettingConfig?.oidcConfigs) {
            portal.portalSettingConfig.oidcConfigs.forEach(oidcConfig => {
                configs.push({
                    ...oidcConfig,
                    type: AuthenticationType.OIDC
                })
            })
        }

        if (portal.portalSettingConfig?.casConfigs) {
            portal.portalSettingConfig.casConfigs.forEach(casConfig => {
                configs.push({
                    ...casConfig,
                    type: AuthenticationType.CAS
                })
            })
        }

        // 添加OAuth2配置
        if (portal.portalSettingConfig?.oauth2Configs) {
            portal.portalSettingConfig.oauth2Configs.forEach(oauth2Config => {
                configs.push({
                    ...oauth2Config,
                    type: AuthenticationType.OAUTH2
                })
            })
        }

        if (portal.portalSettingConfig?.ldapConfigs) {
            portal.portalSettingConfig.ldapConfigs.forEach(ldapConfig => {
                configs.push({
                    ...ldapConfig,
                    type: AuthenticationType.LDAP
                })
            })
        }
        
        return configs
    }, [
        portal.portalSettingConfig?.oidcConfigs,
        portal.portalSettingConfig?.casConfigs,
        portal.portalSettingConfig?.oauth2Configs,
        portal.portalSettingConfig?.ldapConfigs
    ])


    return (
        <div className="p-6 space-y-6">
            <div>
                <h1 className="text-2xl font-bold mb-2">Portal安全配置</h1>
                <p className="text-gray-600">配置Portal的认证与审批方式</p>
            </div>

            <Form
                form={form}
                layout="vertical"
                initialValues={{
                    builtinAuthEnabled: portal.portalSettingConfig?.builtinAuthEnabled,
                    autoApproveDevelopers: portal.portalSettingConfig?.autoApproveDevelopers,
                    autoApproveSubscriptions: portal.portalSettingConfig?.autoApproveSubscriptions,
                    frontendRedirectUrl: portal.portalSettingConfig?.frontendRedirectUrl,
                }}
            >
                <Card>
                    <div className="space-y-6">
                        {/* 基本安全配置标题 */}
                        <h3 className="text-lg font-medium">基本安全配置</h3>
                        
                        {/* 基本安全设置内容 */}
                        <div className="grid grid-cols-2 gap-6">
                            <Form.Item
                                name="builtinAuthEnabled"
                                label="账号密码登录"
                                valuePropName="checked"
                            >
                                <Switch
                                    onChange={() => handleSettingUpdate()}
                                />
                            </Form.Item>
                            <Form.Item
                                name="autoApproveDevelopers"
                                label="开发者自动审批"
                                valuePropName="checked"
                            >
                                <Switch
                                    onChange={() => handleSettingUpdate()}
                                />
                            </Form.Item>
                            <Form.Item
                                name="autoApproveSubscriptions"
                                label="订阅自动审批"
                                valuePropName="checked"
                            >
                                <Switch
                                    onChange={() => handleSettingUpdate()}
                                />
                            </Form.Item>
                        </div>

                        <Form.Item
                            name="frontendRedirectUrl"
                            label="前端回调基址"
                            extra="OIDC 和 CAS 回调将基于该地址生成，需填写 Portal 对外访问的前端域名基址，例如 https://portal.example.com"
                            rules={[
                                {
                                    type: 'url',
                                    message: '请输入有效的 URL'
                                }
                            ]}
                        >
                            <Input placeholder="https://portal.example.com" onBlur={() => handleSettingUpdate()} />
                        </Form.Item>

                        <Divider />
                        
                        {/* 第三方认证管理器 - 内部已有标题，不需要重复添加 */}
                        <ThirdPartyAuthManager
                            configs={thirdPartyAuthConfigs}
                            onSave={handleSaveThirdPartyAuth}
                        />
                    </div>
                </Card>
            </Form>
        </div>
    )
}
