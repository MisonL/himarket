import { Card, Col, Row } from "antd";
import type { RefObject } from "react";

interface ChartConfig {
  key: string;
  title: string;
  ref: RefObject<HTMLDivElement | null>;
}

interface ModelDashboardChartsProps {
  cacheChartRef: RefObject<HTMLDivElement | null>;
  qpsChartRef: RefObject<HTMLDivElement | null>;
  ratelimitedChartRef: RefObject<HTMLDivElement | null>;
  rtChartRef: RefObject<HTMLDivElement | null>;
  successRateChartRef: RefObject<HTMLDivElement | null>;
  timeRangeLabel: string;
  tokenPerSecChartRef: RefObject<HTMLDivElement | null>;
}

function renderChartCard(chart: ChartConfig, timeRangeLabel: string) {
  return (
    <Card
      title={<span>{chart.title}</span>}
      extra={
        timeRangeLabel ? (
          <span className="text-xs text-gray-400">{timeRangeLabel}</span>
        ) : null
      }
    >
      <div ref={chart.ref} style={{ height: 300 }} />
    </Card>
  );
}

export function ModelDashboardCharts({
  cacheChartRef,
  qpsChartRef,
  ratelimitedChartRef,
  rtChartRef,
  successRateChartRef,
  timeRangeLabel,
  tokenPerSecChartRef,
}: ModelDashboardChartsProps) {
  const rows: ChartConfig[][] = [
    [
      { key: "qps", title: "QPS", ref: qpsChartRef },
      { key: "success", title: "请求成功率", ref: successRateChartRef },
    ],
    [
      { key: "token", title: "token消耗数/s", ref: tokenPerSecChartRef },
      { key: "rt", title: "请求平均RT/ms", ref: rtChartRef },
    ],
    [
      { key: "ratelimited", title: "限流请求数/s", ref: ratelimitedChartRef },
      { key: "cache", title: "缓存命中情况/s", ref: cacheChartRef },
    ],
  ];

  return (
    <>
      {rows.map((row, index) => (
        <Row gutter={16} className="mb-6" key={index}>
          {row.map(chart => (
            <Col span={12} key={chart.key}>
              {renderChartCard(chart, timeRangeLabel)}
            </Col>
          ))}
        </Row>
      ))}
    </>
  );
}
