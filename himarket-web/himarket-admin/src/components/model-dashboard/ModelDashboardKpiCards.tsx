import { Card, Col, Row } from "antd";
import type { KpiDataState } from "./types";

interface ModelDashboardKpiCardsProps {
  kpiData: KpiDataState;
  timeRangeLabel: string;
}

const KPI_ITEMS = [
  { key: "pv", label: "PV" },
  { key: "uv", label: "UV" },
  { key: "fallbackCount", label: "Fallback请求数" },
  { key: "inputToken", label: "输入Token数" },
  { key: "outputToken", label: "输出Token数" },
  { key: "totalToken", label: "Token总数" },
] as const;

export function ModelDashboardKpiCards({
  kpiData,
  timeRangeLabel,
}: ModelDashboardKpiCardsProps) {
  return (
    <Row gutter={16} className="mb-6">
      {KPI_ITEMS.map(item => (
        <Col span={4} key={item.key}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">{item.label}</div>
              {timeRangeLabel ? (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              ) : null}
            </div>
            <div className="text-center text-2xl font-medium">
              {kpiData[item.key]}
            </div>
          </Card>
        </Col>
      ))}
    </Row>
  );
}
