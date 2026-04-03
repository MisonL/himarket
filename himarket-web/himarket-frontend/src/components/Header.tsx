import { Link, useLocation } from "react-router-dom";
import { useState, useEffect } from "react";
import { UserInfo } from "./UserInfo";
import { HiMarket, Logo } from "./icon";
import { usePortalConfig } from "../context/PortalConfigContext";

const AUTH_ROUTES = new Set([
  "/login",
  "/register",
  "/callback",
  "/cas/callback",
  "/oidc/callback",
  "/oauth2/callback",
]);

export function Header() {
  const location = useLocation();
  const [isScrolled, setIsScrolled] = useState(false);
  const { visibleTabs } = usePortalConfig();
  const isAuthRoute = AUTH_ROUTES.has(location.pathname);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 10);
    };

    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  const isActiveTab = (path: string) => {
    return (
      location.pathname === path || location.pathname.startsWith(path + "/")
    );
  };

  return (
    <nav
      className={`
        sticky top-0 z-50 h-auto transition-[background-color,box-shadow,backdrop-filter] duration-1000 ease-in-out
        ${
          isScrolled
            ? "bg-gray-100/90 shadow-sm"
            : "backdrop-blur-md bg-transparent"
        }
      `}
    >
      <div className="w-full mx-auto">
        <div className="flex justify-between items-center px-4 py-2 sm:px-8">
          <div className="flex min-w-0 items-center">
            <Link
              to="/"
              className="flex items-center space-x-2 transition-opacity duration-300 hover:opacity-80"
            >
              <div className="w-8 h-8 rounded-full flex items-center justify-center">
                {/* LOGO区域 */}
                <Logo className="w-6 h-6" />
              </div>
              <HiMarket />
            </Link>
            {!isAuthRoute && (
              <>
                <div className="mx-3 hidden h-6 w-[1px] bg-gray-200 sm:mx-5 sm:block"></div>
                <div className="ml-3 flex min-w-0 items-center overflow-x-auto scrollbar-hide sm:ml-0">
                  <div className="flex items-center gap-1.5 pr-1">
                    {visibleTabs.map(tab => (
                      <Link key={tab.path} to={tab.path} className="shrink-0">
                        <div
                          className={`
                            whitespace-nowrap rounded-full px-3 py-1.5 transition-[background-color,color,box-shadow,transform] duration-300 ease-in-out sm:px-4
                            ${
                              isActiveTab(tab.path)
                                ? "bg-white text-gray-900 font-medium shadow-sm scale-[1.02]"
                                : "text-gray-600 hover:bg-white/60 hover:text-gray-900 hover:shadow-sm hover:scale-[1.02]"
                            }
                          `}
                        >
                          {tab.label}
                        </div>
                      </Link>
                    ))}
                  </div>
                </div>
              </>
            )}
          </div>
          <div className="flex items-center space-x-4">
            {isAuthRoute ? (
              <Link
                to="/"
                className="rounded-full px-3 py-1.5 text-sm text-gray-600 transition-colors hover:bg-white/60 hover:text-gray-900"
              >
                返回首页
              </Link>
            ) : (
              <UserInfo />
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
