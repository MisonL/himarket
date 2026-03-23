import axios from "axios";
import type {
  AxiosInstance,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from "axios";
import { message } from "antd";
import qs from "qs";

export interface RespI<T> {
  code: string;
  message?: string;
  data: T;
}

const PUBLIC_PATHS = [
  "/models",
  "/mcp",
  "/agents",
  "/apis",
  "/skills",
  "/chat",
  "/coding",
  "/quest",
];

function isPublicPage(): boolean {
  const pathname = window.location.pathname;
  if (pathname === "/") return true;
  return PUBLIC_PATHS.some(
    path => pathname === path || pathname.startsWith(`${path}/`)
  );
}

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true,
  paramsSerializer: params => {
    return qs.stringify(params, {
      arrayFormat: "repeat",
      skipNulls: true,
      encode: true,
    });
  },
});

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const accessToken = localStorage.getItem("access_token");

    if (config.headers && accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

request.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data;
  },
  error => {
    const status = error.response?.status;
    switch (status) {
      case 401:
        if (isPublicPage()) {
          break;
        }
        message.error("未登录或登录已过期，请重新登录");
        localStorage.removeItem("access_token");
        if (window.location.pathname !== "/login") {
          const returnUrl = encodeURIComponent(
            window.location.pathname +
              window.location.search +
              window.location.hash
          );
          window.location.href = `/login?returnUrl=${returnUrl}`;
        }
        break;
      case 403:
        if (isPublicPage()) {
          break;
        }
        localStorage.removeItem("access_token");
        if (window.location.pathname !== "/login") {
          const returnUrl = encodeURIComponent(
            window.location.pathname +
              window.location.search +
              window.location.hash
          );
          window.location.href = `/login?returnUrl=${returnUrl}`;
        }
        break;
      case 404:
        message.error("请求的资源不存在");
        break;
      case 500:
        message.error("服务器异常，请稍后再试");
        break;
      default:
        message.error(error.response?.data?.message || "请求发生错误");
    }
    return Promise.reject(error);
  }
);

export default request;
