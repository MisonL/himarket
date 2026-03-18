import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Card, Row, Col, Statistic, Button, message } from "antd";
import {
  ApiOutlined,
  GlobalOutlined,
  TeamOutlined,
  EditOutlined,
  CheckCircleFilled,
  MinusCircleFilled,
  CopyOutlined,
  ExclamationCircleFilled,
  ClockCircleFilled,
} from "@ant-design/icons";
import type { ApiProduct } from "@/types/api-product";
import { getServiceName, formatDateTime, copyToClipboard } from "@/lib/utils";
import { apiProductApi } from "@/lib/api";
import { getProductCategories } from "@/lib/productCategoryApi";
import type { ProductCategory } from "@/types/product-category";

interface ApiProductOverviewProps {
  apiProduct: ApiProduct;
  linkedService: any | null;
  onEdit: () => void;
}

export function ApiProductOverview({
  apiProduct,
  linkedService,
  onEdit,
}: ApiProductOverviewProps) {
  const navigate = useNavigate();
  const [portalCount, setPortalCount] = useState(0);
  const [subscriberCount, setSubscriberCount] = useState(0);
  const [productCategories, setProductCategories] = useState<ProductCategory[]>(
    []
  );

  useEffect(() => {
    if (apiProduct.productId) {
      fetchPublishedPortals();
      fetchProductCategories();
      fetchSubscriberCount();
    }
  }, [apiProduct.productId, apiProduct]);

  const fetchPublishedPortals = async () => {
    try {
      const res = await apiProductApi.getApiProductPublications(
        apiProduct.productId
      );
      setPortalCount(res.data.content?.length || 0);
    } catch (error) {
      console.error("获取门户发布信息失败:", error);
    }
  };

  const fetchSubscriberCount = async () => {
    try {
      const res = await apiProductApi.getProductSubscriptions(apiProduct.productId, { page: 0, size: 1 });
      setSubscriberCount(res.data.totalElements || 0);
    } catch (error) {
      console.error("获取订阅用户数失败:", error);
    }
  };

  const fetchProductCategories = async () => {
    try {
      const res = await apiProductApi.getProductCategories(apiProduct.productId);
      let categoriesData = res.data;

      if (!categoriesData || categoriesData.length === 0) {
        setProductCategories([]);
        return;
      }

      const allCategoriesRes = await getProductCategories();
      const allCategories = allCategoriesRes.data.content || allCategoriesRes.data || [];

      const categoriesWithNames = categoriesData.map((category: any) => {
        const categoryId = category.categoryId || category.id;
        const fullCategoryInfo = allCategories.find(
          (c: ProductCategory) => c.categoryId === categoryId || c.id === categoryId
        );
        return fullCategoryInfo || category;
      });

      setProductCategories(categoriesWithNames);
    } catch (error) {
      console.error("获取产品类别失败:", error);
      setProductCategories([]);
    }
  };

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold mb-2">概览</h1>
        <p className="text-gray-600">API产品概览</p>
      </div>

      <Card
        title="基本信息"
        extra={
          <Button type="primary" icon={<EditOutlined />} onClick={onEdit}>
            编辑
          </Button>
        }
      >
        <div className="space-y-4">
          <div className="grid grid-cols-6 gap-8 items-center">
            <span className="text-xs text-gray-600">产品名称:</span>
            <span className="col-span-2 text-xs text-gray-900 font-medium">
              {apiProduct.name}
            </span>
            <span className="text-xs text-gray-600">产品ID:</span>
            <div className="col-span-2 flex items-center gap-2">
              <span className="text-xs text-gray-700">{apiProduct.productId}</span>
              <CopyOutlined
                className="text-gray-400 hover:text-blue-600 cursor-pointer transition-colors"
                style={{ fontSize: "12px" }}
                onClick={() => {
                  copyToClipboard(apiProduct.productId);
                  message.success("产品ID已复制");
                }}
              />
            </div>
          </div>

          <div className="grid grid-cols-6 gap-8 items-center">
            <span className="text-xs text-gray-600">类型:</span>
            <span className="col-span-2 text-xs text-gray-900">
              {apiProduct.type === "REST_API"
                ? "REST API"
                : apiProduct.type === "AGENT_API"
                ? "Agent API"
                : apiProduct.type === "MODEL_API"
                ? "Model API"
                : apiProduct.type === "AGENT_SKILL"
                ? "Agent Skill"
                : "MCP Server"}
            </span>
            <span className="text-xs text-gray-600">状态:</span>
            <div className="col-span-2 flex items-center">
              {apiProduct.status === "PENDING" ? (
                <ExclamationCircleFilled className="text-yellow-500 mr-2" style={{ fontSize: "10px" }} />
              ) : apiProduct.status === "READY" ? (
                <ClockCircleFilled className="text-blue-500 mr-2" style={{ fontSize: "10px" }} />
              ) : (
                <CheckCircleFilled className="text-green-500 mr-2" style={{ fontSize: "10px" }} />
              )}
              <span className="text-xs text-gray-900">
                {apiProduct.status === "PENDING" ? "待配置" : apiProduct.status === "READY" ? "待发布" : "已发布"}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-6 gap-8 items-center">
            {apiProduct.type !== "AGENT_SKILL" && (
              <>
                <span className="text-xs text-gray-600">自动审批订阅:</span>
                <div className="col-span-2 flex items-center">
                  {apiProduct.autoApprove ? (
                    <CheckCircleFilled className="text-green-500 mr-2" style={{ fontSize: "10px" }} />
                  ) : (
                    <MinusCircleFilled className="text-gray-400 mr-2" style={{ fontSize: "10px" }} />
                  )}
                  <span className="text-xs text-gray-900">
                    {apiProduct.autoApprove ? "已开启" : "已关闭"}
                  </span>
                </div>
              </>
            )}
            <span className="text-xs text-gray-600">创建时间:</span>
            <span className={`col-span-2 text-xs text-gray-700 ${apiProduct.type === 'AGENT_SKILL' ? 'col-start-2' : ''}`}>
              {formatDateTime(apiProduct.createAt)}
            </span>
          </div>

          <div className="grid grid-cols-6 gap-8 items-center">
            <span className="text-xs text-gray-600">产品类别:</span>
            <div className="col-span-2 text-xs text-gray-900">
              {productCategories.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {productCategories.map((category, index) => (
                    <span key={category.categoryId}>
                      <span
                        className="text-gray-900 hover:text-blue-600 cursor-pointer hover:underline transition-colors"
                        onClick={() => navigate(`/product-categories/${category.categoryId}`)}
                      >
                        {category.name}
                      </span>
                      {index < productCategories.length - 1 && (
                        <span className="text-gray-400 mx-1">|</span>
                      )}
                    </span>
                  ))}
                </div>
              ) : (
                <span className="text-gray-400">-</span>
              )}
            </div>
            {apiProduct.type === "MODEL_API" && apiProduct.feature?.modelFeature && (
              <>
                <span className="text-xs text-gray-600">模型参数:</span>
                <div className="col-span-2 text-xs text-gray-700">
                  {[
                    apiProduct.feature.modelFeature.model,
                    apiProduct.feature.modelFeature.maxTokens && `${apiProduct.feature.modelFeature.maxTokens} tokens`,
                    apiProduct.feature.modelFeature.temperature !== null &&
                      apiProduct.feature.modelFeature.temperature !== undefined &&
                      `temp ${apiProduct.feature.modelFeature.temperature}`,
                    apiProduct.feature.modelFeature.webSearch && "联网搜索",
                    apiProduct.feature.modelFeature.enableThinking && "深度思考",
                    apiProduct.feature.modelFeature.enableMultiModal && "多模态",
                  ]
                    .filter(Boolean)
                    .map((param, index, array) => (
                      <span key={index}>
                        <span className="text-gray-900">{param}</span>
                        {index < array.length - 1 && <span className="text-gray-400 mx-1">|</span>}
                      </span>
                    ))}
                </div>
              </>
            )}
          </div>

          {apiProduct.description && (
            <div className="grid grid-cols-6 gap-8 pt-2">
              <span className="text-xs text-gray-600">描述:</span>
              <span className="col-span-5 text-xs text-gray-700 leading-relaxed">
                {apiProduct.description}
              </span>
            </div>
          )}
        </div>
      </Card>

      {apiProduct.type !== "AGENT_SKILL" && (
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} lg={8}>
            <Card
              className="cursor-pointer hover:shadow-md transition-shadow"
              onClick={() => navigate(`/api-products/detail?productId=${apiProduct.productId}&tab=portal`)}
            >
              <Statistic
                title="发布的门户"
                value={portalCount}
                prefix={<GlobalOutlined className="text-blue-500" />}
                valueStyle={{ color: "#1677ff", fontSize: "24px" }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={8}>
            <Card
              className="cursor-pointer hover:shadow-md transition-shadow"
              onClick={() => navigate(`/api-products/detail?productId=${apiProduct.productId}&tab=link-api`)}
            >
              <Statistic
                title="关联API"
                value={getServiceName(linkedService) || "未关联"}
                prefix={<ApiOutlined className="text-blue-500" />}
                valueStyle={{ color: "#1677ff", fontSize: "24px" }}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={8}>
            <Card className="hover:shadow-md transition-shadow">
              <Statistic
                title="订阅用户"
                value={subscriberCount}
                prefix={<TeamOutlined className="text-blue-500" />}
                valueStyle={{ color: "#1677ff", fontSize: "24px" }}
              />
            </Card>
          </Col>
        </Row>
      )}
    </div>
  );
}
