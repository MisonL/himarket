import { useState, useEffect, useRef } from "react";
import { Button, Avatar, Dropdown, Skeleton, message } from "antd";
import { useNavigate } from "react-router-dom";
import { useTranslation } from 'react-i18next';
import { LogOut, UserRoundCheck } from "./icon";
import APIs from "../lib/apis";
import { clearLastAuthState, getLastAuthState } from "../lib/authStorage";
import "./UserInfo.css";

interface UserInfo {
  displayName: string;
  email?: string;
  avatar?: string;
}

// 全局缓存用户信息，避免重复请求
let globalUserInfo: UserInfo | null = null;
let globalLoading = false;

export function UserInfo() {
  const { t } = useTranslation('userInfo');
  const [userInfo, setUserInfo] = useState<UserInfo | null>(globalUserInfo);
  const [loading, setLoading] = useState(globalUserInfo ? false : true);
  const navigate = useNavigate();
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;

    // 如果已有缓存数据，直接使用
    if (globalUserInfo) {
      setUserInfo(globalUserInfo);
      setLoading(false);
      return;
    }

    // 如果正在加载中，等待加载完成 - 优化轮询逻辑
    if (globalLoading) {
      const checkLoading = () => {
        if (!globalLoading && mounted.current) {
          setUserInfo(globalUserInfo);
          setLoading(false);
        } else if (globalLoading && mounted.current) {
          // 使用requestAnimationFrame替代setTimeout提升性能
          requestAnimationFrame(checkLoading);
        }
      };
      checkLoading();
      return;
    }

    // 开始加载用户信息
    globalLoading = true;
    setLoading(true);

    APIs.getDeveloperInfo()
      .then((response) => {
        const data = response.data;
        if (data && data.username) {
          const userData = {
            displayName: data.username || data.email || t('unnamedUser'),
            email: data.email,
            avatar: data.avatarUrl || undefined,
          };
          globalUserInfo = userData;
          if (mounted.current) {
            setUserInfo(userData);
          }
        }
      })
      .finally(() => {
        globalLoading = false;
        if (mounted.current) {
          setLoading(false);
        }
      });

    return () => {
      mounted.current = false;
    };
  }, []);

  const handleLogout = async () => {
    const lastAuth = getLastAuthState();
    const shouldCasSlo =
      lastAuth?.type === "CAS" && !!lastAuth.provider && lastAuth.sloEnabled === true;
    let serverLogoutSucceeded = false;

    const buildAuthorizeUrl = (path: string, provider: string) => {
      const apiPrefix = import.meta.env.VITE_API_BASE_URL || "/api/v1";
      const apiBaseUrl = new URL(apiPrefix, window.location.origin);
      if (!apiBaseUrl.pathname.endsWith("/")) {
        apiBaseUrl.pathname += "/";
      }

      const url = new URL(path.replace(/^\//, ""), apiBaseUrl);
      url.searchParams.set("provider", provider);
      return url.toString();
    };

    try {
      await APIs.developerLogout();
      serverLogoutSucceeded = true;
    } catch (error) {
      console.error('退出登录接口调用失败:', error);
      message.error("服务端登出失败，已清理本地登录状态");
    } finally {
      localStorage.removeItem('access_token');
      clearLastAuthState();
      // 清除全局用户信息
      globalUserInfo = null;
      globalLoading = false;
      setUserInfo(null);
    }

    if (shouldCasSlo && lastAuth?.provider) {
      window.location.href = buildAuthorizeUrl("/developers/cas/logout", lastAuth.provider);
      return;
    }

    if (serverLogoutSucceeded) {
      message.success(t('logoutSuccess'), 1);
    }
    navigate("/login");
  };

  const menuItems = [
    {
      key: 'user-info',
      label: (
        <div>
          <div className="font-semibold text-gray-900 text-base">{userInfo?.displayName}</div>
          {userInfo?.email && (
            <div className="text-xs text-gray-500 mt-0.5">{userInfo.email}</div>
          )}
        </div>
      ),
      disabled: true,
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'my-applications',
      icon: <UserRoundCheck className="mr-1" />,
      label: t('consumerManagement'),
      onClick: () => navigate('/consumers'),
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'logout',
      icon: <LogOut className="mr-1" />,
      label: t('logout'),
      onClick: handleLogout,
    },
  ];

  // 获取用户名首字母
  const getInitials = (name: string) => {
    if (!name) return 'U';
    // 如果是中文名，取第一个字
    if (/[\u4e00-\u9fa5]/.test(name)) {
      return name.charAt(0);
    }
    // 如果是英文名，取第一个字母
    return name.charAt(0).toUpperCase();
  };

  if (loading) {
    return (
      <div className="flex items-center space-x-2">
        <Skeleton.Avatar size={32} active />
      </div>
    );
  }

  if (userInfo) {
    return (
      <Dropdown
        menu={{ items: menuItems }}
        placement="bottomRight"
        trigger={['hover']}
        classNames={{
          root: "user-dropdown"
        }}
      >
        <div className="flex items-center space-x-2 cursor-pointer hover:opacity-80 transition-opacity px-2 py-1 rounded-full">
          {userInfo.avatar ? (
            <Avatar src={userInfo.avatar} size="default" />
          ) : (
            <Avatar size="default" className="bg-colorPrimarySecondary text-mainTitle font-medium">
              {getInitials(userInfo.displayName)}
            </Avatar>
          )}
        </div>
      </Dropdown>
    );
  }

  return (
    <Button
      onClick={() => {
        navigate(`/login`);
      }}
      type="text"
      className="rounded-full bg-colorPrimary text-white border-none hover:opacity-90 hover:bg-colorPrimary"
    >
      {t('login')}
    </Button>
  );
}
