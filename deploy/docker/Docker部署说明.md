# AI 开放平台 Docker 部署指南

## 📋 项目说明

AI 开放平台包含四个服务组件：
- **mysql**: 数据库服务，为后端服务提供数据存储；
- **himarket-server**: 后端服务，运行在 8080 端口；
- **himarket-admin**: 管理后台界面，默认运行在 5174 端口，供管理员配置 Portal；
- **himarket-frontend**: 前台用户界面，默认运行在 5173 端口，供用户浏览和使用 API Product。

## 🚀 快速部署（推荐）

### 使用公开镜像部署

#### 1. 创建 docker-compose.yml 文件

```yaml
version: '3'
services:
  mysql:
    image: opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/mysql:latest
    container_name: mysql
    environment:
      - MYSQL_ROOT_PASSWORD=123456
      - MYSQL_DATABASE=portal_db
      - MYSQL_USER=portal_user
      - MYSQL_PASSWORD=portal_pass
    ports:
      - "3306:3306"
    volumes:
      - ./mysql/data:/var/lib/mysql
    restart: always

  himarket-server:
    image: opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-server:latest
    container_name: himarket-server
    environment:
      - DB_HOST=mysql
      - DB_PORT=3306
      - DB_NAME=portal_db
      - DB_USERNAME=portal_user
      - DB_PASSWORD=portal_pass
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    restart: always

  himarket-admin:
    image: opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-admin:latest
    container_name: himarket-admin
    environment:
      - HIMARKET_SERVER=http://himarket-server:8080
    ports:
      - "5174:8000"
    depends_on:
      - himarket-server
    restart: always

  himarket-frontend:
    image: opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-frontend:latest
    container_name: himarket-frontend
    environment:
      - HIMARKET_SERVER=http://himarket-server:8080
    ports:
      - "5173:8000"
    depends_on:
      - himarket-server
    restart: always
```

#### 2. 启动服务

```bash
# 在 docker-compose.yml 所在目录执行
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f
```

#### 3. 访问应用

如果在本机部署，可以访问：
- **管理后台**: http://localhost:5174
- **前台门户**: http://localhost:5173
- **后端**: http://localhost:8080

如果在其他机器上部署，可以访问：
- **管理后台**: http://your-admin-host:5174
- **前台门户**: http://your-front-host:5173
- **后端**: http://your-backend-host:8080

## ⚙️ 自定义配置

### 使用外置 MySQL 数据库

如果你已有 MySQL 数据库，可以移除内置 MySQL 服务：

#### 1. 修改 docker-compose.yml

```yaml
version: '3'
services:
  # 移除 mysql 服务配置

  himarket-server:
    image: opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-server:latest
    container_name: himarket-server
    environment:
      - DB_HOST=your-mysql-host        # 替换为你的数据库地址
      - DB_PORT=3306                   # 替换为你的数据库端口
      - DB_NAME=portal_db              # 替换为你的数据库名
      - DB_USERNAME=portal_user        # 替换为你的数据库用户名
      - DB_PASSWORD=your-password      # 替换为你的数据库密码
    ports:
      - "8080:8080"
    restart: always

  # admin 和 frontend 配置保持不变
```

#### 2. 重新启动

```bash
docker-compose down
docker-compose up -d
```

### 修改端口配置

如果需要修改访问端口，可以调整 ports 映射：

```yaml
# 将 frontend 改为 80 端口访问
himarket-frontend:
  ports:
    - "80:8000"    # 主机80端口 → 容器8000端口

# 将 admin 改为其他端口
himarket-admin:
  ports:
    - "8090:8000"  # 主机8090端口 → 容器8000端口
```

## 🔨 本地构建部署

### 构建镜像

```bash
# 进入项目根目录
cd /path/to/your/project

# 执行构建脚本
./build.sh
```

### 修改 docker-compose.yml

将镜像名称替换为本地构建的镜像：

```yaml
services:
  himarket-server:
    image: himarket-server:latest    # 替换为本地镜像

  himarket-admin:
    image: himarket-admin:latest     # 替换为本地镜像

  himarket-frontend:
    image: himarket-frontend:latest  # 替换为本地镜像
```

### 启动服务

```bash
docker-compose up -d
```
