# 使用多阶段构建，确保环境纯净
FROM node:20-alpine AS builder

WORKDIR /build

# 复制依赖配置
COPY package*.json ./
RUN npm install

# 复制源代码并构建
COPY . .
RUN npm run build

# 第二阶段：运行环境
FROM higress-registry.cn-hangzhou.cr.aliyuncs.com/higress/nginx:alpine

COPY nginx.conf /etc/nginx/nginx.conf
COPY proxy.conf /etc/nginx/default.d/proxy.conf
COPY bin /home/admin/bin

WORKDIR /app

# 从构建阶段复制纯净的 dist
COPY --from=builder /build/dist/ /app

CMD ["/bin/sh", "/home/admin/bin/start.sh"]
